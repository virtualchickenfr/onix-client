const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { download, USER_AGENT } = require('./download');

/**
 * Modrinth API (https://docs.modrinth.com): search, install with required dependencies,
 * identify jars by hash and check them for updates – always for Fabric on this Minecraft version.
 */

const API = 'https://api.modrinth.com/v2';
const versions = require('./versions');

/** Default Minecraft version; every function takes the profile's version as `mc`. */
const MC_VERSION = versions.DEFAULT;
const LOADER = 'fabric';
/** The launcher manages these itself (see game.js), so they are never installed twice. */
const MANAGED = {
  P7dR8mSH: 'Fabric API', AANobbMI: 'Sodium', gvQqBUqZ: 'Lithium', uXXizFIs: 'FerriteCore', '5ZwdcRci': 'ImmediatelyFast', NNAgCjsB: 'EntityCulling',
};
const MANIFEST = '.onix-modrinth.json';
const SORTS = ['relevance', 'downloads', 'follows', 'newest', 'updated'];

async function api(route, { method = 'GET', body } = {}) {
  const res = await fetch(API + route, {
    method,
    headers: { 'User-Agent': USER_AGENT, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 404) return null;
  if (res.status === 429) throw new Error('Modrinth is rate limiting requests – wait a minute and try again.');
  if (!res.ok) throw new Error(`Modrinth answered with HTTP ${res.status}.`);
  return res.json();
}

const q = (value) => encodeURIComponent(JSON.stringify(value));

function sha(file, algorithm) {
  return crypto.createHash(algorithm).update(fs.readFileSync(file)).digest('hex');
}

/** Hashes of a downloaded file must match what Modrinth lists – otherwise it is deleted. */
function verify(file, hashes) {
  if (hashes && hashes.sha512 && sha(file, 'sha512') !== hashes.sha512) throw new Error(`${path.basename(file)} failed the sha512 check`);
  if (hashes && hashes.sha1 && sha(file, 'sha1') !== hashes.sha1) throw new Error(`${path.basename(file)} failed the sha1 check`);
}

/** Only plain file names – nothing from the network decides where a file lands. */
function safeFileName(name) {
  const base = path.basename(String(name || ''));
  if (!base || base === '.' || base === '..' || /[<>:"|?*\u0000-\u001f]/.test(base)) throw new Error(`Invalid file name: ${name}`);
  return base;
}

// ------------------------------------------------------------------ manifest of installed projects

function readManifest(modsDir) {
  try {
    return JSON.parse(fs.readFileSync(path.join(modsDir, MANIFEST), 'utf8'));
  } catch {
    return {};
  }
}

function writeManifest(modsDir, data) {
  fs.mkdirSync(modsDir, { recursive: true });
  fs.writeFileSync(path.join(modsDir, MANIFEST), JSON.stringify(data, null, 2));
}

/** Drops manifest entries whose file no longer exists (deleted by hand). */
function cleanManifest(modsDir) {
  const m = readManifest(modsDir);
  let changed = false;
  for (const [id, entry] of Object.entries(m)) {
    const exists = fs.existsSync(path.join(modsDir, entry.file)) || fs.existsSync(path.join(modsDir, entry.file + '.disabled'));
    if (!exists) {
      delete m[id];
      changed = true;
    }
  }
  if (changed) writeManifest(modsDir, m);
  return m;
}

// ------------------------------------------------------------------ search

/** Search types: Fabric mods and modpacks, resource packs (any loader), shaders (Iris). */
const TYPES = ['mod', 'modpack', 'resourcepack', 'shader'];
const SHADER_LOADER = 'iris';
/** Iris runs shader packs on Fabric (it needs Sodium, which the performance pack brings). */
const IRIS = 'YL57xq9U';

/** Modrinth's name for a profile's loader (vanilla profiles see Fabric content only for modpacks). */
const loaderOf = (loader) => (['fabric', 'quilt', 'neoforge', 'forge'].includes(loader) ? loader : LOADER);

async function search({ query = '', type = 'mod', offset = 0, sort = 'relevance', limit = 20, mc = MC_VERSION, loader = LOADER } = {}) {
  const kind = TYPES.includes(type) ? type : 'mod';
  const facets = [[`project_type:${kind}`], [].concat(mc).map((v) => `versions:${v}`)];
  if (kind === 'mod') facets.push(loader === 'quilt' ? ['categories:quilt', 'categories:fabric'] : [`categories:${loaderOf(loader)}`]);
  if (kind === 'modpack') facets.push([`categories:${LOADER}`]);
  if (kind === 'shader') facets.push([`categories:${SHADER_LOADER}`]);
  const index = SORTS.includes(sort) ? sort : 'relevance';
  const res = await api(`/search?query=${encodeURIComponent(query)}&facets=${q(facets)}&index=${index}&offset=${Math.max(0, offset | 0)}&limit=${Math.min(50, limit)}`);
  return {
    total: res ? res.total_hits : 0,
    hits: (res ? res.hits : []).map((h) => ({
      id: h.project_id,
      slug: h.slug,
      title: h.title,
      description: h.description,
      author: h.author,
      downloads: h.downloads,
      icon: h.icon_url || null,
      categories: (h.categories || []).filter((c) => c !== LOADER && c !== SHADER_LOADER && c !== 'minecraft').slice(0, 3),
      clientSide: h.client_side,
      managed: loader === LOADER && !!MANAGED[h.project_id],
    })),
  };
}

/** The newest version of a project for Fabric on this Minecraft version (releases first). */
async function bestVersion(projectId, mc = MC_VERSION, loader = LOADER) {
  const loaders = loader === 'quilt' ? ['quilt', 'fabric'] : [loaderOf(loader)];
  const list = await api(`/project/${encodeURIComponent(projectId)}/version?loaders=${q(loaders)}&game_versions=${q([mc])}`);
  if (!list || !list.length) return null;
  return list.find((v) => v.version_type === 'release') || list[0];
}

const primaryFile = (version) => version.files.find((f) => f.primary) || version.files[0];
const fitsHere = (v, mc = MC_VERSION, loader = LOADER) => v && (v.game_versions || []).includes(mc)
  && (v.loaders || []).some((l) => l === loaderOf(loader) || (loader === 'quilt' && l === 'fabric'));

// ------------------------------------------------------------------ install

/**
 * Installs a mod and its required dependencies into a mods folder.
 * Returns { installed: [{title, version}], skipped: [reason], warnings: [text] }.
 */
async function install(projectId, modsDir, report = () => {}, mc = MC_VERSION, loader = LOADER) {
  const managedHere = loader === LOADER;
  fs.mkdirSync(modsDir, { recursive: true });
  const manifest = cleanManifest(modsDir);
  const installed = [];
  const skipped = [];
  const warnings = [];
  const seen = new Set();
  const queue = [{ projectId, versionId: null, root: true }];

  while (queue.length) {
    const item = queue.shift();
    if (item.projectId && seen.has(item.projectId)) continue;

    let version = null;
    if (item.versionId) {
      const pinned = await api(`/version/${encodeURIComponent(item.versionId)}`);
      if (pinned && !item.projectId) item.projectId = pinned.project_id;
      // a pinned version for another loader or game version: use the newest fitting one instead
      version = fitsHere(pinned, mc, loader) ? pinned : null;
    }
    if (!item.projectId) continue;
    if (seen.has(item.projectId)) continue;
    seen.add(item.projectId);

    if (managedHere && MANAGED[item.projectId]) {
      if (item.root) skipped.push(`${MANAGED[item.projectId]} is already managed by the launcher`);
      continue;
    }
    const project = await api(`/project/${encodeURIComponent(item.projectId)}`);
    if (!project) {
      warnings.push(`A required dependency (${item.projectId}) no longer exists on Modrinth.`);
      continue;
    }
    if (project.project_type !== 'mod') {
      if (item.root) throw new Error(`${project.title} is a ${project.project_type}, not a mod.`);
      continue;
    }
    if (project.client_side === 'unsupported') {
      if (item.root) throw new Error(`${project.title} only runs on servers.`);
      continue;
    }
    if (!version) version = await bestVersion(item.projectId, mc, loader);
    if (!version) {
      const msg = `${project.title} has no version for ${loaderOf(loader)} ${mc}.`;
      if (item.root) throw new Error(msg);
      warnings.push(`${msg} It is required by the mod you installed.`);
      continue;
    }

    const file = primaryFile(version);
    const name = safeFileName(file.filename);
    const current = manifest[item.projectId];
    const target = path.join(modsDir, name);
    if (current && current.versionId === version.id && (fs.existsSync(path.join(modsDir, current.file)) || fs.existsSync(path.join(modsDir, current.file + '.disabled')))) {
      if (item.root) skipped.push(`${project.title} ${version.version_number} is already installed`);
    } else {
      report({ label: `Downloading ${project.title}`, progress: 0 });
      await download(file.url, target, (p) => report({ label: `Downloading ${project.title}`, progress: p }));
      try {
        verify(target, file.hashes);
      } catch (err) {
        fs.rmSync(target, { force: true });
        throw err;
      }
      if (current && current.file !== name) {
        fs.rmSync(path.join(modsDir, current.file), { force: true });
        fs.rmSync(path.join(modsDir, current.file + '.disabled'), { force: true });
      }
      manifest[item.projectId] = { file: name, versionId: version.id, version: version.version_number, title: project.title, icon: project.icon_url || null };
      writeManifest(modsDir, manifest);
      installed.push({ title: project.title, version: version.version_number, dependency: !item.root });
    }

    for (const dep of version.dependencies || []) {
      if (dep.dependency_type === 'required') queue.push({ projectId: dep.project_id || null, versionId: dep.version_id || null });
      if (dep.dependency_type === 'incompatible' && dep.project_id && manifest[dep.project_id]) {
        warnings.push(`${project.title} does not work together with ${manifest[dep.project_id].title}.`);
      }
    }
  }
  return { installed, skipped, warnings };
}

// ------------------------------------------------------------------ identify and update

/** Looks jars up on Modrinth by sha1. Returns Map<absolute path, version> for the ones Modrinth knows. */
async function identify(files) {
  const byHash = new Map();
  for (const f of files) byHash.set(sha(f, 'sha1'), f);
  const out = new Map();
  if (!byHash.size) return out;
  const res = await api('/version_files', { method: 'POST', body: { hashes: [...byHash.keys()], algorithm: 'sha1' } });
  for (const [hash, version] of Object.entries(res || {})) {
    const file = byHash.get(hash);
    if (file) out.set(file, version);
  }
  return out;
}

const jarFiles = (modsDir) => (fs.existsSync(modsDir) ? fs.readdirSync(modsDir) : [])
  .filter((f) => f.endsWith('.jar') || f.endsWith('.jar.disabled'))
  .map((f) => path.join(modsDir, f));

/**
 * For jar files (any loader, any Minecraft version): the newest version of the same Modrinth project that fits mc and loader.
 * One request for all files. Returns Map(file → { projectId, versionId, version, url, filename, hashes }).
 */
async function versionsFor(files, mc = MC_VERSION, loader = LOADER) {
  const out = new Map();
  if (!files.length) return out;
  const byHash = new Map();
  for (const f of files) {
    try {
      byHash.set(sha(f, 'sha1'), f);
    } catch {
      // unreadable file: left out
    }
  }
  const latest = await api('/version_files/update', {
    method: 'POST',
    body: { hashes: [...byHash.keys()], algorithm: 'sha1', loaders: loader === 'quilt' ? ['quilt', 'fabric'] : [loaderOf(loader)], game_versions: [mc] },
  });
  for (const [hash, version] of Object.entries(latest || {})) {
    const f = primaryFile(version);
    const src = byHash.get(hash);
    if (!f || !src) continue;
    out.set(src, { projectId: version.project_id, versionId: version.id, version: version.version_number, url: f.url, filename: f.filename, hashes: f.hashes, same: f.hashes.sha1 === hash });
  }
  return out;
}

/** Mods in the folder with a newer Fabric version on Modrinth. */
async function checkUpdates(modsDir, mc = MC_VERSION, loader = LOADER) {
  const files = jarFiles(modsDir).filter((f) => !/onix-client-/.test(path.basename(f)));
  if (!files.length) return [];
  const hashes = new Map(files.map((f) => [sha(f, 'sha1'), f]));
  const latest = await api('/version_files/update', {
    method: 'POST',
    body: { hashes: [...hashes.keys()], algorithm: 'sha1', loaders: loader === 'quilt' ? ['quilt', 'fabric'] : [loaderOf(loader)], game_versions: [mc] },
  });
  const updates = [];
  for (const [hash, version] of Object.entries(latest || {})) {
    const file = primaryFile(version);
    if (!file || file.hashes.sha1 === hash || MANAGED[version.project_id]) continue;
    updates.push({ file: path.basename(hashes.get(hash)), projectId: version.project_id, versionId: version.id, version: version.version_number });
  }
  if (updates.length) {
    const projects = await api(`/projects?ids=${q(updates.map((u) => u.projectId))}`);
    for (const u of updates) {
      const p = (projects || []).find((x) => x.id === u.projectId);
      u.title = p ? p.title : u.file;
    }
  }
  return updates;
}

/** Installs the given updates (from checkUpdates); a disabled mod stays disabled. */
async function applyUpdates(modsDir, updates, report = () => {}, mc = MC_VERSION, loader = LOADER) {
  const manifest = cleanManifest(modsDir);
  const done = [];
  for (const u of updates) {
    const version = await api(`/version/${encodeURIComponent(u.versionId)}`);
    if (!fitsHere(version, mc, loader)) continue;
    const file = primaryFile(version);
    const name = safeFileName(file.filename);
    const oldName = safeFileName(u.file);
    const disabled = oldName.endsWith('.disabled');
    const target = path.join(modsDir, disabled ? name + '.disabled' : name);
    await download(file.url, target, (p) => report({ label: `Updating ${u.title}`, progress: p }));
    try {
      verify(target, file.hashes);
    } catch (err) {
      fs.rmSync(target, { force: true });
      throw err;
    }
    if (path.join(modsDir, oldName) !== target) fs.rmSync(path.join(modsDir, oldName), { force: true });
    manifest[u.projectId] = { ...(manifest[u.projectId] || {}), file: name, versionId: version.id, version: version.version_number, title: u.title };
    done.push(`${u.title} ${version.version_number}`);
  }
  writeManifest(modsDir, manifest);
  return done;
}

// ------------------------------------------------------------------ resource packs and shaders

/**
 * Installs a resource pack or shader pack into its folder of the profile (dir), with the same manifest as mods.
 * Shaders also bring Iris into the mods folder. Returns { installed, skipped, warnings } like install().
 */
async function installAsset(projectId, kind, dir, modsDir, report = () => {}, mc = MC_VERSION, loader = LOADER) {
  const project = await api(`/project/${encodeURIComponent(projectId)}`);
  if (!project) throw new Error('This project no longer exists on Modrinth.');
  if (project.project_type !== kind) throw new Error(`${project.title} is a ${project.project_type}, not a ${kind === 'shader' ? 'shader pack' : 'resource pack'}.`);
  const loaders = kind === 'shader' ? `&loaders=${q([SHADER_LOADER])}` : '';
  const list = await api(`/project/${encodeURIComponent(projectId)}/version?game_versions=${q([mc])}${loaders}`);
  const version = list && list.length ? list.find((v) => v.version_type === 'release') || list[0] : null;
  if (!version) throw new Error(`${project.title} has no version for Minecraft ${mc}.`);
  const file = primaryFile(version);
  const name = safeFileName(file.filename);
  fs.mkdirSync(dir, { recursive: true });
  const manifest = cleanManifest(dir);
  const result = { installed: [], skipped: [], warnings: [] };
  const current = manifest[projectId];
  if (current && current.versionId === version.id && fs.existsSync(path.join(dir, current.file))) {
    result.skipped.push(`${project.title} ${version.version_number} is already installed`);
  } else {
    const target = path.join(dir, name);
    report({ label: `Downloading ${project.title}`, progress: 0 });
    await download(file.url, target, (p) => report({ label: `Downloading ${project.title}`, progress: p }));
    try {
      verify(target, file.hashes);
    } catch (err) {
      fs.rmSync(target, { force: true });
      throw err;
    }
    if (current && current.file !== name) fs.rmSync(path.join(dir, current.file), { force: true });
    manifest[projectId] = { file: name, versionId: version.id, version: version.version_number, title: project.title, icon: project.icon_url || null };
    writeManifest(dir, manifest);
    result.installed.push({ title: project.title, version: version.version_number });
  }
  if (kind === 'shader') {
    try {
      const iris = await install(IRIS, modsDir, report, mc, loader);
      for (const i of iris.installed) result.installed.push({ ...i, dependency: true });
      result.warnings.push(...iris.warnings);
    } catch (err) {
      result.warnings.push(`Iris (needed for shaders) could not be added: ${err.message}`);
    }
  }
  return result;
}

/** Newest modpack file (.mrpack) of a Modrinth modpack project for this version. */
async function modpackFile(projectId, mc = MC_VERSION) {
  const project = await api(`/project/${encodeURIComponent(projectId)}`);
  if (!project || project.project_type !== 'modpack') throw new Error('This project is not a modpack.');
  const version = await bestVersion(projectId, mc);
  if (!version) throw new Error(`${project.title} has no version for Fabric ${mc}.`);
  const file = primaryFile(version);
  if (!/\.mrpack$/i.test(file.filename)) throw new Error(`${project.title} does not offer a .mrpack file.`);
  return { project, version, file };
}

/** One version by its id (null when Modrinth does not know it). */
const versionById = (id) => api(`/version/${encodeURIComponent(id)}`);

module.exports = {
  versionById, primaryFile, versionsFor,
  search, bestVersion, install, installAsset, identify, checkUpdates, applyUpdates, modpackFile,
  readManifest, writeManifest, cleanManifest, verify, sha, safeFileName, MANAGED, MC_VERSION, TYPES, IRIS,
};
