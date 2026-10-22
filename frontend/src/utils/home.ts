import api from '@/utils/api'
import type { RecommendedBook } from '@/utils/recommendations'

export interface HomeBook extends RecommendedBook {
  coverHidden?: boolean | null
  rating?: number | null
  createdAt?: string
  updatedAt?: string
  totalProgress?: number | null
  currentChapterTitle?: string | null
  lastReadAt?: string
}
export interface HomeDashboard {
  overview: {
    totalBooks: number
    readingBooks: number
    finishedBooks: number
    favoriteBooks: number
    wantedBooks: number
    fileSizeBytes: number
  }
  categories: { id: number; name: string; count: number }[]
  continuing: HomeBook[]
  recentlyRead: HomeBook[]
  recentlyAdded: HomeBook[]
  recentlyUpdated: HomeBook[]
  wanted: HomeBook[]
}
export const homeApi = {
  dashboard: () => api.get<HomeDashboard>('/api/home').then(response => response.data),
}
export function readingPercent(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value)
    ? Math.round(Math.max(0, Math.min(100, value))) : 0
}
export function homeFileSize(value: number) {
  if (!Number.isFinite(value) || value <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  return `${(value / 1024 ** index).toFixed(index > 0 ? 1 : 0)} ${units[index]}`
}
