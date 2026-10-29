import { reactive } from 'vue'
import api from '@/utils/api'

export const badgeTypes = [
  { key: 'structured', label: '在线章节', defaultValue: '线' },
  { key: 'epub', label: 'EPUB', defaultValue: 'E' },
  { key: 'pdf', label: 'PDF', defaultValue: 'P' },
  { key: 'txt', label: 'TXT', defaultValue: '文' },
  { key: 'mobi', label: 'MOBI', defaultValue: 'M' },
  { key: 'azw3', label: 'AZW3', defaultValue: 'A' },
  { key: 'docx', label: 'DOCX', defaultValue: 'W' },
  { key: 'doc', label: 'DOC', defaultValue: 'W' },
  { key: 'html', label: 'HTML', defaultValue: 'H' },
  { key: 'md', label: 'Markdown', defaultValue: '简' },
  { key: 'cbz', label: 'CBZ', defaultValue: '漫' },
  { key: 'cbr', label: 'CBR', defaultValue: '漫' },
  { key: 'other', label: '其他 / 未知类型', defaultValue: '书' },
]
export interface BadgeSettings {
  abbreviations: Record<string, string>
  transparency: number
}
export const defaultBadgeSettings = (): BadgeSettings => ({
  abbreviations: Object.fromEntries(badgeTypes.map(type => [type.key, type.defaultValue])),
  transparency: 30,
})
export const bookCardBadges = reactive<BadgeSettings>(defaultBadgeSettings())
let generation = 0
export function resetBookCardBadges() {
  generation++
  Object.assign(bookCardBadges, defaultBadgeSettings())
}
export async function hydrateBookCardBadges() {
  resetBookCardBadges()
  const current = generation
  const token = localStorage.getItem('token')
  if (!token) return
  try {
    const { data } = await api.get<BadgeSettings>('/api/book-card-badges')
    if (current === generation && token === localStorage.getItem('token')) {
      Object.assign(bookCardBadges, data)
    }
  } catch (error) {
    console.error('Failed to load book card badges', error)
  }
}
export async function saveBookCardBadges(settings: BadgeSettings) {
  const current = generation
  const token = localStorage.getItem('token')
  const { data } = await api.put<BadgeSettings>('/api/book-card-badges', settings)
  if (current === generation && token === localStorage.getItem('token')) {
    Object.assign(bookCardBadges, data)
  }
  return data
}
export function badgeType(format?: string) {
  const value = format?.toLowerCase()
  const key = value === 'htm' ? 'html' : value === 'markdown' ? 'md' : value
  return badgeTypes.find(type => type.key === key) || badgeTypes[badgeTypes.length - 1]
}
