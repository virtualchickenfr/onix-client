const fs = require('fs');
const path = require('path');
const { Client } = require('minecraft-launcher-core');
const McHandler = require('minecraft-launcher-core/components/handler');
const { download, fetchJson } = require('./download');
const modrinth = require('./modrinth');

const versions = require('./versions');

// the default version; everything below takes the profile's version
const MC_VERSION = versions.DEFAULT;
const LOADER_VERSION = versions.get(MC_VERSION).loader;
const VERSION_ID = versions.versionId(MC_VERSION);

/** Mods the launcher fetches from Modrinth and keeps up to date. */
const MANAGED_MODS = [
  { project: 'fabric-api', id: 'fabric-api', name: 'Fabric API', always: true },
  // performance pack (Profile → Performance mods): faster rendering, less memory, faster logic
  { project: 'sodium', id: 'sodium', name: 'Sodium', performance: true },
  { project: 'lithium', id: 'lithium', name: 'Lithium', performance: true },
  { project: 'ferrite-core', id: 'ferritecore', name: 'FerriteCore', performance: true },
  { project: 'immediatelyfast', id: 'immediatelyfast', name: 'ImmediatelyFast', performance: true },
  { project: 'entityculling', id: 'entityculling', name: 'EntityCulling', performance: true },
];

const MANIFEST = '.onix-managed.json';

/** Fabric API and Sodium are checked for updates at most this often instead of on every launch. */
const UPDATE_CHECK_INTERVAL = 12 * 60 * 60 * 1000;

/**
 * JVM flags for a quick start and smooth frames: G1 tuned for a game client, and a class-data
 * sharing archive that Java 21 creates on the first start and reuses afterwards (faster class loading).
 */
function jvmArgs(root, fastStart, mc) {
  const args = [
    '-XX:+UseG1GC',
    '-XX:+ParallelRefProcEnabled',
    '-XX:MaxGCPauseMillis=50',
    '-XX:+UnlockExperimentalVMOptions',
    '-XX:G1NewSizePercent=20',
    '-XX:G1ReservePercent=20',
    '-XX:G1HeapRegionSize=16M',
    '-XX:+DisableExplicitGC',
  ];
  // class-data sharing archives need Java 19+ (Minecraft before 1.20.5 runs on Java 17 or 8)
  if (fastStart && versions.javaMajor(mc) >= 21) {
    const cache = path.join(root, 'cache');
    fs.mkdirSync(cache, { recursive: true });
    args.push('-XX:+AutoCreateSharedArchive', `-XX:SharedArchiveFile=${path.join(cache, `onix-${mc}.jsa`)}`);
  }
  return args;
}

function readManifest(modsDir) {
  try {
    return JSON.parse(fs.readFileSync(path.join(modsDir, MANIFEST), 'utf8'));
  } catch {
    return {};
  }
}

function writeManifest(modsDir, data) {
  fs.writeFileSync(path.join(modsDir, MANIFEST), JSON.stringify(data, null, 2));
}

/*
 * minecraft-launcher-core hashes every asset (about 4,000 files) on every start, which takes from several
 * seconds to minutes on a slow disk. After one start that checked everything, later starts only check
 * that the files are there; "Fast start" off in the settings (or a crash right at start) checks everything again.
 */
const verifiedMarker = (root, versionId) => path.join(root, 'cache', `verified-${versionId}.json`);
let trustFilesThatExist = false;
const originalCheckSum = McHandler.prototype.checkSum;
McHandler.prototype.checkSum = function checkSum(hash, file) {
  if (trustFilesThatExist) {
    try {
      return Promise.resolve(fs.statSync(file).size > 0);
    } catch {
      return Promise.resolve(false);
    }
  }
  return originalCheckSum.call(this, hash, file);
};

function filesVerified(root, versionId) {
  try {
    return JSON.parse(fs.readFileSync(verifiedMarker(root, versionId), 'utf8')).ok === true;
  } catch {
    return false;
  }
}

function setFilesVerified(root, versionId, ok) {
  const file = verifiedMarker(root, versionId);
  try {
    if (ok) {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, JSON.stringify({ ok: true, at: Date.now() }));
    } else {
      fs.rmSync(file, { force: true });
    }
  } catch {
    // only a speed-up
  }
}

/** Creates the Fabric version profile that MCLC launches as a "custom" version. */
async function ensureFabric(root, report, mc = MC_VERSION) {
  const v = versions.get(mc);
  const id = versions.versionId(mc);
  const file = path.join(root, 'versions', id, `${id}.json`);
  if (fs.existsSync(file)) return;
  report('Setting up Fabric', 0);
  const profile = await fetchJson(
    `https://meta.fabricmc.net/v2/versions/loader/${v.mc}/${v.loader}/profile/json`
  );
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(profile, null, 2));
}

/** Finds the bundled Onix mod for a Minecraft version (packaged or in the development folder). */
function findBundledMod(mc = MC_VERSION) {
  const devDir = mc === versions.DEFAULT ? 'mod' : `mod-${mc}`;
  const candidates = [
    process.resourcesPath ? path.join(process.resourcesPath, 'mods', mc) : null,
    // launchers built before multi-version support kept the 1.21.1 jar directly in mods/
    process.resourcesPath && mc === versions.DEFAULT ? path.join(process.resourcesPath, 'mods') : null,
    path.join(__dirname, '..', '..', devDir, 'build', 'libs'),
    // versions generated by tools/port.py
    path.join(__dirname, '..', '..', 'ports', 'build', `mod-${mc}`, 'build', 'libs'),
  ].filter(Boolean);
  for (const dir of candidates) {
    if (!fs.existsSync(dir)) continue;
    const jar = fs
      .readdirSync(dir)
      .find((f) => /^onix-client-.*\.jar$/.test(f) && !f.endsWith('-sources.jar'));
    if (jar) return path.join(dir, jar);
  }
  return null;
}

async function ensureMods(root, settings, report, log, throttle = true, mc = MC_VERSION) {
  const modsDir = path.join(root, 'mods');
  fs.mkdirSync(modsDir, { recursive: true });

  // 1. Onix Client mod
  const bundled = findBundledMod(mc);
  if (bundled) {
    const name = path.basename(bundled);
    for (const f of fs.readdirSync(modsDir)) {
      if (/^onix-client-.*\.jar(\.disabled)?$/.test(f) && f !== name) fs.rmSync(path.join(modsDir, f));
    }
    const target = path.join(modsDir, name);
    if (!fs.existsSync(target) || fs.statSync(target).size !== fs.statSync(bundled).size) {
      fs.copyFileSync(bundled, target);
      log(`Installed Onix Client mod: ${name}`);
    }
  } else {
    log(versions.hasOnix(mc)
      ? `Warning: Onix mod for ${mc} not found. Run "gradlew build" in the mod folder first.`
      : `Onix is not available for Minecraft ${mc} yet – starting with Fabric API and the performance pack.`);
    // an Onix jar from another version would stop Fabric
    for (const f of fs.readdirSync(modsDir)) {
      if (/^onix-client-.*\.jar$/.test(f)) fs.renameSync(path.join(modsDir, f), path.join(modsDir, f + '.disabled'));
    }
  }

  // 2. Fabric API and performance mods from Modrinth (all checked at the same time)
  const manifest = readManifest(modsDir);
  const managedFiles = new Set(MANAGED_MODS.map((m) => manifest[m.project]).filter(Boolean));
  // mods the player added themselves: never download a second copy of those (two copies stop Fabric)
  const { readFabricModJson, flushCache } = require('./importer');
  const ownIds = new Set();
  for (const f of fs.readdirSync(modsDir)) {
    if (!f.endsWith('.jar') || managedFiles.has(f) || /^onix-client-/.test(f)) continue;
    const info = await readFabricModJson(path.join(modsDir, f));
    if (info && info.id) ownIds.add(info.id);
  }
  flushCache();
  await Promise.all(MANAGED_MODS.map(async (mod) => {
    const wanted = (mod.always || (mod.performance && settings.performanceMods)) && !ownIds.has(mod.id);
    const old = manifest[mod.project];
    if (!wanted) {
      if (old) {
        fs.rmSync(path.join(modsDir, old), { force: true });
        delete manifest[mod.project];
        log(ownIds.has(mod.id) ? `${mod.name}: using your own copy` : `Removed ${mod.name}`);
      }
      return;
    }
    const checked = (manifest._checked || {})[mod.project] || 0;
    if (throttle && old && fs.existsSync(path.join(modsDir, old)) && Date.now() - checked < UPDATE_CHECK_INTERVAL) return;
    try {
      report(`Checking ${mod.name}`, 0);
      const query = `game_versions=${encodeURIComponent(JSON.stringify([mc]))}&loaders=${encodeURIComponent(JSON.stringify(['fabric']))}`;
      const versions = await fetchJson(`https://api.modrinth.com/v2/project/${mod.project}/version?${query}`);
      const version = versions.find((v) => v.version_type === 'release') || versions[0];
      if (!version) throw new Error('no matching version');
      const file = version.files.find((f) => f.primary) || version.files[0];
      const filename = modrinth.safeFileName(file.filename);
      manifest._checked = { ...(manifest._checked || {}), [mod.project]: Date.now() };
      if (old === filename && fs.existsSync(path.join(modsDir, filename))) return;
      // respect a disabled copy
      if (old && fs.existsSync(path.join(modsDir, old + '.disabled'))) return;
      const target = path.join(modsDir, filename);
      await download(file.url, target, (p) => report(`Downloading ${mod.name}`, p));
      try {
        modrinth.verify(target, file.hashes);
      } catch (err) {
        fs.rmSync(target, { force: true });
        throw err;
      }
      if (old && old !== filename) fs.rmSync(path.join(modsDir, old), { force: true });
      manifest[mod.project] = filename;
      log(`Installed ${mod.name} ${version.version_number}`);
    } catch (err) {
      log(`Could not update ${mod.name}: ${err.message}`);
      if (mod.always && !old) throw err;
    }
  }));
  writeManifest(modsDir, manifest);
}

/** Profiles with another loader cannot load the Onix (Fabric) mod: switch its jar off. */
function disableOnixFor(gameDir, log) {
  const modsDir = path.join(gameDir, 'mods');
  if (!fs.existsSync(modsDir)) return;
  for (const f of fs.readdirSync(modsDir)) {
    if (/^onix-client-.*\.jar$/.test(f)) {
      fs.renameSync(path.join(modsDir, f), path.join(modsDir, `${f}.disabled`));
      log(`The Onix mod needs Fabric – switched off ${f} for this profile.`);
    }
  }
}

/**
 * Splits the profile's extra Java arguments like a shell would (quotes keep spaces). Memory flags are left to the
 * RAM slider and nothing but JVM options (starting with -) is passed on.
 */
function extraJvmArgs(text) {
  const out = [];
  const re = /"([^"]*)"|'([^']*)'|(\S+)/g;
  let m;
  while ((m = re.exec(String(text || '')))) out.push(m[1] ?? m[2] ?? m[3]);
  return out.filter((a) => a.startsWith('-') && !/^-Xm[sx]/i.test(a)).slice(0, 64);
}

/**
 * Launches Minecraft. root holds shared files (versions, libraries, assets),
 * gameDir is the profile folder (mods, worlds, settings).
 */
async function launch({ root, gameDir, auth, javaPath, settings, world = null, server = null, fastStart = true, report, log, onStart, onClose }) {
  const prepStarted = Date.now();
  const mc = versions.ofProfile(settings).mc;
  const VERSION_ID = versions.versionId(mc);
  log(`Minecraft ${mc} with Fabric Loader ${versions.get(mc).loader}`);
  // a profile's mods are for one version: a profile moved to another version keeps a note of it
  const marker = path.join(gameDir, 'mods', '.onix-version');
  try {
    const before = fs.existsSync(marker) ? fs.readFileSync(marker, 'utf8').trim() : null;
    if (before && before !== mc) log(`This profile was on ${before} before – mods made only for ${before} may not load.`);
    fs.mkdirSync(path.dirname(marker), { recursive: true });
    fs.writeFileSync(marker, mc);
  } catch {
    // only a note
  }
  const loader = settings.loader || 'fabric';
  let VERSION_ID_RUN = VERSION_ID;
  if (loader === 'fabric') {
    await Promise.all([ensureFabric(root, report, mc), ensureMods(gameDir, settings, report, log, fastStart, mc)]);
    log(`Fabric and mods ready after ${((Date.now() - prepStarted) / 1000).toFixed(1)} s`);
  } else {
    VERSION_ID_RUN = await require('./loaders').install({ loader, game: mc, version: settings.loaderVersion, root, javaPath, report, log });
    disableOnixFor(gameDir, log);
    log(`${loader} ready (${VERSION_ID_RUN || 'vanilla'}) after ${((Date.now() - prepStarted) / 1000).toFixed(1)} s`);
  }

  const launcher = new Client();
  const options = {
    root,
    authorization: auth,
    javaPath,
    version: VERSION_ID_RUN ? { number: mc, type: 'release', custom: VERSION_ID_RUN } : { number: mc, type: 'release' },
    memory: { max: `${settings.ramMb}M`, min: `${Math.min(settings.ramMb, 2048)}M` },
    customArgs: [...jvmArgs(root, fastStart, mc), ...extraJvmArgs(settings.jvmArgs)],
    overrides: { maxSockets: 24, gameDirectory: gameDir },
  };
  if (settings.fullscreen) options.window = { fullscreen: true };
  else if (settings.width > 0 && settings.height > 0) options.window = { width: settings.width, height: settings.height };
  if (world && fs.existsSync(path.join(gameDir, 'saves', world, 'level.dat'))) {
    // straight into a singleplayer world (Quick Play, Minecraft 1.20 and newer)
    options.quickPlay = { type: 'singleplayer', identifier: world };
    log(`Opening the world "${world}"`);
  } else if (server) {
    options.quickPlay = { type: 'multiplayer', identifier: server };
  } else if (settings.quickJoin && settings.quickJoin.trim()) {
    options.quickPlay = { type: 'multiplayer', identifier: settings.quickJoin.trim() };
  }

  let started = false;
  const VERIFY_ID = VERSION_ID_RUN || `vanilla-${mc}`;
  const quick = fastStart && filesVerified(root, VERIFY_ID);
  trustFilesThatExist = quick;
  log(quick ? 'Game files were checked before: only looking for missing files' : 'Checking all game files (first start or Fast start off)');
  launcher.on('debug', (line) => log(String(line)));
  launcher.on('data', (line) => {
    if (!started) {
      started = true;
      trustFilesThatExist = false;
      setFilesVerified(root, VERIFY_ID, true);
      onStart();
    }
    log(String(line).trimEnd());
  });
  launcher.on('progress', (e) => {
    if (e && e.total) report(`Downloading Minecraft (${e.type})`, e.task / e.total);
  });
  const launchedAt = Date.now();
  launcher.on('close', (code) => {
    trustFilesThatExist = false;
    // a crash within the first seconds may be a damaged file: check everything next time
    if (code !== 0 && Date.now() - launchedAt < 60_000) setFilesVerified(root, VERIFY_ID, false);
    onClose(code);
  });

  report('Starting Minecraft', 1);
  let proc;
  try {
    proc = await launcher.launch(options);
  } finally {
    trustFilesThatExist = false;
  }
  if (!proc) throw new Error('Minecraft could not be started (see the console).');
  return proc;
}

/**
 * Downloads everything a profile needs (Minecraft, libraries, assets, natives, Fabric and the mods) without
 * playing, so its first start is as quick as any later one. Minecraft Launcher Core is run as for a real start,
 * but the JVM gets -version, which prints the Java version and exits before the game would load.
 */
async function prepare({ root, gameDir, javaPath, settings, report, log }) {
  const mc = versions.ofProfile(settings).mc;
  const VERSION_ID = versions.versionId(mc);
  const loader = settings.loader || 'fabric';
  if (loader !== 'fabric') return; // other loaders are set up on the first start (Forge needs its installer)
  await Promise.all([ensureFabric(root, report, mc), ensureMods(gameDir, settings, report, log, true, mc)]);
  if (filesVerified(root, VERSION_ID)) return;
  const launcher = new Client();
  launcher.on('progress', (e) => {
    if (e && e.total) report(`Downloading Minecraft (${e.type})`, e.task / e.total);
  });
  launcher.on('debug', (line) => log(String(line)));
  const closed = new Promise((resolve) => launcher.on('close', resolve));
  const proc = await launcher.launch({
    root,
    authorization: { access_token: '0', client_token: '0', uuid: '00000000000000000000000000000000', name: 'Player', user_properties: '{}', meta: { type: 'msa' } },
    javaPath,
    version: { number: mc, type: 'release', custom: VERSION_ID },
    memory: { max: '512M', min: '256M' },
    customArgs: ['-version'],
    overrides: { maxSockets: 24, gameDirectory: gameDir },
  });
  if (!proc) throw new Error('The game files could not be downloaded (see the console).');
  const code = await closed;
  if (code === 0) setFilesVerified(root, VERSION_ID, true);
}

/**
 * After an import: if the pack brought its own Fabric API or Sodium, let the launcher manage that
 * file instead of downloading a second copy (two copies of a mod stop Fabric from starting).
 */
async function adoptManagedMods(root) {
  const { readFabricModJson } = require('./importer');
  const modsDir = path.join(root, 'mods');
  if (!fs.existsSync(modsDir)) return;
  const manifest = readManifest(modsDir);
  for (const f of fs.readdirSync(modsDir).filter((n) => n.endsWith('.jar'))) {
    const info = await readFabricModJson(path.join(modsDir, f));
    const id = info && info.id;
    const managed = MANAGED_MODS.find((m) => m.id === id);
    if (managed) manifest[managed.project] = f;
  }
  writeManifest(modsDir, manifest);
}

/** Every mod in the mods folder, for the Mods page. */
function listMods(root) {
  const modsDir = path.join(root, 'mods');
  if (!fs.existsSync(modsDir)) return [];
  const manifest = readManifest(modsDir);
  const managed = new Set(Object.entries(manifest).filter(([k]) => !k.startsWith('_')).map(([, v]) => v));
  return fs
    .readdirSync(modsDir)
    .filter((f) => f.endsWith('.jar') || f.endsWith('.jar.disabled'))
    .map((f) => {
      const enabled = f.endsWith('.jar');
      const base = enabled ? f : f.slice(0, -'.disabled'.length);
      return {
        file: f,
        name: base.replace(/\.jar$/, ''),
        enabled,
        core: /^onix-client-/.test(base) || base === manifest['fabric-api'],
        managed: managed.has(base),
      };
    })
    .sort((a, b) => Number(b.core) - Number(a.core) || a.name.localeCompare(b.name));
}

function toggleMod(root, file) {
  const modsDir = path.join(root, 'mods');
  const src = path.join(modsDir, path.basename(file));
  if (!fs.existsSync(src)) return;
  const dest = src.endsWith('.disabled') ? src.slice(0, -'.disabled'.length) : src + '.disabled';
  fs.renameSync(src, dest);
}

module.exports = {
  extraJvmArgs, prepare, launch, listMods, toggleMod, adoptManagedMods, ensureFabric, findBundledMod, MC_VERSION, LOADER_VERSION, VERSION_ID };
