'use strict';
// Tests for coins, shop and gifts (economy.js) and their HTTP routes. No Mojang verification.
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'onix-eco-'));
process.env.VERIFY = '0';
process.env.DATA_FILE = path.join(dir, 'http.json');
const { Economy, MINUTE, cleanMessage } = require('./economy');

const shop = { prices: { free_cape: 0, hat_common: 60, wings_epic: 400, crown_gold: 150 }, aliases: { crown: 'crown_gold' } };
const A = 'a'.repeat(32);
const B = 'b'.repeat(32);
const C = 'c'.repeat(32);

function clock() {
  let t = 1_000_000;
  const now = () => t;
  now.add = (ms) => { t += ms; };
  return now;
}

// ---------------------------------------------------------------- earning
{
  const now = clock();
  const eco = new Economy({ shop, now });
  eco.heartbeat(A, 'Alice', true);              // first heartbeat only starts the clock
  assert.strictEqual(eco.view(A).coins, 0);
  for (let i = 0; i < 10; i++) { now.add(MINUTE); eco.heartbeat(A, 'Alice', true); }
  assert.strictEqual(eco.view(A).coins, 10, '1 coin per minute');
  // many heartbeats in one minute earn no more than the minute itself
  for (let i = 0; i < 60; i++) { now.add(1000); eco.heartbeat(A, 'Alice', true); }
  assert.strictEqual(eco.view(A).coins, 11, 'spamming heartbeats does not earn more');
  // a long break does not count
  now.add(30 * MINUTE);
  eco.heartbeat(A, 'Alice', true);
  assert.strictEqual(eco.view(A).coins, 11, 'gaps longer than 3 minutes are a break');
  // not playing (title screen) does not count, and resets the clock
  now.add(MINUTE); eco.heartbeat(A, 'Alice', false);
  now.add(MINUTE); eco.heartbeat(A, 'Alice', true);
  now.add(30_000); eco.heartbeat(A, 'Alice', true);
  assert.strictEqual(eco.view(A).coins, 11);
  now.add(30_000); eco.heartbeat(A, 'Alice', true);
  assert.strictEqual(eco.view(A).coins, 12, 'half minutes add up');
  assert.strictEqual(eco.view(A).playMinutes, 12);
}

// ---------------------------------------------------------------- shop
{
  const now = clock();
  const eco = new Economy({ shop, now });
  eco.heartbeat(A, 'Alice', true);
  for (let i = 0; i < 70; i++) { now.add(MINUTE); eco.heartbeat(A, 'Alice', true); }
  assert.strictEqual(eco.view(A).coins, 70);
  assert.strictEqual(eco.buy(A, 'nope')[0], 404);
  assert.strictEqual(eco.buy(A, '../x')[0], 404);
  assert.strictEqual(eco.buy(A, 'free_cape')[0], 409, 'free items are owned already');
  let [status, body] = eco.buy(A, 'wings_epic');
  assert.strictEqual(status, 402);
  assert.strictEqual(body.needed, 330);
  [status, body] = eco.buy(A, 'hat_common');
  assert.strictEqual(status, 200);
  assert.strictEqual(body.coins, 10);
  assert.deepStrictEqual(body.owned, ['hat_common']);
  assert.strictEqual(eco.buy(A, 'hat_common')[0], 409);
  assert.strictEqual(eco.resolve('crown'), 'crown_gold', 'old ids still work');
}

// ---------------------------------------------------------------- gifts
{
  const now = clock();
  const eco = new Economy({ shop, now });
  eco.heartbeat(A, 'Alice', true);
  for (let i = 0; i < 200; i++) { now.add(MINUTE); eco.heartbeat(A, 'Alice', true); }
  eco.heartbeat(B, 'Bob', false);
  assert.strictEqual(eco.gift(A, 'crown_gold', 'Nobody', 'hi')[0], 404, 'unknown players cannot get gifts');
  assert.strictEqual(eco.gift(A, 'crown_gold', 'alice', 'hi')[0], 400, 'no gifts to yourself');
  assert.strictEqual(eco.gift(A, 'free_cape', 'Bob', 'hi')[0], 400);
  assert.strictEqual(eco.gift(A, 'crown_gold', 'bad name!', 'hi')[0], 400);
  const [status, body] = eco.gift(A, 'crown_gold', 'bob', '§cHello\nBob, enjoy! '.padEnd(120, 'x'));
  assert.strictEqual(status, 200);
  assert.strictEqual(body.coins, 50);
  assert.strictEqual(body.to, 'Bob');
  const bob = eco.view(B);
  assert.deepStrictEqual(bob.owned, ['crown_gold']);
  assert.strictEqual(bob.gifts.length, 1);
  assert.strictEqual(bob.gifts[0].from, 'Alice');
  assert.ok(!bob.gifts[0].message.includes('§') && !bob.gifts[0].message.includes('\n'));
  assert.ok(bob.gifts[0].message.length <= 80);
  assert.strictEqual(eco.gift(A, 'crown_gold', 'Bob', '')[0], 409, 'Bob already has it');
  assert.strictEqual(eco.gift(A, 'wings_epic', 'Bob', '')[0], 402);
  eco.seen(B, [bob.gifts[0].id]);
  assert.strictEqual(eco.view(B).gifts.length, 0);
  // a renamed player is found under the new name only
  eco.heartbeat(B, 'Bobby', false);
  eco.heartbeat(C, 'Bob', false);
  assert.strictEqual(eco.byName.get('bob'), C);
  assert.strictEqual(eco.byName.get('bobby'), B);
}

// ---------------------------------------------------------------- unlimited coins
{
  const eco = new Economy({ shop, now: clock(), unlimited: ['Virtualchickenfr', 'Fuzed_d'], verify: true });
  eco.heartbeat(A, 'virtualchickenfr', false);
  const v = eco.view(A);
  assert.strictEqual(v.unlimited, true);
  assert.strictEqual(v.coins, null);
  assert.strictEqual(eco.buy(A, 'wings_epic')[0], 200);
  eco.heartbeat(B, 'Fuzed_d', false);
  eco.heartbeat(C, 'Carl', false);
  assert.strictEqual(eco.gift(B, 'wings_epic', 'Carl', 'for you')[0], 200, 'unlimited accounts can gift anything');
  assert.strictEqual(eco.view(B).unlimited, true);
  assert.strictEqual(eco.view(C).unlimited, false);
  const unsafe = new Economy({ shop, now: clock(), unlimited: ['Fuzed_d'], verify: false });
  unsafe.heartbeat(A, 'Fuzed_d', false);
  assert.strictEqual(unsafe.view(A).unlimited, false, 'names are not trusted without verification');
}

// ---------------------------------------------------------------- persistence
{
  const file = path.join(dir, 'store.json');
  const now = clock();
  const eco = new Economy({ shop, now, dataFile: file });
  eco.heartbeat(A, 'Alice', true);
  for (let i = 0; i < 61; i++) { now.add(MINUTE); eco.heartbeat(A, 'Alice', true); }
  eco.buy(A, 'hat_common');
  eco.flush();
  const again = new Economy({ shop, now, dataFile: file });
  assert.strictEqual(again.view(A).coins, 1);
  assert.deepStrictEqual(again.view(A).owned, ['hat_common']);
  assert.strictEqual(again.byName.get('alice'), A);
  fs.writeFileSync(file, '{broken');
  const broken = new Economy({ shop, now, dataFile: file });
  assert.deepStrictEqual(broken.accounts, {});
  assert.ok(fs.readdirSync(dir).some((f) => f.startsWith('store.json.broken-')), 'a broken file is kept');
}

assert.strictEqual(cleanMessage(42), '');

// ---------------------------------------------------------------- HTTP
(async () => {
  const { server, economy } = require('./index.js');
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = async (p, body) => {
    const res = await fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
    return { status: res.status, body: await res.json() };
  };
  const hello = await (await fetch(base + '/v1/hello')).json();
  assert.strictEqual(hello.economy, true);
  const shopRes = await (await fetch(base + '/v1/shop')).json();
  assert.ok(Object.keys(shopRes.prices).length > 1000, 'the generated price list is served');

  const tA = (await post('/v1/session/start', { uuid: A, name: 'Fuzed_d' })).body.token;
  const tB = (await post('/v1/session/start', { uuid: B, name: 'Bob' })).body.token;
  assert.strictEqual((await post('/v1/wallet', { token: 'nope' })).status, 401);
  let w = await post('/v1/heartbeat', { token: tA, playing: true });
  assert.strictEqual(w.body.wallet.coins, 0);
  assert.strictEqual(w.body.wallet.unlimited, false, 'VERIFY=0: no unlimited coins');
  economy.accounts[A].coins = 500;
  const item = Object.entries(shopRes.prices).find(([, p]) => p === 150)[0];
  const buy = await post('/v1/shop/buy', { token: tA, item });
  assert.strictEqual(buy.status, 200);
  assert.strictEqual(buy.body.coins, 350);
  await post('/v1/wallet', { token: tB });
  const other = Object.entries(shopRes.prices).find(([id, p]) => p === 60 && id !== item)[0];
  const gift = await post('/v1/gift', { token: tA, item: other, to: 'bob', message: 'gg' });
  assert.strictEqual(gift.status, 200);
  w = await post('/v1/wallet', { token: tB });
  assert.strictEqual(w.body.gifts.length, 1);
  assert.strictEqual(w.body.gifts[0].from, 'Fuzed_d');
  w = await post('/v1/gifts/seen', { token: tB });
  assert.strictEqual(w.body.gifts.length, 0);
  server.close();
  economy.flush();
  assert.ok(fs.existsSync(process.env.DATA_FILE));
  console.log('all economy tests passed');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
