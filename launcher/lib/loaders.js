'use strict';

// Mod loaders besides Fabric (from the Carbon Launcher). Quilt publishes ready-made version JSONs; NeoForge and Forge
// are installed by running their official installer against the shared game folder (it has the layout they expect).
// Vanilla needs nothing. Fabric stays with game.js, which pins the Loader version Onix was tested with.
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { download, USER_AGENT } = require('./download');

const LOADERS = [
  { id: 'fabric', name: 'Fabric', note: 'Onix, Modrinth mods, performance pack' },
  { id: 'quilt', name: 'Quilt', note: 'Fabric-compatible mods' },
  { id: 'neoforge', name: 'NeoForge', note: 'NeoForge mods' },
  { id: 'forge', name: 'Forge', note: 'Forge mods' },
  { id: 'vanilla', name: 'Vanilla', note: 'No mods' },
];
const IDS = LOADERS.map((l) => l.id);

const QUILT = 'https://meta.quiltmc.org/v3';
const NEOFORGE_LIST = 'https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge';
const FORGE_LIST = 'https://files.minecraftforge.net/net/minecraftforge/forge/maven-metadata.json';

const memo = new Map();
async function json(url) {
  if (memo.has(url)) return memo.get(url);
  const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT }, signal: AbortSignal.timeout(20000) });
  if (!res.ok) throw new Error(`${new URL(url).host} answered with HTTP ${res.status}`);
  const value = await res.json();
  memo.set(url, value);
  return value;
}

/** NeoForge numbers its builds after the game: 1.21.1 → 21.1.x */
function neoforgePrefix(game) {
  const p = game.split('.');
  return p[0] === '1' ? `${p[1]}.${p[2] || 0}.` : `${p[0]}.${p[1] || 0}.${p[2] || 0}.`;
}

/** Loader versions for a Minecraft version, newest first: [{ version, stable }] */
async function versions(loader, game) {
  switch (loader) {
    case 'quilt': {
      const list = await json(`${QUILT}/versions/loader/${encodeURIComponent(game)}`);
      return list.map((e) => ({ version: e.loader.version, stable: !/beta|pre|rc/.test(e.loader.version) }));
    }
    case 'neoforge': {
      const all = await json(NEOFORGE_LIST);
      const prefix = neoforgePrefix(game);
      return all.versions.filter((v) => v.startsWith(prefix)).reverse().map((v) => ({ version: v, stable: !v.includes('beta') }));
    }
    case 'forge': {
      const all = await json(FORGE_LIST);
      return (all[game] || []).slice().reverse().map((full) => ({ version: full.slice(game.length + 1), stable: true }));
    }
    default:
      return [];
  }
}

async function latest(loader, game) {
  const list = await versions(loader, game);
  const pick = list.find((v) => v.stable) || list[0];
  return pick ? pick.version : null;
}

const versionJson = (root, id) => path.join(root, 'versions', id, `${id}.json`);

/**
 * Installs the loader if needed and returns the version id to launch (null for vanilla).
 * javaPath runs the Forge/NeoForge installers; log receives their output.
 */
async function install({ loader, game, version, root, javaPath, report = () => {}, log = () => {} }) {
  if (!loader || loader === 'vanilla') return null;
  if (!IDS.includes(loader) || loader === 'fabric') throw new Error(`Unknown loader ${loader}`);
  if (!version) version = await latest(loader, game);
  if (!version) throw new Error(`${loader === 'neoforge' ? 'NeoForge' : loader === 'forge' ? 'Forge' : 'Quilt'} has no build for Minecraft ${game}.`);

  if (loader === 'quilt') {
    const known = `quilt-loader-${version}-${game}`;
    if (fs.existsSync(versionJson(root, known))) return known;
    report('Setting up Quilt', 0);
    const profile = await json(`${QUILT}/versions/loader/${encodeURIComponent(game)}/${encodeURIComponent(version)}/profile/json`);
    fs.mkdirSync(path.dirname(versionJson(root, profile.id)), { recursive: true });
    fs.writeFileSync(versionJson(root, profile.id), JSON.stringify(profile, null, 2));
    return profile.id;
  }

  const id = loader === 'neoforge' ? `neoforge-${version}` : `${game}-forge-${version}`;
  if (fs.existsSync(versionJson(root, id))) return id;
  const full = loader === 'neoforge' ? version : `${game}-${version}`;
  const url = loader === 'neoforge'
    ? `https://maven.neoforged.net/releases/net/neoforged/neoforge/${full}/neoforge-${full}-installer.jar`
    : `https://maven.minecraftforge.net/net/minecraftforge/forge/${full}/forge-${full}-installer.jar`;
  const jar = path.join(root, 'cache', 'installers', path.basename(url));
  const name = loader === 'neoforge' ? 'NeoForge' : 'Forge';
  if (!fs.existsSync(jar)) {
    report(`Downloading the ${name} installer`, 0);
    await download(url, jar, (p) => report(`Downloading the ${name} installer`, p));
  }
  // the installer wants a launcher_profiles.json and the vanilla folder layout
  const profiles = path.join(root, 'launcher_profiles.json');
  if (!fs.existsSync(profiles)) fs.writeFileSync(profiles, JSON.stringify({ profiles: {} }));
  const java = String(javaPath || 'java').replace(/javaw(\.exe)?$/i, (m, ext) => `java${ext || ''}`);
  const args = ['-jar', jar, loader === 'neoforge' ? '--install-client' : '--installClient', root];
  report(`Installing ${name} ${version}`, null);
  log(`> ${path.basename(java)} ${args.join(' ')}`);
  await new Promise((resolve, reject) => {
    const proc = spawn(java, args, { cwd: path.dirname(jar), windowsHide: true });
    const pipe = (d) => String(d).split(/\r?\n/).filter(Boolean).forEach(log);
    proc.stdout.on('data', pipe);
    proc.stderr.on('data', pipe);
    proc.on('error', reject);
    proc.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`The ${name} installer stopped with code ${code} (see the console).`))));
  });
  if (fs.existsSync(versionJson(root, id))) return id;
  // some installers name the profile differently: pick the matching folder
  const dirs = fs.readdirSync(path.join(root, 'versions'));
  const hit = dirs.find((d) => d.includes(version) && d.toLowerCase().includes(loader));
  if (hit) return hit;
  throw new Error(`The ${name} installer finished, but no game version was created.`);
}

module.exports = { LOADERS, IDS, versions, latest, install };
