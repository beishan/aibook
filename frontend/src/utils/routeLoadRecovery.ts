import type { Router } from 'vue-router'

const recoveryKey = 'aibook:route-load-recovery'
const recoveryCooldown = 5 * 60 * 1000

export const isRouteAssetLoadError = (error: unknown): boolean => {
  const text = error instanceof Error ? error.message : String(error)
  return /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed|Loading (?:CSS )?chunk .+ failed|Unable to preload CSS/i.test(text)
}

// sessionStorage 仅用于当前标签页的刷新保护，不保存账户偏好。
export const recoverRouteLoad = (
  error: unknown,
  target: string,
  environment: {
    online: boolean
    storage: Pick<Storage, 'getItem' | 'setItem'>
    origin: string
    now: number
    replace: (url: string) => void
  },
): boolean => {
  if (!isRouteAssetLoadError(error) || !environment.online) return false

  const url = new URL(target, environment.origin)
  if (url.origin !== environment.origin) return false

  try {
    const lastRecovery = Number(environment.storage.getItem(recoveryKey))
    if (lastRecovery > 0 && environment.now - lastRecovery < recoveryCooldown) return false
    environment.storage.setItem(recoveryKey, String(environment.now))
  } catch {
    // 无法记录刷新保护时保留当前页面，避免反复刷新。
    return false
  }

  environment.replace(url.href)
  return true
}

export const installRouteLoadRecovery = (
  router: Router,
  notify: (text: string) => void,
) => {
  router.onError((error, to) => {
    console.error('[Router] 页面加载失败', to.fullPath, error)
    try {
      if (recoverRouteLoad(error, to.fullPath, {
        online: navigator.onLine,
        storage: window.sessionStorage,
        origin: window.location.origin,
        now: Date.now(),
        replace: url => window.location.replace(url),
      })) return
    } catch {
      // 部分浏览器会在读取 sessionStorage 时直接抛出异常。
    }

    notify(isRouteAssetLoadError(error)
      ? '页面文件加载失败，请检查网络后刷新网页重试。'
      : '页面打开失败，请重试；若仍失败，请联系管理员查看浏览器错误日志。')
  })
}
