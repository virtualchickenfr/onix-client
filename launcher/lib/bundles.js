'use strict';

/**
 * Mod bundles: hand-picked sets of Modrinth mods that go into a profile with one click (on the first start in the
 * welcome steps, later in Profile → Bundles). A bundle only names Modrinth projects; each one is installed with its
 * required dependencies for the profile's Minecraft version (lib/modrinth.js). A mod without a version for that
 * Minecraft version is skipped and named, the rest of the bundle still goes in.
 * Mods the launcher manages itself (Fabric API, the performance pack, see game.js) are never part of a bundle.
 */

const modrinth = require('./modrinth');
const { USER_AGENT } = require('./download');

const BUNDLES = [
  {
    id: 'qol',
    name: 'Quality of Life',
    about: 'Small things that make everyday play nicer: inventory sorting by mouse, food stats, block info and a mod list.',
    color: '#5fd0da',
    mods: ['mouse-tweaks', 'appleskin', 'jade', 'modmenu', 'controlling', 'shulkerboxtooltip'],
  },
  {
    id: 'visuals',
    name: 'Visuals',
    about: 'Connected glass and textures, light from torches in your hand and nicer particles. No shaders needed.',
    color: '#e0a84f',
    mods: ['continuity', 'lambdynamiclights', 'visuality', 'falling-leaves'],
  },
  {
    id: 'audio',
    name: 'Sound',
    about: 'Sound that echoes in caves and footsteps that match the block you walk on.',
    color: '#9b7cf0',
    mods: ['sound-physics-remastered', 'presence-footsteps'],
  },
  {
    id: 'fps',
    name: 'Extra FPS',
    about: 'On top of the performance pack: lower FPS in the background, fewer hidden blocks drawn, faster world loading.',
    color: '#6fd08a',
    mods: ['dynamic-fps', 'moreculling', 'c2me-fabric'],
  },
  {
    id: 'creator',
    name: 'Content Creator',
    about: 'Record your games and edit them later, with free camera paths – for videos and montages.',
    color: '#f06f8a',
    mods: ['flashback'],
  },
];

const byId = (id) => BUNDLES.find((b) => b.id === id);

/** Titles and icons of every mod in the bundles, one request (cached for the session). */
let infoCache = null;
async function info() {
  if (infoCache) return infoCache;
  const slugs = [...new Set(BUNDLES.flatMap((b) => b.mods))];
  const res = await fetch(`https://api.modrinth.com/v2/projects?ids=${encodeURIComponent(JSON.stringify(slugs))}`, { headers: { 'User-Agent': USER_AGENT } });
  if (!res.ok) throw new Error(`Modrinth answered with HTTP ${res.status}.`);
  const out = {};
  for (const p of await res.json()) out[p.slug] = { id: p.id, title: p.title, icon: p.icon_url || null, description: p.description || '' };
  infoCache = out;
  return out;
}

/** The bundles with their mods and, for a mods folder, which ones are already in it. */
async function list(modsDir) {
  let mods = {};
  try {
    mods = await info();
  } catch {
    // offline: the bundles are still listed, by slug
  }
  const installed = new Set(Object.keys(modrinth.cleanManifest(modsDir)));
  return BUNDLES.map((b) => {
    const items = b.mods.map((slug) => {
      const m = mods[slug] || { id: null, title: slug, icon: null, description: '' };
      return { slug, ...m, installed: !!(m.id && installed.has(m.id)) || installed.has(slug) };
    });
    return { id: b.id, name: b.name, about: b.about, color: b.color, mods: items, installed: items.every((i) => i.installed) };
  });
}

/**
 * Installs bundles into a mods folder. report gets { label, progress } like modrinth.install.
 * Returns { installed: [title], missing: [title], failed: [{ title, error }] }.
 */
async function install(ids, modsDir, report = () => {}, mc, loader) {
  let mods = {};
  try {
    mods = await info();
  } catch (err) {
    throw new Error(`Could not reach Modrinth: ${err.message}`);
  }
  const slugs = [...new Set(ids.map(byId).filter(Boolean).flatMap((b) => b.mods))];
  const out = { installed: [], missing: [], failed: [] };
  for (let i = 0; i < slugs.length; i++) {
    const m = mods[slugs[i]];
    if (!m) {
      out.missing.push(slugs[i]);
      continue;
    }
    const step = (p) => report({ label: `${p.label || `Installing ${m.title}`} (${i + 1}/${slugs.length})`, progress: (i + (p.progress || 0)) / slugs.length });
    step({ progress: 0 });
    try {
      const res = await modrinth.install(m.id, modsDir, step, mc, loader);
      if (res.installed.length || res.skipped.length) out.installed.push(m.title);
    } catch (err) {
      if (/has no version for/.test(err.message)) out.missing.push(m.title);
      else out.failed.push({ title: m.title, error: err.message });
    }
  }
  report({ label: 'Done', progress: 1 });
  return out;
}

module.exports = { BUNDLES, list, install };
