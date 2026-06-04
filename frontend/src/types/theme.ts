export type ThemeId = 'modern' | 'warm' | 'natural'

export type LayoutType = 'sidebar' | 'topbar' | 'dock'

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
    description: '干净利落，Notion/Linear 风格',
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
