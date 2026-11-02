const { contextBridge, ipcRenderer } = require('electron');

// Kept in preload so both current and older NAS frontends receive the drag fix.
window.addEventListener('DOMContentLoaded', () => {
  let pointerId = null;
  let handle = null;
  let active = false;

  function send(phase, event) {
    const point = event ? { x: event.screenX, y: event.screenY } : undefined;
    return ipcRenderer.invoke('desktop:drag', phase, point).catch(() => end());
  }

  function end() {
    if (!active) return;
    active = false;
    const previous = handle;
    const previousId = pointerId;
    handle = null;
    pointerId = null;
    if (previous?.hasPointerCapture(previousId)) previous.releasePointerCapture(previousId);
    void ipcRenderer.invoke('desktop:drag', 'end').catch(() => {});
  }

  const selector = '[data-desktop-drag], .desktop-controls__drag';
  function prepare(root = document) {
    const elements = [
      ...(root.matches?.(selector) ? [root] : []),
      ...(root.querySelectorAll?.(selector) ?? []),
    ];
    for (const element of elements) {
      // Native app-region drag consumes pointer events, so explicitly opt out.
      element.style.setProperty('-webkit-app-region', 'no-drag', 'important');
      element.style.setProperty('app-region', 'no-drag', 'important');
      element.style.userSelect = 'none';
      element.style.touchAction = 'none';
      element.style.cursor = 'move';
      element.style.minWidth = '44px';
      element.style.minHeight = '36px';
      if (!element.hasAttribute('tabindex')) element.tabIndex = 0;
      if (!element.hasAttribute('aria-label')) element.setAttribute('aria-label', '移动窗口');
      element.setAttribute('role', 'button');
    }
  }

  document.addEventListener('pointerdown', event => {
    if (!event.isTrusted || event.button !== 0 || !event.isPrimary) return;
    const target = event.target.closest?.('[data-desktop-drag], .desktop-controls__drag');
    if (!target) return;
    const control = event.target.closest?.('button, input, select, textarea, a');
    if (control && control !== target) return;
    event.preventDefault();
    end();
    active = true;
    handle = target;
    pointerId = event.pointerId;
    handle.focus({ preventScroll: true });
    handle.setPointerCapture(pointerId);
    void send('begin', event);
  }, true);
  document.addEventListener('pointermove', event => {
    if (!active || event.pointerId !== pointerId || !event.isTrusted) return;
    if (!(event.buttons & 1)) { end(); return; }
    event.preventDefault();
    void send('move', event);
  }, true);
  for (const name of ['pointerup', 'pointercancel', 'lostpointercapture']) {
    document.addEventListener(name, event => {
      if (event.pointerId === pointerId) end();
    }, true);
  }
  document.addEventListener('keydown', event => {
    const target = event.target.closest?.('[data-desktop-drag], .desktop-controls__drag');
    if (!target || !event.isTrusted || event.target !== target) return;
    const step = event.shiftKey ? 20 : 5;
    const direction = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }[event.key];
    if (!direction) return;
    event.preventDefault();
    void ipcRenderer.invoke('desktop:drag', 'nudge', { x: direction[0], y: direction[1] }).catch(() => {});
  }, true);
  window.addEventListener('blur', end);
  window.addEventListener('pagehide', end);
  prepare();
  new MutationObserver(records => {
    for (const record of records) {
      for (const node of record.addedNodes) prepare(node);
    }
  }).observe(document.documentElement, { childList: true, subtree: true });
});

contextBridge.exposeInMainWorld('desktopReader', {
  connect: (address) => ipcRenderer.invoke('desktop:connect', address),
  settings: (value) => ipcRenderer.invoke('desktop:settings', value),
  icon: (value) => ipcRenderer.invoke('desktop:icon', value),
  action: (action) => ipcRenderer.invoke('desktop:action', action),
  onRecover: (callback) => {
    const listener = () => callback();
    ipcRenderer.on('desktop:recover', listener);
    return () => ipcRenderer.removeListener('desktop:recover', listener);
  },
});
