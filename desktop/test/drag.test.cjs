const assert = require('node:assert/strict');
const { test } = require('node:test');
const { dragPoint, dragPosition } = require('../drag.cjs');

test('drag uses an initial screen anchor so window movement never accumulates drift', () => {
  const origin = [100, 200];
  const pointer = { x: 400, y: 600 };
  assert.deepEqual(dragPosition(origin, pointer, { x: 430, y: 620 }), [130, 220]);
  assert.deepEqual(dragPosition(origin, pointer, { x: 450, y: 630 }), [150, 230]);
  assert.deepEqual(dragPosition([-1200, -300], { x: -500, y: -100 }, { x: -450, y: -80 }), [-1150, -280]);
});

test('rejects nonnumeric and unbounded renderer coordinates', () => {
  for (const value of [null, {}, { x: '30', y: 5 }, { x: NaN, y: 0 },
    { x: Infinity, y: 0 }, { x: 1000001, y: 0 }]) {
    assert.throws(() => dragPoint(value));
  }
});
