import api from '@/utils/api'

export interface RecommendationFilter {
  categoryIds: number[]
  includeSubcategories: boolean
  tagIds: number[]
  matchAllTags: boolean
  excludedTagIds: number[]
  authorIds: number[]
  shelfGroupIds: number[]
  statuses: string[]
  formats: string[]
  sources: string[]
  favorite: boolean | null
  wanted: boolean | null
  onShelf: boolean | null
  addedWithinDays: number | null
  addedBeforeDays: number | null
  notReadDays: number | null
  count: number
  avoidDays: number
  strictAvoid: boolean
}
export interface RecommendationRule {
  id?: number
  name: string
  enabled: boolean
  sortOrder: number
  filter: RecommendationFilter
}
export interface RecommendedBook {
  id: number
  title: string
  author: string
  coverUrl: string | null
  coverHidden?: boolean | null
  readingStatus: string
  format: string
}
export interface RecommendationGroup {
  rule: RecommendationRule
  date: string
  generatedAt: string
  candidateCount: number
  repeatedCount: number
  invalidCount: number
  books: RecommendedBook[]
}
export interface TodayRecommendations {
  date: string
  groups: RecommendationGroup[]
}
export type RecommendationOptions = Record<'categories' | 'tags' | 'authors' | 'shelf_groups', { id: number; name: string }[]>

export const emptyRecommendationRule = (): RecommendationRule => ({
  name: '', enabled: true, sortOrder: 0,
  filter: {
    categoryIds: [], includeSubcategories: true,
    tagIds: [], matchAllTags: false, excludedTagIds: [], authorIds: [], shelfGroupIds: [],
    statuses: [], formats: [], sources: [], favorite: null, wanted: null, onShelf: null,
    addedWithinDays: null, addedBeforeDays: null, notReadDays: null,
    count: 6, avoidDays: 7, strictAvoid: false,
  },
})
export function recommendationDate() {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date())
}
export const recommendationApi = {
  today: () => api.get<TodayRecommendations>('/api/recommendations/today').then(r => r.data),
  rules: () => api.get<RecommendationRule[]>('/api/recommendations/rules').then(r => r.data),
  options: () => api.get<RecommendationOptions>('/api/recommendations/options').then(r => r.data),
  save: (rule: RecommendationRule) => (rule.id
    ? api.put(`/api/recommendations/rules/${rule.id}`, rule)
    : api.post('/api/recommendations/rules', rule)),
  delete: (id: number) => api.delete(`/api/recommendations/rules/${id}`),
  reroll: (id: number) => api.post<RecommendationGroup>(`/api/recommendations/rules/${id}/reroll`).then(r => r.data),
  preview: (rule: RecommendationRule) => api.post<{ count: number }>('/api/recommendations/preview', rule).then(r => r.data.count),
}
