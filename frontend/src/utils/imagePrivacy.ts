import { ref } from 'vue'
import api from '@/utils/api'
import { explicitBookCoverOverrides } from '@/utils/bookCoverVisibility'

const ALL_BOOK_COVERS_HIDDEN_STORAGE_KEY = 'aibook-cover-images-hidden'
const BOOK_COVER_OVERRIDES_STORAGE_KEY = 'aibook-book-cover-visibility-overrides'
const LEGACY_MIGRATION_STORAGE_KEY = 'aibook-book-cover-privacy-database-migrated'

type BookCoverOverrides = Record<string, boolean>

const readStoredBoolean = (storageKey: string) => {
  try {
    return localStorage.getItem(storageKey) === 'true'
  } catch {
    return false
  }
}

const readBookCoverOverrides = (): BookCoverOverrides => {
  try {
    const storedValue = localStorage.getItem(BOOK_COVER_OVERRIDES_STORAGE_KEY)
    if (!storedValue) return {}
    const parsedValue = JSON.parse(storedValue)
    if (!parsedValue || typeof parsedValue !== 'object' || Array.isArray(parsedValue)) return {}

    return Object.fromEntries(
      Object.entries(parsedValue).filter(([, hidden]) => typeof hidden === 'boolean'),
    )
  } catch {
    return {}
  }
}

interface CoverPrivacyScope {
  initialized: boolean
  allHidden: boolean
  overrides: Record<string, boolean>
  bookOverrides?: Record<string, boolean>
}

const persistLocal = (storageKey: string, value: string) => {
  try {
    localStorage.setItem(storageKey, value)
  } catch {
    // 浏览器禁止本地存储时仍可在当前会话中切换封面可见性。
  }
}

export const allBookCoversHidden = ref(
  readStoredBoolean(ALL_BOOK_COVERS_HIDDEN_STORAGE_KEY),
)
const bookCoverOverrides = ref<BookCoverOverrides>(readBookCoverOverrides())
let saveQueue: Promise<unknown> = Promise.resolve()

const normalizeOverrides = (value: unknown): BookCoverOverrides => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return Object.fromEntries(
    Object.entries(value).filter(([id, hidden]) => /^\d+$/.test(id) && typeof hidden === 'boolean'),
  )
}

const applyBookCoverSettings = (allHidden: boolean, overrides: BookCoverOverrides) => {
  allBookCoversHidden.value = allHidden
  bookCoverOverrides.value = overrides
  persistLocal(ALL_BOOK_COVERS_HIDDEN_STORAGE_KEY, String(allHidden))
  persistLocal(BOOK_COVER_OVERRIDES_STORAGE_KEY, JSON.stringify(overrides))
}

const persistRemote = () => {
  const activeToken = localStorage.getItem('token')
  if (!activeToken) return
  const payload = {
    initialized: true,
    allHidden: allBookCoversHidden.value,
    overrides: { ...bookCoverOverrides.value },
  }
  saveQueue = saveQueue
    .catch(() => undefined)
    .then(() => localStorage.getItem('token') === activeToken
      ? api.put('/api/cover-privacy/books', payload)
      : undefined)
    .catch(error => console.error('Failed to persist book cover privacy:', error))
}

/** 单书显式设置优先；未设置的书籍继续使用账户默认与旧版单书偏好。 */
export const isBookCoverHidden = (bookId: number) =>
  explicitBookCoverOverrides.value[String(bookId)]
  ?? (allBookCoversHidden.value || (bookCoverOverrides.value[String(bookId)] ?? false))

/** 创建 img/src 或预加载前统一判断；单书显式显示允许覆盖全局默认隐藏。 */
export const shouldLoadBookCover = (bookId?: number | null) =>
  bookId == null ? !allBookCoversHidden.value : !isBookCoverHidden(bookId)

export const setAllBookCoversHidden = (hidden: boolean) => {
  if (allBookCoversHidden.value === hidden && Object.keys(bookCoverOverrides.value).length === 0) {
    return
  }
  allBookCoversHidden.value = hidden
  bookCoverOverrides.value = {}
  persistLocal(ALL_BOOK_COVERS_HIDDEN_STORAGE_KEY, String(hidden))
  persistLocal(BOOK_COVER_OVERRIDES_STORAGE_KEY, '{}')
  persistRemote()
}

export const toggleAllBookCovers = () => {
  setAllBookCoversHidden(!allBookCoversHidden.value)
}

export const toggleBookCover = async (bookId: number) => {
  const activeToken = localStorage.getItem('token')
  if (!activeToken) throw new Error('请先登录后再设置书籍封面')
  const { data } = await api.put<{ bookId: number; hidden: boolean | null }>(
    `/api/cover-privacy/books/${bookId}`, { hidden: !isBookCoverHidden(bookId) },
  )
  if (localStorage.getItem('token') !== activeToken) return
  const next = { ...explicitBookCoverOverrides.value }
  if (typeof data.hidden === 'boolean') next[String(bookId)] = data.hidden
  else delete next[String(bookId)]
  explicitBookCoverOverrides.value = next
}

export const hydrateBookCoverPrivacy = async (userId: number) => {
  const activeToken = localStorage.getItem('token')
  if (!activeToken) return
  try {
    const { data } = await api.get<CoverPrivacyScope>('/api/cover-privacy/books', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    if (localStorage.getItem('token') !== activeToken) return
    explicitBookCoverOverrides.value = normalizeOverrides(data.bookOverrides)
    if (data.initialized) {
      applyBookCoverSettings(Boolean(data.allHidden), normalizeOverrides(data.overrides))
    } else {
      const legacyAlreadyMigrated = localStorage.getItem(LEGACY_MIGRATION_STORAGE_KEY) !== null
      const allHidden = legacyAlreadyMigrated ? false : allBookCoversHidden.value
      const overrides = legacyAlreadyMigrated ? {} : bookCoverOverrides.value
      applyBookCoverSettings(allHidden, overrides)
      await api.put('/api/cover-privacy/books', {
        initialized: true,
        allHidden,
        overrides,
      })
    }
    persistLocal(LEGACY_MIGRATION_STORAGE_KEY, String(userId))
  } catch (error) {
    console.error('Failed to hydrate book cover privacy:', error)
  }
}

export const resetBookCoverPrivacy = () => {
  allBookCoversHidden.value = false
  bookCoverOverrides.value = {}
  explicitBookCoverOverrides.value = {}
  saveQueue = Promise.resolve()
}
