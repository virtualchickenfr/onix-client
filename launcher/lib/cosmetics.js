const fs = require('fs');
const path = require('path');

/**
 * Reads and writes config/onix/cosmetics.json in the profile folder (the same file the mod uses).
 * Ids are checked against the shared catalog (src/assets/cosmetics/catalog.json, made by tools/cosmetics.py).
 */
const SLOTS = ['cape', 'head', 'face', 'neck', 'back', 'aura', 'pet', 'nametag', 'tag'];
const nametags = require('./nametags');
let catalog = null;

function loadCatalog() {
  if (catalog) return catalog;
  const raw = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'src', 'assets', 'cosmetics', 'catalog.json'), 'utf8'));
  const slotOf = new Map(raw.items.map((i) => [i.id, i.slot]));
  catalog = { slotOf, aliases: raw.aliases || {} };
  return catalog;
}

/** The catalog id for an id or an old alias, or null. */
function resolve(id) {
  if (typeof id !== 'string') return null;
  const { slotOf, aliases } = loadCatalog();
  if (slotOf.has(id)) return id;
  const alias = aliases[id];
  return alias && slotOf.has(alias) ? alias : null;
}

function file(profileDir) {
  return path.join(profileDir, 'config', 'onix', 'cosmetics.json');
}

/** The selection as { slot: id }. Old files ("hat", "extra", old ids) are read the same way the mod reads them. */
function get(profileDir) {
  let raw;
  try {
    raw = JSON.parse(fs.readFileSync(file(profileDir), 'utf8'));
  } catch {
    return {};
  }
  if (!raw || typeof raw !== 'object') return {};
  const { slotOf } = loadCatalog();
  const out = {};
  if (typeof raw.tagText === 'string' && !nametags.problem(raw.tagText)) out.tagText = raw.tagText;
  for (const [key, value] of Object.entries(raw)) {
    if (key === 'tagText') continue;
    const id = resolve(value);
    if (!id) continue;
    const slot = slotOf.get(id);
    if (SLOTS.includes(key)) {
      if (key === slot) out[slot] = id;
    } else if (!out[slot]) {
      out[slot] = id; // "hat" / "extra" from older versions
    }
  }
  return out;
}

function write(profileDir, data) {
  fs.mkdirSync(path.dirname(file(profileDir)), { recursive: true });
  const ordered = {};
  for (const slot of SLOTS) if (data[slot]) ordered[slot] = data[slot];
  if (data.tagText) ordered.tagText = data.tagText;
  fs.writeFileSync(file(profileDir), JSON.stringify(ordered, null, 2));
  return ordered;
}

/** Puts an item into a slot (id null = empty the slot). Unknown ids or ids of another slot are refused. */
function set(profileDir, slot, id) {
  if (!SLOTS.includes(slot)) throw new Error(`Unknown cosmetic slot "${slot}"`);
  const data = get(profileDir);
  if (id == null || id === '') {
    delete data[slot];
  } else {
    const resolved = resolve(String(id));
    if (!resolved || loadCatalog().slotOf.get(resolved) !== slot) throw new Error(`"${id}" is not a ${slot} cosmetic`);
    data[slot] = resolved;
  }
  return write(profileDir, data);
}

/** Replaces the whole selection, keeping only valid entries. */
function setAll(profileDir, outfit) {
  const data = {};
  const old = get(profileDir).tagText;
  if (old) data.tagText = old;
  if (outfit && typeof outfit === 'object') {
    for (const slot of SLOTS) {
      const id = resolve(outfit[slot]);
      if (id && loadCatalog().slotOf.get(id) === slot) data[slot] = id;
    }
  }
  return write(profileDir, data);
}

/** The text of custom tags (empty = none); refused with a message if it is not allowed. */
function setTagText(profileDir, text) {
  const clean = String(text || '').replace(/\s+/g, ' ').trim();
  if (clean) {
    const problem = nametags.problem(clean);
    if (problem) throw new Error(problem);
  }
  const data = get(profileDir);
  if (clean) data.tagText = clean;
  else delete data.tagText;
  return write(profileDir, data);
}

module.exports = { get, set, setAll, setTagText, resolve, SLOTS };
