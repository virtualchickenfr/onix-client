const { safeStorage } = require('electron');
const { Auth } = require('msmc');

const authManager = new Auth('select_account');

/**
 * Minecraft sessions stay valid for about a day. Reusing one for a few hours skips the
 * Microsoft → Xbox → Minecraft round trips (several seconds) on every launch.
 */
const SESSION_MAX_AGE = 6 * 60 * 60 * 1000;

function encrypt(text) {
  if (safeStorage.isEncryptionAvailable()) {
    return { enc: true, data: safeStorage.encryptString(text).toString('base64') };
  }
  return { enc: false, data: Buffer.from(text, 'utf8').toString('base64') };
}

function decrypt(value) {
  const buf = Buffer.from(value.data, 'base64');
  return value.enc ? safeStorage.decryptString(buf) : buf.toString('utf8');
}

// ------------------------------------------------------------------ saved accounts

/** Moves the single account of older versions into the account list. */
function migrate(store) {
  const old = store.get('account');
  if (!old) return;
  const accounts = store.get('accounts') || [];
  if (!accounts.some((a) => a.uuid === old.uuid)) accounts.push({ ...old, addedAt: Date.now() });
  store.set('accounts', accounts);
  if (!store.get('activeAccount')) store.set('activeAccount', old.uuid);
  store.set('account', undefined);
}

function accounts(store) {
  migrate(store);
  return store.get('accounts') || [];
}

function save(store, list) {
  store.set('accounts', list);
}

function active(store) {
  const list = accounts(store);
  return list.find((a) => a.uuid === store.get('activeAccount')) || list[0] || null;
}

const publicInfo = (a, activeUuid) => ({ name: a.name, uuid: a.uuid, active: a.uuid === activeUuid, lastUsed: a.lastUsed || a.addedAt || 0 });

/** All accounts that have signed in on this launcher; the active one first. */
function list(store) {
  const current = active(store);
  const id = current ? current.uuid : null;
  return accounts(store)
    .map((a) => publicInfo(a, id))
    .sort((a, b) => Number(b.active) - Number(a.active) || b.lastUsed - a.lastUsed);
}

function current(store) {
  const a = active(store);
  return a ? { name: a.name, uuid: a.uuid } : null;
}

/** Stores (or updates) an account after Microsoft returned a session. */
function remember(store, xbox, minecraft) {
  const list = accounts(store);
  const uuid = minecraft.profile.id;
  const entry = list.find((a) => a.uuid === uuid) || { uuid, addedAt: Date.now() };
  entry.name = minecraft.profile.name;
  entry.refresh = encrypt(xbox.save());
  entry.session = encrypt(JSON.stringify(minecraft.mclc()));
  entry.sessionAt = Date.now();
  if (!list.includes(entry)) list.push(entry);
  save(store, list);
  return entry;
}

async function minecraftFrom(xbox) {
  const minecraft = await xbox.getMinecraft();
  if (!minecraft || !minecraft.profile) {
    throw new Error('This Microsoft account does not own Minecraft: Java Edition.');
  }
  return minecraft;
}

/** Opens the Microsoft sign-in window and adds the account (or refreshes it if it is already saved). */
async function login(store) {
  const xbox = await authManager.launch('electron', {
    width: 520,
    height: 680,
    resizable: false,
    title: 'Sign in with Microsoft',
  });
  const minecraft = await minecraftFrom(xbox);
  const entry = remember(store, xbox, minecraft);
  store.set('activeAccount', entry.uuid);
  return { name: entry.name, uuid: entry.uuid };
}

/** Makes a saved account the active one. No sign-in window – its saved token is used at launch. */
function switchTo(store, uuid) {
  const entry = accounts(store).find((a) => a.uuid === uuid);
  if (!entry) throw new Error('This account is no longer saved. Please sign in again.');
  store.set('activeAccount', uuid);
  return { name: entry.name, uuid: entry.uuid };
}

/** Forgets a saved account; the next one becomes active. */
function remove(store, uuid) {
  const list = accounts(store).filter((a) => a.uuid !== uuid);
  save(store, list);
  if (store.get('activeAccount') === uuid) store.set('activeAccount', list[0] ? list[0].uuid : undefined);
  return current(store);
}

function logout(store) {
  const a = active(store);
  if (a) remove(store, a.uuid);
}

// ------------------------------------------------------------------ sessions

const inFlight = new Map();

function freshSession(entry) {
  if (!entry.session || !entry.sessionAt || Date.now() - entry.sessionAt > SESSION_MAX_AGE) return null;
  try {
    return JSON.parse(decrypt(entry.session));
  } catch {
    return null;
  }
}

/** Gets a new session from the saved refresh token. One request per account at a time. */
function renew(store, entry) {
  if (inFlight.has(entry.uuid)) return inFlight.get(entry.uuid);
  const job = (async () => {
    try {
      const xbox = await authManager.refresh(decrypt(entry.refresh));
      const minecraft = await minecraftFrom(xbox);
      remember(store, xbox, minecraft);
      return minecraft.mclc();
    } finally {
      inFlight.delete(entry.uuid);
    }
  })();
  inFlight.set(entry.uuid, job);
  return job;
}

/**
 * Returns MCLC credentials for the active account: the saved session while it is fresh,
 * otherwise a renewed one.
 */
async function refresh(store, reuse = true) {
  const entry = active(store);
  if (!entry) throw new Error('Sign in with Microsoft first.');
  const list = accounts(store);
  const stored = list.find((a) => a.uuid === entry.uuid);
  stored.lastUsed = Date.now();
  save(store, list);
  return (reuse && freshSession(entry)) || renew(store, entry);
}

/**
 * Renews the active account's session in the background (at launcher start and after
 * switching accounts), so pressing Play does not have to wait for Microsoft.
 */
function prefetch(store) {
  const entry = active(store);
  if (!entry || freshSession(entry)) return Promise.resolve(false);
  return renew(store, entry).then(() => true, () => false);
}

module.exports = { login, refresh, prefetch, current, list, switchTo, remove, logout };
