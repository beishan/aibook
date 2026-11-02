// Run explicitly with DESKTOP_PLAYWRIGHT_PATH pointing to a local Playwright installation.
const assert = require('node:assert/strict');
const path = require('node:path');
const { createServer } = require('node:http');
const { mkdir } = require('node:fs/promises');
const { _electron } = require(process.env.DESKTOP_PLAYWRIGHT_PATH || 'playwright');

(async () => {
  const server = createServer((_request, response) => {
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end('<!doctype html><title>阅读测试</title><p>透明阅读测试</p><span class="desktop-controls__drag" style="display:inline-flex;padding:12px;-webkit-app-region:drag">移动窗口</span><button id="control">普通按钮</button><script>window.desktopReader.onRecover(() => { document.body.dataset.recovered = "true" })</script>');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  server.unref();
  const dataPath = path.join(__dirname, `../dist/native-smoke-${process.pid}`);
  await mkdir(dataPath, { recursive: true });
  const environment = { ...process.env };
  delete environment.ELECTRON_RUN_AS_NODE;
  const application = await _electron.launch({
    executablePath: process.env.DESKTOP_EXECUTABLE_PATH || require('electron'),
    args: [
      `--user-data-dir=${dataPath}`,
      ...(process.env.DESKTOP_EXECUTABLE_PATH ? [] : [path.join(__dirname, '..')]),
    ],
    env: environment,
  });
  try {
    const position = () => application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getPosition());
    async function dragWindow(page, selector) {
      const target = page.locator(selector);
      await target.waitFor();
      await page.waitForFunction(selector => getComputedStyle(document.querySelector(selector)).getPropertyValue('-webkit-app-region') === 'no-drag', selector);
      const before = await position();
      const box = await target.boundingBox();
      const x = box.x + box.width / 2;
      const y = box.y + box.height / 2;
      await page.mouse.move(x, y);
      await page.mouse.down();
      await page.mouse.move(x + 40, y + 24);
      await page.waitForTimeout(80);
      await page.mouse.up();
      const after = await position();
      assert.deepEqual(after, [before[0] + 40, before[1] + 24]);
      await page.mouse.move(x + 80, y + 60);
      await page.waitForTimeout(40);
      assert.deepEqual(await position(), after, 'released pointer must not keep moving the window');
      await target.focus();
      await page.keyboard.press('ArrowRight');
      await page.keyboard.press('Shift+ArrowDown');
      await page.waitForTimeout(60);
      assert.deepEqual(await position(), [after[0] + 5, after[1] + 20]);
    }
    const page = await application.firstWindow();
    await page.getByRole('heading', { name: '留一隅，给阅读。' }).waitFor();
    await dragWindow(page, '[data-desktop-drag]');
    assert.equal(await page.evaluate(() => typeof window.require), 'undefined');
    assert.equal(await page.evaluate(() => typeof window.desktopReader.action), 'function');
    const options = await application.evaluate(({ BrowserWindow }) => {
      const win = BrowserWindow.getAllWindows()[0];
      const preferences = win.webContents.getLastWebPreferences();
      return { frame: win.hasShadow(), opacity: win.getOpacity(),
        sandbox: preferences.sandbox, contextIsolation: preferences.contextIsolation,
        nodeIntegration: preferences.nodeIntegration };
    });
    assert.equal(options.sandbox, true);
    assert.equal(options.contextIsolation, true);
    assert.equal(options.nodeIntegration, false);
    assert.equal(options.opacity, 1);
    const result = await page.evaluate(() => window.desktopReader.connect('https://user:pass@books.test'));
    assert.equal(result.ok, false);
    const unauthorized = await page.evaluate(async () => {
      try {
        await window.desktopReader.settings({ immersive: true, opacity: 15, alwaysOnTop: true });
        return false;
      } catch { return true; }
    });
    assert.equal(unauthorized, true);
    await page.evaluate(() => window.desktopReader.action('resize-small'));
    await page.waitForTimeout(100);
    const smallSize = await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getSize());
    // Windows may round frameless bounds by a couple of DIPs after moving.
    assert.ok(Math.abs(smallSize[0] - 480) <= 2 && Math.abs(smallSize[1] - 620) <= 2);
    await page.evaluate(() => window.desktopReader.action('hide'));
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].isVisible()), false);
    await application.evaluate(({ app }) => app.emit('second-instance'));
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].isVisible()), true);
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getOpacity()), 1);
    await application.evaluate(({ app }, directory) => app.setPath('userData', directory), dataPath);
    const nextWindow = application.waitForEvent('window');
    void page.evaluate(address => window.desktopReader.connect(address), `http://127.0.0.1:${server.address().port}`).catch(() => {});
    const reader = await nextWindow;
    await reader.getByText('透明阅读测试', { exact: true }).waitFor();
    await dragWindow(reader, '.desktop-controls__drag');
    // Ordinary controls must not move the window.
    const beforeControl = await position();
    const controlBox = await reader.locator('#control').boundingBox();
    await reader.mouse.move(controlBox.x + 10, controlBox.y + 10);
    await reader.mouse.down();
    await reader.mouse.move(controlBox.x + 30, controlBox.y + 20);
    await reader.mouse.up();
    assert.deepEqual(await position(), beforeControl);
    const previews = [];
    for (const value of ['book', 'note', 'grid', 'calendar']) {
      const png = await reader.evaluate(icon => window.desktopReader.icon(icon), value);
      assert.ok(png.startsWith('data:image/png;base64,'));
      previews.push(png);
    }
    assert.equal(new Set(previews).size, 4);
    const size = await application.evaluate(({ nativeImage }, png) => nativeImage.createFromDataURL(png).getSize(), previews[1]);
    assert.deepEqual(size, { width: 128, height: 128 });
    assert.equal(await reader.evaluate(png => window.desktopReader.icon(png), previews[1]), previews[1]);
    const iconDenied = await reader.evaluate(async () => {
      try {
        await window.desktopReader.icon('file:///private.png');
        return false;
      } catch { return true; }
    });
    assert.equal(iconDenied, true);
    await reader.evaluate(() => window.desktopReader.settings({ immersive: true, opacity: 35, alwaysOnTop: true }));
    const state = await application.evaluate(({ BrowserWindow }) => {
      const win = BrowserWindow.getAllWindows()[0];
      return { opacity: win.getOpacity(), top: win.isAlwaysOnTop() };
    });
    assert.ok(Math.abs(state.opacity - .35) < .01);
    assert.equal(state.top, true);
    assert.equal(await reader.evaluate(() => typeof window.require), 'undefined');
    const invalid = await reader.evaluate(async () => {
      try {
        await window.desktopReader.settings({ immersive: true, opacity: 0, alwaysOnTop: true });
        return false;
      } catch { return true; }
    });
    assert.equal(invalid, true);
    const recoveryReady = await application.evaluate(({ globalShortcut }) =>
      globalShortcut.isRegistered('CommandOrControl+Shift+R'));
    if (recoveryReady) {
      assert.equal(await reader.evaluate(() => window.desktopReader.action('passthrough')), true);
    } else {
      await assert.rejects(reader.evaluate(() => window.desktopReader.action('passthrough')), /恢复快捷键不可用/);
    }
    // Recovery from the host must restore full opacity even without renderer cooperation.
    await reader.evaluate(() => window.desktopReader.action('hide'));
    await application.evaluate(({ app }) => app.emit('second-instance'));
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getOpacity()), 1);
    await reader.waitForFunction(() => document.body.dataset.recovered === 'true');
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].isAlwaysOnTop()), false);
    if (recoveryReady) {
      assert.equal(await reader.evaluate(() => window.desktopReader.action('passthrough')), true);
      assert.equal(await reader.evaluate(() => window.desktopReader.action('passthrough')), false);
    }
    console.log('Electron native smoke passed: launcher/remote pointer drag, release, keyboard movement, sandbox, icons, resize, opacity, on-top, hide and recovery');
  } finally {
    await application.close();
    server.close();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
