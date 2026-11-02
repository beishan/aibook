// A drag is anchored in screen coordinates, not coordinates relative to a moving window.
function dragPoint(value) {
  if (!value || !Number.isFinite(value.x) || !Number.isFinite(value.y)
      || Math.abs(value.x) > 1000000 || Math.abs(value.y) > 1000000) {
    throw new Error('无效的拖动坐标');
  }
  return { x: value.x, y: value.y };
}

function dragPosition(startWindow, startPointer, pointer) {
  const next = dragPoint(pointer);
  return [
    Math.round(startWindow[0] + next.x - startPointer.x),
    Math.round(startWindow[1] + next.y - startPointer.y),
  ];
}

module.exports = { dragPoint, dragPosition };
