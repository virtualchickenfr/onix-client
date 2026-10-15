'use strict';

/**
 * Onix presence server.
 *
 * Tells Onix clients which players are currently playing with Onix, so they can show the
 * Onix badge next to those players (kept in memory only), and runs the Onix coins: 1 coin per
 * minute played with Onix, a cosmetics shop and gifts (economy.js, stored in DATA_FILE).
 *
 * With VERIFY=1 (the default) a player proves they own their account with the same
 * Mojang session check that Minecraft servers use, so nobody can show a badge for someone else.
 *
 * No dependencies. Node 18+.
 *
 *   PORT=8080 VERIFY=1 node index.js
 *
 * Economy settings: DATA_FILE (default ./data/economy.json), ECONOMY=0 turns coins off,
 * UNLIMITED=name,name (accounts with unlimited coins; only while VERIFY is on).
 */

const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { Economy } = require('./economy');

const PORT = Number(process.env.PORT) || 8080;
const TRUST_PROXY = process.env.TRUST_PROXY === '1';
const VERIFY = process.env.VERIFY !== '0';
const ONLINE_TTL_MS = (Number(process.env.ONLINE_TTL) || 150) * 1000;
const SESSION_TTL_MS = 24 * 60 * 60 * 1000;
const CHALLENGE_TTL_MS = 60 * 1000;
const MAX_LOOKUP = 500;
const MAX_BODY = 64 * 1024;
const RATE_PER_MINUTE = Number(process.env.RATE_LIMIT) || 240;
const ECONOMY = process.env.ECONOMY !== '0';
const UNLIMITED = (process.env.UNLIMITED == null ? 'Virtualchickenfr,Fuzed_d' : process.env.UNLIMITED).split(',').map((s) => s.trim()).filter(Boolean);

function loadShop() {
  const file = process.env.SHOP_FILE || path.join(__dirname, 'shop.json');
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (err) {
    console.error(`No shop prices (${file}): ${err.message}`);
    return { prices: {}, aliases: {} };
  }
}

const shop = loadShop();
const economy = ECONOMY
  ? new Economy({ dataFile: process.env.DATA_FILE || path.join(__dirname, 'data', 'economy.json'), shop, unlimited: UNLIMITED, verify: VERIFY })
  : null;

/** uuid (no dashes, lower case) -> { name, seen } */
const online = new Map();
/** token -> { uuid, name, expires } */
const sessions = new Map();
/** uuid -> { serverId, name, expires } */
const challenges = new Map();
/** ip -> { count, windowStart } */
const rates = new Map();

const UUID_RE = /^[0-9a-f]{32}$/;
const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;

function normUuid(value) {
  if (typeof value !== 'string') return null;
  const u = value.replace(/-/g, '').toLowerCase();
  return UUID_RE.test(u) ? u : null;
}

function send(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(json),
    'cache-control': 'no-store',
  });
  res.end(json);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(Object.assign(new Error('Body too large'), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch {
        reject(Object.assign(new Error('Invalid JSON'), { status: 400 }));
      }
    });
    req.on('error', reject);
  });
}

function rateLimited(ip) {
  const now = Date.now();
  let r = rates.get(ip);
  if (!r || now - r.windowStart > 60_000) {
    r = { count: 0, windowStart: now };
    rates.set(ip, r);
  }
  r.count++;
  return r.count > RATE_PER_MINUTE;
}

function newToken(uuid, name) {
  const token = crypto.randomBytes(24).toString('hex');
  sessions.set(token, { uuid, name, expires: Date.now() + SESSION_TTL_MS });
  return token;
}

function sessionFor(token) {
  if (typeof token !== 'string') return null;
  const s = sessions.get(token);
  if (!s || s.expires < Date.now()) return null;
  return s;
}

async function mojangHasJoined(name, serverId) {
  const url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`;
  const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (res.status !== 200) return null;
  const body = await res.json();
  return normUuid(body.id);
}

/** Runs an economy action for the session's account. */
function withAccount(body, action) {
  if (!economy) return [404, { error: 'coins are turned off on this server' }];
  const s = sessionFor(body.token);
  if (!s) return [401, { error: 'invalid token' }];
  economy.account(s.uuid, s.name);
  return action(s);
}

const routes = {
  'GET /': () => [200, { name: 'onix-presence', online: online.size }],
  'GET /v1/hello': () => [200, { name: 'onix-presence', version: 2, verify: VERIFY, economy: !!economy }],

  /** Step 1: without verification returns a token right away; otherwise a serverId to join. */
  'POST /v1/session/start': (body) => {
    const uuid = normUuid(body.uuid);
    const name = typeof body.name === 'string' && NAME_RE.test(body.name) ? body.name : null;
    if (!uuid || !name) return [400, { error: 'uuid and name required' }];
    if (!VERIFY) return [200, { token: newToken(uuid, name) }];
    const serverId = crypto.randomBytes(16).toString('hex');
    challenges.set(uuid, { serverId, name, expires: Date.now() + CHALLENGE_TTL_MS });
    return [200, { serverId }];
  },

  /** Step 2: after the client called Mojang's join endpoint with serverId. */
  'POST /v1/session/finish': async (body) => {
    const uuid = normUuid(body.uuid);
    const c = uuid && challenges.get(uuid);
    if (!c || c.expires < Date.now() || c.serverId !== body.serverId) return [400, { error: 'no open challenge' }];
    challenges.delete(uuid);
    let verified;
    try {
      verified = await mojangHasJoined(c.name, c.serverId);
    } catch {
      return [502, { error: 'Mojang session server unreachable' }];
    }
    if (verified !== uuid) return [403, { error: 'verification failed' }];
    return [200, { token: newToken(uuid, c.name) }];
  },

  /**
   * Marks the player as online (or hidden, with visible: false). With playing: true the time since the
   * last heartbeat counts as playtime for coins.
   */
  'POST /v1/heartbeat': (body) => {
    const s = sessionFor(body.token);
    if (!s) return [401, { error: 'invalid token' }];
    if (body.visible === false) online.delete(s.uuid);
    else online.set(s.uuid, { name: s.name, seen: Date.now() });
    const reply = { ok: true, online: online.size };
    if (economy) reply.wallet = economy.heartbeat(s.uuid, s.name, body.playing === true);
    return [200, reply];
  },

  // ---------------------------------------------------------------- coins, shop, gifts

  /** Prices of every cosmetic (0 = free for everyone). */
  'GET /v1/shop': () => (economy ? [200, { version: shop.version || 1, prices: shop.prices, aliases: shop.aliases || {} }] : [404, { error: 'coins are turned off on this server' }]),

  'POST /v1/wallet': (body) => withAccount(body, (s) => [200, economy.view(s.uuid)]),
  'POST /v1/shop/buy': (body) => withAccount(body, (s) => economy.buy(s.uuid, body.item)),
  'POST /v1/gift': (body) => withAccount(body, (s) => economy.gift(s.uuid, body.item, body.to, body.message)),
  'POST /v1/gifts/seen': (body) => withAccount(body, (s) => economy.seen(s.uuid, body.ids)),

  'POST /v1/leave': (body) => {
    const s = sessionFor(body.token);
    if (s) online.delete(s.uuid);
    return [200, { ok: true }];
  },

  /** Which of these players use Onix right now? */
  'POST /v1/lookup': (body) => {
    if (!Array.isArray(body.uuids)) return [400, { error: 'uuids required' }];
    const now = Date.now();
    const result = [];
    for (const raw of body.uuids.slice(0, MAX_LOOKUP)) {
      const u = normUuid(raw);
      const entry = u && online.get(u);
      if (entry && now - entry.seen < ONLINE_TTL_MS) result.push(u);
    }
    return [200, { online: result }];
  },
};

const server = http.createServer(async (req, res) => {
  // X-Forwarded-For can be set by anyone: only trust it behind a proxy you run (TRUST_PROXY=1)
  const forwarded = TRUST_PROXY ? String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() : '';
  const ip = forwarded || req.socket.remoteAddress || '';
  if (rateLimited(ip)) return send(res, 429, { error: 'too many requests' });
  const path = (req.url || '/').split('?')[0];
  const handler = routes[`${req.method} ${path}`];
  if (!handler) return send(res, 404, { error: 'not found' });
  try {
    const body = req.method === 'POST' ? await readBody(req) : {};
    const [status, payload] = await handler(body);
    send(res, status, payload);
  } catch (err) {
    send(res, err.status || 500, { error: err.status ? err.message : 'internal error' });
  }
});

// housekeeping
setInterval(() => {
  const now = Date.now();
  for (const [u, e] of online) if (now - e.seen > ONLINE_TTL_MS) online.delete(u);
  for (const [t, s] of sessions) if (s.expires < now) sessions.delete(t);
  for (const [u, c] of challenges) if (c.expires < now) challenges.delete(u);
  for (const [ip, r] of rates) if (now - r.windowStart > 120_000) rates.delete(ip);
}, 30_000).unref();

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Onix presence server on :${PORT} (verify: ${VERIFY}, coins: ${economy ? 'on' : 'off'})`);
    if (economy && !VERIFY && UNLIMITED.length) console.log('Unlimited coins are ignored while VERIFY=0.');
  });
  const stop = () => {
    if (economy) economy.flush();
    process.exit(0);
  };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
}

module.exports = { server, economy };
