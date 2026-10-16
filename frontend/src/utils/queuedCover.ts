import type { ObjectDirective } from 'vue'

// 留出同源连接供分页等 API 使用；封面请求不经过全局 API 错误提示。
const MAX_CONCURRENT = 3
const REQUEST_TIMEOUT_MS = 12000
const pending: CoverRequest[] = []
let active = 0
let pauseCount = 0
const coverControls = new Set<{ cancel: () => void; resume: () => void }>()

/** 批量操作优先使用连接和解码资源；嵌套暂停需全部释放后才恢复。 */
export function pauseQueuedCoverLoads(): () => void {
  pauseCount++
  if (pauseCount === 1) {
    coverControls.forEach(control => control.cancel())
  }
  let released = false
  return () => {
    if (released) return
    released = true
    pauseCount--
    if (pauseCount === 0) {
      coverControls.forEach(control => control.resume())
      drain()
    }
  }
}

interface CoverRequest {
  controller: AbortController
  start: () => Promise<void>
}

function drain() {
  while (pauseCount === 0 && active < MAX_CONCURRENT && pending.length) {
    const request = pending.shift()!
    if (request.controller.signal.aborted) continue
    active++
    void request.start().finally(() => {
      active--
      drain()
    })
  }
}

function observeCover(image: HTMLImageElement, source: string) {
  let disposed = false
  let objectUrl = ''
  let request: CoverRequest | undefined
  let nearViewport = typeof IntersectionObserver === 'undefined'
  image.removeAttribute('src')
  image.style.visibility = 'hidden'
  image.style.removeProperty('display')

  const cancel = () => {
    if (!request) return
    const index = pending.indexOf(request)
    if (index >= 0) pending.splice(index, 1)
    request.controller.abort()
    request = undefined
  }

  const load = () => {
    if (disposed || pauseCount > 0 || !nearViewport || request || objectUrl || !source) return
    const controller = new AbortController()
    const current: CoverRequest = {
      controller,
      async start() {
        const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
        try {
          // 使用 HTTP 缓存，翻回已加载页时不重复下载远程原图。
          const response = await fetch(source, { signal: controller.signal })
          if (!response.ok) return
          const blob = await response.blob()
          if (disposed || controller.signal.aborted) return
          objectUrl = URL.createObjectURL(blob)
          image.src = objectUrl
          image.style.visibility = 'visible'
        } catch {
          // 下载失败或取消时保留卡片已有的文字封面。
        } finally {
          clearTimeout(timeout)
          if (request === current) request = undefined
        }
      },
    }
    request = current
    pending.push(current)
    drain()
  }

  const observer = typeof IntersectionObserver === 'undefined' ? undefined
    : new IntersectionObserver(entries => {
      nearViewport = entries.some(entry => entry.isIntersecting)
      if (nearViewport) load()
      else cancel()
    }, { rootMargin: '160px 0px' })
  const control = { cancel, resume: load }
  coverControls.add(control)
  if (observer) observer.observe(image)
  else load()

  return () => {
    disposed = true
    coverControls.delete(control)
    observer?.disconnect()
    cancel()
    image.removeAttribute('src')
    if (objectUrl) URL.revokeObjectURL(objectUrl)
  }
}

const cleanup = new WeakMap<HTMLImageElement, () => void>()

/** 仅加载临近视口的封面，换页、隐藏封面及切换来源时释放旧请求和图片内存。 */
export const vQueuedCover: ObjectDirective<HTMLImageElement, string> = {
  mounted(image, { value }) {
    cleanup.set(image, observeCover(image, value))
  },
  updated(image, { value, oldValue }) {
    if (value === oldValue) return
    cleanup.get(image)?.()
    cleanup.set(image, observeCover(image, value))
  },
  beforeUnmount(image) {
    cleanup.get(image)?.()
    cleanup.delete(image)
  },
}
