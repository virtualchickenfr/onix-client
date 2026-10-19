'use strict';

/**
 * Minecraft versions the launcher can play.
 *
 * ONIX: versions with an Onix mod build (bundled under resources/mods/<version>) and a fixed Fabric Loader.
 * Every other release that Fabric supports can be played too (Fabric API and the performance pack, without the
 * Onix mod until a build exists); that list comes from Fabric's meta API and is cached on disk.
 */
const fs = require('fs');
const path = require('path');

const ONIX = {
  '1.21.1': { mc: '1.21.1', loader: '0.16.5', note: 'Onix Classic' },
  '1.21.11': { mc: '1.21.11', loader: '0.19.5', note: 'Onix + Carbon' },
};

const DEFAULT = '1.21.1';
const LIST = Object.keys(ONIX);
/** Oldest version offered (Fabric starts at 1.14). */
const OLDEST = '1.14';

let catalog = { loader: '0.17.2', games: [] };
let cacheFile = null;

/** Compares two Minecraft versions like 1.21.1 and 1.21.11 (negative when a is older). */
function compare(a, b) {
  const pa = String(a).split('.').map((n) => parseInt(n, 10) || 0);
  const pb = String(b).split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) - (pb[i] || 0);
  return 0;
}

const isRelease = (v) => /^\d+\.\d+(\.\d+)?$/.test(String(v));
const hasOnix = (v) => Object.prototype.hasOwnProperty.call(ONIX, String(v));
/** Onix versions, Fabric's list, and any release from 1.14 on (the list may not be loaded yet). */
const isSupported = (v) => hasOnix(v) || catalog.games.includes(String(v)) || (isRelease(v) && compare(v, OLDEST) >= 0);

function get(v) {
  if (hasOnix(v)) return { ...ONIX[v], onix: true };
  if (isSupported(v)) return { mc: String(v), loader: catalog.loader, note: '', onix: false };
  return { ...ONIX[DEFAULT], onix: true };
}

const ofProfile = (profile) => get(profile && profile.mcVersion);
const versionId = (v) => {
  const x = get(v);
  return `fabric-loader-${x.loader}-${x.mc}`;
};

/** Java a Minecraft version needs: 21 from 1.20.5, 17 from 1.17 (16 is enough there, 17 runs it too), 8 before. */
function javaMajor(v) {
  const mc = get(v).mc;
  if (compare(mc, '1.20.5') >= 0) return 21;
  if (compare(mc, '1.17') >= 0) return 17;
  return 8;
}

/** All playable versions, newest first: { id, note, onix }. */
function all() {
  const ids = new Set([...catalog.games, ...LIST]);
  return [...ids].sort((a, b) => compare(b, a)).map((id) => ({ id, ...get(id) }));
}

/** Loads the cached list (instant) – call refresh() afterwards to update it from Fabric. */
function init(file) {
  cacheFile = file;
  try {
    const saved = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (saved && Array.isArray(saved.games) && saved.loader) catalog = saved;
  } catch {
    // first start: only the Onix versions until refresh() has run
  }
}

async function refresh(fetchJson) {
  const [games, loaders] = await Promise.all([
    fetchJson('https://meta.fabricmc.net/v2/versions/game'),
    fetchJson('https://meta.fabricmc.net/v2/versions/loader'),
  ]);
  const list = (games || []).filter((g) => g.stable && isRelease(g.version) && compare(g.version, OLDEST) >= 0).map((g) => g.version);
  const loader = ((loaders || []).find((l) => l.stable) || (loaders || [])[0] || {}).version;
  if (!list.length || !loader) return false;
  catalog = { loader, games: list, at: Date.now() };
  if (cacheFile) {
    try {
      fs.mkdirSync(path.dirname(cacheFile), { recursive: true });
      fs.writeFileSync(cacheFile, JSON.stringify(catalog));
    } catch {
      // only a cache
    }
  }
  return true;
}

module.exports = { ONIX, VERSIONS: ONIX, DEFAULT, LIST, isSupported, hasOnix, get, ofProfile, versionId, compare, javaMajor, all, init, refresh };
