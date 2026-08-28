import { ref } from 'vue'
import api from '@/utils/api'

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

export const isBookCoverHidden = (bookId: number) =>
  bookCoverOverrides.value[String(bookId)] ?? allBookCoversHidden.value

export const toggleAllBookCovers = () => {
  allBookCoversHidden.value = !allBookCoversHidden.value
  bookCoverOverrides.value = {}
  persistLocal(ALL_BOOK_COVERS_HIDDEN_STORAGE_KEY, String(allBookCoversHidden.value))
  persistLocal(BOOK_COVER_OVERRIDES_STORAGE_KEY, '{}')
  persistRemote()
}

export const toggleBookCover = (bookId: number) => {
  const nextHidden = !isBookCoverHidden(bookId)
  const nextOverrides = { ...bookCoverOverrides.value }

  if (nextHidden === allBookCoversHidden.value) {
    delete nextOverrides[String(bookId)]
  } else {
    nextOverrides[String(bookId)] = nextHidden
  }

  bookCoverOverrides.value = nextOverrides
  persistLocal(BOOK_COVER_OVERRIDES_STORAGE_KEY, JSON.stringify(nextOverrides))
  persistRemote()
}

export const hydrateBookCoverPrivacy = async (userId: number) => {
  if (!localStorage.getItem('token')) return
  try {
    const { data } = await api.get<CoverPrivacyScope>('/api/cover-privacy/books', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
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
  saveQueue = Promise.resolve()
}
