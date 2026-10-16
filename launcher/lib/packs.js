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
async function inspect(file) {
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
    if (manifest.manifestType !== 'minecraftModpack') throw new Error('This zip is not a CurseForge modpack.');
    const mc = manifest.minecraft || {};
    if (!versions.isSupported(mc.version)) throw new Error(`This pack is for Minecraft ${mc.version || 'an unknown version'}. Onix runs on ${versions.LIST.join(' and ')}.`);
    const loaders = (mc.modLoaders || []).map((l) => String(l.id || ''));
    const other = loaders.find((l) => !/^fabric/i.test(l));
    if (other) throw new Error(`This pack needs ${other.split('-')[0]}. Onix runs on Fabric.`);
    return { type: 'curseforge', name: String(manifest.name || path.basename(file, '.zip')), manifest, mc: mc.version };
  }
  throw new Error('This is not a Modrinth (.mrpack) or CurseForge modpack.');
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
async function importPack(file, targetDir, report = () => {}) {
  const info = await inspect(file);
  fs.mkdirSync(targetDir, { recursive: true });
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
