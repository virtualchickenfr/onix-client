'use strict';
// End-to-end test without Mojang verification.
process.env.VERIFY = '0';
process.env.DATA_FILE = require('path').join(require('fs').mkdtempSync(require('path').join(require('os').tmpdir(), 'onix-')), 'economy.json');
const assert = require('assert');
const { server } = require('./index.js');

(async () => {
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = async (p, body) => {
    const res = await fetch(base + p, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
    return { status: res.status, body: await res.json() };
  };
  const a = '8667ba71-b85a-4004-af54-457a9734eed7';
  const b = '069a79f444e94726a5befca90e38aaf5';

  const hello = await (await fetch(base + '/v1/hello')).json();
  assert.strictEqual(hello.verify, false);
  assert.strictEqual((await post('/v1/session/start', { uuid: 'bad', name: 'x' })).status, 400);

  const start = await post('/v1/session/start', { uuid: a, name: 'Steve' });
  assert.ok(start.body.token);
  assert.strictEqual((await post('/v1/heartbeat', { token: 'nope' })).status, 401);
  assert.strictEqual((await post('/v1/heartbeat', { token: start.body.token })).body.ok, true);

  let look = await post('/v1/lookup', { uuids: [a, b] });
  assert.deepStrictEqual(look.body.online, ['8667ba71b85a4004af54457a9734eed7']);

  await post('/v1/heartbeat', { token: start.body.token, visible: false });
  look = await post('/v1/lookup', { uuids: [a] });
  assert.deepStrictEqual(look.body.online, []);

  await post('/v1/heartbeat', { token: start.body.token });
  await post('/v1/leave', { token: start.body.token });
  look = await post('/v1/lookup', { uuids: [a] });
  assert.deepStrictEqual(look.body.online, []);

  console.log('all presence server tests passed');
  server.close();
})().catch((e) => { console.error(e); process.exit(1); });
