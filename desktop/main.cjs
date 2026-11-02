const { app, BrowserWindow, Menu, Tray, nativeImage, ipcMain, globalShortcut, dialog } = require('electron');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { readFile, writeFile } = require('node:fs/promises');
const { serverOrigin, sameOrigin, windowSettings, partitionFor } = require('./policy.cjs');
const { createIcon } = require('./icons.cjs');

let win;
let tray;
let origin = null;
let quitting = false;
let passthrough = false;
let recoveryAvailable = false;
let currentIcon;
let currentIconValue;
const launcher = pathToFileURL(path.join(__dirname, 'launcher.html')).href;
const shortcuts = { hide: 'CommandOrControl+Shift+H', recover: 'CommandOrControl+Shift+R' };

function applyIcon(value = 'book') {
  if (value === currentIconValue && currentIcon) return currentIcon.toDataURL();
  const image = createIcon(nativeImage, value);
  if (process.platform === 'darwin') {
    app.dock?.setIcon(image);
  } else if (win && !win.isDestroyed()) {
    win.setIcon(image);
  }
  tray?.setImage(image.resize({ width: process.platform === 'darwin' ? 20 : 32 }));
  currentIcon = image;
  currentIconValue = value;
  return image.toDataURL();
}

function recover() {
  if (!win || win.isDestroyed()) return;
  passthrough = false;
  win.setIgnoreMouseEvents(false);
  win.setAlwaysOnTop(false);
  win.setOpacity(1);
  win.show();
  win.focus();
  win.webContents.send('desktop:recover');
}

function trusted(event, local = false) {
  if (!win || event.sender !== win.webContents || event.senderFrame !== win.webContents.mainFrame) {
    throw new Error('拒绝非主窗口请求');
  }
  const url = event.senderFrame.url;
  if (local ? url !== launcher : !origin || !sameOrigin(url, origin)) {
    throw new Error('拒绝非书库来源请求');
  }
}

function attachSecurity(window) {
  const contents = window.webContents;
  contents.setWindowOpenHandler(() => ({ action: 'deny' }));
  const guard = (event, url) => {
    if (!origin || !sameOrigin(url, origin)) event.preventDefault();
  };
  contents.on('will-navigate', guard);
  contents.on('will-redirect', guard);
  contents.on('will-attach-webview', event => event.preventDefault());
  contents.session.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
  contents.session.setPermissionCheckHandler(() => false);
  contents.session.on('will-download', event => event.preventDefault());
  contents.on('page-title-updated', event => {
    event.preventDefault();
    window.setTitle('汗牛充栋');
  });
  contents.on('render-process-gone', () => recover());
}

function createWindow(partition) {
  const window = new BrowserWindow({
    title: '汗牛充栋', width: 1000, height: 760, minWidth: 380, minHeight: 300,
    frame: false, transparent: true, backgroundColor: '#00000000', hasShadow: false,
    icon: currentIcon,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'), partition,
      nodeIntegration: false, contextIsolation: true, sandbox: true,
      webSecurity: true, allowRunningInsecureContent: false,
    },
  });
  attachSecurity(window);
  window.on('close', event => {
    if (!quitting && tray) {
      event.preventDefault();
      window.hide();
    }
  });
  return window;
}

async function showLauncher() {
  origin = null;
  applyIcon('book');
  const previous = win;
  win = createWindow();
  previous?.destroy();
  await win.loadURL(launcher);
}

ipcMain.handle('desktop:connect', async (event, address) => {
  trusted(event, true);
  try {
    const nextOrigin = serverOrigin(address);
    origin = nextOrigin;
    const previous = win;
    win = createWindow(partitionFor(origin));
    previous.hide();
    try {
      await win.loadURL(origin);
      await writeFile(path.join(app.getPath('userData'), 'server.json'), JSON.stringify({ origin }));
      previous.destroy();
      return { ok: true };
    } catch (error) {
      win.destroy();
      win = previous;
      origin = null;
      win.show();
      return { ok: false, error: `连接失败，请检查书库地址和网络：${error.message}` };
    }
  } catch (error) {
    return { ok: false, error: error.message };
  }
});

ipcMain.handle('desktop:settings', (event, value) => {
  trusted(event);
  const settings = windowSettings(value);
  win.setAlwaysOnTop(settings.immersive && settings.alwaysOnTop);
  win.setOpacity(settings.immersive ? settings.opacity / 100 : 1);
  if (!settings.immersive) {
    passthrough = false;
    win.setIgnoreMouseEvents(false);
  }
});

ipcMain.handle('desktop:icon', (event, value) => {
  trusted(event);
  return applyIcon(value);
});

ipcMain.handle('desktop:action', async (event, action) => {
  trusted(event, event.senderFrame.url === launcher);
  switch (action) {
    case 'hide': win.hide(); break;
    case 'minimize': win.minimize(); break;
    case 'quit': app.quit(); break;
    case 'recover': recover(); break;
    case 'resize-small': win.setSize(480, 620); break;
    case 'resize-large': win.setSize(1000, 760); break;
    case 'server': await showLauncher(); break;
    case 'passthrough':
      if (!recoveryAvailable || !tray) throw new Error('恢复快捷键不可用，无法启用鼠标穿透');
      passthrough = !passthrough;
      win.setIgnoreMouseEvents(passthrough, { forward: true });
      return passthrough;
    default: throw new Error('未知窗口操作');
  }
});

if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', recover);
  app.whenReady().then(async () => {
    Menu.setApplicationMenu(null);
    const icon = createIcon(nativeImage, 'book');
    tray = new Tray(icon.resize({ width: process.platform === 'darwin' ? 20 : 32 }));
    tray.setToolTip('汗牛充栋 · 阅读');
    tray.setContextMenu(Menu.buildFromTemplate([
      { label: '恢复窗口 / 退出穿透', click: recover },
      { label: '切换书库地址', click: () => void showLauncher() },
      { type: 'separator' },
      { label: '退出', click: () => app.quit() },
    ]));
    tray.on('click', recover);
    recoveryAvailable = globalShortcut.register(shortcuts.recover, recover);
    const hideAvailable = globalShortcut.register(shortcuts.hide, () => {
      if (win?.isVisible()) win.hide(); else recover();
    });
    await showLauncher();
    if (!recoveryAvailable || !hideAvailable) {
      void dialog.showMessageBox(win, {
        type: 'warning', message: '部分全局快捷键被占用',
        detail: '可通过托盘菜单恢复窗口。恢复快捷键不可用时，鼠标穿透会被禁用。',
      });
    }
    try {
      const saved = JSON.parse(await readFile(path.join(app.getPath('userData'), 'server.json'), 'utf8'));
      await win.webContents.executeJavaScript(`document.querySelector('input').value = ${JSON.stringify(serverOrigin(saved.origin))}`);
    } catch { /* First launch has no saved server. */ }
  });
  app.on('activate', recover);
  app.on('before-quit', () => { quitting = true; });
  app.on('will-quit', () => { globalShortcut.unregisterAll(); tray?.destroy(); });
}
