'use strict';

/**
 * Takes a whole profile to another Minecraft version: a copy of the profile with everything in it (worlds, Onix
 * modules and HUD, key binds, video settings, servers, mod configs, resource and shader packs, screenshots), whose
 * mods are swapped for their versions for the new Minecraft version from Modrinth. Mods without such a version are
 * kept, switched off, so nothing is lost. The original profile is not touched.
 *
 * plan()  – what will happen to every mod (one request to Modrinth for all of them)
 * apply() – does it in an already copied profile folder
 */

const fs = require('fs');
const path = require('path');
const modrinth = require('./modrinth');
const { download, USER_AGENT } = require('./download');

const MANAGED_MANIFEST = '.onix-managed.json';     // Fabric API and the performance pack (game.js)
const isJar = (f) => f.endsWith('.jar') || f.endsWith('.jar.disabled');
const isOnix = (f) => /^onix-client-/.test(f);

function managedFiles(modsDir) {
  try {
    const m = JSON.parse(fs.readFileSync(path.join(modsDir, MANAGED_MANIFEST), 'utf8'));
    return new Set(Object.entries(m).filter(([k, v]) => !k.startsWith('_') && typeof v === 'string').map(([, v]) => v));
  } catch {
    return new Set();
  }
}

/** The player's own mods in a mods folder: not Onix, not the ones the launcher manages (it sets those up again). */
function ownMods(modsDir) {
  if (!fs.existsSync(modsDir)) return [];
  const managed = managedFiles(modsDir);
  return fs.readdirSync(modsDir).filter((f) => isJar(f) && !isOnix(f) && !managed.has(f.replace(/\.disabled$/, '')));
}

const nameOf = (file, manifest) => {
  const entry = Object.values(manifest).find((e) => e && (e.file === file || `${e.file}.disabled` === file));
  return entry && entry.title ? entry.title : file.replace(/\.jar(\.disabled)?$/, '');
};

/**
 * What happens to each mod on the way to Minecraft `mc`:
 * same (the file also runs there), update (a different file for mc), missing (no version for mc on Modrinth; kept switched off).
 */
async function plan(profileDir, mc, loader = 'fabric') {
  const modsDir = path.join(profileDir, 'mods');
  const files = ownMods(modsDir);
  const manifest = modrinth.cleanManifest(modsDir);
  let found = new Map();
  let offline = false;
  try {
    found = await modrinth.versionsFor(files.map((f) => path.join(modsDir, f)), mc, loader);
  } catch {
    offline = true;
  }
  // titles for mods Modrinth knows but the profile did not install through it
  const titles = {};
  const ids = [...new Set([...found.values()].map((v) => v.projectId))];
  if (ids.length) {
    try {
      const res = await fetch(`https://api.modrinth.com/v2/projects?ids=${encodeURIComponent(JSON.stringify(ids))}`, { headers: { 'User-Agent': USER_AGENT } });
      if (res.ok) for (const p of await res.json()) titles[p.id] = p.title;
    } catch {
      // names from the files then
    }
  }
  const mods = files.map((file) => {
    const v = found.get(path.join(modsDir, file));
    const disabled = file.endsWith('.disabled');
    const name = (v && titles[v.projectId]) || nameOf(file, manifest);
    if (!v) return { file, name, disabled, status: 'missing' };
    if (v.same) return { file, name, disabled, status: 'same' };
    return { file, name, disabled, status: 'update', to: v.version, version: v };
  }).sort((a, b) => a.name.localeCompare(b.name));
  const packs = (kind) => (fs.existsSync(path.join(profileDir, kind)) ? fs.readdirSync(path.join(profileDir, kind)).filter((f) => !f.startsWith('.')).length : 0);
  const worlds = fs.existsSync(path.join(profileDir, 'saves'))
    ? fs.readdirSync(path.join(profileDir, 'saves'), { withFileTypes: true }).filter((d) => d.isDirectory()).length : 0;
  return {
    mc,
    offline,
    mods,
    counts: {
      same: mods.filter((m) => m.status === 'same').length,
      update: mods.filter((m) => m.status === 'update').length,
      missing: mods.filter((m) => m.status === 'missing').length,
      resourcepacks: packs('resourcepacks'),
      shaderpacks: packs('shaderpacks'),
      worlds,
    },
  };
}

/** Swaps the mods in the copied profile folder `profileDir` according to a plan made for it. */
async function apply(profileDir, thePlan, report = () => {}) {
  const modsDir = path.join(profileDir, 'mods');
  fs.mkdirSync(modsDir, { recursive: true });
  // the launcher sets up Onix, Fabric API and the performance pack for the new version on the first start
  for (const f of managedFiles(modsDir)) {
    fs.rmSync(path.join(modsDir, f), { force: true });
    fs.rmSync(path.join(modsDir, `${f}.disabled`), { force: true });
  }
  fs.rmSync(path.join(modsDir, MANAGED_MANIFEST), { force: true });
  for (const f of fs.readdirSync(modsDir)) if (isOnix(f)) fs.rmSync(path.join(modsDir, f), { force: true });

  const manifest = modrinth.readManifest(modsDir);
  const out = { updated: [], kept: [], disabled: [], failed: [] };
  const updates = thePlan.mods.filter((m) => m.status === 'update');
  let done = 0;
  for (const m of thePlan.mods) {
    const old = path.join(modsDir, m.file);
    if (!fs.existsSync(old)) continue;
    if (m.status === 'same') {
      out.kept.push(m.name);
      continue;
    }
    if (m.status === 'missing') {
      if (!m.disabled) fs.renameSync(old, `${old}.disabled`);
      out.disabled.push(m.name);
      continue;
    }
    const v = m.version;
    report({ label: `Updating ${m.name} (${++done}/${updates.length})`, progress: (done - 1) / Math.max(1, updates.length) });
    try {
      const name = modrinth.safeFileName(v.filename);
      const target = path.join(modsDir, name);
      const tmp = `${target}.part`;
      await download(v.url, tmp);
      try {
        modrinth.verify(tmp, v.hashes);
      } catch (err) {
        fs.rmSync(tmp, { force: true });
        throw err;
      }
      fs.rmSync(old, { force: true });
      fs.renameSync(tmp, m.disabled ? `${target}.disabled` : target);
      const entry = manifest[v.projectId];
      manifest[v.projectId] = { ...(entry || {}), file: name, versionId: v.versionId, version: v.version, title: (entry && entry.title) || m.name, icon: (entry && entry.icon) || null };
      out.updated.push(m.name);
    } catch (err) {
      // the old file would stop the game: keep it, switched off
      if (!m.disabled && fs.existsSync(old)) fs.renameSync(old, `${old}.disabled`);
      out.failed.push({ name: m.name, error: err.message });
    }
  }
  modrinth.writeManifest(modsDir, manifest);
  report({ label: 'Done', progress: 1 });
  return out;
}

module.exports = { plan, apply };
