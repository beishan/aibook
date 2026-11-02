const assert = require('node:assert/strict');
const { test } = require('node:test');
const { validateIcon } = require('../icons.cjs');

test('icon settings accept only named presets and bounded PNG payloads', () => {
  for (const preset of ['book', 'note', 'grid', 'calendar']) {
    assert.equal(validateIcon(preset), preset);
  }
  assert.equal(validateIcon(), 'book');
  for (const value of [null, 3, {}, 'file:///secret.png', 'https://test/icon.png',
    'data:image/svg+xml,<svg/>', 'data:image/png;base64,AAA=',
    'data:image/png;base64,' + 'A'.repeat(44000)]) {
    assert.throws(() => validateIcon(value));
  }
  const png = Buffer.alloc(33);
  Buffer.from('89504e470d0a1a0a', 'hex').copy(png);
  png.write('IHDR', 12);
  png.writeUInt32BE(128, 16);
  png.writeUInt32BE(128, 20);
  const url = 'data:image/png;base64,' + png.toString('base64');
  assert.equal(validateIcon(url), url);
  png.writeUInt32BE(100000, 16);
  assert.throws(() => validateIcon('data:image/png;base64,' + png.toString('base64')));
});
