import type { ThemeBackgroundConfig } from '@/types/theme'
import { normalizeHexColor } from '@/utils/themeColor'

const toRgb = (hex: string) => {
  const normalized = normalizeHexColor(hex) || '#FFFFFF'
  return {
    r: Number.parseInt(normalized.slice(1, 3), 16),
    g: Number.parseInt(normalized.slice(3, 5), 16),
    b: Number.parseInt(normalized.slice(5, 7), 16),
  }
}

const rgba = (hex: string, opacity: number) => {
  const { r, g, b } = toRgb(hex)
  return `rgba(${r}, ${g}, ${b}, ${Math.max(0, Math.min(100, opacity)) / 100})`
}

const mix = (hex: string, target: '#000000' | '#FFFFFF', amount: number) => {
  const source = toRgb(hex)
  const destination = toRgb(target)
  const channel = (from: number, to: number) => Math.round(from + (to - from) * amount)
  return `rgb(${channel(source.r, destination.r)}, ${channel(source.g, destination.g)}, ${channel(source.b, destination.b)})`
}

const isDark = (hex: string) => {
  const { r, g, b } = toRgb(hex)
  const linear = [r, g, b].map(value => {
    const channel = value / 255
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  })
  return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722 < 0.28
}

export const normalizeThemeBackgroundConfig = (
  value: Partial<ThemeBackgroundConfig>,
  fallback: ThemeBackgroundConfig,
): ThemeBackgroundConfig => ({
  mode: value.mode === 'gradient' || value.mode === 'solid' ? value.mode : fallback.mode,
  pageColor: normalizeHexColor(value.pageColor || '') || fallback.pageColor,
  secondaryColor: normalizeHexColor(value.secondaryColor || '') || fallback.secondaryColor,
  navColor: normalizeHexColor(value.navColor || '') || fallback.navColor,
  navOpacity: typeof value.navOpacity === 'number' && value.navOpacity >= 20 && value.navOpacity <= 100
    ? Math.round(value.navOpacity) : fallback.navOpacity,
  surfaceColor: normalizeHexColor(value.surfaceColor || '') || fallback.surfaceColor,
  surfaceOpacity: typeof value.surfaceOpacity === 'number' && value.surfaceOpacity >= 35 && value.surfaceOpacity <= 100
    ? Math.round(value.surfaceOpacity) : fallback.surfaceOpacity,
})

export const createThemeBackgroundTokens = (config: ThemeBackgroundConfig) => {
  const pageDark = isDark(config.pageColor)
  const navDark = isDark(config.navColor)
  const surfaceDark = isDark(config.surfaceColor)
  const pagePrimary = pageDark ? '#F8FAFC' : '#17211B'
  const pageSecondary = pageDark ? 'rgba(248, 250, 252, 0.72)' : 'rgba(23, 33, 27, 0.66)'
  const navPrimary = navDark ? '#F8FAFC' : '#17211B'
  const navSecondary = navDark ? 'rgba(248, 250, 252, 0.72)' : 'rgba(23, 33, 27, 0.66)'
  const surfacePrimary = surfaceDark ? '#F8FAFC' : '#1F2923'
  const surfaceSecondary = surfaceDark ? 'rgba(248, 250, 252, 0.70)' : 'rgba(31, 41, 35, 0.66)'
  const surfaceTertiary = surfaceDark ? 'rgba(248, 250, 252, 0.48)' : 'rgba(31, 41, 35, 0.46)'
  const borderTarget = surfaceDark ? '#FFFFFF' : '#000000'

  return {
    '--bg-page': config.pageColor,
    '--bg-page-gradient': config.mode === 'gradient'
      ? `linear-gradient(135deg, ${config.pageColor} 0%, ${config.secondaryColor} 100%)`
      : config.pageColor,
    '--bg-surface': rgba(config.surfaceColor, Math.min(100, config.surfaceOpacity + 12)),
    '--surface-card': rgba(config.surfaceColor, config.surfaceOpacity),
    '--surface-elevated': rgba(config.surfaceColor, Math.min(100, config.surfaceOpacity + 16)),
    '--surface-hover': rgba(config.surfaceColor, Math.max(35, config.surfaceOpacity - 16)),
    '--glass-bg': rgba(config.surfaceColor, Math.max(35, config.surfaceOpacity - 12)),
    '--nav-bg': rgba(config.navColor, config.navOpacity),
    '--nav-border': rgba(config.navColor, Math.max(20, config.navOpacity - 35)),
    '--nav-text-primary': navPrimary,
    '--nav-text-secondary': navSecondary,
    '--text-primary': surfacePrimary,
    '--text-secondary': surfaceSecondary,
    '--text-tertiary': surfaceTertiary,
    '--text-on-page-bg': pagePrimary,
    '--text-on-page-bg-secondary': pageSecondary,
    '--border-color': mix(config.surfaceColor, borderTarget, surfaceDark ? 0.22 : 0.12),
    '--border-color-light': mix(config.surfaceColor, borderTarget, surfaceDark ? 0.13 : 0.06),
    '--shadow-color': pageDark ? 'rgba(0, 0, 0, 0.24)' : 'rgba(24, 45, 34, 0.08)',
    '--el-bg-color': config.surfaceColor,
    '--el-bg-color-page': config.pageColor,
    '--el-fill-color-blank': config.surfaceColor,
    '--el-text-color-primary': surfacePrimary,
    '--el-text-color-regular': surfaceSecondary,
  }
}

export const applyThemeBackgroundTokens = (config: ThemeBackgroundConfig) => {
  const root = document.documentElement
  Object.entries(createThemeBackgroundTokens(config)).forEach(([name, value]) => {
    root.style.setProperty(name, value)
  })
}
