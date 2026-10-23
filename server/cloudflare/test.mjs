// Tests for the Cloudflare server against a local SQLite database (node:sqlite stands in for D1).
//   node test.mjs        (Node 22.5+)
import assert from 'node:assert';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const here = path.dirname(new URL(import.meta.url).pathname);
globalThis.__shop = { version: 1, prices: { free_cape: 0, hat_common: 60, wings_epic: 400, crown_gold: 150, name_fire: 5, tag_pro: 5, tag_custom_tag: 5 }, aliases: { crown: 'crown_gold' },
  looks: { name_fire: 'nametag', tag_pro: 'tag', tag_custom_tag: 'custom', tag_staff_owner: 'staff' }, customMax: 12 };
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

// name styles and tags: only owned ones are shared, custom text is checked
{
  const L = 'c'.repeat(32);
  const M = 'd'.repeat(32);
  const tl = await login(L, 'Lena');
  const tm = await login(M, 'Max');
  await call('POST', '/v1/heartbeat', { token: tm, playing: true });
  for (let i = 0; i < 20; i++) { now += 60_000; await call('POST', '/v1/heartbeat', { token: tl, playing: true }); }
  let [, hb] = await call('POST', '/v1/heartbeat', { token: tl, playing: true, look: { style: 'name_fire', tag: '', text: '' } });
  assert.ok(hb.lookError, 'not owned yet');
  for (const item of ['name_fire', 'tag_custom_tag']) assert.strictEqual((await call('POST', '/v1/shop/buy', { token: tl, item }))[0], 200);
  [, hb] = await call('POST', '/v1/heartbeat', { token: tl, playing: true, look: { style: 'name_fire', tag: 'tag_custom_tag', text: 'Admin' } });
  assert.match(hb.lookError, /not allowed/);
  [, hb] = await call('POST', '/v1/heartbeat', { token: tl, playing: true, look: { style: 'name_fire', tag: 'tag_custom_tag', text: '  Chicken  ★ ' } });
  assert.strictEqual(hb.lookError, undefined);
  await call('POST', '/v1/heartbeat', { token: tm, playing: true });
  const [, lk] = await call('POST', '/v1/lookup', { uuids: [L, M] });
  assert.deepStrictEqual(lk.looks[L], { style: 'name_fire', tag: 'tag_custom_tag', text: 'Chicken ★' });
  assert.strictEqual(lk.looks[M], undefined);
  await call('POST', '/v1/heartbeat', { token: tl, playing: true, look: { style: '', tag: '', text: '' } });
  assert.strictEqual((await call('POST', '/v1/lookup', { uuids: [L] }))[1].looks[L], undefined, 'taken off');
  // owners (only with VERIFY on) always show the OWNER tag; nobody can wear it otherwise
  [, hb] = await call('POST', '/v1/heartbeat', { token: tm, playing: true, look: { style: '', tag: 'tag_staff_owner', text: '' } });
  assert.strictEqual((await call('POST', '/v1/lookup', { uuids: [M] }))[1].looks[M], undefined, 'staff tag cannot be chosen');
  const venv = { ...env, VERIFY: '1' };
  const [, lo] = await call('POST', '/v1/lookup', { uuids: [L, M] }, venv);
  assert.deepStrictEqual(lo.looks[L], { style: '', tag: 'tag_staff_owner', text: '', owner: true });
  assert.strictEqual(lo.looks[M], undefined);
}

assert.strictEqual((await call('GET', '/nope'))[0], 404);
assert.strictEqual((await call('GET', '/v1/shop'))[1].prices.crown_gold, 150);
await worker.scheduled({}, env);
console.log('all cloudflare server tests passed');
