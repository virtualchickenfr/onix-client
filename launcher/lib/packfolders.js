'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');

/**
 * Every resource pack folder on this PC: other Onix profiles, the vanilla launcher and other launchers
 * (Modrinth App, CurseForge, Prism, Carbon, GDLauncher, ATLauncher). The Onix mod shows their packs in the pack menu.
 */

const home = os.homedir();
const appData = process.env.APPDATA
  || (process.platform === 'darwin' ? path.join(home, 'Library', 'Application Support') : path.join(home, '.config'));
const localShare = process.platform === 'win32' || process.platform === 'darwin' ? appData : path.join(home, '.local', 'share');

function isDir(p) {
  try {
    return fs.statSync(p).isDirectory();
  } catch {
    return false;
  }
}

function subdirs(p) {
  try {
    return fs.readdirSync(p, { withFileTypes: true }).filter((e) => e.isDirectory()).slice(0, 200).map((e) => path.join(p, e.name));
  } catch {
    return [];
  }
}

/** Launcher instance folders: [base folder, path of the game folder inside one instance]. */
function instancePacks() {
  const out = [];
  const launchers = [
    [path.join(appData, 'ModrinthApp', 'profiles'), ''],
    [path.join(appData, 'com.modrinth.theseus', 'profiles'), ''],
    [path.join(localShare, 'ModrinthApp', 'profiles'), ''],
    [path.join(home, 'curseforge', 'minecraft', 'Instances'), ''],
    [path.join(home, 'Documents', 'Curseforge', 'Minecraft', 'Instances'), ''],
    [path.join(appData, 'PrismLauncher', 'instances'), 'minecraft'],
    [path.join(appData, 'PrismLauncher', 'instances'), '.minecraft'],
    [path.join(localShare, 'PrismLauncher', 'instances'), 'minecraft'],
    [path.join(localShare, 'PrismLauncher', 'instances'), '.minecraft'],
    [path.join(appData, 'CarbonLauncher', 'instances'), 'minecraft'],
    [path.join(appData, 'gdlauncher_next', 'instances'), ''],
    [path.join(appData, 'gdlauncher_carbon', 'data', 'instances'), 'instance'],
    [path.join(appData, '.atlauncher', 'instances'), ''],
    [path.join(home, '.atlauncher', 'instances'), ''],
  ];
  for (const [base, inner] of launchers) {
    for (const inst of subdirs(base)) out.push(path.join(inst, inner, 'resourcepacks'));
  }
  return out;
}

function vanillaPacks() {
  if (process.platform === 'win32') return [path.join(appData, '.minecraft', 'resourcepacks')];
  if (process.platform === 'darwin') return [path.join(appData, 'minecraft', 'resourcepacks')];
  return [path.join(home, '.minecraft', 'resourcepacks')];
}

/** All existing pack folders except the profile's own, without duplicates. */
function collect(ownGameDir, profileDirs) {
  const own = path.resolve(ownGameDir, 'resourcepacks');
  const seen = new Set([own.toLowerCase()]);
  const out = [];
  const candidates = [
    ...profileDirs.map((d) => path.join(d, 'resourcepacks')),
    ...vanillaPacks(),
    ...instancePacks(),
  ];
  for (const c of candidates) {
    const p = path.resolve(c);
    let key = p.toLowerCase();
    try {
      key = fs.realpathSync(p).toLowerCase();
    } catch {
      continue;
    }
    if (seen.has(key) || !isDir(p)) continue;
    seen.add(key);
    out.push(p);
  }
  return out;
}

/** Writes config/onix/pack-folders.txt into the profile; the mod reads it when the game starts. */
function write(ownGameDir, profileDirs, log) {
  try {
    const folders = collect(ownGameDir, profileDirs);
    const dir = path.join(ownGameDir, 'config', 'onix');
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, 'pack-folders.txt'), `# Written by the Onix launcher before every start\n${folders.join('\n')}\n`);
    if (log && folders.length) log(`Resource packs from ${folders.length} other folder${folders.length === 1 ? '' : 's'} on this PC`);
  } catch (e) {
    if (log) log(`Could not list other resource pack folders: ${e.message}`);
  }
}

module.exports = { collect, write };
