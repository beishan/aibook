import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'
import { useThemeStore } from '@/stores/theme'
import { useFontStore } from '@/stores/font'
import { THEMES, type ThemeId } from '@/types/theme'

export type LibraryViewMode = 'card' | 'compact-card' | 'list'
export const LIBRARY_PAGE_SIZE_OPTIONS = [12, 18, 24, 36, 60] as const
export type LibraryPageSize = (typeof LIBRARY_PAGE_SIZE_OPTIONS)[number]

interface UserPreferences {
  theme: ThemeId | null
  libraryViewMode: LibraryViewMode | null
  libraryPageSize: number | null
  scanThreadCount: number | null
  uiFontId: number | null
  readerFontId: number | null
}

const LIBRARY_VIEW_MODE_KEY = 'ai-book-view-mode'
const LIBRARY_PAGE_SIZE_KEY = 'aibook-library-page-size'
const DEFAULT_LIBRARY_PAGE_SIZE: LibraryPageSize = 18
const DEFAULT_SCAN_THREAD_COUNT = 2

const readLocalLibraryViewMode = (): LibraryViewMode => {
  const saved = localStorage.getItem(LIBRARY_VIEW_MODE_KEY)
  return saved === 'list' || saved === 'card' || saved === 'compact-card' ? saved : 'card'
}

const isLibraryPageSize = (value: unknown): value is LibraryPageSize =>
  typeof value === 'number'
  && LIBRARY_PAGE_SIZE_OPTIONS.some(size => size === value)

const readLocalLibraryPageSize = (): LibraryPageSize => {
  const saved = Number(localStorage.getItem(LIBRARY_PAGE_SIZE_KEY))
  return isLibraryPageSize(saved) ? saved : DEFAULT_LIBRARY_PAGE_SIZE
}

const isTheme = (value: unknown): value is ThemeId =>
  typeof value === 'string' && THEMES.some(theme => theme.id === value)

const isLibraryViewMode = (value: unknown): value is LibraryViewMode =>
  value === 'card' || value === 'compact-card' || value === 'list'

const isScanThreadCount = (value: unknown): value is number =>
  Number.isInteger(value) && Number(value) >= 1 && Number(value) <= 16

export const usePreferencesStore = defineStore('preferences', () => {
  const themeStore = useThemeStore()
  const libraryViewMode = ref<LibraryViewMode>(readLocalLibraryViewMode())
  const libraryPageSize = ref<LibraryPageSize>(readLocalLibraryPageSize())
  const scanThreadCount = ref(DEFAULT_SCAN_THREAD_COUNT)
  const uiFontId = ref<number | null>(null)
  const readerFontId = ref<number | null>(null)
  const hydrated = ref(false)
  let saveQueue: Promise<unknown> = Promise.resolve()

  const persistRemote = (preferences: Partial<UserPreferences>) => {
    if (!localStorage.getItem('token')) return

    saveQueue = saveQueue
      .catch(() => undefined)
      .then(() => api.put('/api/user/preferences', preferences))
      .catch(error => {
        console.error('Failed to persist user preferences:', error)
      })
  }

  const setTheme = (theme: ThemeId, syncRemote = true) => {
    themeStore.setTheme(theme)
    if (syncRemote) persistRemote({ theme })
  }

  const setLibraryViewMode = (mode: LibraryViewMode, syncRemote = true) => {
    libraryViewMode.value = mode
    localStorage.setItem(LIBRARY_VIEW_MODE_KEY, mode)
    if (syncRemote) persistRemote({ libraryViewMode: mode })
  }

  const setLibraryPageSize = (value: LibraryPageSize, syncRemote = true) => {
    if (!isLibraryPageSize(value)) return
    libraryPageSize.value = value
    localStorage.setItem(LIBRARY_PAGE_SIZE_KEY, String(value))
    if (syncRemote) persistRemote({ libraryPageSize: value })
  }

  const setScanThreadCount = (value: number, syncRemote = true) => {
    if (!isScanThreadCount(value)) return
    scanThreadCount.value = value
    if (syncRemote) persistRemote({ scanThreadCount: value })
  }

  const setUiFontId = (value: number | null, syncRemote = true) => {
    uiFontId.value = value
    void useFontStore().applySystemFont(value)
    if (syncRemote) persistRemote({ uiFontId: value })
  }

  const setReaderFontId = (value: number | null, syncRemote = true) => {
    readerFontId.value = value
    if (syncRemote) persistRemote({ readerFontId: value })
  }

  const hydrate = async (force = false) => {
    if (hydrated.value && !force) return
    if (!localStorage.getItem('token')) return

    try {
      const { data } = await api.get<UserPreferences>('/api/user/preferences')
      const missingPreferences: Partial<UserPreferences> = {}

      if (isTheme(data.theme)) {
        setTheme(data.theme, false)
      } else {
        missingPreferences.theme = themeStore.currentTheme
      }

      if (isLibraryViewMode(data.libraryViewMode)) {
        setLibraryViewMode(data.libraryViewMode, false)
      } else {
        missingPreferences.libraryViewMode = libraryViewMode.value
      }

      if (isLibraryPageSize(data.libraryPageSize)) {
        setLibraryPageSize(data.libraryPageSize, false)
      } else {
        missingPreferences.libraryPageSize = libraryPageSize.value
      }

      if (isScanThreadCount(data.scanThreadCount)) {
        setScanThreadCount(data.scanThreadCount, false)
      } else {
        missingPreferences.scanThreadCount = scanThreadCount.value
      }

      setUiFontId(Number.isInteger(data.uiFontId) ? data.uiFontId : null, false)
      setReaderFontId(
        Number.isInteger(data.readerFontId) ? data.readerFontId : null,
        false
      )

      hydrated.value = true

      if (Object.keys(missingPreferences).length > 0) {
        persistRemote(missingPreferences)
      }
    } catch (error) {
      console.error('Failed to load user preferences:', error)
    }
  }

  const resetHydration = () => {
    hydrated.value = false
    uiFontId.value = null
    readerFontId.value = null
    void useFontStore().applySystemFont(null)
  }

  return {
    libraryViewMode,
    libraryPageSize,
    scanThreadCount,
    uiFontId,
    readerFontId,
    hydrated,
    setTheme,
    setLibraryViewMode,
    setLibraryPageSize,
    setScanThreadCount,
    setUiFontId,
    setReaderFontId,
    hydrate,
    resetHydration,
  }
})
