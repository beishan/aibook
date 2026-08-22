import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  DEFAULT_THEME_ACCENT_COLORS,
  DEFAULT_THEME_BACKGROUND_SETTINGS,
  THEMES,
  type ThemeAccentColors,
  type ThemeBackgroundConfig,
  type ThemeBackgroundSettings,
  type ThemeId,
  type ThemeDefinition,
} from '@/types/theme'
import { applyThemeColorTokens, normalizeHexColor } from '@/utils/themeColor'
import { applyThemeBackgroundTokens, normalizeThemeBackgroundConfig } from '@/utils/themeBackground'

const STORAGE_KEY = 'ai-book-theme'
const ACCENT_STORAGE_KEY = 'ai-book-theme-accent-colors'
const BACKGROUND_STORAGE_KEY = 'ai-book-theme-background-settings'
const DEFAULT_THEME: ThemeId = 'macos26'

const readAccentColors = (): ThemeAccentColors => {
  try {
    const saved = JSON.parse(localStorage.getItem(ACCENT_STORAGE_KEY) || '{}')
    return {
      modern: normalizeHexColor(saved.modern) || DEFAULT_THEME_ACCENT_COLORS.modern,
      warm: normalizeHexColor(saved.warm) || DEFAULT_THEME_ACCENT_COLORS.warm,
      natural: normalizeHexColor(saved.natural) || DEFAULT_THEME_ACCENT_COLORS.natural,
      macos26: normalizeHexColor(saved.macos26) || DEFAULT_THEME_ACCENT_COLORS.macos26,
    }
  } catch {
    return { ...DEFAULT_THEME_ACCENT_COLORS }
  }
}

const cloneDefaultBackgroundSettings = (): ThemeBackgroundSettings => ({
  modern: { ...DEFAULT_THEME_BACKGROUND_SETTINGS.modern },
  warm: { ...DEFAULT_THEME_BACKGROUND_SETTINGS.warm },
  natural: { ...DEFAULT_THEME_BACKGROUND_SETTINGS.natural },
  macos26: { ...DEFAULT_THEME_BACKGROUND_SETTINGS.macos26 },
})

const readBackgroundSettings = (): ThemeBackgroundSettings => {
  try {
    const saved = JSON.parse(localStorage.getItem(BACKGROUND_STORAGE_KEY) || '{}')
    return {
      modern: normalizeThemeBackgroundConfig(saved.modern || {}, DEFAULT_THEME_BACKGROUND_SETTINGS.modern),
      warm: normalizeThemeBackgroundConfig(saved.warm || {}, DEFAULT_THEME_BACKGROUND_SETTINGS.warm),
      natural: normalizeThemeBackgroundConfig(saved.natural || {}, DEFAULT_THEME_BACKGROUND_SETTINGS.natural),
      macos26: normalizeThemeBackgroundConfig(saved.macos26 || {}, DEFAULT_THEME_BACKGROUND_SETTINGS.macos26),
    }
  } catch {
    return cloneDefaultBackgroundSettings()
  }
}

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeId>(DEFAULT_THEME)
  const accentColors = ref<ThemeAccentColors>(readAccentColors())
  const backgroundSettings = ref<ThemeBackgroundSettings>(readBackgroundSettings())

  const currentThemeDef = computed<ThemeDefinition>(() => {
    return THEMES.find(t => t.id === currentTheme.value)
      || THEMES.find(t => t.id === DEFAULT_THEME)!
  })

  const currentLayout = computed(() => currentThemeDef.value.layout)
  const currentAccentColor = computed(() => accentColors.value[currentTheme.value])
  const currentBackgroundSettings = computed(() => backgroundSettings.value[currentTheme.value])

  function setTheme(id: ThemeId) {
    currentTheme.value = id
    document.documentElement.dataset.theme = id
    localStorage.setItem(STORAGE_KEY, id)
    applyThemeColorTokens(accentColors.value[id])
    applyThemeBackgroundTokens(backgroundSettings.value[id])
  }

  function setBackgroundSettings(theme: ThemeId, config: ThemeBackgroundConfig) {
    const normalized = normalizeThemeBackgroundConfig(config, DEFAULT_THEME_BACKGROUND_SETTINGS[theme])
    backgroundSettings.value = { ...backgroundSettings.value, [theme]: normalized }
    localStorage.setItem(BACKGROUND_STORAGE_KEY, JSON.stringify(backgroundSettings.value))
    if (theme === currentTheme.value) applyThemeBackgroundTokens(normalized)
  }

  function resetBackgroundSettings(theme: ThemeId) {
    setBackgroundSettings(theme, { ...DEFAULT_THEME_BACKGROUND_SETTINGS[theme] })
  }

  function resetAllBackgroundSettings() {
    backgroundSettings.value = cloneDefaultBackgroundSettings()
    localStorage.setItem(BACKGROUND_STORAGE_KEY, JSON.stringify(backgroundSettings.value))
    applyThemeBackgroundTokens(backgroundSettings.value[currentTheme.value])
  }

  function setAccentColor(theme: ThemeId, color: string) {
    const normalized = normalizeHexColor(color)
    if (!normalized) return false
    accentColors.value = { ...accentColors.value, [theme]: normalized }
    localStorage.setItem(ACCENT_STORAGE_KEY, JSON.stringify(accentColors.value))
    if (theme === currentTheme.value) applyThemeColorTokens(normalized)
    return true
  }

  function resetAccentColor(theme: ThemeId) {
    setAccentColor(theme, DEFAULT_THEME_ACCENT_COLORS[theme])
  }

  function resetAllAccentColors() {
    accentColors.value = { ...DEFAULT_THEME_ACCENT_COLORS }
    localStorage.setItem(ACCENT_STORAGE_KEY, JSON.stringify(accentColors.value))
    applyThemeColorTokens(accentColors.value[currentTheme.value])
  }

  function initTheme() {
    const saved = localStorage.getItem(STORAGE_KEY) as ThemeId | null
    if (saved && THEMES.some(t => t.id === saved)) {
      setTheme(saved)
    } else {
      setTheme(DEFAULT_THEME)
    }
  }

  return {
    currentTheme,
    currentThemeDef,
    currentLayout,
    accentColors,
    currentAccentColor,
    backgroundSettings,
    currentBackgroundSettings,
    setTheme,
    setAccentColor,
    resetAccentColor,
    resetAllAccentColors,
    setBackgroundSettings,
    resetBackgroundSettings,
    resetAllBackgroundSettings,
    initTheme,
  }
})
