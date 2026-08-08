import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { DEFAULT_THEME_ACCENT_COLORS, THEMES, type ThemeAccentColors, type ThemeId, type ThemeDefinition } from '@/types/theme'
import { applyThemeColorTokens, normalizeHexColor } from '@/utils/themeColor'

const STORAGE_KEY = 'ai-book-theme'
const ACCENT_STORAGE_KEY = 'ai-book-theme-accent-colors'

const readAccentColors = (): ThemeAccentColors => {
  try {
    const saved = JSON.parse(localStorage.getItem(ACCENT_STORAGE_KEY) || '{}')
    return {
      modern: normalizeHexColor(saved.modern) || DEFAULT_THEME_ACCENT_COLORS.modern,
      warm: normalizeHexColor(saved.warm) || DEFAULT_THEME_ACCENT_COLORS.warm,
      natural: normalizeHexColor(saved.natural) || DEFAULT_THEME_ACCENT_COLORS.natural,
    }
  } catch {
    return { ...DEFAULT_THEME_ACCENT_COLORS }
  }
}

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeId>('natural')
  const accentColors = ref<ThemeAccentColors>(readAccentColors())

  const currentThemeDef = computed<ThemeDefinition>(() => {
    return THEMES.find(t => t.id === currentTheme.value) || THEMES[2]
  })

  const currentLayout = computed(() => currentThemeDef.value.layout)
  const currentAccentColor = computed(() => accentColors.value[currentTheme.value])

  function setTheme(id: ThemeId) {
    currentTheme.value = id
    document.documentElement.dataset.theme = id
    localStorage.setItem(STORAGE_KEY, id)
    applyThemeColorTokens(accentColors.value[id])
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
      setTheme('natural')
    }
  }

  return {
    currentTheme,
    currentThemeDef,
    currentLayout,
    accentColors,
    currentAccentColor,
    setTheme,
    setAccentColor,
    resetAccentColor,
    resetAllAccentColors,
    initTheme,
  }
})
