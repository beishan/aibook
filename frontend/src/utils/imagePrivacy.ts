import { ref } from 'vue'

const ALL_BOOK_COVERS_HIDDEN_STORAGE_KEY = 'aibook-cover-images-hidden'
const BOOK_COVER_OVERRIDES_STORAGE_KEY = 'aibook-book-cover-visibility-overrides'

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

const persist = (storageKey: string, value: string) => {
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

export const isBookCoverHidden = (bookId: number) =>
  bookCoverOverrides.value[String(bookId)] ?? allBookCoversHidden.value

export const toggleAllBookCovers = () => {
  allBookCoversHidden.value = !allBookCoversHidden.value
  bookCoverOverrides.value = {}
  persist(ALL_BOOK_COVERS_HIDDEN_STORAGE_KEY, String(allBookCoversHidden.value))
  persist(BOOK_COVER_OVERRIDES_STORAGE_KEY, '{}')
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
  persist(BOOK_COVER_OVERRIDES_STORAGE_KEY, JSON.stringify(nextOverrides))
}
