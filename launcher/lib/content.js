'use strict';

const fs = require('fs');
const path = require('path');
const { readManifest } = require('./modrinth');

/**
 * What lives in a profile folder besides mods: resource packs, shader packs, worlds, screenshots and logs.
 * Everything is read from disk; names that come from the page are reduced to plain file names.
 */

const KINDS = {
  resourcepacks: { dir: 'resourcepacks', label: 'resource pack' },
  shaderpacks: { dir: 'shaderpacks', label: 'shader pack' },
};

function plainName(name) {
  const base = path.basename(String(name || ''));
  if (!base || base === '.' || base === '..') throw new Error('Invalid file name');
  return base;
}

function kindDir(gameDir, kind) {
  const k = KINDS[kind];
  if (!k) throw new Error('Unknown kind');
  return path.join(gameDir, k.dir);
}

function sizeOf(p) {
  try {
    const st = fs.statSync(p);
    if (!st.isDirectory()) return st.size;
    return fs.readdirSync(p).reduce((sum, f) => sum + sizeOf(path.join(p, f)), 0);
  } catch {
    return 0;
  }
}

/** Packs in resourcepacks/ or shaderpacks/: zips and folders; "name.zip.disabled" is switched off. */
function listPacks(gameDir, kind) {
  const dir = kindDir(gameDir, kind);
  if (!fs.existsSync(dir)) return [];
  const manifest = readManifest(dir);
  const byFile = new Map(Object.entries(manifest).map(([id, m]) => [m.file, { id, ...m }]));
  return fs.readdirSync(dir, { withFileTypes: true })
    .filter((e) => !e.name.startsWith('.') && (e.isDirectory() || /\.zip(\.disabled)?$/i.test(e.name)))
    .map((e) => {
      const enabled = !e.name.endsWith('.disabled');
      const base = enabled ? e.name : e.name.slice(0, -'.disabled'.length);
      const m = byFile.get(base);
      const full = path.join(dir, e.name);
      return {
        file: e.name,
        name: base.replace(/\.zip$/i, ''),
        folder: e.isDirectory(),
        enabled,
        size: e.isDirectory() ? 0 : sizeOf(full),
        modrinth: m ? { id: m.id, title: m.title, version: m.version, icon: m.icon || null } : null,
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
}

function togglePack(gameDir, kind, file) {
  const dir = kindDir(gameDir, kind);
  const name = plainName(file);
  const src = path.join(dir, name);
  if (!fs.existsSync(src)) throw new Error('File not found');
  const dst = name.endsWith('.disabled') ? path.join(dir, name.slice(0, -'.disabled'.length)) : `${src}.disabled`;
  fs.renameSync(src, dst);
}

function packPath(gameDir, kind, file) {
  return path.join(kindDir(gameDir, kind), plainName(file));
}

/** Copies dropped or picked files into the folder; only .zip files (and folders for packs). */
function addPacks(gameDir, kind, paths) {
  const dir = kindDir(gameDir, kind);
  fs.mkdirSync(dir, { recursive: true });
  const added = [];
  for (const p of paths || []) {
    const src = String(p || '');
    if (!src || !fs.existsSync(src)) continue;
    const st = fs.statSync(src);
    if (!st.isDirectory() && !/\.zip$/i.test(src)) continue;
    const target = path.join(dir, path.basename(src));
    fs.cpSync(src, target, { recursive: true });
    added.push(path.basename(src));
  }
  return added;
}

// ------------------------------------------------------------------ worlds

const MODES = ['Survival', 'Creative', 'Adventure', 'Spectator'];

/** Reads a few fields from level.dat without a full NBT parser: the name and game mode are plain tags. */
function levelInfo(file) {
  try {
    const raw = require('zlib').gunzipSync(fs.readFileSync(file));
    const info = {};
    const nameTag = Buffer.from([8, 0, 9]); // TAG_String, name length 9: "LevelName"
    let i = raw.indexOf(Buffer.concat([nameTag, Buffer.from('LevelName')]));
    if (i >= 0) {
      i += 3 + 9;
      const len = raw.readUInt16BE(i);
      info.name = raw.slice(i + 2, i + 2 + len).toString('utf8');
    }
    const modeTag = Buffer.concat([Buffer.from([3, 0, 8]), Buffer.from('GameType')]);
    const j = raw.indexOf(modeTag);
    if (j >= 0) info.mode = MODES[raw.readInt32BE(j + modeTag.length)] || null;
    const hcTag = Buffer.concat([Buffer.from([1, 0, 8]), Buffer.from('hardcore')]);
    const h = raw.indexOf(hcTag);
    if (h >= 0) info.hardcore = raw[h + hcTag.length] === 1;
    return info;
  } catch {
    return {};
  }
}

function listWorlds(gameDir) {
  const saves = path.join(gameDir, 'saves');
  if (!fs.existsSync(saves)) return [];
  return fs.readdirSync(saves, { withFileTypes: true })
    .filter((e) => e.isDirectory() && fs.existsSync(path.join(saves, e.name, 'level.dat')))
    .map((e) => {
      const dir = path.join(saves, e.name);
      const level = path.join(dir, 'level.dat');
      const info = levelInfo(level);
      let icon = null;
      const iconFile = path.join(dir, 'icon.png');
      try {
        if (fs.existsSync(iconFile) && fs.statSync(iconFile).size < 256 * 1024) icon = `data:image/png;base64,${fs.readFileSync(iconFile).toString('base64')}`;
      } catch {
        icon = null;
      }
      return {
        folder: e.name,
        name: info.name || e.name,
        mode: info.hardcore ? 'Hardcore' : info.mode || null,
        lastPlayed: fs.statSync(level).mtimeMs,
        icon,
      };
    })
    .sort((a, b) => b.lastPlayed - a.lastPlayed);
}

function worldPath(gameDir, folder) {
  return path.join(gameDir, 'saves', plainName(folder));
}

// ------------------------------------------------------------------ screenshots and logs

function listScreenshots(gameDir) {
  const dir = path.join(gameDir, 'screenshots');
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter((f) => /\.(png|jpe?g)$/i.test(f))
    .map((f) => ({ file: f, time: fs.statSync(path.join(dir, f)).mtimeMs }))
    .sort((a, b) => b.time - a.time);
}

function screenshotPath(gameDir, file) {
  return path.join(gameDir, 'screenshots', plainName(file));
}

/** Log files of a profile: the current log first, then crash reports, newest first. */
function listLogs(gameDir) {
  const out = [];
  const latest = path.join(gameDir, 'logs', 'latest.log');
  if (fs.existsSync(latest)) out.push({ id: 'logs/latest.log', name: 'Latest game log', time: fs.statSync(latest).mtimeMs, crash: false });
  const crashes = path.join(gameDir, 'crash-reports');
  if (fs.existsSync(crashes)) {
    for (const f of fs.readdirSync(crashes).filter((x) => x.endsWith('.txt'))) {
      out.push({ id: `crash-reports/${f}`, name: f.replace(/^crash-/, 'Crash ').replace(/-client\.txt$|\.txt$/, ''), time: fs.statSync(path.join(crashes, f)).mtimeMs, crash: true });
    }
  }
  return out.sort((a, b) => Number(a.crash) - Number(b.crash) || b.time - a.time);
}

/** The last part of a log (at most 512 KB). */
function readLog(gameDir, id) {
  const [folder, file] = String(id || '').split('/');
  if (!['logs', 'crash-reports'].includes(folder)) throw new Error('Unknown log');
  const p = path.join(gameDir, folder, plainName(file));
  const st = fs.statSync(p);
  const max = 512 * 1024;
  const fd = fs.openSync(p, 'r');
  try {
    const len = Math.min(st.size, max);
    const buf = Buffer.alloc(len);
    fs.readSync(fd, buf, 0, len, st.size - len);
    return (st.size > max ? '… (start of the log cut off)\n' : '') + buf.toString('utf8');
  } finally {
    fs.closeSync(fd);
  }
}

module.exports = {
  KINDS, listPacks, togglePack, packPath, addPacks, listWorlds, worldPath, listScreenshots, screenshotPath, listLogs, readLog, sizeOf,
};
