export {}

declare global {
  interface Window {
    desktopReader?: {
      connect(address: string): Promise<{ ok: boolean; error?: string }>
      settings(value: { immersive: boolean; alwaysOnTop: boolean; opacity: number }): Promise<void>
      action(action: string): Promise<boolean | void>
      onRecover(callback: () => void): () => void
    }
  }
}
