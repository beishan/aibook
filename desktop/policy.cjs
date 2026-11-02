const { createHash } = require('node:crypto');

function serverOrigin(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) {
    throw new Error('请填写 HTTP 或 HTTPS 书库地址，不包含用户名或密码。');
  }
  if (url.pathname !== '/' || url.search || url.hash) {
    throw new Error('请填写书库根地址，例如 http://192.168.1.10:8080');
  }
  return url.origin;
}

function sameOrigin(value, origin) {
  try { return new URL(value).origin === origin; } catch { return false; }
}

function windowSettings(value) {
  if (!value || typeof value !== 'object' || typeof value.immersive !== 'boolean'
      || typeof value.alwaysOnTop !== 'boolean'
      || !Number.isFinite(value.opacity) || value.opacity < 15 || value.opacity > 100) {
    throw new Error('无效的窗口设置');
  }
  return { immersive: value.immersive, alwaysOnTop: value.alwaysOnTop, opacity: value.opacity };
}

function partitionFor(origin) {
  return `persist:library-${createHash('sha256').update(origin).digest('hex').slice(0, 24)}`;
}

module.exports = { serverOrigin, sameOrigin, windowSettings, partitionFor };
