/**
 * Onix server for Cloudflare Workers + D1: badge presence, coins (1 per minute played), the cosmetics shop
 * and gifts. Same HTTP API as ../index.js (the Node server), so launcher and mod work with either.
 * Free tier: about 100,000 requests a day – one heartbeat per minute per player.
 *
 * Settings (wrangler.toml [vars]): VERIFY ("1" = prove account ownership with Mojang), UNLIMITED (names
 * with unlimited coins, only while VERIFY is on). The token secret is created in the database on first use.
 */
import shop from '../../shop.json';
import schema from './schema.js';

const MINUTE = 60_000;
const MAX_GAP_MS = 3 * MINUTE;
const ONLINE_TTL_MS = 150_000;
const SESSION_TTL_MS = 24 * 60 * 60 * 1000;
const CHALLENGE_TTL_MS = 60_000;
const MAX_LOOKUP = 500;
const MAX_MESSAGE = 80;
const MAX_GIFTS_KEPT = 50;
const UUID_RE = /^[0-9a-f]{32}$/;
const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
const ITEM_RE = /^[a-z0-9_]{1,64}$/;

const prices = shop.prices || {};
const aliases = shop.aliases || {};
const has = (o, k) => Object.prototype.hasOwnProperty.call(o, k);

const json = (status, body) => new Response(JSON.stringify(body), {
  status,
  headers: { 'content-type': 'application/json', 'cache-control': 'no-store', 'access-control-allow-origin': '*' },
});

function normUuid(v) {
  if (typeof v !== 'string') return null;
  const u = v.replace(/-/g, '').toLowerCase();
  return UUID_RE.test(u) ? u : null;
}

function cleanMessage(v) {
  if (typeof v !== 'string') return '';
  return v.replace(/[\u0000-\u001f\u007f§]/g, '').trim().slice(0, MAX_MESSAGE);
}

function resolveItem(id) {
  if (typeof id !== 'string' || !ITEM_RE.test(id)) return null;
  if (has(prices, id)) return id;
  const a = aliases[id];
  return a && has(prices, a) ? a : null;
}

const hex = (bytes) => [...new Uint8Array(bytes)].map((b) => b.toString(16).padStart(2, '0')).join('');
const randomHex = (n) => hex(crypto.getRandomValues(new Uint8Array(n)));

const schemaReady = new WeakMap();
/** Creates the tables once per running instance and database (IF NOT EXISTS, so it is cheap and safe). */
function ensureSchema(db) {
  if (!schemaReady.has(db)) {
    schemaReady.set(db, db.batch(schema.map((sql) => db.prepare(sql))).catch((err) => {
      schemaReady.delete(db);
      throw err;
    }));
  }
  return schemaReady.get(db);
}

// ---------------------------------------------------------------- tokens (signed, nothing to store)

async function secret(db) {
  let row = await db.prepare('SELECT value FROM meta WHERE key = ?').bind('token_secret').first();
  if (!row) {
    await db.prepare('INSERT OR IGNORE INTO meta (key, value) VALUES (?, ?)').bind('token_secret', randomHex(32)).run();
    row = await db.prepare('SELECT value FROM meta WHERE key = ?').bind('token_secret').first();
  }
  return row.value;
}

async function hmac(key, text) {
  const k = await crypto.subtle.importKey('raw', new TextEncoder().encode(key), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return hex(await crypto.subtle.sign('HMAC', k, new TextEncoder().encode(text)));
}

async function newToken(db, uuid, name) {
  const payload = `${uuid}.${name}.${Date.now() + SESSION_TTL_MS}`;
  return `${payload}.${await hmac(await secret(db), payload)}`;
}

async function sessionFor(db, token) {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 4) return null;
  const [uuid, name, expires, sig] = parts;
  if (!UUID_RE.test(uuid) || !NAME_RE.test(name) || !(Number(expires) > Date.now())) return null;
  if ((await hmac(await secret(db), `${uuid}.${name}.${expires}`)) !== sig) return null;
  return { uuid, name };
}

// ---------------------------------------------------------------- accounts and wallet

function unlimitedNames(env) {
  return new Set(String(env.UNLIMITED == null ? 'Virtualchickenfr,Fuzed_d' : env.UNLIMITED).split(',').map((s) => s.trim().toLowerCase()).filter(Boolean));
}

const verifyOn = (env) => String(env.VERIFY == null ? '1' : env.VERIFY) !== '0';
const isUnlimited = (env, name) => verifyOn(env) && unlimitedNames(env).has(String(name).toLowerCase());

async function account(db, uuid, name) {
  const now = Date.now();
  await db.prepare('INSERT INTO accounts (uuid, name, created) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name WHERE accounts.name <> excluded.name')
    .bind(uuid, name, now).run();
  return db.prepare('SELECT * FROM accounts WHERE uuid = ?').bind(uuid).first();
}

async function view(env, db, a) {
  const owned = (await db.prepare('SELECT item FROM owned WHERE uuid = ? ORDER BY at').bind(a.uuid).all()).results.map((r) => r.item);
  const gifts = (await db.prepare('SELECT id, item, from_name AS "from", message, at FROM gifts WHERE to_uuid = ? AND seen = 0 ORDER BY at').bind(a.uuid).all()).results
    .map((g) => ({ ...g, seen: false }));
  const unlimited = isUnlimited(env, a.name);
  return {
    name: a.name,
    coins: unlimited ? null : a.coins,
    unlimited,
    owned,
    playMinutes: Math.floor(a.play_ms / MINUTE),
    nextCoinMs: MINUTE - a.pending_ms,
    gifts,
  };
}

async function owns(db, uuid, id) {
  if (prices[id] === 0) return true;
  return !!(await db.prepare('SELECT 1 FROM owned WHERE uuid = ? AND item = ?').bind(uuid, id).first());
}

async function heartbeat(env, db, s, playing) {
  const a = await account(db, s.uuid, s.name);
  const now = Date.now();
  if (playing) {
    let { play_ms: playMs, pending_ms: pending, coins } = a;
    const gap = a.last_play ? now - a.last_play : 0;
    if (gap > 0 && gap <= MAX_GAP_MS) {
      playMs += gap;
      pending += gap;
      const minutes = Math.floor(pending / MINUTE);
      coins += minutes;
      pending -= minutes * MINUTE;
    }
    // only counts if nobody else (a second client) moved the clock in between
    await db.prepare('UPDATE accounts SET play_ms = ?, pending_ms = ?, coins = coins + ?, last_play = ? WHERE uuid = ? AND last_play = ?')
      .bind(playMs, pending, coins - a.coins, now, s.uuid, a.last_play).run();
  } else if (a.last_play) {
    await db.prepare('UPDATE accounts SET last_play = 0 WHERE uuid = ?').bind(s.uuid).run();
  }
  return view(env, db, await db.prepare('SELECT * FROM accounts WHERE uuid = ?').bind(s.uuid).first());
}

async function pay(env, db, a, price) {
  if (isUnlimited(env, a.name)) return true;
  const r = await db.prepare('UPDATE accounts SET coins = coins - ? WHERE uuid = ? AND coins >= ?').bind(price, a.uuid, price).run();
  return r.meta.changes === 1;
}

async function buy(env, db, s, raw) {
  const a = await account(db, s.uuid, s.name);
  const id = resolveItem(raw);
  if (!id) return [404, { error: 'This cosmetic does not exist.' }];
  if (await owns(db, a.uuid, id)) return [409, { error: 'You already own this cosmetic.' }];
  const price = prices[id];
  if (!(await pay(env, db, a, price))) return [402, { error: `Not enough coins: ${price} needed, you have ${a.coins}.`, needed: price - a.coins }];
  await db.prepare('INSERT OR IGNORE INTO owned (uuid, item, at) VALUES (?, ?, ?)').bind(a.uuid, id, Date.now()).run();
  return [200, { item: id, price, ...(await view(env, db, await db.prepare('SELECT * FROM accounts WHERE uuid = ?').bind(a.uuid).first())) }];
}

async function gift(env, db, s, raw, rawTo, rawMessage) {
  const a = await account(db, s.uuid, s.name);
  const id = resolveItem(raw);
  if (!id) return [404, { error: 'This cosmetic does not exist.' }];
  const price = prices[id];
  if (price === 0) return [400, { error: 'This cosmetic is free for everyone.' }];
  const to = typeof rawTo === 'string' ? rawTo.trim() : '';
  if (!NAME_RE.test(to)) return [400, { error: 'Enter a Minecraft name.' }];
  const b = await db.prepare('SELECT * FROM accounts WHERE name = ? COLLATE NOCASE ORDER BY created DESC').bind(to).first();
  if (!b) return [404, { error: `${to} has not played with Onix yet.` }];
  if (b.uuid === a.uuid) return [400, { error: 'You cannot gift yourself.' }];
  if (await owns(db, b.uuid, id)) return [409, { error: `${b.name} already owns this cosmetic.` }];
  if (!(await pay(env, db, a, price))) return [402, { error: `Not enough coins: ${price} needed, you have ${a.coins}.`, needed: price - a.coins }];
  const at = Date.now();
  await db.batch([
    db.prepare('INSERT OR IGNORE INTO owned (uuid, item, at) VALUES (?, ?, ?)').bind(b.uuid, id, at),
    db.prepare('INSERT INTO gifts (id, to_uuid, from_uuid, from_name, item, message, at) VALUES (?, ?, ?, ?, ?, ?, ?)')
      .bind(at.toString(36) + randomHex(3), b.uuid, a.uuid, a.name, id, cleanMessage(rawMessage), at),
    db.prepare('DELETE FROM gifts WHERE to_uuid = ? AND id NOT IN (SELECT id FROM gifts WHERE to_uuid = ? ORDER BY at DESC LIMIT ?)').bind(b.uuid, b.uuid, MAX_GIFTS_KEPT),
  ]);
  return [200, { item: id, price, to: b.name, ...(await view(env, db, await db.prepare('SELECT * FROM accounts WHERE uuid = ?').bind(a.uuid).first())) }];
}

async function seen(env, db, s, ids) {
  const a = await account(db, s.uuid, s.name);
  if (Array.isArray(ids)) {
    const list = ids.filter((x) => typeof x === 'string').slice(0, 50);
    if (list.length) await db.prepare(`UPDATE gifts SET seen = 1 WHERE to_uuid = ? AND id IN (${list.map(() => '?').join(',')})`).bind(a.uuid, ...list).run();
  } else {
    await db.prepare('UPDATE gifts SET seen = 1 WHERE to_uuid = ?').bind(a.uuid).run();
  }
  return [200, await view(env, db, a)];
}

// ---------------------------------------------------------------- account proof

/*
 * Mojang's session server refuses requests from Cloudflare Workers, so hasJoined cannot be used here.
 * Instead the client proves its account with its Minecraft player certificate (the chat signing key):
 * Mojang signs (uuid, expiry, public key) with these keys (api.minecraftservices.com/publickeys →
 * playerCertificateKeys), and the client signs our challenge with the matching private key.
 */
const MOJANG_CERT_KEYS = [
  'MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==',
  'MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ==',
];
const unb64 = (s) => Uint8Array.from(atob(String(s)), (c) => c.charCodeAt(0));
const proofMessage = (serverId, uuid) => new TextEncoder().encode(`onix-session:${serverId}:${uuid}`);

/** Checks the player certificate and the signed challenge; returns null when valid, else the reason. */
async function verifyCertificate(uuid, serverId, cert, proof, extraKeys = []) {
  try {
    const expires = Number(cert.expiresAt);
    if (!Number.isFinite(expires)) return 'certificate without expiry';
    if (expires < Date.now()) return 'certificate expired – restart Minecraft or the launcher';
    const key = unb64(cert.publicKey);
    const signed = new Uint8Array(24 + key.length);
    for (let i = 0; i < 16; i++) signed[i] = parseInt(uuid.slice(i * 2, i * 2 + 2), 16);
    new DataView(signed.buffer).setBigInt64(16, BigInt(Math.trunc(expires)));
    signed.set(key, 24);
    const signature = unb64(cert.signature);
    let byMojang = false;
    for (const k of [...MOJANG_CERT_KEYS, ...extraKeys]) {
      const mojang = await crypto.subtle.importKey('spki', unb64(k), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-1' }, false, ['verify']);
      if (await crypto.subtle.verify('RSASSA-PKCS1-v1_5', mojang, signature, signed)) { byMojang = true; break; }
    }
    if (!byMojang) return 'certificate not signed by Mojang';
    const player = await crypto.subtle.importKey('spki', key, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', player, unb64(proof), proofMessage(serverId, uuid));
    return ok ? null : 'challenge signature does not match';
  } catch (e) {
    return `unreadable certificate (${e && e.message ? e.message : e})`;
  }
}

/** Current name of an account (the certificate proves the UUID only). playerdb.co answers Workers, Mojang does not. */
async function nameOf(uuid) {
  const res = await fetch(`https://playerdb.co/api/player/minecraft/${uuid}`, {
    headers: { 'user-agent': 'OnixServer/1.0 (+https://github.com/virtualchickenfr/onix-client)' },
    signal: AbortSignal.timeout(8000),
  });
  if (res.status !== 200) return null;
  const j = await res.json();
  const p = j && j.data && j.data.player;
  return p && normUuid(p.raw_id || p.id) === uuid && typeof p.username === 'string' ? p.username : null;
}

// ---------------------------------------------------------------- routes

async function mojangHasJoined(name, serverId) {
  const url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`;
  const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (res.status !== 200) return null;
  return normUuid((await res.json()).id);
}

async function withAccount(env, db, body, action) {
  const s = await sessionFor(db, body.token);
  if (!s) return [401, { error: 'invalid token' }];
  return action(s);
}

// ---------------------------------------------------------------- friends, chat and McReal

const MAX_FRIENDS = 200;
const MAX_CHAT = 256;
const MAX_IMAGE_B64 = 700 * 1024;
const SERVER_RE = /^[A-Za-z0-9.\-_]{1,80}(:\d{1,5})?$/;
const pair = (x, y) => (x < y ? [x, y] : [y, x]);
const today = () => new Date(Date.now()).toISOString().slice(0, 10);

function cleanChat(v) {
  if (typeof v !== 'string') return '';
  return v.replace(/[\u0000-\u001f\u007f§]/g, ' ').trim().slice(0, MAX_CHAT);
}

async function areFriends(db, x, y) {
  const [a, b] = pair(x, y);
  const row = await db.prepare('SELECT accepted FROM friends WHERE a = ? AND b = ?').bind(a, b).first();
  return !!(row && row.accepted);
}

async function friendList(db, s) {
  const rows = (await db.prepare(
    `SELECT f.a, f.b, f.requester, f.accepted, f.at,
            ac.name AS name, st.server AS server, st.updated AS updated
       FROM friends f
       JOIN accounts ac ON ac.uuid = CASE WHEN f.a = ?1 THEN f.b ELSE f.a END
       LEFT JOIN status st ON st.uuid = ac.uuid
      WHERE f.a = ?1 OR f.b = ?1`).bind(s.uuid).all()).results;
  const unread = (await db.prepare('SELECT from_uuid, COUNT(*) AS n FROM messages WHERE to_uuid = ? AND read = 0 GROUP BY from_uuid').bind(s.uuid).all()).results;
  const unreadBy = Object.fromEntries(unread.map((r) => [r.from_uuid, r.n]));
  const now = Date.now();
  const friends = [];
  const incoming = [];
  const outgoing = [];
  for (const r of rows) {
    const uuid = r.a === s.uuid ? r.b : r.a;
    if (!r.accepted) {
      (r.requester === s.uuid ? outgoing : incoming).push({ uuid, name: r.name, at: r.at });
      continue;
    }
    const online = !!(r.updated && r.updated > now - ONLINE_TTL_MS);
    friends.push({
      uuid, name: r.name, online,
      server: online && r.server ? r.server : '',
      unread: unreadBy[uuid] || 0, since: r.at,
    });
  }
  friends.sort((x, y) => Number(y.online) - Number(x.online) || x.name.localeCompare(y.name));
  return { friends, incoming, outgoing };
}

async function addFriend(db, s, rawName) {
  const name = typeof rawName === 'string' ? rawName.trim() : '';
  if (!NAME_RE.test(name)) return [400, { error: 'Enter a Minecraft name.' }];
  const other = await db.prepare('SELECT uuid, name FROM accounts WHERE name = ? COLLATE NOCASE').bind(name).first();
  if (!other) return [404, { error: `${name} has not played with Onix yet.` }];
  if (other.uuid === s.uuid) return [400, { error: 'That is you.' }];
  const count = (await db.prepare('SELECT COUNT(*) AS n FROM friends WHERE a = ?1 OR b = ?1').bind(s.uuid).first()).n;
  if (count >= MAX_FRIENDS) return [400, { error: 'Your friend list is full.' }];
  const [a, b] = pair(s.uuid, other.uuid);
  const row = await db.prepare('SELECT requester, accepted FROM friends WHERE a = ? AND b = ?').bind(a, b).first();
  if (row && row.accepted) return [200, { ok: true, state: 'friends', name: other.name }];
  if (row && row.requester !== s.uuid) {
    await db.prepare('UPDATE friends SET accepted = 1, at = ? WHERE a = ? AND b = ?').bind(Date.now(), a, b).run();
    return [200, { ok: true, state: 'friends', name: other.name }];
  }
  if (!row) await db.prepare('INSERT INTO friends (a, b, requester, accepted, at) VALUES (?, ?, ?, 0, ?)').bind(a, b, s.uuid, Date.now()).run();
  return [200, { ok: true, state: 'requested', name: other.name }];
}

async function acceptFriend(db, s, rawUuid) {
  const other = normUuid(rawUuid);
  if (!other) return [400, { error: 'uuid required' }];
  const [a, b] = pair(s.uuid, other);
  const r = await db.prepare('UPDATE friends SET accepted = 1, at = ? WHERE a = ? AND b = ? AND requester <> ? AND accepted = 0').bind(Date.now(), a, b, s.uuid).run();
  return r.meta.changes ? [200, { ok: true }] : [404, { error: 'No such request.' }];
}

async function removeFriend(db, s, rawUuid) {
  const other = normUuid(rawUuid);
  if (!other) return [400, { error: 'uuid required' }];
  const [a, b] = pair(s.uuid, other);
  await db.batch([
    db.prepare('DELETE FROM friends WHERE a = ? AND b = ?').bind(a, b),
    db.prepare('DELETE FROM messages WHERE (from_uuid = ?1 AND to_uuid = ?2) OR (from_uuid = ?2 AND to_uuid = ?1)').bind(s.uuid, other),
  ]);
  return [200, { ok: true }];
}

async function sendChat(db, s, rawTo, rawText) {
  const to = normUuid(rawTo);
  const text = cleanChat(rawText);
  if (!to || !text) return [400, { error: 'to and text required' }];
  if (!(await areFriends(db, s.uuid, to))) return [403, { error: 'You can only write to friends.' }];
  const msg = { id: randomHex(12), from: s.uuid, to, text, at: Date.now() };
  await db.prepare('INSERT INTO messages (id, from_uuid, to_uuid, text, at) VALUES (?, ?, ?, ?, ?)').bind(msg.id, msg.from, msg.to, msg.text, msg.at).run();
  return [200, { ok: true, message: msg }];
}

async function chatHistory(db, s, rawWith, after) {
  const other = normUuid(rawWith);
  if (!other) return [400, { error: 'with required' }];
  if (!(await areFriends(db, s.uuid, other))) return [403, { error: 'Not a friend.' }];
  const since = Number(after) > 0 ? Number(after) : 0;
  const rows = (await db.prepare(
    `SELECT id, from_uuid AS "from", to_uuid AS "to", text, at FROM messages
      WHERE ((from_uuid = ?1 AND to_uuid = ?2) OR (from_uuid = ?2 AND to_uuid = ?1)) AND at > ?3
      ORDER BY at DESC LIMIT 100`).bind(s.uuid, other, since).all()).results.reverse();
  await db.prepare('UPDATE messages SET read = 1 WHERE to_uuid = ? AND from_uuid = ? AND read = 0').bind(s.uuid, other).run();
  return [200, { messages: rows }];
}

/** New messages for this player from anyone (the client shows them as notifications), and marks nothing read. */
async function inbox(db, s, after) {
  const since = Number(after) > 0 ? Number(after) : Date.now() - 10 * MINUTE;
  const rows = (await db.prepare(
    `SELECT m.id, m.from_uuid AS "from", a.name AS name, m.text, m.at FROM messages m JOIN accounts a ON a.uuid = m.from_uuid
      WHERE m.to_uuid = ? AND m.read = 0 AND m.at > ? ORDER BY m.at LIMIT 50`).bind(s.uuid, since).all()).results;
  return [200, { messages: rows }];
}

async function postMcReal(db, s, body) {
  const image = typeof body.image === 'string' ? body.image : '';
  if (!/^[A-Za-z0-9+/=]+$/.test(image) || image.length < 100 || image.length > MAX_IMAGE_B64) return [400, { error: 'image (base64 JPEG/PNG, up to 500 KB) required' }];
  const head = atob(image.slice(0, 16));
  const jpeg = head.charCodeAt(0) === 0xff && head.charCodeAt(1) === 0xd8;
  const png = head.startsWith('\u0089PNG');
  if (!jpeg && !png) return [400, { error: 'Only JPEG or PNG.' }];
  const day = today();
  const exists = await db.prepare('SELECT 1 FROM mcreal WHERE uuid = ? AND day = ?').bind(s.uuid, day).first();
  if (exists) return [409, { error: 'You already shared your McReal today.' }];
  await db.prepare('INSERT INTO mcreal (uuid, day, name, caption, image, at) VALUES (?, ?, ?, ?, ?, ?)')
    .bind(s.uuid, day, s.name, cleanChat(body.caption).slice(0, 100), image, Date.now()).run();
  return [200, { ok: true, day }];
}

/** Today's and yesterday's McReals of you and your friends (without the pictures). */
async function mcrealFeed(db, s) {
  const d0 = today();
  const d1 = new Date(Date.now() - 86400e3).toISOString().slice(0, 10);
  const rows = (await db.prepare(
    `SELECT uuid, day, name, caption, at FROM mcreal
      WHERE day IN (?2, ?3) AND (uuid = ?1 OR uuid IN (
        SELECT CASE WHEN a = ?1 THEN b ELSE a END FROM friends WHERE (a = ?1 OR b = ?1) AND accepted = 1))
      ORDER BY at DESC LIMIT 100`).bind(s.uuid, d0, d1).all()).results;
  return [200, { today: d0, posted: rows.some((r) => r.uuid === s.uuid && r.day === d0), posts: rows }];
}

async function mcrealImage(db, s, rawUuid, day) {
  const uuid = normUuid(rawUuid);
  if (!uuid || typeof day !== 'string' || !/^\d{4}-\d\d-\d\d$/.test(day)) return [400, { error: 'uuid and day required' }];
  if (uuid !== s.uuid && !(await areFriends(db, s.uuid, uuid))) return [403, { error: 'Not a friend.' }];
  const row = await db.prepare('SELECT image FROM mcreal WHERE uuid = ? AND day = ?').bind(uuid, day).first();
  return row ? [200, { image: row.image }] : [404, { error: 'not found' }];
}

const routes = {
  'GET /': async (env, db) => [200, { name: 'onix-presence', online: (await db.prepare('SELECT COUNT(*) AS n FROM online WHERE seen > ?').bind(Date.now() - ONLINE_TTL_MS).first()).n }],
  'GET /v1/hello': async (env) => [200, { name: 'onix-presence', version: 3, verify: verifyOn(env), proof: ['certificate', 'hasJoined'], economy: true, host: 'cloudflare' }],

  'POST /v1/session/start': async (env, db, body) => {
    const uuid = normUuid(body.uuid);
    const name = typeof body.name === 'string' && NAME_RE.test(body.name) ? body.name : null;
    if (!uuid || !name) return [400, { error: 'uuid and name required' }];
    if (!verifyOn(env)) return [200, { token: await newToken(db, uuid, name) }];
    const serverId = randomHex(16);
    await db.prepare('INSERT OR REPLACE INTO challenges (uuid, server_id, name, expires) VALUES (?, ?, ?, ?)').bind(uuid, serverId, name, Date.now() + CHALLENGE_TTL_MS).run();
    return [200, { serverId }];
  },

  'POST /v1/session/finish': async (env, db, body) => {
    const uuid = normUuid(body.uuid);
    const c = uuid && (await db.prepare('SELECT * FROM challenges WHERE uuid = ?').bind(uuid).first());
    if (!c || c.expires < Date.now() || c.server_id !== body.serverId) return [400, { error: 'no open challenge' }];
    await db.prepare('DELETE FROM challenges WHERE uuid = ?').bind(uuid).run();
    if (body.cert && typeof body.cert === 'object') {
      // TEST_CERT_KEYS: only for the tests (test.mjs)
      const extra = String(env.TEST_CERT_KEYS || '').split(',').filter(Boolean);
      const problem = await verifyCertificate(uuid, c.server_id, body.cert, body.proof, extra);
      if (problem) {
        console.log(`verification failed for ${uuid}: ${problem}`);
        return [403, { error: `verification failed: ${problem}` }];
      }
      let name;
      try {
        name = await nameOf(uuid);
      } catch {
        name = null;
      }
      if (!name) {
        // the name lookup service is down or slow: go on with the name the client gave, but never with a name
        // that unlocks unlimited coins
        if (unlimitedNames(env).has(String(c.name).toLowerCase())) {
          return [502, { error: 'Could not look up your Minecraft name – try again in a minute' }];
        }
        name = c.name;
      }
      return [200, { token: await newToken(db, uuid, name) }];
    }
    let verified;
    try {
      verified = await mojangHasJoined(c.name, c.server_id);
    } catch {
      return [502, { error: 'Mojang session server unreachable' }];
    }
    if (verified !== uuid) return [403, { error: 'verification failed – this Onix version is too old, update the launcher' }];
    return [200, { token: await newToken(db, uuid, c.name) }];
  },

  'POST /v1/heartbeat': async (env, db, body) => {
    const s = await sessionFor(db, body.token);
    if (!s) return [401, { error: 'invalid token' }];
    if (body.visible === false) await db.prepare('DELETE FROM online WHERE uuid = ?').bind(s.uuid).run();
    else await db.prepare('INSERT OR REPLACE INTO online (uuid, name, seen) VALUES (?, ?, ?)').bind(s.uuid, s.name, Date.now()).run();
    // online for friends (separate from the public badge) and the server they may join, if shared
    if (body.social === false) {
      await db.prepare('DELETE FROM status WHERE uuid = ?').bind(s.uuid).run();
    } else {
      const server = typeof body.server === 'string' && SERVER_RE.test(body.server) ? body.server.toLowerCase() : '';
      await db.prepare('INSERT OR REPLACE INTO status (uuid, server, updated) VALUES (?, ?, ?)').bind(s.uuid, server, Date.now()).run();
    }
    const wallet = await heartbeat(env, db, s, body.playing === true);
    const n = (await db.prepare('SELECT COUNT(*) AS n FROM online WHERE seen > ?').bind(Date.now() - ONLINE_TTL_MS).first()).n;
    return [200, { ok: true, online: n, wallet }];
  },

  'GET /v1/shop': async () => [200, { version: shop.version || 1, prices, aliases }],
  'POST /v1/wallet': (env, db, body) => withAccount(env, db, body, async (s) => [200, await view(env, db, await account(db, s.uuid, s.name))]),
  'POST /v1/shop/buy': (env, db, body) => withAccount(env, db, body, (s) => buy(env, db, s, body.item)),
  'POST /v1/gift': (env, db, body) => withAccount(env, db, body, (s) => gift(env, db, s, body.item, body.to, body.message)),
  'POST /v1/gifts/seen': (env, db, body) => withAccount(env, db, body, (s) => seen(env, db, s, body.ids)),

  'POST /v1/friends': (env, db, body) => withAccount(env, db, body, async (s) => [200, await friendList(db, s)]),
  'POST /v1/friends/add': (env, db, body) => withAccount(env, db, body, (s) => addFriend(db, s, body.name)),
  'POST /v1/friends/accept': (env, db, body) => withAccount(env, db, body, (s) => acceptFriend(db, s, body.uuid)),
  'POST /v1/friends/remove': (env, db, body) => withAccount(env, db, body, (s) => removeFriend(db, s, body.uuid)),
  'POST /v1/chat/send': (env, db, body) => withAccount(env, db, body, (s) => sendChat(db, s, body.to, body.text)),
  'POST /v1/chat/history': (env, db, body) => withAccount(env, db, body, (s) => chatHistory(db, s, body.with, body.after)),
  'POST /v1/chat/inbox': (env, db, body) => withAccount(env, db, body, (s) => inbox(db, s, body.after)),
  'POST /v1/mcreal/post': (env, db, body) => withAccount(env, db, body, (s) => postMcReal(db, s, body)),
  'POST /v1/mcreal/feed': (env, db, body) => withAccount(env, db, body, (s) => mcrealFeed(db, s)),
  'POST /v1/mcreal/image': (env, db, body) => withAccount(env, db, body, (s) => mcrealImage(db, s, body.uuid, body.day)),

  'POST /v1/leave': async (env, db, body) => {
    const s = await sessionFor(db, body.token);
    if (s) await db.prepare('DELETE FROM online WHERE uuid = ?').bind(s.uuid).run();
    return [200, { ok: true }];
  },

  'POST /v1/lookup': async (env, db, body) => {
    if (!Array.isArray(body.uuids)) return [400, { error: 'uuids required' }];
    const asked = [...new Set(body.uuids.slice(0, MAX_LOOKUP).map(normUuid).filter(Boolean))];
    const result = [];
    for (let i = 0; i < asked.length; i += 90) {   // D1 allows 100 bound values per statement
      const part = asked.slice(i, i + 90);
      const rows = await db.prepare(`SELECT uuid FROM online WHERE seen > ? AND uuid IN (${part.map(() => '?').join(',')})`).bind(Date.now() - ONLINE_TTL_MS, ...part).all();
      result.push(...rows.results.map((r) => r.uuid));
    }
    return [200, { online: result }];
  },
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === 'OPTIONS') return json(204, {});
    const handler = routes[`${request.method} ${url.pathname}`];
    if (!handler) return json(404, { error: 'not found' });
    let body = {};
    if (request.method === 'POST') {
      const text = await request.text();
      if (text.length > (url.pathname === '/v1/mcreal/post' ? 768 * 1024 : 64 * 1024)) return json(413, { error: 'Body too large' });
      try {
        body = text ? JSON.parse(text) : {};
      } catch {
        return json(400, { error: 'Invalid JSON' });
      }
      if (!body || typeof body !== 'object') body = {};
    }
    try {
      await ensureSchema(env.DB);
      const [status, payload] = await handler(env, env.DB, body);
      return json(status, payload);
    } catch (err) {
      console.error(err);
      return json(500, { error: 'internal error' });
    }
  },

  /** Every hour: forget old challenges and players who went offline long ago. */
  async scheduled(event, env) {
    const now = Date.now();
    await ensureSchema(env.DB);
    await env.DB.batch([
      env.DB.prepare('DELETE FROM challenges WHERE expires < ?').bind(now),
      env.DB.prepare('DELETE FROM online WHERE seen < ?').bind(now - 24 * 60 * 60 * 1000),
      env.DB.prepare('DELETE FROM status WHERE updated < ?').bind(now - 24 * 60 * 60 * 1000),
      env.DB.prepare('DELETE FROM messages WHERE at < ?').bind(now - 30 * 24 * 60 * 60 * 1000),
      env.DB.prepare('DELETE FROM mcreal WHERE at < ?').bind(now - 3 * 24 * 60 * 60 * 1000),
    ]);
  },
};
