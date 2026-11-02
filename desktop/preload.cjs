const { contextBridge, ipcRenderer } = require('electron');

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
