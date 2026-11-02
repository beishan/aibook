const PRESETS = ['book', 'note', 'grid', 'calendar'];
const PNG_PREFIX = 'data:image/png;base64,';
const MAX_ICON_BYTES = 32768;

function validateIcon(value = 'book') {
  if (PRESETS.includes(value)) return value;
  if (typeof value !== 'string' || value.length > 43714 || !value.startsWith(PNG_PREFIX)) {
    throw new Error('图标请选择内置样式或不超过 32 KiB 的 PNG');
  }
  const encoded = value.slice(PNG_PREFIX.length);
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(encoded) || encoded.length % 4 !== 0) {
    throw new Error('PNG 图标编码无效');
  }
  const bytes = Buffer.from(encoded, 'base64');
  if (bytes.length > MAX_ICON_BYTES || bytes.length < 33
      || bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a'
      || bytes.subarray(12, 16).toString() !== 'IHDR') {
    throw new Error('PNG 图标内容无效');
  }
  const width = bytes.readUInt32BE(16);
  const height = bytes.readUInt32BE(20);
  if (width < 16 || height < 16 || width > 256 || height > 256) {
    throw new Error('PNG 图标尺寸需在 16–256 像素之间');
  }
  return value;
}

function createIcon(nativeImage, value = 'book') {
  validateIcon(value);
  if (!PRESETS.includes(value)) {
    const image = nativeImage.createFromDataURL(value);
    if (image.isEmpty()) throw new Error('PNG 图标无法解码');
    return image;
  }
  const size = 128;
  const pixels = Buffer.alloc(size * size * 4);
  function rect(x, y, width, height, color) {
    for (let row = y; row < y + height; row++) {
      for (let col = x; col < x + width; col++) {
        const offset = (row * size + col) * 4;
        // nativeImage bitmap data uses BGRA byte order.
        pixels[offset] = color[2];
        pixels[offset + 1] = color[1];
        pixels[offset + 2] = color[0];
        pixels[offset + 3] = 255;
      }
    }
  }
  const green = [72, 91, 70];
  const paper = [245, 243, 231];
  const muted = [159, 172, 145];
  rect(10, 10, 108, 108, green);
  if (value === 'book') {
    rect(27, 30, 74, 68, paper);
    rect(61, 30, 6, 68, green);
    rect(34, 40, 20, 3, muted);
    rect(74, 40, 20, 3, muted);
  } else if (value === 'note') {
    rect(30, 25, 68, 78, paper);
    for (let y = 42; y < 90; y += 15) rect(40, y, 48, 4, muted);
  } else if (value === 'grid') {
    for (let y = 28; y <= 80; y += 26) {
      for (let x = 28; x <= 80; x += 26) rect(x, y, 20, 20, paper);
    }
  } else {
    rect(26, 31, 76, 72, paper);
    rect(26, 31, 76, 16, muted);
    rect(42, 23, 6, 17, paper);
    rect(80, 23, 6, 17, paper);
    rect(44, 62, 40, 6, green);
    rect(44, 79, 24, 6, green);
  }
  return nativeImage.createFromBitmap(pixels, { width: size, height: size });
}

module.exports = { validateIcon, createIcon };
