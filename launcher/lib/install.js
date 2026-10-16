/**
 * Getting Onix into Minecraft installations outside the Onix Launcher.
 *
 *  - running():   finds Minecraft processes that are running right now (version, folder, Onix or not)
 *  - targets():   finds other launchers' installations (official launcher, Prism, MultiMC, Modrinth App, …)
 *  - install():   copies Onix (+ Fabric API) and your Onix profiles into an installation
 *  - pushProfile(): sends a profile to a running game that already has Onix; the mod loads it live
 *  - exportMod(): saves the mod jar (+ Fabric API) into a folder of your choice
 *
 * Onix is never injected into a running game: a Fabric mod has to be present when the game starts.
 */

const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const { download, fetchJson } = require('./download');
const game = require('./game');
const versions = require('./versions');
const importer = require('./importer');
const modrinth = require('./modrinth');

const ONIX_JAR = /^onix-client-.*\.jar$/i;

const exists = (p) => { try { fs.accessSync(p); return true; } catch { return false; } };
const isDir = (p) => { try { return fs.statSync(p).isDirectory(); } catch { return false; } };
const list = (p) => { try { return fs.readdirSync(p, { withFileTypes: true }); } catch { return []; } };
const readJson = (p) => { try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch { return null; } };

function hasOnix(gameDir) {
  return list(path.join(gameDir, 'mods')).some((e) => e.isFile() && ONIX_JAR.test(e.name));
}

function hasFabricApi(gameDir) {
  return list(path.join(gameDir, 'mods')).some((e) => e.isFile() && /^fabric-api-.*\.jar$/i.test(e.name));
}

// ------------------------------------------------------------------ running games

function run(cmd, args) {
  return new Promise((resolve) => {
    execFile(cmd, args, { windowsHide: true, maxBuffer: 16 * 1024 * 1024, timeout: 8000 }, (err, stdout) => resolve(err ? '' : String(stdout)));
  });
}

async function processes() {
  if (process.platform === 'win32') {
    const out = await run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
      "Get-CimInstance Win32_Process -Filter \"Name like 'java%'\" | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"]);
    if (!out.trim()) return [];
    try {
      const parsed = JSON.parse(out);
      return [].concat(parsed).filter((p) => p && p.CommandLine).map((p) => ({ pid: p.ProcessId, cmd: p.CommandLine }));
    } catch {
      return [];
    }
  }
  const out = await run('ps', ['-axww', '-o', 'pid=,command=']);
  return out.split('\n').map((line) => {
    const m = line.trim().match(/^(\d+)\s+(.*)$/);
    return m ? { pid: Number(m[1]), cmd: m[2] } : null;
  }).filter(Boolean);
}

/** Value of a "--name value" argument; paths with spaces run until the next " --". */
function arg(cmd, name) {
  const i = cmd.indexOf(`--${name} `);
  if (i < 0) return null;
  let rest = cmd.slice(i + name.length + 3);
  if (rest.startsWith('"')) return rest.slice(1, rest.indexOf('"', 1));
  const next = rest.search(/\s--[a-zA-Z]/);
  if (next >= 0) rest = rest.slice(0, next);
  return rest.trim();
}

async function running(onixGameRoot) {
  const found = [];
  for (const p of await processes()) {
    const cmd = p.cmd;
    if (p.pid === process.pid) continue;
    const executable = cmd.split(/\s-/)[0];
    const isMinecraft = /java(w)?(\.exe)?"?$|\/java\s*$|javaw?\.exe/i.test(executable.trim())
      && /net\.minecraft\.client\.main\.Main|net\.fabricmc\.loader\.impl\.launch\.knot\.KnotClient|--accessToken/.test(cmd)
      && /--version\s/.test(cmd);
    if (!isMinecraft) continue;
    const gameDir = arg(cmd, 'gameDir');
    const version = arg(cmd, 'version') || 'unknown';
    const fabric = /fabric|KnotClient/i.test(cmd);
    const mc = (version.match(/(\d+\.\d+(?:\.\d+)?)(?!.*\d+\.\d+)/) || [])[1] || version;
    found.push({
      pid: p.pid,
      version,
      minecraft: mc,
      fabric,
      gameDir,
      user: arg(cmd, 'username'),
      onix: gameDir ? hasOnix(gameDir) : false,
      ownLauncher: !!(gameDir && onixGameRoot && path.resolve(gameDir).startsWith(path.resolve(onixGameRoot))),
      compatible: fabric && versions.isSupported(mc),
    });
  }
  return found;
}

// ------------------------------------------------------------------ other launchers

/** Minecraft version and loader of an instance, from the launcher's own instance file. */
function instanceInfo(instanceDir) {
  const prism = readJson(path.join(instanceDir, 'mmc-pack.json'));
  if (prism && Array.isArray(prism.components)) {
    const mc = prism.components.find((c) => c.uid === 'net.minecraft');
    const fabric = prism.components.some((c) => c.uid === 'net.fabricmc.fabric-loader');
    return { minecraft: mc && mc.version, fabric };
  }
  const modrinth = readJson(path.join(instanceDir, 'profile.json'));
  if (modrinth && (modrinth.game_version || (modrinth.metadata && modrinth.metadata.game_version))) {
    const meta = modrinth.metadata || modrinth;
    return { minecraft: meta.game_version, fabric: String(meta.loader || '').toLowerCase() === 'fabric' };
  }
  const curse = readJson(path.join(instanceDir, 'minecraftinstance.json'));
  if (curse && curse.gameVersion) {
    const loader = curse.baseModLoader && curse.baseModLoader.name ? curse.baseModLoader.name : '';
    return { minecraft: curse.gameVersion, fabric: /fabric/i.test(loader) };
  }
  const atl = readJson(path.join(instanceDir, 'instance.json'));
  if (atl && atl.id) {
    const loader = atl.launcher && atl.launcher.loaderVersion ? atl.launcher.loaderVersion.type : '';
    return { minecraft: atl.id, fabric: /fabric/i.test(String(loader)) };
  }
  return { minecraft: null, fabric: null };
}

function targets() {
  const out = [];
  const { minecraft } = importer.dirs();
  if (isDir(minecraft)) {
    const dir = path.join(minecraft, 'onix-client');
    out.push({
      id: 'official',
      client: 'minecraft',
      name: 'Minecraft Launcher',
      label: 'New "Onix Client" installation',
      gameDir: dir,
      root: minecraft,
      minecraft: game.MC_VERSION,
      fabric: true,
      onix: hasOnix(dir),
      official: true,
      note: 'Adds Fabric and an "Onix Client" installation to the official launcher.',
    });
  }
  for (const src of importer.knownSources()) {
    if (!src.instances) continue;
    for (const root of src.instances) {
      for (const e of list(root)) {
        if (!e.isDirectory()) continue;
        const instanceDir = path.join(root, e.name);
        const gameDir = importer.gameDirOfInstance(instanceDir) || path.join(instanceDir, '.minecraft');
        const info = instanceInfo(instanceDir);
        const compatible = versions.isSupported(info.minecraft) && info.fabric === true;
        let note = null;
        if (info.minecraft && !versions.isSupported(info.minecraft)) note = `Minecraft ${info.minecraft} – Onix runs on ${versions.LIST.join(' and ')}`;
        else if (info.fabric === false) note = 'Not a Fabric instance';
        else if (!info.minecraft) note = `Version unknown – make sure it is Fabric ${versions.LIST.join(' or ')}`;
        out.push({
          id: `${src.id}:${e.name}`,
          client: src.id,
          name: src.name,
          label: e.name,
          gameDir,
          minecraft: info.minecraft,
          fabric: info.fabric,
          onix: hasOnix(gameDir),
          compatible,
          note,
        });
      }
    }
  }
  return out;
}

// ------------------------------------------------------------------ installing

async function fabricApiFile(mc = versions.DEFAULT) {
  const query = `game_versions=${encodeURIComponent(JSON.stringify([mc]))}&loaders=${encodeURIComponent(JSON.stringify(['fabric']))}`;
  const list = await fetchJson(`https://api.modrinth.com/v2/project/fabric-api/version?${query}`);
  const version = list.find((v) => v.version_type === 'release') || list[0];
  if (!version) throw new Error(`No Fabric API for Minecraft ${mc} found`);
  return version.files.find((f) => f.primary) || version.files[0];
}

function copyOnixJar(modsDir, mc = versions.DEFAULT) {
  const bundled = game.findBundledMod(mc);
  if (!bundled) throw new Error('The Onix mod is missing from this launcher build. Run "gradlew build" in the mod folder first.');
  fs.mkdirSync(modsDir, { recursive: true });
  for (const e of list(modsDir)) {
    if (e.isFile() && ONIX_JAR.test(e.name.replace(/\.disabled$/, ''))) fs.rmSync(path.join(modsDir, e.name));
  }
  fs.copyFileSync(bundled, path.join(modsDir, path.basename(bundled)));
  return path.basename(bundled);
}

/** Copies Onix profiles, client settings and cosmetics from a launcher profile into a game folder. */
function copyOnixConfig(fromGameDir, toGameDir, { overwriteClient = false } = {}) {
  const from = path.join(fromGameDir, 'config', 'onix');
  const to = path.join(toGameDir, 'config', 'onix');
  if (!isDir(from)) return { profiles: [], active: null };
  fs.mkdirSync(path.join(to, 'profiles'), { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const backupDir = path.join(to, 'backups', stamp);
  const backup = (file) => {
    if (!exists(file)) return;
    fs.mkdirSync(backupDir, { recursive: true });
    fs.copyFileSync(file, path.join(backupDir, path.basename(file)));
  };
  const profiles = [];
  for (const e of list(path.join(from, 'profiles'))) {
    if (!e.isFile() || !e.name.endsWith('.json')) continue;
    const target = path.join(to, 'profiles', e.name);
    backup(target);
    fs.copyFileSync(path.join(from, 'profiles', e.name), target);
    profiles.push(e.name.slice(0, -5));
  }
  // keybind profiles (Keybind Profiles module) travel with the Onix profiles
  for (const e of list(path.join(from, 'keybinds'))) {
    if (!e.isFile() || !e.name.endsWith('.json') || e.name.startsWith('_')) continue;
    fs.mkdirSync(path.join(to, 'keybinds'), { recursive: true });
    const target = path.join(to, 'keybinds', e.name);
    backup(target);
    fs.copyFileSync(path.join(from, 'keybinds', e.name), target);
  }
  if (exists(path.join(from, 'cosmetics.json'))) {
    backup(path.join(to, 'cosmetics.json'));
    fs.copyFileSync(path.join(from, 'cosmetics.json'), path.join(to, 'cosmetics.json'));
  }
  if (exists(path.join(from, 'client.json')) && (overwriteClient || !exists(path.join(to, 'client.json')))) {
    backup(path.join(to, 'client.json'));
    fs.copyFileSync(path.join(from, 'client.json'), path.join(to, 'client.json'));
  }
  const client = readJson(path.join(from, 'client.json'));
  return { profiles, active: (client && client.activeProfile) || 'Default' };
}

function launcherIcon() {
  try {
    const png = fs.readFileSync(path.join(__dirname, '..', 'src', 'assets', 'logo.png'));
    if (png.length < 200 * 1024) return `data:image/png;base64,${png.toString('base64')}`;
  } catch {
    // fall back to a block icon
  }
  return 'Obsidian';
}

/** Adds the Fabric version and an "Onix Client" entry to the official launcher. */
async function setupOfficialLauncher(root, gameDir, mc = versions.DEFAULT) {
  await game.ensureFabric(root, () => {}, mc);
  const file = path.join(root, 'launcher_profiles.json');
  const data = readJson(file) || { profiles: {}, settings: {}, version: 3 };
  data.profiles = data.profiles || {};
  const now = new Date().toISOString();
  const old = data.profiles['onix-client'] || {};
  data.profiles['onix-client'] = {
    ...old,
    name: 'Onix Client',
    type: 'custom',
    created: old.created || now,
    lastUsed: now,
    lastVersionId: versions.versionId(mc),
    gameDir,
    icon: launcherIcon(),
  };
  if (exists(file)) fs.copyFileSync(file, `${file}.before-onix`);
  fs.writeFileSync(file, JSON.stringify(data, null, 2));
}

/**
 * Installs Onix into a game folder.
 * options: { fabricApi: bool, profilesFrom: game folder of a launcher profile or null, official: { root } }
 */
async function install(target, options, report) {
  const done = [];
  const gameDir = target.gameDir;
  const mc = versions.isSupported(target.minecraft) ? target.minecraft : versions.DEFAULT;
  fs.mkdirSync(gameDir, { recursive: true });
  if (target.official) {
    report({ label: 'Setting up Fabric for the Minecraft Launcher', progress: 0.1 });
    await setupOfficialLauncher(target.root, gameDir, mc);
    done.push('Fabric and an "Onix Client" installation in the Minecraft Launcher');
  }
  report({ label: 'Copying Onix', progress: 0.3 });
  const jar = copyOnixJar(path.join(gameDir, 'mods'), mc);
  done.push(`Onix (${jar})`);
  if (options.fabricApi && !hasFabricApi(gameDir)) {
    const file = await fabricApiFile(mc);
    const target = path.join(gameDir, 'mods', modrinth.safeFileName(file.filename));
    await download(file.url, target, (p) => report({ label: 'Downloading Fabric API', progress: 0.4 + p * 0.4 }));
    try {
      modrinth.verify(target, file.hashes);
    } catch (err) {
      fs.rmSync(target, { force: true });
      throw err;
    }
    done.push(`Fabric API (${file.filename})`);
  }
  if (options.profilesFrom) {
    report({ label: 'Copying your profiles', progress: 0.9 });
    const r = copyOnixConfig(options.profilesFrom, gameDir);
    if (r.profiles.length) done.push(`${r.profiles.length} Onix profile${r.profiles.length === 1 ? '' : 's'}`);
  }
  report({ label: 'Done', progress: 1 });
  return done;
}

/** Fabric mod ids of the jars in a folder: Map<id, file name>. */
async function modIds(modsDir) {
  const ids = new Map();
  for (const e of list(modsDir)) {
    if (!e.isFile() || !e.name.endsWith('.jar')) continue;
    const info = await importer.readFabricModJson(path.join(modsDir, e.name));
    if (info && info.id) ids.set(info.id, e.name);
  }
  return ids;
}

/**
 * Copies a whole launcher profile into another launcher's Fabric 1.21.1 instance: Onix, Fabric API,
 * every enabled mod of the profile and the Onix settings. Mods the instance already has (same mod id)
 * keep the instance's version, so nothing is installed twice.
 * options: { settings: also copy options.txt and servers.dat }
 */
async function copyProfileInto(target, profileDir, options, report) {
  if (!target.official && target.compatible === false) throw new Error(target.note || `This instance is not Fabric ${versions.LIST.join(' or ')}.`);
  const done = await install(target, { fabricApi: true, profilesFrom: profileDir }, (p) => report({ ...p, progress: p.progress * 0.6 }));
  const from = path.join(profileDir, 'mods');
  const to = path.join(target.gameDir, 'mods');
  report({ label: 'Copying mods', progress: 0.7 });
  const theirs = await modIds(to);
  const ours = await modIds(from);
  let copied = 0;
  const kept = [];
  for (const [id, file] of ours) {
    if (ONIX_JAR.test(file)) continue;
    if (theirs.has(id)) {
      if (theirs.get(id) !== file) kept.push(id);
      continue;
    }
    fs.copyFileSync(path.join(from, file), path.join(to, file));
    copied++;
  }
  if (copied) done.push(`${copied} mod${copied === 1 ? '' : 's'}`);
  if (kept.length) done.push(`kept the instance's own version of ${kept.length} mod${kept.length === 1 ? '' : 's'}`);
  if (options && options.settings) {
    for (const f of ['options.txt', 'servers.dat']) {
      if (!exists(path.join(profileDir, f))) continue;
      if (exists(path.join(target.gameDir, f))) fs.copyFileSync(path.join(target.gameDir, f), path.join(target.gameDir, `${f}.before-onix`));
      fs.copyFileSync(path.join(profileDir, f), path.join(target.gameDir, f));
    }
    done.push('options and server list (the old ones were saved as *.before-onix)');
  }
  report({ label: 'Done', progress: 1 });
  return done;
}

/**
 * Sends Onix profiles to a running game that already has Onix. The mod watches
 * config/onix/inbox.json and loads the profile without a restart.
 */
function pushProfile(gameDir, profilesFrom) {
  if (!hasOnix(gameDir)) throw new Error('This game does not have Onix yet – install it and restart Minecraft first.');
  const r = copyOnixConfig(profilesFrom, gameDir);
  if (!r.profiles.length) throw new Error('This launcher profile has no Onix profiles yet – start it once first.');
  const inbox = path.join(gameDir, 'config', 'onix', 'inbox.json');
  fs.writeFileSync(inbox, JSON.stringify({ action: 'load-profile', profile: r.active, from: 'Onix Launcher', at: Date.now() }, null, 2));
  return { profile: r.active, profiles: r.profiles.length };
}

/** Saves the mod jar (and optionally Fabric API) into a folder. */
async function exportMod(folder, withFabricApi, report, mc = versions.DEFAULT) {
  const bundled = game.findBundledMod(mc);
  if (!bundled) throw new Error('The Onix mod is missing from this launcher build.');
  fs.mkdirSync(folder, { recursive: true });
  const files = [path.basename(bundled)];
  fs.copyFileSync(bundled, path.join(folder, path.basename(bundled)));
  if (withFabricApi) {
    const file = await fabricApiFile(mc);
    const name = modrinth.safeFileName(file.filename);
    const target = path.join(folder, name);
    await download(file.url, target, (p) => report({ label: 'Downloading Fabric API', progress: p }));
    try {
      modrinth.verify(target, file.hashes);
    } catch (err) {
      fs.rmSync(target, { force: true });
      throw err;
    }
    files.push(name);
  }
  return files;
}

function bundledInfo(mc = versions.DEFAULT) {
  const jar = game.findBundledMod(mc);
  if (!jar) return null;
  return { file: path.basename(jar), size: fs.statSync(jar).size, minecraft: mc, loader: versions.get(mc).loader };
}

module.exports = { running, targets, install, copyProfileInto, pushProfile, exportMod, bundledInfo, arg, instanceInfo, copyOnixConfig };
