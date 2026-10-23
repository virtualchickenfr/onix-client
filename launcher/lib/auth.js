const { safeStorage } = require('electron');
const { Auth } = require('msmc');

const authManager = new Auth('select_account');

/**
 * Minecraft sessions stay valid for about a day. Reusing one for a few hours skips the
 * Microsoft → Xbox → Minecraft round trips (several seconds) on every launch.
 */
const SESSION_MAX_AGE = 6 * 60 * 60 * 1000;
/** Offline play: an account whose ownership Microsoft confirmed within this time may start without internet. */
const OFFLINE_MAX_AGE = 30 * 24 * 60 * 60 * 1000;

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

const publicInfo = (a, activeUuid) => ({ name: a.name, uuid: a.uuid, active: a.uuid === activeUuid, lastUsed: a.lastUsed || a.addedAt || 0, demo: !!a.demo });

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
  return a ? { name: a.name, uuid: a.uuid, demo: !!a.demo } : null;
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

/**
 * Opens the Microsoft sign-in window and adds the account (or refreshes it if it is already saved).
 * A Microsoft account without Minecraft: Java Edition is added as a demo account (Mojang's free demo).
 */
async function login(store) {
  const xbox = await authManager.launch('electron', {
    width: 520,
    height: 680,
    resizable: false,
    title: 'Sign in with Microsoft',
  });
  let minecraft = null;
  let failure = null;
  try {
    minecraft = await minecraftFrom(xbox);
  } catch (e) {
    failure = e;
  }
  if (!minecraft) {
    if (isNetworkError(failure)) throw failure;
    const entry = rememberDemo(store, xbox);
    store.set('activeAccount', entry.uuid);
    return { name: entry.name, uuid: entry.uuid, demo: true };
  }
  const entry = remember(store, xbox, minecraft);
  store.set('activeAccount', entry.uuid);
  return { name: entry.name, uuid: entry.uuid };
}

/** A Microsoft account that does not own the game: plays Mojang's demo (one world, five in-game days, no servers). */
function rememberDemo(store, xbox) {
  const list = accounts(store);
  const key = `demo-${require('crypto').createHash('sha256').update(String(xbox.save())).digest('hex').slice(0, 8)}`;
  let entry = list.find((a) => a.demo && a.demoKey === key);
  if (!entry) {
    const uuid = require('crypto').randomUUID().replace(/-/g, '');
    entry = { uuid, name: 'Demo', demo: true, demoKey: key, addedAt: Date.now() };
    list.push(entry);
  }
  entry.refresh = encrypt(xbox.save());
  save(store, list);
  return entry;
}

/** True for "no internet / Microsoft not reachable", false for a real answer such as "does not own the game". */
function isNetworkError(e) {
  if (!e) return false;
  const text = `${e.code || ''} ${e.message || e} ${e.cause ? `${e.cause.code || ''} ${e.cause.message || ''}` : ''}`;
  return /ENOTFOUND|EAI_AGAIN|ECONNREFUSED|ECONNRESET|ETIMEDOUT|ENETUNREACH|EHOSTUNREACH|fetch failed|network|socket|getaddrinfo|timed? ?out/i.test(text);
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
  if (entry.demo) return demoSession(entry);
  const fresh = reuse && freshSession(entry);
  if (fresh) return fresh;
  try {
    return await renew(store, entry);
  } catch (e) {
    // no internet: an account Microsoft confirmed recently may still play offline (singleplayer and LAN)
    if (isNetworkError(e) && entry.sessionAt && Date.now() - entry.sessionAt < OFFLINE_MAX_AGE) {
      return { ...offlineSession(entry), offline: true };
    }
    throw e;
  }
}

/** Credentials for playing without Microsoft: the account's real name and id, but no token (servers refuse it). */
function offlineSession(entry) {
  return { access_token: '0', client_token: '0', uuid: entry.uuid, name: entry.name, user_properties: '{}', meta: { type: 'msa' } };
}

function demoSession(entry) {
  return { ...offlineSession(entry), demo: true };
}

/**
 * Renews the active account's session in the background (at launcher start and after
 * switching accounts), so pressing Play does not have to wait for Microsoft.
 */
function prefetch(store) {
  const entry = active(store);
  if (!entry || entry.demo || freshSession(entry)) return Promise.resolve(false);
  return renew(store, entry).then(() => true, () => false);
}

module.exports = { login, refresh, prefetch, current, list, switchTo, remove, logout };
