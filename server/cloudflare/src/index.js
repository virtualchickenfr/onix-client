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

const routes = {
  'GET /': async (env, db) => [200, { name: 'onix-presence', online: (await db.prepare('SELECT COUNT(*) AS n FROM online WHERE seen > ?').bind(Date.now() - ONLINE_TTL_MS).first()).n }],
  'GET /v1/hello': async (env) => [200, { name: 'onix-presence', version: 2, verify: verifyOn(env), economy: true, host: 'cloudflare' }],

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
    let verified;
    try {
      verified = await mojangHasJoined(c.name, c.server_id);
    } catch {
      return [502, { error: 'Mojang session server unreachable' }];
    }
    if (verified !== uuid) return [403, { error: 'verification failed' }];
    return [200, { token: await newToken(db, uuid, c.name) }];
  },

  'POST /v1/heartbeat': async (env, db, body) => {
    const s = await sessionFor(db, body.token);
    if (!s) return [401, { error: 'invalid token' }];
    if (body.visible === false) await db.prepare('DELETE FROM online WHERE uuid = ?').bind(s.uuid).run();
    else await db.prepare('INSERT OR REPLACE INTO online (uuid, name, seen) VALUES (?, ?, ?)').bind(s.uuid, s.name, Date.now()).run();
    const wallet = await heartbeat(env, db, s, body.playing === true);
    const n = (await db.prepare('SELECT COUNT(*) AS n FROM online WHERE seen > ?').bind(Date.now() - ONLINE_TTL_MS).first()).n;
    return [200, { ok: true, online: n, wallet }];
  },

  'GET /v1/shop': async () => [200, { version: shop.version || 1, prices, aliases }],
  'POST /v1/wallet': (env, db, body) => withAccount(env, db, body, async (s) => [200, await view(env, db, await account(db, s.uuid, s.name))]),
  'POST /v1/shop/buy': (env, db, body) => withAccount(env, db, body, (s) => buy(env, db, s, body.item)),
  'POST /v1/gift': (env, db, body) => withAccount(env, db, body, (s) => gift(env, db, s, body.item, body.to, body.message)),
  'POST /v1/gifts/seen': (env, db, body) => withAccount(env, db, body, (s) => seen(env, db, s, body.ids)),

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
      if (text.length > 64 * 1024) return json(413, { error: 'Body too large' });
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
    ]);
  },
};
