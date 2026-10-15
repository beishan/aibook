import { ref } from 'vue'

/** 服务端书籍字段的会话缓存，不写浏览器存储；退出账户时清空。 */
export const explicitBookCoverOverrides = ref<Record<string, boolean>>({})

/** 从各书籍接口返回的 DTO 同步，避免仅依赖账户偏好初始化接口。 */
export function captureBookCoverVisibility(payload: unknown) {
  const changes: Record<string, boolean | null> = {}
  function visit(value: unknown) {
    if (Array.isArray(value)) {
      value.forEach(visit)
      return
    }
    if (!value || typeof value !== 'object') return
    const item = value as Record<string, unknown>
    if (typeof item.id === 'number' && Number.isSafeInteger(item.id) && item.id > 0
        && typeof item.title === 'string' && Object.prototype.hasOwnProperty.call(item, 'coverHidden')
        && (item.coverHidden === null || typeof item.coverHidden === 'boolean')) {
      changes[String(item.id)] = item.coverHidden as boolean | null
    }
    for (const key of ['content', 'books', 'book', 'results', 'items']) {
      if (item[key] != null) visit(item[key])
    }
  }
  visit(payload)
  if (!Object.keys(changes).length) return
  const next = { ...explicitBookCoverOverrides.value }
  for (const [id, hidden] of Object.entries(changes)) {
    if (hidden === null) delete next[id]
    else next[id] = hidden
  }
  explicitBookCoverOverrides.value = next
}
