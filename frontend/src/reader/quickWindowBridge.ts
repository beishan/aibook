export type QuickReaderControl =
  | { action: 'ready' | 'flush' | 'move-end' }
  | { action: 'mask'; value: number }
  | { action: 'move'; dx: number; dy: number; persist?: boolean }

export const sendQuickReaderControl = (control: QuickReaderControl) => {
  if (window.parent === window) return
  window.parent.postMessage({
    type: 'aibook:quick-reader-window-control',
    ...control,
  }, window.location.origin)
}
