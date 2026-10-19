'use strict';

// Skins and capes of the signed-in Microsoft account through Mojang's profile API (like the official launcher),
// plus a local skin library so earlier skins are never lost: the skin being replaced is saved there first.
// Taken over from the Carbon Launcher.
const path = require('path');
const crypto = require('crypto');
const fsp = require('fs').promises;
const { USER_AGENT } = require('./download');

const API = 'https://api.minecraftservices.com/minecraft/profile';
const VARIANTS = ['classic', 'slim'];

let DIR = null;
let INDEX = null;
let tokenOf = null;

/** dir: where the library lives; token: async () => Minecraft access token of the active account. */
function init(dir, token) {
  DIR = dir;
  INDEX = path.join(DIR, 'index.json');
  tokenOf = token;
}

const https = (url) => String(url || '').replace(/^http:/, 'https:');
const sha1 = (buf) => crypto.createHash('sha1').update(buf).digest('hex');
const dataUrl = (buf) => `data:image/png;base64,${buf.toString('base64')}`;

/** Throws unless buf is a Minecraft skin: a PNG of 64×64 (or the old 64×32). */
function checkSkin(buf) {
  const png = buf.length > 24 && buf.readUInt32BE(0) === 0x89504e47 && buf.toString('ascii', 12, 16) === 'IHDR';
  if (!png) throw new Error('That is not a PNG file.');
  const w = buf.readUInt32BE(16);
  const h = buf.readUInt32BE(20);
  if (w !== 64 || (h !== 64 && h !== 32)) throw new Error(`A skin has to be 64×64 pixels (this file: ${w}×${h}).`);
  if (buf.length > 1024 * 1024) throw new Error('The file is too big for a skin.');
}

// ------------------------------------------------------------------ Mojang API

async function api(method, sub = '', body) {
  const headers = { Authorization: `Bearer ${await tokenOf()}`, 'User-Agent': USER_AGENT };
  let payload = body;
  if (body && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(API + sub, { method, headers, body: payload, signal: AbortSignal.timeout(20000) });
  if (res.status === 429) throw new Error('Mojang only allows a few changes per minute – try again in a moment.');
  if (res.status === 401) throw new Error('The sign-in has expired – sign in again.');
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    let msg = '';
    try { msg = JSON.parse(text).errorMessage || ''; } catch { /* not JSON */ }
    if (res.status === 400 && /skin/i.test(msg + text)) throw new Error(`Mojang rejected this skin: ${msg || 'invalid file'}`);
    throw new Error(`Mojang answered with error ${res.status}${msg ? `: ${msg}` : ''}`);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

async function texture(url) {
  const res = await fetch(https(url), { headers: { 'User-Agent': USER_AGENT }, signal: AbortSignal.timeout(15000) });
  if (!res.ok) throw new Error(`Texture could not be loaded (HTTP ${res.status})`);
  return Buffer.from(await res.arrayBuffer());
}

/** What the page shows: the active skin and every cape, with the textures as data URLs. */
async function view(profile) {
  if (!profile) throw new Error('Mojang sent no profile – try again in a moment.');
  const active = (profile.skins || []).find((s) => s.state === 'ACTIVE') || (profile.skins || [])[0] || null;
  let skin = null;
  if (active) {
    const buf = await texture(active.url);
    skin = { url: https(active.url), variant: (active.variant || 'CLASSIC').toLowerCase(), data: dataUrl(buf), sha1: sha1(buf) };
  }
  const capes = await Promise.all((profile.capes || []).map(async (c) => ({
    id: c.id,
    name: c.alias || 'Cape',
    active: c.state === 'ACTIVE',
    data: await texture(c.url).then(dataUrl).catch(() => null),
  })));
  return { name: profile.name, skin, capes };
}

/** Mojang answers changes with the new profile; ask again if an answer ever comes without it. */
async function after(p) {
  return p && Array.isArray(p.skins) ? view(p) : profile();
}

async function profile() {
  return view(await api('GET'));
}

/** Uploads a skin from the library. The skin it replaces is kept in the library. */
async function apply(sha, variant) {
  if (!VARIANTS.includes(variant)) variant = 'classic';
  const entry = (await index()).find((e) => e.sha1 === sha);
  if (!entry) throw new Error('This skin is no longer in your library.');
  const buf = await fsp.readFile(path.join(DIR, `${entry.sha1}.png`));
  await keepCurrent();
  entry.variant = variant;
  await saveIndex();
  const form = new FormData();
  form.append('variant', variant);
  form.append('file', new Blob([buf], { type: 'image/png' }), 'skin.png');
  return after(await api('POST', '/skins', form));
}

/** Switches the arm width (classic 4 px / slim 3 px) of the current skin. */
async function setVariant(variant) {
  if (!VARIANTS.includes(variant)) throw new Error('Unknown model');
  const now = await profile();
  if (!now.skin) throw new Error('This account has no skin.');
  const result = await after(await api('POST', '/skins', { variant, url: now.skin.url }));
  const entry = (await index()).find((e) => e.sha1 === now.skin.sha1);
  if (entry) {
    entry.variant = variant;
    await saveIndex();
  }
  return result;
}

/** Back to a default skin (Mojang picks Steve, Alex or one of the other default characters). */
async function reset() {
  await keepCurrent();
  return after(await api('DELETE', '/skins/active'));
}

/** Shows the cape with that id, or none (null). */
async function setCape(capeId) {
  const p = capeId ? await api('PUT', '/capes/active', { capeId }) : await api('DELETE', '/capes/active');
  return after(p);
}

// ------------------------------------------------------------------ library

let entries = null;

async function index() {
  if (!entries) {
    try {
      entries = JSON.parse(await fsp.readFile(INDEX, 'utf8'));
    } catch {
      entries = [];
    }
  }
  return entries;
}

async function saveIndex() {
  await fsp.mkdir(DIR, { recursive: true });
  await fsp.writeFile(INDEX, JSON.stringify(entries, null, 2));
}

async function add(buf, name, variant = 'classic') {
  checkSkin(buf);
  const list = await index();
  const sha = sha1(buf);
  let entry = list.find((e) => e.sha1 === sha);
  if (!entry) {
    await fsp.mkdir(DIR, { recursive: true });
    await fsp.writeFile(path.join(DIR, `${sha}.png`), buf);
    entry = { sha1: sha, name: String(name || 'Skin').slice(0, 40), variant, added: Date.now() };
    list.unshift(entry);
    await saveIndex();
  }
  return entry;
}

/** Saves the account's current skin to the library (no-op when it is already there). */
async function keepCurrent() {
  const now = await profile().catch(() => null);
  if (!now || !now.skin) return null;
  const buf = Buffer.from(now.skin.data.split(',')[1], 'base64');
  const day = new Date().toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  return add(buf, `${now.name} · ${day}`, now.skin.variant);
}

async function library() {
  const list = await index();
  const out = [];
  for (const e of list) {
    const buf = await fsp.readFile(path.join(DIR, `${e.sha1}.png`)).catch(() => null);
    if (buf) out.push({ ...e, data: dataUrl(buf) });
  }
  return out;
}

/** Adds PNG files to the library. Returns { added, errors }. */
async function importFiles(files) {
  const added = [];
  const errors = [];
  for (const f of files) {
    try {
      const buf = await fsp.readFile(f);
      added.push(await add(buf, path.basename(f, path.extname(f))));
    } catch (e) {
      errors.push(`${path.basename(f)}: ${e.message}`);
    }
  }
  return { added, errors };
}

async function removeFromLibrary(sha) {
  await index();
  entries = entries.filter((e) => e.sha1 !== sha);
  await saveIndex();
  await fsp.rm(path.join(DIR, `${path.basename(String(sha))}.png`), { force: true });
  return library();
}

async function rename(sha, name) {
  const e = (await index()).find((x) => x.sha1 === sha);
  if (e) {
    e.name = String(name || '').trim().slice(0, 40) || e.name;
    await saveIndex();
  }
  return library();
}

module.exports = {
  init, profile, apply, setVariant, reset, setCape, library, importFiles, removeFromLibrary, rename, keepCurrent, checkSkin,
};
