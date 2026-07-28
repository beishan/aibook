import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'
import { useThemeStore } from '@/stores/theme'
import { THEMES, type ThemeId } from '@/types/theme'

export type LibraryViewMode = 'card' | 'list'

interface UserPreferences {
  theme: ThemeId | null
  libraryViewMode: LibraryViewMode | null
}

const LIBRARY_VIEW_MODE_KEY = 'ai-book-view-mode'

const readLocalLibraryViewMode = (): LibraryViewMode => {
  const saved = localStorage.getItem(LIBRARY_VIEW_MODE_KEY)
  return saved === 'list' || saved === 'card' ? saved : 'card'
}

const isTheme = (value: unknown): value is ThemeId =>
  typeof value === 'string' && THEMES.some(theme => theme.id === value)

const isLibraryViewMode = (value: unknown): value is LibraryViewMode =>
  value === 'card' || value === 'list'

export const usePreferencesStore = defineStore('preferences', () => {
  const themeStore = useThemeStore()
  const libraryViewMode = ref<LibraryViewMode>(readLocalLibraryViewMode())
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
    hydrated,
    setTheme,
    setLibraryViewMode,
    hydrate,
    resetHydration,
  }
})
