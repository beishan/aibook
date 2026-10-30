import { reactive } from 'vue'
import api from '@/utils/api'

export interface PlaceholderSettings {
  mode: 'PREFIX' | 'FULL' | 'CUSTOM'
  length: number
  text: string
}
export const defaultPlaceholderSettings = (): PlaceholderSettings => ({ mode: 'PREFIX', length: 2, text: '书' })
export const bookCoverPlaceholder = reactive(defaultPlaceholderSettings())
let generation = 0
export function resetBookCoverPlaceholder() {
  generation++
  Object.assign(bookCoverPlaceholder, defaultPlaceholderSettings())
}
export async function hydrateBookCoverPlaceholder() {
  resetBookCoverPlaceholder()
  const current = generation
  const token = localStorage.getItem('token')
  if (!token) return
  try {
    const { data } = await api.get<PlaceholderSettings>('/api/book-cover-placeholder', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    if (current === generation && token === localStorage.getItem('token')) {
      Object.assign(bookCoverPlaceholder, data)
    }
  } catch (error) {
    console.error('Failed to load cover placeholder settings', error)
  }
}
export async function saveBookCoverPlaceholder(settings: PlaceholderSettings) {
  const current = generation
  const token = localStorage.getItem('token')
  const { data } = await api.put<PlaceholderSettings>('/api/book-cover-placeholder', settings)
  if (current === generation && token === localStorage.getItem('token')) {
    Object.assign(bookCoverPlaceholder, data)
  }
}
export function placeholderText(title?: string | null, settings = bookCoverPlaceholder): string {
  if (settings.mode === 'CUSTOM') return settings.text || '书'
  const value = title?.trim() || '书'
  if (settings.mode === 'FULL') return value
  return Array.from(value).slice(0, settings.length).join('')
}
