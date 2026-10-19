const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { writeZip, walkZip } = require('./zip');
const { download } = require('./download');
const modrinth = require('./modrinth');

/*
 * Profiles as modpacks.
 *  - Modrinth (.mrpack): Modrinth App, Prism Launcher, ATLauncher, GDLauncher and others.
 *    Mods that are on Modrinth become download links with hashes; everything else
 *    (including Onix itself and its settings) travels in "overrides".
 *  - CurseForge (.zip): CurseForge App. Every mod travels inside the file.
 * Import reads both. Format reference: https://support.modrinth.com/en/articles/8802351
 */

const versions = require('./versions');
/** Hosts the .mrpack format allows downloads from. */
const ALLOWED_HOSTS = ['cdn.modrinth.com', 'github.com', 'raw.githubusercontent.com', 'gitlab.com'];
const OTHER_LOADERS = { forge: 'Forge', neoforge: 'NeoForge', 'quilt-loader': 'Quilt' };
const ONIX_JAR = /^onix-client-.*\.jar$/i;

/** What can go into an export, relative to the profile folder. */
const PARTS = {
  config: { label: 'Mod and Onix settings', paths: ['config'], default: true },
  options: { label: 'Game options and keys', paths: ['options.txt'], default: true },
  servers: { label: 'Server list', paths: ['servers.dat'], default: true },
  resourcepacks: { label: 'Resource packs', paths: ['resourcepacks'], default: false },
  shaderpacks: { label: 'Shader packs', paths: ['shaderpacks'], default: false },
};
/** Never exported: machine-specific, temporary or private files. */
const SKIP = [/(^|\/)(backups|\.cache|logs|crash-reports)(\/|$)/i, /\.(part|tmp)$/i, /^config\/onix\/inbox\.json$/];

function walk(root, rel) {
  const full = path.join(root, rel);
  let stat;
  try {
    stat = fs.statSync(full);
  } catch {
    return [];
  }
  if (stat.isFile()) return [rel];
  if (!stat.isDirectory()) return [];
  const out = [];
  for (const name of fs.readdirSync(full)) out.push(...walk(root, `${rel}/${name}`));
  return out;
}

/** Resolves a path from a pack inside `root`; throws for anything that would land outside it. */
function inside(root, rel) {
  const clean = String(rel || '').replace(/\\/g, '/');
  if (!clean || clean.startsWith('/') || /^[a-zA-Z]:/.test(clean) || clean.split('/').some((part) => part === '..')) {
    throw new Error(`The pack contains an unsafe path: ${rel}`);
  }
  const base = path.resolve(root);
  const target = path.resolve(base, clean);
  if (!target.startsWith(base + path.sep)) throw new Error(`The pack contains an unsafe path: ${rel}`);
  return target;
}

const allowedUrl = (u) => {
  try {
    const parsed = new URL(u);
    return parsed.protocol === 'https:' && ALLOWED_HOSTS.includes(parsed.hostname);
  } catch {
    return false;
  }
};

// ------------------------------------------------------------------ export

/**
 * Exports a profile folder.
 * options: { format: 'mrpack' | 'curseforge', name, version, author, includeOnix, parts: {config, options, …} }
 * Returns { file, linked, packed, notes }.
 */
async function exportProfile(profileDir, out, options = {}, report = () => {}) {
  const format = options.format === 'curseforge' ? 'curseforge' : 'mrpack';
  const { mc: MC_VERSION, loader: LOADER_VERSION } = versions.get(options.mc);
  const modsDir = path.join(profileDir, 'mods');
  const all = fs.existsSync(modsDir) ? fs.readdirSync(modsDir) : [];
  const jars = all.filter((f) => /\.jar$/i.test(f) && !(ONIX_JAR.test(f) && options.includeOnix === false));
  const notes = [];
  const disabled = all.filter((f) => /\.jar\.disabled$/i.test(f)).length;
  if (disabled) notes.push(`${disabled} disabled ${disabled === 1 ? 'mod was' : 'mods were'} left out`);

  const entries = [];
  const files = [];
  if (format === 'mrpack') {
    report({ label: 'Looking up your mods on Modrinth', progress: 0.1 });
    let known = new Map();
    try {
      known = await modrinth.identify(jars.filter((f) => !ONIX_JAR.test(f)).map((f) => path.join(modsDir, f)));
    } catch {
      notes.push('Modrinth could not be reached – every mod is packed into the file');
    }
    for (const name of jars) {
      const abs = path.join(modsDir, name);
      const version = known.get(abs);
      const sha1 = version ? modrinth.sha(abs, 'sha1') : null;
      const remote = version && version.files.find((f) => f.hashes && f.hashes.sha1 === sha1);
      if (remote && allowedUrl(remote.url)) {
        files.push({
          path: `mods/${name}`,
          hashes: { sha1, sha512: modrinth.sha(abs, 'sha512') },
          env: { client: 'required', server: 'optional' },
          downloads: [remote.url],
          fileSize: fs.statSync(abs).size,
        });
      } else {
        entries.push({ name: `overrides/mods/${name}`, file: abs });
      }
    }
  } else {
    for (const name of jars) entries.push({ name: `overrides/mods/${name}`, file: path.join(modsDir, name) });
  }

  const parts = {};
  for (const [key, part] of Object.entries(PARTS)) parts[key] = options.parts && key in options.parts ? !!options.parts[key] : part.default;
  for (const [key, part] of Object.entries(PARTS)) {
    if (!parts[key]) continue;
    for (const p of part.paths) {
      for (const rel of walk(profileDir, p)) {
        if (SKIP.some((re) => re.test(rel))) continue;
        entries.push({ name: `overrides/${rel}`, file: path.join(profileDir, rel) });
      }
    }
  }

  const name = String(options.name || 'Onix Profile').slice(0, 64);
  const version = String(options.version || '1.0.0').slice(0, 32);
  const author = String(options.author || '').slice(0, 32);
  if (format === 'mrpack') {
    entries.unshift({
      name: 'modrinth.index.json',
      data: JSON.stringify({
        formatVersion: 1,
        game: 'minecraft',
        versionId: version,
        name,
        summary: `Onix Client profile for Fabric ${MC_VERSION}${author ? ` by ${author}` : ''}`,
        files,
        dependencies: { minecraft: MC_VERSION, 'fabric-loader': LOADER_VERSION },
      }, null, 2),
    });
  } else {
    entries.unshift({
      name: 'manifest.json',
      data: JSON.stringify({
        minecraft: { version: MC_VERSION, modLoaders: [{ id: `fabric-${LOADER_VERSION}`, primary: true }] },
        manifestType: 'minecraftModpack',
        manifestVersion: 1,
        name,
        version,
        author: author || 'Onix Client',
        files: [],
        overrides: 'overrides',
      }, null, 2),
    });
  }
  report({ label: 'Writing the file', progress: 0.6 });
  writeZip(out, entries);
  report({ label: 'Done', progress: 1 });
  const packed = entries.filter((e) => e.name.startsWith('overrides/mods/')).length;
  return { file: out, format, linked: files.length, packed, notes };
}

// ------------------------------------------------------------------ import

/** Type, name and index of a pack, checked against Onix's Minecraft version and loader. */
async function inspect(file, fallbackMc) {
  if (!isZip(file)) return inspectLoose(file, fallbackMc);
  const { buffers } = await walkZip(file, (name) => (name === 'modrinth.index.json' || name === 'manifest.json' ? 'buffer' : null));
  const parse = (name) => {
    try {
      return JSON.parse(buffers.get(name).toString('utf8'));
    } catch {
      throw new Error(`${name} in this pack is damaged.`);
    }
  };
  if (buffers.has('modrinth.index.json')) {
    const index = parse('modrinth.index.json');
    if (index.game !== 'minecraft' || index.formatVersion !== 1) throw new Error('This modpack format version is not supported.');
    const deps = index.dependencies || {};
    for (const [id, label] of Object.entries(OTHER_LOADERS)) {
      if (deps[id]) throw new Error(`This pack needs ${label}. Onix runs on Fabric, so its mods would not load.`);
    }
    if (!versions.isSupported(deps.minecraft)) throw new Error(`This pack is for Minecraft ${deps.minecraft || 'an unknown version'}. Onix runs on ${versions.LIST.join(' and ')}.`);
    if (!deps['fabric-loader']) throw new Error('This pack does not use Fabric.');
    return { type: 'mrpack', name: String(index.name || path.basename(file, path.extname(file))), index, mc: deps.minecraft };
  }
  if (buffers.has('manifest.json')) {
    const manifest = parse('manifest.json');
    if (manifest.manifestType !== 'minecraftModpack') return inspectLoose(file, fallbackMc);
    const mc = manifest.minecraft || {};
    if (!versions.isSupported(mc.version)) throw new Error(`This pack is for Minecraft ${mc.version || 'an unknown version'}. Onix runs on ${versions.LIST.join(' and ')}.`);
    const loaders = (mc.modLoaders || []).map((l) => String(l.id || ''));
    const other = loaders.find((l) => !/^fabric/i.test(l));
    if (other) throw new Error(`This pack needs ${other.split('-')[0]}. Onix runs on Fabric.`);
    return { type: 'curseforge', name: String(manifest.name || path.basename(file, '.zip')), manifest, mc: mc.version };
  }
  return inspectLoose(file, fallbackMc);
}

// ------------------------------------------------------------------ .dawnpack and other mod sets

/** Folders and files of a game folder that a pack may carry as they are. */
const GAME_PARTS = /^(mods|resourcepacks|shaderpacks|config|defaultconfigs|kubejs|schematics|saves)\/|^(options|optionsof|optionsshaders|servers)\.(txt|dat)$/i;
const MC_RE = /^1\.\d{1,2}(\.\d{1,2})?$/;

/** Finds a Minecraft version in a manifest: under keys like minecraft, mcVersion or gameVersion. */
function findMc(node, depth = 0) {
  if (!node || typeof node !== 'object' || depth > 6) return null;
  for (const [k, v] of Object.entries(node)) {
    if (/minecraft|^mc_?version$|^game_?versions?$/i.test(k)) {
      const vals = [].concat(v).filter((x) => typeof x === 'string' && MC_RE.test(x.trim()));
      if (vals.length) return vals[vals.length - 1].trim();
    }
  }
  for (const v of Object.values(node)) {
    const found = typeof v === 'object' ? findMc(v, depth + 1) : null;
    if (found) return found;
  }
  return null;
}

/** Mod entries of a manifest: objects inside lists that carry a download link or a Modrinth id. */
function findModEntries(node, out = [], depth = 0) {
  if (!node || typeof node !== 'object' || depth > 6) return out;
  if (Array.isArray(node)) {
    for (const item of node) {
      if (item && typeof item === 'object' && !Array.isArray(item)) {
        const url = [item.url, item.downloadUrl, item.download_url, ...[].concat(item.downloads || [])].find((u) => typeof u === 'string' && /^https:/.test(u));
        const versionId = item.versionId || item.version_id || item.modrinthVersionId;
        const projectId = item.projectId || item.project_id || item.modrinthId || item.modrinth_id || item.slug;
        if (url || versionId || projectId) {
          out.push({
            url: url || null,
            versionId: typeof versionId === 'string' ? versionId : null,
            projectId: typeof projectId === 'string' ? projectId : null,
            name: String(item.fileName || item.filename || item.file || item.name || item.title || projectId || ''),
            hashes: item.hashes || (item.sha1 || item.sha512 ? { sha1: item.sha1, sha512: item.sha512 } : null),
          });
          continue;
        }
      }
      findModEntries(item, out, depth + 1);
    }
    return out;
  }
  for (const v of Object.values(node)) findModEntries(v, out, depth + 1);
  return out;
}

/** Where a file of a loose pack goes in the profile, or null to leave it out. */
function looseTarget(rel) {
  if (GAME_PARTS.test(rel)) return rel;
  const base = rel.split('/').pop();
  if (/\.jar$/i.test(base)) return `mods/${base}`;
  if (/\.zip$/i.test(base)) return /shader/i.test(rel) ? `shaderpacks/${base}` : `resourcepacks/${base}`;
  return null;
}

/** A .dawnpack (or any zip/JSON with mods that is no Modrinth or CurseForge pack). */
function isZip(file) {
  const head = Buffer.alloc(2);
  const fd = fs.openSync(file, 'r');
  try {
    fs.readSync(fd, head, 0, 2, 0);
  } finally {
    fs.closeSync(fd);
  }
  return head[0] === 0x50 && head[1] === 0x4b;
}

async function inspectLoose(file, fallbackMc) {
  const name = path.basename(file).replace(/\.(dawnpack|zip|json|mrpack)$/i, '');
  if (!isZip(file)) {
    // not a zip: a manifest on its own
    let json;
    try {
      json = JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
    } catch {
      throw new Error('This file is neither a modpack nor a .dawnpack Onix can read.');
    }
    const mods = findModEntries(json);
    if (!mods.length) throw new Error('This pack lists no mods Onix can download.');
    return { type: 'loose', name: String(json.name || json.title || name), mc: pickMc(findMc(json), fallbackMc), packMc: findMc(json), manifests: [json], mods, files: [], strip: '' };
  }
  const names = [];
  const { buffers } = await walkZip(file, (entry, size) => {
    names.push(entry);
    return /\.json$/i.test(entry) && entry.split('/').length <= 2 && size < 4 * 1024 * 1024 ? 'buffer' : null;
  });
  // one shared top folder (MyPack/...) or an overrides/ or .minecraft/ folder is not part of the target path
  const tops = new Set(names.map((n) => n.split('/')[0]));
  let strip = tops.size === 1 && names.every((n) => n.includes('/')) ? `${[...tops][0]}/` : '';
  const rest = (n) => n.slice(strip.length);
  const inner = names.map(rest);
  for (const pre of ['overrides/', 'client-overrides/', '.minecraft/', 'minecraft/']) {
    if (inner.some((n) => n.startsWith(pre))) { strip += pre; break; }
  }
  const manifests = [];
  for (const buf of buffers.values()) {
    try {
      manifests.push(JSON.parse(buf.toString('utf8').replace(/^\uFEFF/, '')));
    } catch {
      // not a manifest
    }
  }
  const files = names.filter((n) => n.startsWith(strip)).map((n) => ({ entry: n, target: looseTarget(n.slice(strip.length)) })).filter((f) => f.target);
  const mods = manifests.flatMap((m) => findModEntries(m));
  if (!files.length && !mods.length) throw new Error('This pack contains no mods, resource packs or settings Onix can use.');
  const packMc = manifests.map((m) => findMc(m)).find(Boolean) || null;
  const title = manifests.map((m) => m && (m.name || m.title)).find((x) => typeof x === 'string' && x.trim());
  return { type: 'loose', name: String(title || name), mc: pickMc(packMc, fallbackMc), packMc, manifests, mods, files, strip };
}

const pickMc = (packMc, fallbackMc) => (packMc && versions.isSupported(packMc) ? packMc : versions.isSupported(fallbackMc) ? fallbackMc : versions.DEFAULT);

async function importLoose(file, info, targetDir, report) {
  const missing = [];
  let downloaded = 0;
  if (info.packMc && info.packMc !== info.mc) missing.push(`The pack was made for Minecraft ${info.packMc}; the profile uses ${info.mc}`);
  // files inside the pack
  report({ label: 'Unpacking', progress: 0.1 });
  const wanted = new Map(info.files.map((f) => [f.entry, inside(targetDir, f.target)]));
  const { written } = info.files.length ? await walkZip(file, (entry) => wanted.get(entry) || null) : { written: 0 };
  // mods the pack only links to
  let finished = 0;
  await pool(info.mods, 6, async (m) => {
    const label = m.name || m.projectId || m.versionId || 'mod';
    try {
      let url = m.url;
      let fileName = m.name && /\.jar$/i.test(m.name) ? m.name : null;
      let hashes = m.hashes;
      if (!url || !allowedUrl(url)) {
        const version = m.versionId ? await modrinth.versionById(m.versionId).catch(() => null)
          : m.projectId ? await modrinth.bestVersion(m.projectId, info.mc) : null;
        const pf = version && modrinth.primaryFile(version);
        if (!pf) throw new Error(m.url ? 'download link from an unknown site' : `not on Modrinth for ${info.mc}`);
        url = pf.url;
        fileName = pf.filename;
        hashes = pf.hashes;
      }
      if (!allowedUrl(url)) throw new Error('download link from an unknown site');
      const dest = inside(targetDir, `mods/${modrinth.safeFileName(fileName || decodeURIComponent(new URL(url).pathname.split('/').pop()))}`);
      await download(url, dest);
      try {
        if (hashes && (hashes.sha1 || hashes.sha512)) modrinth.verify(dest, hashes);
      } catch (err) {
        fs.rmSync(dest, { force: true });
        throw err;
      }
      downloaded++;
    } catch (err) {
      missing.push(`${label} (${err.message})`);
    }
    finished++;
    report({ label: `Downloading mods (${finished}/${info.mods.length})`, progress: 0.15 + (finished / Math.max(1, info.mods.length)) * 0.8 });
  });
  report({ label: 'Done', progress: 1 });
  return { type: 'dawnpack', name: info.name, mc: info.mc, downloaded, extracted: written, missing };
}

/** Runs async jobs with a small concurrency limit. */
async function pool(items, limit, job) {
  let next = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const i = next++;
      await job(items[i], i);
    }
  });
  await Promise.all(workers);
}

/**
 * Imports a pack into an empty profile folder.
 * Returns { type, name, downloaded, extracted, missing: [..] }.
 */
async function importPack(file, targetDir, report = () => {}, fallbackMc) {
  const info = await inspect(file, fallbackMc);
  fs.mkdirSync(targetDir, { recursive: true });
  if (info.type === 'loose') return importLoose(file, info, targetDir, report);
  const missing = [];
  let downloaded = 0;

  if (info.type === 'mrpack') {
    const files = (info.index.files || []).filter((f) => !(f.env && f.env.client === 'unsupported'));
    // check every path before downloading anything
    const jobs = files.map((f) => ({ f, dest: inside(targetDir, f.path), url: (f.downloads || []).find(allowedUrl) }));
    let finished = 0;
    await pool(jobs, 6, async ({ f, dest, url }) => {
      const label = path.basename(dest);
      if (!url || !f.hashes || !f.hashes.sha1) {
        missing.push(`${label} (no allowed download link)`);
      } else {
        try {
          await download(url, dest);
          modrinth.verify(dest, f.hashes);
          downloaded++;
        } catch (err) {
          fs.rmSync(dest, { force: true });
          missing.push(`${label} (${err.message})`);
        }
      }
      finished++;
      report({ label: `Downloading mods (${finished}/${jobs.length})`, progress: (finished / Math.max(1, jobs.length)) * 0.85 });
    });
  } else {
    const count = (info.manifest.files || []).filter((f) => f.required !== false).length;
    // CurseForge only hands out downloads through its keyed API: mods that are not inside the
    // pack cannot be fetched here, so name them instead of failing silently.
    if (count) missing.push(`${count} CurseForge mod${count === 1 ? '' : 's'} listed by ID only – open the pack in the CurseForge App once, then use Import`);
  }

  report({ label: 'Copying settings and files', progress: 0.9 });
  const prefixes = info.type === 'mrpack'
    ? ['overrides/', 'client-overrides/'] // client-overrides win, as the format defines
    : [`${String(info.manifest.overrides || 'overrides').replace(/\/+$/, '')}/`];
  let extracted = 0;
  for (const prefix of prefixes) {
    const { written } = await walkZip(file, (name) => (name.startsWith(prefix) && name.length > prefix.length ? inside(targetDir, name.slice(prefix.length)) : null));
    extracted += written;
  }
  report({ label: 'Done', progress: 1 });
  return { type: info.type, name: info.name, mc: info.mc, downloaded, extracted, missing };
}

/** Downloads a Modrinth modpack (.mrpack) to a temporary file. */
async function downloadModpack(projectId, report = () => {}, mc = versions.DEFAULT) {
  const { project, version, file } = await modrinth.modpackFile(projectId, mc);
  if (!allowedUrl(file.url)) throw new Error('This modpack is hosted somewhere Onix does not download from.');
  const tmp = path.join(os.tmpdir(), `onix-${crypto.randomBytes(4).toString('hex')}.mrpack`);
  await download(file.url, tmp, (p) => report({ label: `Downloading ${project.title}`, progress: p }));
  try {
    modrinth.verify(tmp, file.hashes);
  } catch (err) {
    fs.rmSync(tmp, { force: true });
    throw err;
  }
  return { file: tmp, title: project.title, version: version.version_number };
}

module.exports = { exportProfile, importPack, inspect, downloadModpack, inside, PARTS, ALLOWED_HOSTS };
