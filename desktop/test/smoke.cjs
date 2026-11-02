// Run explicitly with DESKTOP_PLAYWRIGHT_PATH pointing to a local Playwright installation.
const assert = require('node:assert/strict');
const path = require('node:path');
const { createServer } = require('node:http');
const { mkdir } = require('node:fs/promises');
const { _electron } = require(process.env.DESKTOP_PLAYWRIGHT_PATH || 'playwright');

(async () => {
  const server = createServer((_request, response) => {
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end('<!doctype html><title>阅读测试</title><p>透明阅读测试</p><script>window.desktopReader.onRecover(() => { document.body.dataset.recovered = "true" })</script>');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const environment = { ...process.env };
  delete environment.ELECTRON_RUN_AS_NODE;
  const application = await _electron.launch({
    executablePath: process.env.DESKTOP_EXECUTABLE_PATH || require('electron'),
    args: process.env.DESKTOP_EXECUTABLE_PATH ? [] : [path.join(__dirname, '..')],
    env: environment,
  });
  try {
    const page = await application.firstWindow();
    await page.getByRole('heading', { name: '留一隅，给阅读。' }).waitFor();
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
    assert.deepEqual(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getSize()), [480, 620]);
    await page.evaluate(() => window.desktopReader.action('hide'));
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].isVisible()), false);
    await application.evaluate(({ app }) => app.emit('second-instance'));
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].isVisible()), true);
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getOpacity()), 1);
    const dataPath = path.join(__dirname, '../dist/native-smoke');
    await mkdir(dataPath, { recursive: true });
    await application.evaluate(({ app }, directory) => app.setPath('userData', directory), dataPath);
    const nextWindow = application.waitForEvent('window');
    void page.evaluate(address => window.desktopReader.connect(address), `http://127.0.0.1:${server.address().port}`).catch(() => {});
    const reader = await nextWindow;
    await reader.getByText('透明阅读测试', { exact: true }).waitFor();
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
    assert.equal(await reader.evaluate(() => window.desktopReader.action('passthrough')), true);
    // Recovery from the host must restore full opacity even without renderer cooperation.
    await reader.evaluate(() => window.desktopReader.action('hide'));
    await application.evaluate(({ app }) => app.emit('second-instance'));
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getOpacity()), 1);
    await reader.waitForFunction(() => document.body.dataset.recovered === 'true');
    assert.equal(await application.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].isAlwaysOnTop()), false);
    assert.equal(await reader.evaluate(() => window.desktopReader.action('passthrough')), true);
    assert.equal(await reader.evaluate(() => window.desktopReader.action('passthrough')), false);
    console.log('Electron native smoke passed: launcher, sandbox, IPC origin checks, resize, remote connection, opacity, on-top, hide and recovery');
  } finally {
    await application.close();
    server.close();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
