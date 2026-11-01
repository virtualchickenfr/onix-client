// Tests for the Cloudflare server against a local SQLite database (node:sqlite stands in for D1).
//   node test.mjs        (Node 22.5+)
import assert from 'node:assert';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const here = path.dirname(new URL(import.meta.url).pathname);
globalThis.__shop = { version: 1, prices: { free_cape: 0, hat_common: 60, wings_epic: 400, crown_gold: 150, name_fire: 5 }, aliases: { crown: 'crown_gold' },
  looks: { name_fire: 'nametag' } };
const src = fs.readFileSync(path.join(here, 'src/index.js'), 'utf8').replace("import shop from '../../shop.json';", 'const shop = globalThis.__shop;')
  .replace("import schema from './schema.js';", `import schema from '${path.join(here, 'src/schema.js')}';`);
const tmp = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'onix-cf-')), 'worker.mjs');
fs.writeFileSync(tmp, src);
const worker = (await import(tmp)).default;

class Stmt {
  constructor(db, sql, args = []) { Object.assign(this, { db, sql, args }); }
  bind(...args) { return new Stmt(this.db, this.sql, args); }
  async first() { return this.db.prepare(this.sql).get(...this.args) ?? null; }
  async all() { return { results: this.db.prepare(this.sql).all(...this.args) }; }
  async run() { const r = this.db.prepare(this.sql).run(...this.args); return { meta: { changes: Number(r.changes) } }; }
}
function d1() {
  const db = new DatabaseSync(':memory:');
  return {
    prepare: (sql) => new Stmt(db, sql),
    async batch(stmts) { db.exec('BEGIN'); try { for (const s of stmts) await s.run(); db.exec('COMMIT'); } catch (e) { db.exec('ROLLBACK'); throw e; } },
  };
}

let now = 1_000_000_000;
Date.now = () => now;
const env = { DB: d1(), VERIFY: '0', UNLIMITED: 'Virtualchickenfr,Fuzed_d', OWNERS: 'Lena' };
async function call(method, p, body, e = env) {
  const res = await worker.fetch(new Request(`https://x${p}`, { method, body: body ? JSON.stringify(body) : undefined }), e);
  return [res.status, await res.json()];
}
const A = 'a'.repeat(32);
const B = 'b'.repeat(32);
const login = async (uuid, name, e = env) => (await call('POST', '/v1/session/start', { uuid, name }, e))[1].token;

// sessions and tokens
const ta = await login(A, 'Alice');
const tb = await login(B, 'Bob');
assert.ok(ta && ta.split('.').length === 4);
assert.strictEqual((await call('POST', '/v1/wallet', { token: ta.replace(/.$/, ta.endsWith('0') ? '1' : '0') }))[0], 401, 'tampered token');
assert.strictEqual((await call('POST', '/v1/wallet', { token: `${B}.Alice.${now + 1e9}.00` }))[0], 401, 'forged token');

// earning: 1 coin per minute, gaps over 3 minutes do not count, spam does not earn more
await call('POST', '/v1/heartbeat', { token: ta, playing: true });
for (let i = 0; i < 10; i++) { now += 60_000; await call('POST', '/v1/heartbeat', { token: ta, playing: true }); }
let w = (await call('POST', '/v1/wallet', { token: ta }))[1];
assert.strictEqual(w.coins, 10);
for (let i = 0; i < 30; i++) { now += 1000; await call('POST', '/v1/heartbeat', { token: ta, playing: true }); }
assert.strictEqual((await call('POST', '/v1/wallet', { token: ta }))[1].coins, 10);
now += 30_000; await call('POST', '/v1/heartbeat', { token: ta, playing: true });
assert.strictEqual((await call('POST', '/v1/wallet', { token: ta }))[1].coins, 11, 'half minutes add up');
now += 30 * 60_000; await call('POST', '/v1/heartbeat', { token: ta, playing: true });
assert.strictEqual((await call('POST', '/v1/wallet', { token: ta }))[1].coins, 11, 'break');
assert.strictEqual((await call('POST', '/v1/wallet', { token: ta }))[1].playMinutes, 11);

// presence
let [, look] = await call('POST', '/v1/lookup', { uuids: [A, B, 'x'] });
assert.deepStrictEqual(look.online, [A]);
now += 200_000;
assert.deepStrictEqual((await call('POST', '/v1/lookup', { uuids: [A] }))[1].online, [], 'stale players drop out');

// shop
for (let i = 0; i < 70; i++) { now += 60_000; await call('POST', '/v1/heartbeat', { token: ta, playing: true }); }
w = (await call('POST', '/v1/wallet', { token: ta }))[1];
assert.strictEqual(w.coins, 80);
assert.strictEqual((await call('POST', '/v1/shop/buy', { token: ta, item: 'nope' }))[0], 404);
assert.strictEqual((await call('POST', '/v1/shop/buy', { token: ta, item: 'free_cape' }))[0], 409);
assert.strictEqual((await call('POST', '/v1/shop/buy', { token: ta, item: 'wings_epic' }))[0], 402);
let [st, bought] = await call('POST', '/v1/shop/buy', { token: ta, item: 'hat_common' });
assert.strictEqual(st, 200); assert.strictEqual(bought.coins, 20); assert.deepStrictEqual(bought.owned, ['hat_common']);
assert.strictEqual((await call('POST', '/v1/shop/buy', { token: ta, item: 'hat_common' }))[0], 409);

// gifts
assert.strictEqual((await call('POST', '/v1/gift', { token: ta, item: 'crown', to: 'nobody' }))[0], 404);
assert.strictEqual((await call('POST', '/v1/gift', { token: ta, item: 'crown', to: 'Alice' }))[0], 400, 'no self gifts');
await call('POST', '/v1/heartbeat', { token: tb, playing: false });
assert.strictEqual((await call('POST', '/v1/gift', { token: ta, item: 'crown', to: 'bob' }))[0], 402, 'not enough');
for (let i = 0; i < 140; i++) { now += 60_000; await call('POST', '/v1/heartbeat', { token: ta, playing: true }); }
[st, w] = await call('POST', '/v1/gift', { token: ta, item: 'crown', to: 'bob', message: 'gg §cwp\n' });
assert.strictEqual(st, 200); assert.strictEqual(w.to, 'Bob'); assert.strictEqual(w.coins, 10);
let wb = (await call('POST', '/v1/wallet', { token: tb }))[1];
assert.deepStrictEqual(wb.owned, ['crown_gold']);
assert.strictEqual(wb.gifts.length, 1); assert.strictEqual(wb.gifts[0].from, 'Alice'); assert.strictEqual(wb.gifts[0].message, 'gg cwp');
wb = (await call('POST', '/v1/gifts/seen', { token: tb, ids: [wb.gifts[0].id] }))[1];
assert.strictEqual(wb.gifts.length, 0);
assert.strictEqual((await call('POST', '/v1/gift', { token: ta, item: 'crown', to: 'Bob' }))[0], 409, 'already owned');

// unlimited coins: only with Mojang verification
const venv = { DB: d1(), VERIFY: '1', UNLIMITED: 'Virtualchickenfr,Fuzed_d' };
const V = 'c'.repeat(32);
const realFetch = globalThis.fetch;
globalThis.fetch = async (url) => (url.includes('Virtualchickenfr') ? new Response(JSON.stringify({ id: V, name: 'Virtualchickenfr' }), { status: 200 }) : new Response(null, { status: 204 }));
const { serverId } = (await call('POST', '/v1/session/start', { uuid: V, name: 'Virtualchickenfr' }, venv))[1];
assert.ok(serverId);
const [fs1, fin] = await call('POST', '/v1/session/finish', { uuid: V, serverId }, venv);
assert.strictEqual(fs1, 200);
w = (await call('POST', '/v1/wallet', { token: fin.token }, venv))[1];
assert.strictEqual(w.unlimited, true); assert.strictEqual(w.coins, null);
assert.strictEqual((await call('POST', '/v1/shop/buy', { token: fin.token, item: 'wings_epic' }, venv))[0], 200);
assert.strictEqual((await call('POST', '/v1/session/finish', { uuid: V, serverId }, venv))[0], 400, 'challenge used once');
// a different account cannot pass the Mojang check with that name
const { serverId: s2 } = (await call('POST', '/v1/session/start', { uuid: 'd'.repeat(32), name: 'Faker' }, venv))[1];
assert.strictEqual((await call('POST', '/v1/session/finish', { uuid: 'd'.repeat(32), serverId: s2 }, venv))[0], 403);
// without verification nobody is unlimited
const fake = await login(V, 'Virtualchickenfr');
assert.strictEqual((await call('POST', '/v1/wallet', { token: fake }))[1].unlimited, false);
// player certificate proof (what Cloudflare uses, since Mojang blocks Workers)
{
  const { generateKeyPairSync, sign } = await import('node:crypto');
  const mojang = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const player = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const cenv = { ...venv, TEST_CERT_KEYS: mojang.publicKey.export({ type: 'spki', format: 'der' }).toString('base64') };
  const pub = player.publicKey.export({ type: 'spki', format: 'der' });
  const certFor = (uuid, expiresAt) => {
    const data = Buffer.alloc(24 + pub.length);
    Buffer.from(uuid, 'hex').copy(data, 0);
    data.writeBigInt64BE(BigInt(expiresAt), 16);
    pub.copy(data, 24);
    return { publicKey: pub.toString('base64'), expiresAt, signature: sign('sha1', data, mojang.privateKey).toString('base64') };
  };
  globalThis.fetch = async (url) => (String(url).includes('playerdb.co') && String(url).includes(V)
    ? new Response(JSON.stringify({ code: 'player.found', data: { player: { username: 'Virtualchickenfr', raw_id: V } } }), { status: 200 })
    : new Response('', { status: 403 }));
  const begin = async () => (await call('POST', '/v1/session/start', { uuid: V, name: 'Virtualchickenfr' }, cenv))[1].serverId;
  const proofFor = (sid, uuid = V) => sign('sha256', Buffer.from(`onix-session:${sid}:${uuid}`), player.privateKey).toString('base64');
  let sid = await begin();
  const [okStatus, okBody] = await call('POST', '/v1/session/finish', { uuid: V, serverId: sid, cert: certFor(V, Date.now() + 3600e3), proof: proofFor(sid) }, cenv);
  assert.strictEqual(okStatus, 200, 'certificate proof accepted');
  assert.ok(okBody.token);
  const [ws, w] = await call('POST', '/v1/wallet', { token: okBody.token }, cenv);
  assert.strictEqual(ws, 200);
  assert.strictEqual(w.unlimited, true, 'name from playerdb counts for unlimited');
  sid = await begin();
  assert.strictEqual((await call('POST', '/v1/session/finish', { uuid: V, serverId: sid, cert: certFor(V, Date.now() - 1), proof: proofFor(sid) }, cenv))[0], 403, 'expired certificate');
  sid = await begin();
  assert.strictEqual((await call('POST', '/v1/session/finish', { uuid: V, serverId: sid, cert: certFor('e'.repeat(32), Date.now() + 3600e3), proof: proofFor(sid) }, cenv))[0], 403, 'certificate of another account');
  sid = await begin();
  assert.strictEqual((await call('POST', '/v1/session/finish', { uuid: V, serverId: sid, cert: certFor(V, Date.now() + 3600e3), proof: proofFor('0'.repeat(32)) }, cenv))[0], 403, 'proof for another challenge');
  sid = await begin();
  assert.strictEqual((await call('POST', '/v1/session/finish', { uuid: V, serverId: sid, cert: certFor(V, Date.now() + 3600e3), proof: proofFor(sid) }, venv))[0], 403, 'not signed by Mojang');
}
globalThis.fetch = realFetch;

// ---- friends, chat, McReal
{
  const C = 'c'.repeat(32);
  const tc = await login(C, 'Carol');
  let r = await call('POST', '/v1/friends/add', { token: ta, name: 'bob' });
  assert.deepStrictEqual([r[0], r[1].state], [200, 'requested']);
  assert.strictEqual((await call('POST', '/v1/friends/add', { token: ta, name: 'Nobody' }))[0], 404);
  assert.strictEqual((await call('POST', '/v1/friends/add', { token: ta, name: 'Alice' }))[0], 400, 'not yourself');
  let fb = (await call('POST', '/v1/friends', { token: tb }))[1];
  assert.strictEqual(fb.incoming[0].name, 'Alice');
  assert.strictEqual((await call('POST', '/v1/chat/send', { token: ta, to: B, text: 'hi' }))[0], 403, 'not yet friends');
  assert.strictEqual((await call('POST', '/v1/friends/accept', { token: ta, uuid: B }))[0], 404, 'cannot accept own request');
  assert.strictEqual((await call('POST', '/v1/friends/accept', { token: tb, uuid: A }))[0], 200);
  await call('POST', '/v1/heartbeat', { token: tb, server: 'play.example.net:25565' });
  await call('POST', '/v1/heartbeat', { token: ta });
  let fa = (await call('POST', '/v1/friends', { token: ta }))[1];
  assert.deepStrictEqual([fa.friends[0].name, fa.friends[0].online, fa.friends[0].server], ['Bob', true, 'play.example.net:25565']);
  await call('POST', '/v1/heartbeat', { token: tb, visible: false, server: 'bad host!' });
  let bob = (await call('POST', '/v1/friends', { token: ta }))[1].friends[0];
  assert.deepStrictEqual([bob.online, bob.server], [true, ''], 'online for friends without the badge; invalid server not shared');
  await call('POST', '/v1/heartbeat', { token: tb, social: false });
  assert.strictEqual((await call('POST', '/v1/friends', { token: ta }))[1].friends[0].online, false, 'social off');
  r = await call('POST', '/v1/chat/send', { token: ta, to: B, text: 'hello §cBob\n' });
  assert.strictEqual(r[1].message.text, 'hello  cBob', 'colour codes and line breaks removed');
  assert.strictEqual((await call('POST', '/v1/friends', { token: tb }))[1].friends[0].unread, 1);
  assert.strictEqual((await call('POST', '/v1/chat/inbox', { token: tb, after: 0 }))[1].messages[0].name, 'Alice');
  const h = (await call('POST', '/v1/chat/history', { token: tb, with: A }))[1];
  assert.strictEqual(h.messages.length, 1);
  assert.strictEqual((await call('POST', '/v1/friends', { token: tb }))[1].friends[0].unread, 0, 'read after history');
  assert.strictEqual((await call('POST', '/v1/chat/history', { token: tc, with: A }))[0], 403);
  // McReal: one per day, friends only
  const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(200, 7)]).toString('base64');
  assert.strictEqual((await call('POST', '/v1/mcreal/post', { token: tb, image: jpeg, caption: 'base' }))[0], 200);
  assert.strictEqual((await call('POST', '/v1/mcreal/post', { token: tb, image: jpeg }))[0], 409);
  assert.strictEqual((await call('POST', '/v1/mcreal/post', { token: tc, image: Buffer.alloc(200, 1).toString('base64') }))[0], 400, 'not an image');
  const feed = (await call('POST', '/v1/mcreal/feed', { token: ta }))[1];
  assert.strictEqual(feed.posts[0].name, 'Bob');
  assert.strictEqual((await call('POST', '/v1/mcreal/image', { token: ta, uuid: B, day: feed.today }))[1].image, jpeg);
  assert.strictEqual((await call('POST', '/v1/mcreal/image', { token: tc, uuid: B, day: feed.today }))[0], 403);
  assert.strictEqual((await call('POST', '/v1/mcreal/feed', { token: tc }))[1].posts.length, 0);
  assert.strictEqual((await call('POST', '/v1/friends/remove', { token: ta, uuid: B }))[0], 200);
  assert.strictEqual((await call('POST', '/v1/friends', { token: ta }))[1].friends.length, 0);
}

// name styles: only owned ones are shared; owners are marked; bought tags (removed) are refunded
{
  const L = 'c'.repeat(32);
  const M = 'd'.repeat(32);
  const tl = await login(L, 'Lena');
  const tm = await login(M, 'Max');
  await call('POST', '/v1/heartbeat', { token: tm, playing: true });
  for (let i = 0; i < 20; i++) { now += 60_000; await call('POST', '/v1/heartbeat', { token: tl, playing: true }); }
  let [, hb] = await call('POST', '/v1/heartbeat', { token: tl, playing: true, look: { style: 'name_fire' } });
  assert.ok(hb.lookError, 'not owned yet');
  assert.strictEqual((await call('POST', '/v1/shop/buy', { token: tl, item: 'name_fire' }))[0], 200);
  [, hb] = await call('POST', '/v1/heartbeat', { token: tl, playing: true, look: { style: 'name_fire' } });
  assert.strictEqual(hb.lookError, undefined);
  await call('POST', '/v1/heartbeat', { token: tm, playing: true });
  const [, lk] = await call('POST', '/v1/lookup', { uuids: [L, M] });
  assert.deepStrictEqual(lk.looks[L], { style: 'name_fire' });
  assert.strictEqual(lk.looks[M], undefined);
  // badge sharing off: not in the online list, but the name style is still seen
  await call('POST', '/v1/heartbeat', { token: tl, playing: true, visible: false });
  const [, hid] = await call('POST', '/v1/lookup', { uuids: [L] });
  assert.deepStrictEqual(hid.online, []);
  assert.deepStrictEqual(hid.looks[L], { style: 'name_fire' }, 'style seen with sharing off');
  now += 200_000;
  assert.strictEqual((await call('POST', '/v1/lookup', { uuids: [L] }))[1].looks[L], undefined, 'gone after leaving');
  await call('POST', '/v1/heartbeat', { token: tl, playing: true });
  await call('POST', '/v1/heartbeat', { token: tm, playing: true });
  const venv = { ...env, VERIFY: '1' };
  const [, lo] = await call('POST', '/v1/lookup', { uuids: [L, M] }, venv);
  assert.deepStrictEqual(lo.looks[L], { style: 'name_fire', owner: true });
  await call('POST', '/v1/heartbeat', { token: tl, playing: true, look: { style: '' } });
  assert.strictEqual((await call('POST', '/v1/lookup', { uuids: [L] }))[1].looks[L], undefined, 'taken off');
  // a tag bought in 1.0.31 comes back as coins
  const before = (await call('POST', '/v1/wallet', { token: tm }))[1].coins;
  await env.DB.prepare('INSERT INTO owned (uuid, item, at) VALUES (?, ?, ?)').bind(M, 'tag_custom_rainbow_tag', now).run();
  [, hb] = await call('POST', '/v1/heartbeat', { token: tm, playing: false });
  assert.strictEqual(hb.wallet.coins, before + 1200);
  assert.ok(!hb.wallet.owned.includes('tag_custom_rainbow_tag'));
  [, hb] = await call('POST', '/v1/heartbeat', { token: tm, playing: false });
  assert.strictEqual(hb.wallet.coins, before + 1200, 'only once');
}

assert.strictEqual((await call('GET', '/nope'))[0], 404);
assert.strictEqual((await call('GET', '/v1/shop'))[1].prices.crown_gold, 150);
await worker.scheduled({}, env);
// ---- parties
{
  const P1 = '1'.repeat(32), P2 = '2'.repeat(32), P3 = '3'.repeat(32), P4 = '4'.repeat(32);
  const t1 = await login(P1, 'Leo'), t2 = await login(P2, 'Mia'), t3 = await login(P3, 'Noa'), t4 = await login(P4, 'Ola');
  for (const t of [t1, t2, t3, t4]) await call('POST', '/v1/heartbeat', { token: t, playing: true });
  for (const [t, n] of [[t2, 'Leo'], [t3, 'Leo']]) { await call('POST', '/v1/friends/add', { token: t, name: n }); }
  await call('POST', '/v1/friends/add', { token: t1, name: 'Mia' });
  await call('POST', '/v1/friends/add', { token: t1, name: 'Noa' });
  assert.strictEqual((await call('POST', '/v1/party/invite', { token: t1, uuid: P4 }))[0], 403, 'only friends');
  let r = await call('POST', '/v1/party/invite', { token: t1, uuid: P2 });
  assert.deepStrictEqual([r[0], r[1].state], [200, 'invited']);
  const pid = r[1].party;
  let v2 = (await call('POST', '/v1/party', { token: t2 }))[1];
  assert.strictEqual(v2.party, null);
  assert.deepStrictEqual(v2.invites.map((i) => [i.party, i.from]), [[pid, 'Leo']]);
  // the leader joins a server before anyone accepts
  await call('POST', '/v1/party/target', { token: t1, kind: 'server', value: 'play.example.net' });
  v2 = (await call('POST', '/v1/party/accept', { token: t2, party: pid }))[1];
  assert.strictEqual(v2.party.id, pid);
  assert.deepStrictEqual(v2.party.members.map((m) => m.name), ['Leo', 'Mia']);
  assert.deepStrictEqual(v2.party.target, { kind: 'server', value: 'play.example.net', seq: 1 });
  assert.strictEqual((await call('POST', '/v1/party/invite', { token: t2, uuid: P3 }))[0], 403, 'members cannot invite');
  assert.strictEqual((await call('POST', '/v1/party/target', { token: t2, kind: 'server', value: 'x.net' }))[1].leader, false);
  await call('POST', '/v1/party/target', { token: t1, kind: 'server', value: 'play.example.net' });
  assert.strictEqual((await call('POST', '/v1/party', { token: t2 }))[1].party.target.seq, 1, 'same target, no new seq');
  await call('POST', '/v1/party/target', { token: t1, kind: 'world' });
  assert.deepStrictEqual((await call('POST', '/v1/party', { token: t2 }))[1].party.target, { kind: 'world', value: pid, seq: 2 });
  assert.strictEqual((await call('POST', '/v1/party/target', { token: t1, kind: 'server', value: 'bad address!' }))[0], 400);
  // invites run out
  await call('POST', '/v1/party/invite', { token: t1, uuid: P3 });
  now += 11 * 60_000;
  assert.strictEqual((await call('POST', '/v1/party/accept', { token: t3, party: pid }))[0], 404, 'expired');
  await call('POST', '/v1/party/invite', { token: t1, uuid: P3 });
  await call('POST', '/v1/party/accept', { token: t3, party: pid });
  // the leader leaves: the next member leads
  await call('POST', '/v1/party/leave', { token: t1 });
  let v3 = (await call('POST', '/v1/party', { token: t3 }))[1];
  assert.strictEqual(v3.party.leader, P2);
  assert.deepStrictEqual(v3.party.members.map((m) => [m.name, m.leader]), [['Mia', true], ['Noa', false]]);
  // the new leader removes the last member: a party of one closes
  v2 = (await call('POST', '/v1/party/kick', { token: t2, uuid: P3 }))[1];
  assert.strictEqual(v2.party, null);
  assert.strictEqual((await call('POST', '/v1/party', { token: t3 }))[1].party, null);
  // relay needs a WebSocket upgrade and a party
  const res = await worker.fetch(new Request(`https://x/v1/relay?token=${t1}&role=host`), env);
  assert.strictEqual(res.status, 426);
}

// ---- relay object: pairs a guest connection with the leader's data connection and pipes bytes both ways
{
  class FakeSocket {
    constructor() { this.l = {}; this.sent = []; this.closed = false; this.peer = null; }
    accept() {}
    addEventListener(t, f) { (this.l[t] = this.l[t] || []).push(f); }
    send(d) { if (this.closed) throw new Error('closed'); this.peer.sent.push(d); }
    close() { if (this.closed) return; this.closed = true; for (const f of this.peer.l.close || []) f({}); }
    emit(t, e) { for (const f of this.l[t] || []) f(e); }
  }
  globalThis.WebSocketPair = function () { const a = new FakeSocket(), b = new FakeSocket(); a.peer = b; b.peer = a; return { 0: a, 1: b }; };
  const OrigResponse = Response;
  globalThis.Response = class extends OrigResponse { constructor(body, init = {}) { super(body, init.status === 101 ? { status: 200 } : init); this.webSocket = init.webSocket; } };
  const mod = await import(tmp);
  const relay = new mod.PartyRelay({});
  const req = (role, conn = '') => new Request('https://relay/', { headers: { 'x-role': role, 'x-conn': conn, 'x-name': 'Mia' } });
  // no host yet: the guest is turned away
  let g0 = (await relay.fetch(req('guest'))).webSocket;
  assert.ok(g0.sent.length === 0 && g0.peer.closed, 'guest closed without host');
  const host = (await relay.fetch(req('host'))).webSocket;           // what the leader's game holds
  const guest = (await relay.fetch(req('guest'))).webSocket;         // what the member's game holds
  const open = JSON.parse(host.sent.at(-1));
  assert.strictEqual(open.name, 'Mia');
  guest.peer.emit('message', { data: 'hello-early' });               // sent before the leader's data socket exists
  const data = (await relay.fetch(req('data', open.open))).webSocket;
  assert.deepStrictEqual(data.sent, ['hello-early'], 'queued bytes are flushed');
  guest.peer.emit('message', { data: 'g2h' });
  data.peer.emit('message', { data: 'h2g' });
  assert.deepStrictEqual(data.sent, ['hello-early', 'g2h']);
  assert.deepStrictEqual(guest.sent, ['h2g']);
  // the member disconnects: the leader's data socket closes too
  guest.close();
  assert.ok(data.peer.closed, 'data closed with guest');
  globalThis.Response = OrigResponse;
}

console.log('all cloudflare server tests passed');
