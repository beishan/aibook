import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { THEMES, type ThemeId, type ThemeDefinition } from '@/types/theme'

const STORAGE_KEY = 'ai-book-theme'

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeId>('natural')

  const currentThemeDef = computed<ThemeDefinition>(() => {
    return THEMES.find(t => t.id === currentTheme.value) || THEMES[2]
  })

  const currentLayout = computed(() => currentThemeDef.value.layout)

  function setTheme(id: ThemeId) {
    currentTheme.value = id
    document.documentElement.dataset.theme = id
    localStorage.setItem(STORAGE_KEY, id)
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
    setTheme,
    initTheme
  }
})
