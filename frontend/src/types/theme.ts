export type ThemeId = 'modern' | 'warm' | 'natural'

export type LayoutType = 'sidebar' | 'topbar' | 'dock'

export type ThemeAccentColors = Record<ThemeId, string>

export type ThemeBackgroundMode = 'solid' | 'gradient'

export interface ThemeBackgroundConfig {
  mode: ThemeBackgroundMode
  pageColor: string
  secondaryColor: string
  navColor: string
  navOpacity: number
  surfaceColor: string
  surfaceOpacity: number
}

export type ThemeBackgroundSettings = Record<ThemeId, ThemeBackgroundConfig>

export const DEFAULT_THEME_ACCENT_COLORS: ThemeAccentColors = {
  modern: '#2563EB',
  warm: '#A0522D',
  natural: '#2E7D5A',
}

export const DEFAULT_THEME_BACKGROUND_SETTINGS: ThemeBackgroundSettings = {
  modern: {
    mode: 'solid',
    pageColor: '#F5F5F5',
    secondaryColor: '#EEF2F7',
    navColor: '#FFFFFF',
    navOpacity: 100,
    surfaceColor: '#FFFFFF',
    surfaceOpacity: 100,
  },
  warm: {
    mode: 'solid',
    pageColor: '#FAF6F1',
    secondaryColor: '#F3E9DC',
    navColor: '#FFFBF5',
    navOpacity: 100,
    surfaceColor: '#FFFBF5',
    surfaceOpacity: 100,
  },
  natural: {
    mode: 'gradient',
    pageColor: '#E8F5E9',
    secondaryColor: '#E0F2F1',
    navColor: '#FFFFFF',
    navOpacity: 75,
    surfaceColor: '#FFFFFF',
    surfaceOpacity: 72,
  },
}

export interface ThemeDefinition {
  id: ThemeId
  name: string
  icon: string
  description: string
  layout: LayoutType
}

export const THEMES: ThemeDefinition[] = [
  {
    id: 'modern',
    name: '现代简约',
    icon: '⬜',
    description: 'Element Plus 组件化管理界面',
    layout: 'sidebar'
  },
  {
    id: 'warm',
    name: '暖色文艺',
    icon: '🟤',
    description: '温暖纸张质感，豆瓣/微信读书风格',
    layout: 'topbar'
  },
  {
    id: 'natural',
    name: '自然清新',
    icon: '🟢',
    description: '柔和绿色渐变，轻松舒适',
    layout: 'dock'
  }
]
