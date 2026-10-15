const fs = require('fs');
const path = require('path');
const { Client } = require('minecraft-launcher-core');
const { download, fetchJson } = require('./download');
const modrinth = require('./modrinth');

const MC_VERSION = '1.21.1';
const LOADER_VERSION = '0.16.5';
const VERSION_ID = `fabric-loader-${LOADER_VERSION}-${MC_VERSION}`;

/** Mods the launcher fetches from Modrinth and keeps up to date. */
const MANAGED_MODS = [
  { project: 'fabric-api', name: 'Fabric API', always: true },
  { project: 'sodium', name: 'Sodium', performance: true },
];

const MANIFEST = '.onix-managed.json';

/** Fabric API and Sodium are checked for updates at most this often instead of on every launch. */
const UPDATE_CHECK_INTERVAL = 12 * 60 * 60 * 1000;

/**
 * JVM flags for a quick start and smooth frames: G1 tuned for a game client, and a class-data
 * sharing archive that Java 21 creates on the first start and reuses afterwards (faster class loading).
 */
function jvmArgs(root, fastStart) {
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
  if (fastStart) {
    const cache = path.join(root, 'cache');
    fs.mkdirSync(cache, { recursive: true });
    args.push('-XX:+AutoCreateSharedArchive', `-XX:SharedArchiveFile=${path.join(cache, 'onix-1.21.1.jsa')}`);
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

/** Creates the Fabric version profile that MCLC launches as a "custom" version. */
async function ensureFabric(root, report) {
  const file = path.join(root, 'versions', VERSION_ID, `${VERSION_ID}.json`);
  if (fs.existsSync(file)) return;
  report('Setting up Fabric', 0);
  const profile = await fetchJson(
    `https://meta.fabricmc.net/v2/versions/loader/${MC_VERSION}/${LOADER_VERSION}/profile/json`
  );
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(profile, null, 2));
}

/** Finds the bundled Onix mod (packaged or in the development folder). */
function findBundledMod() {
  const candidates = [
    process.resourcesPath ? path.join(process.resourcesPath, 'mods') : null,
    path.join(__dirname, '..', '..', 'mod', 'build', 'libs'),
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

async function ensureMods(root, settings, report, log, throttle = true) {
  const modsDir = path.join(root, 'mods');
  fs.mkdirSync(modsDir, { recursive: true });

  // 1. Onix Client mod
  const bundled = findBundledMod();
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
    log('Warning: Onix mod not found. Run "gradlew build" in the mod folder first.');
  }

  // 2. Fabric API and performance mods from Modrinth
  const manifest = readManifest(modsDir);
  for (const mod of MANAGED_MODS) {
    const wanted = mod.always || (mod.performance && settings.performanceMods);
    const old = manifest[mod.project];
    if (!wanted) {
      if (old) {
        fs.rmSync(path.join(modsDir, old), { force: true });
        delete manifest[mod.project];
        log(`Removed ${mod.name}`);
      }
      continue;
    }
    const checked = (manifest._checked || {})[mod.project] || 0;
    if (throttle && old && fs.existsSync(path.join(modsDir, old)) && Date.now() - checked < UPDATE_CHECK_INTERVAL) continue;
    try {
      report(`Checking ${mod.name}`, 0);
      const query = `game_versions=${encodeURIComponent(JSON.stringify([MC_VERSION]))}&loaders=${encodeURIComponent(JSON.stringify(['fabric']))}`;
      const versions = await fetchJson(`https://api.modrinth.com/v2/project/${mod.project}/version?${query}`);
      const version = versions.find((v) => v.version_type === 'release') || versions[0];
      if (!version) throw new Error('no matching version');
      const file = version.files.find((f) => f.primary) || version.files[0];
      const filename = modrinth.safeFileName(file.filename);
      manifest._checked = { ...(manifest._checked || {}), [mod.project]: Date.now() };
      if (old === filename && fs.existsSync(path.join(modsDir, filename))) continue;
      // respect a disabled copy
      if (old && fs.existsSync(path.join(modsDir, old + '.disabled'))) continue;
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
  }
  writeManifest(modsDir, manifest);
}

/**
 * Launches Minecraft. root holds shared files (versions, libraries, assets),
 * gameDir is the profile folder (mods, worlds, settings).
 */
async function launch({ root, gameDir, auth, javaPath, settings, fastStart = true, report, log, onStart, onClose }) {
  const prepStarted = Date.now();
  await Promise.all([ensureFabric(root, report), ensureMods(gameDir, settings, report, log, fastStart)]);
  log(`Fabric and mods ready after ${((Date.now() - prepStarted) / 1000).toFixed(1)} s`);

  const launcher = new Client();
  const options = {
    root,
    authorization: auth,
    javaPath,
    version: { number: MC_VERSION, type: 'release', custom: VERSION_ID },
    memory: { max: `${settings.ramMb}M`, min: `${Math.min(settings.ramMb, 2048)}M` },
    customArgs: jvmArgs(root, fastStart),
    overrides: { maxSockets: 24, gameDirectory: gameDir },
  };
  if (settings.quickJoin && settings.quickJoin.trim()) {
    options.quickPlay = { type: 'multiplayer', identifier: settings.quickJoin.trim() };
  }

  let started = false;
  launcher.on('debug', (line) => log(String(line)));
  launcher.on('data', (line) => {
    if (!started) {
      started = true;
      onStart();
    }
    log(String(line).trimEnd());
  });
  launcher.on('progress', (e) => {
    if (e && e.total) report(`Downloading Minecraft (${e.type})`, e.task / e.total);
  });
  launcher.on('close', (code) => onClose(code));

  report('Starting Minecraft', 1);
  const proc = await launcher.launch(options);
  if (!proc) throw new Error('Minecraft could not be started (see the console).');
  return proc;
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
    if (id === 'fabric-api' || id === 'sodium') manifest[id] = f;
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

module.exports = { launch, listMods, toggleMod, adoptManagedMods, ensureFabric, findBundledMod, MC_VERSION, LOADER_VERSION, VERSION_ID };
