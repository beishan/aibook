const { test } = require('node:test');
const assert = require('node:assert/strict');
const { serverOrigin, sameOrigin, windowSettings, partitionFor } = require('../policy.cjs');

test('accepts NAS HTTP/HTTPS roots and rejects credentials, paths and active schemes', () => {
  assert.equal(serverOrigin('http://192.168.1.10:8080/'), 'http://192.168.1.10:8080');
  assert.equal(serverOrigin('https://books.example.com'), 'https://books.example.com');
  for (const url of ['file:///etc/passwd', 'javascript:alert(1)', 'https://user:pass@books.test',
    'https://books.test/reader/1', 'https://books.test/?token=secret', 'https://books.test/#fragment']) {
    assert.throws(() => serverOrigin(url));
  }
});

test('navigation stays on exact origin and server sessions are isolated', () => {
  const origin = 'https://books.example.com';
  assert.equal(sameOrigin(`${origin}/reader/1`, origin), true);
  for (const value of ['https://books.example.com.evil.test', 'http://books.example.com',
    'https://books.example.com:8080', 'about:blank', 'file:///test']) {
    assert.equal(sameOrigin(value, origin), false);
  }
  assert.equal(partitionFor(origin), partitionFor(origin));
  assert.notEqual(partitionFor(origin), partitionFor('https://other.test'));
});

test('window opacity cannot make controls permanently invisible; IPC values are checked', () => {
  const settings = { immersive: true, opacity: 15, alwaysOnTop: true };
  assert.deepEqual(windowSettings(settings), settings);
  for (const value of [0, 101, NaN, Infinity, '50', null]) {
    assert.throws(() => windowSettings({ ...settings, opacity: value }));
  }
  assert.throws(() => windowSettings({ ...settings, immersive: 'true' }));
  assert.throws(() => windowSettings({ ...settings, alwaysOnTop: 1 }));
});
