import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'
import { useThemeStore } from '@/stores/theme'
import { THEMES, type ThemeId } from '@/types/theme'

export type LibraryViewMode = 'card' | 'list'

interface UserPreferences {
  theme: ThemeId | null
  libraryViewMode: LibraryViewMode | null
  scanThreadCount: number | null
}

const LIBRARY_VIEW_MODE_KEY = 'ai-book-view-mode'
const DEFAULT_SCAN_THREAD_COUNT = 2

const readLocalLibraryViewMode = (): LibraryViewMode => {
  const saved = localStorage.getItem(LIBRARY_VIEW_MODE_KEY)
  return saved === 'list' || saved === 'card' ? saved : 'card'
}

const isTheme = (value: unknown): value is ThemeId =>
  typeof value === 'string' && THEMES.some(theme => theme.id === value)

const isLibraryViewMode = (value: unknown): value is LibraryViewMode =>
  value === 'card' || value === 'list'

const isScanThreadCount = (value: unknown): value is number =>
  Number.isInteger(value) && Number(value) >= 1 && Number(value) <= 16

export const usePreferencesStore = defineStore('preferences', () => {
  const themeStore = useThemeStore()
  const libraryViewMode = ref<LibraryViewMode>(readLocalLibraryViewMode())
  const scanThreadCount = ref(DEFAULT_SCAN_THREAD_COUNT)
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

  const setScanThreadCount = (value: number, syncRemote = true) => {
    if (!isScanThreadCount(value)) return
    scanThreadCount.value = value
    if (syncRemote) persistRemote({ scanThreadCount: value })
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

      if (isScanThreadCount(data.scanThreadCount)) {
        setScanThreadCount(data.scanThreadCount, false)
      } else {
        missingPreferences.scanThreadCount = scanThreadCount.value
      }

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
  }

  return {
    libraryViewMode,
    scanThreadCount,
    hydrated,
    setTheme,
    setLibraryViewMode,
    setScanThreadCount,
    hydrate,
    resetHydration,
  }
})
