import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'
import { useThemeStore } from '@/stores/theme'
import { useFontStore } from '@/stores/font'
import {
  DEFAULT_THEME_BACKGROUND_SETTINGS,
  THEMES,
  type ThemeBackgroundConfig,
  type ThemeBackgroundSettings,
  type ThemeId,
} from '@/types/theme'
import { normalizeThemeBackgroundConfig } from '@/utils/themeBackground'

export type LibraryViewMode = 'card' | 'compact-card' | 'list'
export type DockIconStyle = 'minimal' | 'skeuomorphic' | 'macos26' | 'custom'
export const LIBRARY_PAGE_SIZE_OPTIONS = [12, 18, 24, 36, 60] as const
export type LibraryPageSize = (typeof LIBRARY_PAGE_SIZE_OPTIONS)[number]

interface UserPreferences {
  theme: ThemeId | null
  libraryViewMode: LibraryViewMode | null
  libraryPageSize: number | null
  libraryCardPageSize: number | null
  libraryListPageSize: number | null
  scanThreadCount: number | null
  modernThemeColor: string | null
  warmThemeColor: string | null
  naturalThemeColor: string | null
  macos26ThemeColor: string | null
  themeBackgrounds: ThemeBackgroundSettings | null
  dockSize: number | null
  dockOpacity: number | null
  dockMagnification: number | null
  dockBlur: number | null
  dockIconStyle: DockIconStyle | null
  uiFontId: number | null
  readerFontId: number | null
}

const LIBRARY_VIEW_MODE_KEY = 'ai-book-view-mode'
const LIBRARY_PAGE_SIZE_KEY = 'aibook-library-page-size'
const LIBRARY_CARD_PAGE_SIZE_KEY = 'aibook-library-card-page-size'
const LIBRARY_LIST_PAGE_SIZE_KEY = 'aibook-library-list-page-size'
const DOCK_SIZE_KEY = 'aibook-dock-size'
const DOCK_OPACITY_KEY = 'aibook-dock-opacity'
const DOCK_MAGNIFICATION_KEY = 'aibook-dock-magnification'
const DOCK_BLUR_KEY = 'aibook-dock-blur'
const DOCK_ICON_STYLE_KEY = 'aibook-dock-icon-style'
const DEFAULT_LIBRARY_PAGE_SIZE: LibraryPageSize = 18
const DEFAULT_SCAN_THREAD_COUNT = 2
export const DEFAULT_DOCK_SIZE = 58
export const DEFAULT_DOCK_OPACITY = 72
export const DEFAULT_DOCK_MAGNIFICATION = 128
export const DEFAULT_DOCK_BLUR = 24
export const DEFAULT_DOCK_ICON_STYLE: DockIconStyle = 'minimal'

const readLocalLibraryViewMode = (): LibraryViewMode => {
  const saved = localStorage.getItem(LIBRARY_VIEW_MODE_KEY)
  return saved === 'list' || saved === 'card' || saved === 'compact-card' ? saved : 'card'
}

const isLibraryPageSize = (value: unknown): value is LibraryPageSize =>
  typeof value === 'number'
  && LIBRARY_PAGE_SIZE_OPTIONS.some(size => size === value)

const readLocalLibraryPageSize = (key: string): LibraryPageSize => {
  const saved = Number(localStorage.getItem(key) || localStorage.getItem(LIBRARY_PAGE_SIZE_KEY))
  return isLibraryPageSize(saved) ? saved : DEFAULT_LIBRARY_PAGE_SIZE
}

const readLocalNumber = (key: string, fallback: number, min: number, max: number) => {
  const saved = Number(localStorage.getItem(key))
  return Number.isFinite(saved) && saved >= min && saved <= max ? saved : fallback
}

const isDockIconStyle = (value: unknown): value is DockIconStyle =>
  value === 'minimal' || value === 'skeuomorphic' || value === 'macos26' || value === 'custom'

const readLocalDockIconStyle = (): DockIconStyle => {
  const saved = localStorage.getItem(DOCK_ICON_STYLE_KEY)
  return isDockIconStyle(saved) ? saved : DEFAULT_DOCK_ICON_STYLE
}

const isNumberInRange = (value: unknown, min: number, max: number): value is number =>
  typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max

const isTheme = (value: unknown): value is ThemeId =>
  typeof value === 'string' && THEMES.some(theme => theme.id === value)

const isLibraryViewMode = (value: unknown): value is LibraryViewMode =>
  value === 'card' || value === 'compact-card' || value === 'list'

const isScanThreadCount = (value: unknown): value is number =>
  Number.isInteger(value) && Number(value) >= 1 && Number(value) <= 16

const isThemeColor = (value: unknown): value is string =>
  typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value)

const isBackgroundConfig = (value: unknown): value is ThemeBackgroundConfig => {
  if (!value || typeof value !== 'object') return false
  const config = value as Partial<ThemeBackgroundConfig>
  return (config.mode === 'solid' || config.mode === 'gradient')
    && isThemeColor(config.pageColor)
    && isThemeColor(config.secondaryColor)
    && isThemeColor(config.navColor)
    && isNumberInRange(config.navOpacity, 20, 100)
    && isThemeColor(config.surfaceColor)
    && isNumberInRange(config.surfaceOpacity, 35, 100)
}

const isBackgroundSettings = (value: unknown): value is ThemeBackgroundSettings => {
  if (!value || typeof value !== 'object') return false
  const settings = value as Partial<ThemeBackgroundSettings>
  return isBackgroundConfig(settings.modern)
    && isBackgroundConfig(settings.warm)
    && isBackgroundConfig(settings.natural)
    && isBackgroundConfig(settings.macos26)
}

export const usePreferencesStore = defineStore('preferences', () => {
  const themeStore = useThemeStore()
  const libraryViewMode = ref<LibraryViewMode>(readLocalLibraryViewMode())
  const libraryCardPageSize = ref<LibraryPageSize>(
    readLocalLibraryPageSize(LIBRARY_CARD_PAGE_SIZE_KEY)
  )
  const libraryListPageSize = ref<LibraryPageSize>(
    readLocalLibraryPageSize(LIBRARY_LIST_PAGE_SIZE_KEY)
  )
  const scanThreadCount = ref(DEFAULT_SCAN_THREAD_COUNT)
  const dockSize = ref(readLocalNumber(DOCK_SIZE_KEY, DEFAULT_DOCK_SIZE, 44, 76))
  const dockOpacity = ref(readLocalNumber(DOCK_OPACITY_KEY, DEFAULT_DOCK_OPACITY, 40, 96))
  const dockMagnification = ref(readLocalNumber(DOCK_MAGNIFICATION_KEY, DEFAULT_DOCK_MAGNIFICATION, 100, 150))
  const dockBlur = ref(readLocalNumber(DOCK_BLUR_KEY, DEFAULT_DOCK_BLUR, 8, 40))
  const dockIconStyle = ref<DockIconStyle>(readLocalDockIconStyle())
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

  const setLibraryCardPageSize = (value: LibraryPageSize, syncRemote = true) => {
    if (!isLibraryPageSize(value)) return
    libraryCardPageSize.value = value
    localStorage.setItem(LIBRARY_CARD_PAGE_SIZE_KEY, String(value))
    if (syncRemote) persistRemote({ libraryCardPageSize: value })
  }

  const setLibraryListPageSize = (value: LibraryPageSize, syncRemote = true) => {
    if (!isLibraryPageSize(value)) return
    libraryListPageSize.value = value
    localStorage.setItem(LIBRARY_LIST_PAGE_SIZE_KEY, String(value))
    if (syncRemote) persistRemote({ libraryListPageSize: value })
  }

  const setScanThreadCount = (value: number, syncRemote = true) => {
    if (!isScanThreadCount(value)) return
    scanThreadCount.value = value
    if (syncRemote) persistRemote({ scanThreadCount: value })
  }

  const themeColorPreferenceKey: Record<ThemeId, keyof UserPreferences> = {
    modern: 'modernThemeColor',
    warm: 'warmThemeColor',
    natural: 'naturalThemeColor',
    macos26: 'macos26ThemeColor',
  }

  const setThemeAccentColor = (theme: ThemeId, color: string, syncRemote = true) => {
    if (!isThemeColor(color) || !themeStore.setAccentColor(theme, color)) return
    if (syncRemote) persistRemote({ [themeColorPreferenceKey[theme]]: color.toUpperCase() })
  }

  const resetThemeAccentColor = (theme: ThemeId) => {
    themeStore.resetAccentColor(theme)
    persistRemote({ [themeColorPreferenceKey[theme]]: themeStore.accentColors[theme] })
  }

  const resetAllThemeAccentColors = () => {
    themeStore.resetAllAccentColors()
    persistRemote({
      modernThemeColor: themeStore.accentColors.modern,
      warmThemeColor: themeStore.accentColors.warm,
      naturalThemeColor: themeStore.accentColors.natural,
      macos26ThemeColor: themeStore.accentColors.macos26,
    })
  }

  const setThemeBackground = (
    theme: ThemeId,
    config: ThemeBackgroundConfig,
    syncRemote = true,
  ) => {
    const normalized = normalizeThemeBackgroundConfig(
      config,
      DEFAULT_THEME_BACKGROUND_SETTINGS[theme],
    )
    themeStore.setBackgroundSettings(theme, normalized)
    if (syncRemote) persistRemote({ themeBackgrounds: themeStore.backgroundSettings })
  }

  const resetThemeBackground = (theme: ThemeId) => {
    themeStore.resetBackgroundSettings(theme)
    persistRemote({ themeBackgrounds: themeStore.backgroundSettings })
  }

  const resetAllThemeBackgrounds = () => {
    themeStore.resetAllBackgroundSettings()
    persistRemote({ themeBackgrounds: themeStore.backgroundSettings })
  }

  const setDockSize = (value: number, syncRemote = true) => {
    if (!isNumberInRange(value, 44, 76)) return
    dockSize.value = value
    localStorage.setItem(DOCK_SIZE_KEY, String(value))
    if (syncRemote) persistRemote({ dockSize: value })
  }

  const setDockOpacity = (value: number, syncRemote = true) => {
    if (!isNumberInRange(value, 40, 96)) return
    dockOpacity.value = value
    localStorage.setItem(DOCK_OPACITY_KEY, String(value))
    if (syncRemote) persistRemote({ dockOpacity: value })
  }

  const setDockMagnification = (value: number, syncRemote = true) => {
    if (!isNumberInRange(value, 100, 150)) return
    dockMagnification.value = value
    localStorage.setItem(DOCK_MAGNIFICATION_KEY, String(value))
    if (syncRemote) persistRemote({ dockMagnification: value })
  }

  const setDockBlur = (value: number, syncRemote = true) => {
    if (!isNumberInRange(value, 8, 40)) return
    dockBlur.value = value
    localStorage.setItem(DOCK_BLUR_KEY, String(value))
    if (syncRemote) persistRemote({ dockBlur: value })
  }

  const setDockIconStyle = (value: DockIconStyle, syncRemote = true) => {
    if (!isDockIconStyle(value)) return
    dockIconStyle.value = value
    localStorage.setItem(DOCK_ICON_STYLE_KEY, value)
    if (syncRemote) persistRemote({ dockIconStyle: value })
  }

  const resetDockAppearance = () => {
    setDockSize(DEFAULT_DOCK_SIZE, false)
    setDockOpacity(DEFAULT_DOCK_OPACITY, false)
    setDockMagnification(DEFAULT_DOCK_MAGNIFICATION, false)
    setDockBlur(DEFAULT_DOCK_BLUR, false)
    setDockIconStyle(DEFAULT_DOCK_ICON_STYLE, false)
    persistRemote({
      dockSize: DEFAULT_DOCK_SIZE,
      dockOpacity: DEFAULT_DOCK_OPACITY,
      dockMagnification: DEFAULT_DOCK_MAGNIFICATION,
      dockBlur: DEFAULT_DOCK_BLUR,
      dockIconStyle: DEFAULT_DOCK_ICON_STYLE,
    })
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

      const legacyPageSize = isLibraryPageSize(data.libraryPageSize)
        ? data.libraryPageSize
        : null
      if (isLibraryPageSize(data.libraryCardPageSize)) {
        setLibraryCardPageSize(data.libraryCardPageSize, false)
      } else if (legacyPageSize) {
        setLibraryCardPageSize(legacyPageSize, false)
        missingPreferences.libraryCardPageSize = legacyPageSize
      } else {
        missingPreferences.libraryCardPageSize = libraryCardPageSize.value
      }

      if (isLibraryPageSize(data.libraryListPageSize)) {
        setLibraryListPageSize(data.libraryListPageSize, false)
      } else if (legacyPageSize) {
        setLibraryListPageSize(legacyPageSize, false)
        missingPreferences.libraryListPageSize = legacyPageSize
      } else {
        missingPreferences.libraryListPageSize = libraryListPageSize.value
      }

      if (isScanThreadCount(data.scanThreadCount)) {
        setScanThreadCount(data.scanThreadCount, false)
      } else {
        missingPreferences.scanThreadCount = scanThreadCount.value
      }

      const remoteThemeColors: Array<[ThemeId, string | null]> = [
        ['modern', data.modernThemeColor],
        ['warm', data.warmThemeColor],
        ['natural', data.naturalThemeColor],
        ['macos26', data.macos26ThemeColor],
      ]
      remoteThemeColors.forEach(([theme, color]) => {
        if (isThemeColor(color)) setThemeAccentColor(theme, color, false)
        else missingPreferences[themeColorPreferenceKey[theme]] = themeStore.accentColors[theme]
      })

      if (isBackgroundSettings(data.themeBackgrounds)) {
        THEMES.forEach(({ id }) => setThemeBackground(id, data.themeBackgrounds![id], false))
      } else {
        missingPreferences.themeBackgrounds = themeStore.backgroundSettings
      }

      if (isNumberInRange(data.dockSize, 44, 76)) setDockSize(data.dockSize, false)
      else missingPreferences.dockSize = dockSize.value

      if (isNumberInRange(data.dockOpacity, 40, 96)) setDockOpacity(data.dockOpacity, false)
      else missingPreferences.dockOpacity = dockOpacity.value

      if (isNumberInRange(data.dockMagnification, 100, 150)) setDockMagnification(data.dockMagnification, false)
      else missingPreferences.dockMagnification = dockMagnification.value

      if (isNumberInRange(data.dockBlur, 8, 40)) setDockBlur(data.dockBlur, false)
      else missingPreferences.dockBlur = dockBlur.value

      if (isDockIconStyle(data.dockIconStyle)) setDockIconStyle(data.dockIconStyle, false)
      else missingPreferences.dockIconStyle = dockIconStyle.value

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
    libraryCardPageSize,
    libraryListPageSize,
    scanThreadCount,
    dockSize,
    dockOpacity,
    dockMagnification,
    dockBlur,
    dockIconStyle,
    uiFontId,
    readerFontId,
    hydrated,
    setTheme,
    setLibraryViewMode,
    setLibraryCardPageSize,
    setLibraryListPageSize,
    setScanThreadCount,
    setThemeAccentColor,
    resetThemeAccentColor,
    resetAllThemeAccentColors,
    setThemeBackground,
    resetThemeBackground,
    resetAllThemeBackgrounds,
    setDockSize,
    setDockOpacity,
    setDockMagnification,
    setDockBlur,
    setDockIconStyle,
    resetDockAppearance,
    setUiFontId,
    setReaderFontId,
    hydrate,
    resetHydration,
  }
})
