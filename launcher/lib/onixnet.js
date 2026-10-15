'use strict';

/**
 * Talks to the Onix server (see /server) for coins, the cosmetics shop and gifts.
 * The launcher proves the account the same way the game does: it asks the server for a
 * serverId, joins it at Mojang's session server with the account's access token, and gets a
 * session token back.
 */

/** Same as OnixClient.DEFAULT_SERVER in the mod: the server used when none is set. */
const DEFAULT_SERVER = '';
const MOJANG_JOIN = 'https://sessionserver.mojang.com/session/minecraft/join';

/** "server|uuid" → token */
const tokens = new Map();

function normalize(url) {
  const u = String(url || '').trim().replace(/\/+$/, '');
  if (!u) return '';
  if (!/^https?:\/\/[^\s/]+/i.test(u)) throw new Error('The Onix server address must start with http:// or https://');
  return u;
}

async function request(url, body, timeoutMs = 10_000) {
  let res;
  try {
    res = await fetch(url, {
      method: body ? 'POST' : 'GET',
      headers: body ? { 'content-type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch {
    throw Object.assign(new Error('The Onix server cannot be reached.'), { status: 0 });
  }
  let json = null;
  try {
    json = await res.json();
  } catch {
    json = null;
  }
  return { status: res.status, json };
}

function fail(r) {
  const message = r.json && r.json.error ? r.json.error : `Onix server error ${r.status}`;
  return Object.assign(new Error(message), { status: r.status, details: r.json });
}

/** creds = { uuid, name, access_token } of the signed-in Minecraft account. */
async function signIn(server, creds) {
  const uuid = String(creds.uuid || '').replace(/-/g, '');
  const start = await request(`${server}/v1/session/start`, { uuid, name: creds.name });
  if (start.status !== 200 || !start.json) throw fail(start);
  if (start.json.token) return start.json.token;
  const join = await fetch(MOJANG_JOIN, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ accessToken: creds.access_token, selectedProfile: uuid, serverId: start.json.serverId }),
    signal: AbortSignal.timeout(10_000),
  }).catch(() => null);
  if (!join || (join.status !== 204 && join.status !== 200)) throw new Error('Mojang did not confirm your account. Sign in again and retry.');
  const finish = await request(`${server}/v1/session/finish`, { uuid, serverId: start.json.serverId }, 15_000);
  if (finish.status !== 200 || !finish.json || !finish.json.token) throw fail(finish);
  return finish.json.token;
}

/** Sends an authenticated request; signs in (again) when needed. */
async function call(serverUrl, getCreds, path, body = {}) {
  const server = normalize(serverUrl);
  if (!server) throw Object.assign(new Error('No Onix server is set (Settings → Onix server).'), { status: -1 });
  const creds = await getCreds();
  const key = `${server}|${creds.uuid}`;
  for (let attempt = 0; attempt < 2; attempt++) {
    if (!tokens.has(key)) tokens.set(key, await signIn(server, creds));
    const r = await request(server + path, { ...body, token: tokens.get(key) });
    if (r.status === 401 && attempt === 0) {
      tokens.delete(key);
      continue;
    }
    if (r.status !== 200 || !r.json) throw fail(r);
    return r.json;
  }
  throw new Error('Could not sign in to the Onix server.');
}

const ITEM_RE = /^[a-z0-9_]{1,64}$/;
const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;

module.exports = {
  DEFAULT_SERVER,
  normalize,
  wallet: (server, getCreds) => call(server, getCreds, '/v1/wallet'),
  buy(server, getCreds, item) {
    if (!ITEM_RE.test(String(item))) throw new Error('Unknown cosmetic');
    return call(server, getCreds, '/v1/shop/buy', { item });
  },
  gift(server, getCreds, item, to, message) {
    if (!ITEM_RE.test(String(item))) throw new Error('Unknown cosmetic');
    if (!NAME_RE.test(String(to || '').trim())) throw new Error('Enter a Minecraft name.');
    return call(server, getCreds, '/v1/gift', { item, to: String(to).trim(), message: String(message || '').slice(0, 80) });
  },
  seen: (server, getCreds, ids) => call(server, getCreds, '/v1/gifts/seen', { ids: Array.isArray(ids) ? ids.slice(0, 50) : undefined }),
  _tokens: tokens,
};
