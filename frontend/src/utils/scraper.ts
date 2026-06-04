import api from './api'

export interface ScrapeResult {
  bookId: number
  title: string
  success: boolean
  updatedFields?: string[]
  error?: string
}

export interface ScrapeResponse {
  success: boolean
  message: string
  book?: any
  results?: ScrapeResult[]
  count?: number
}

/**
 * 刮削单本书籍
 */
export async function scrapeBook(bookId: number): Promise<ScrapeResponse> {
  const response = await api.post(`/api/scraper/books/${bookId}`)
  return response.data
}

/**
 * 批量刮削书籍
 */
export async function scrapeBooks(bookIds: number[]): Promise<ScrapeResponse> {
  const response = await api.post('/api/scraper/books/batch', bookIds)
  return response.data
}

/**
 * 刮削所有缺少元数据的书籍
 */
export async function scrapeAllIncomplete(): Promise<ScrapeResponse> {
  const response = await api.post('/api/scraper/scrape-all')
  return response.data
}

/**
 * 下载书籍封面
 */
export async function downloadCover(bookId: number): Promise<ScrapeResponse> {
  const response = await api.post(`/api/scraper/covers/${bookId}`)
  return response.data
}

/**
 * 批量下载缺失封面
 */
export async function downloadMissingCovers(): Promise<ScrapeResponse> {
  const response = await api.post('/api/scraper/covers/download-missing')
  return response.data
}

/**
 * 清除书籍缓存
 */
export async function evictCache(bookId: number): Promise<ScrapeResponse> {
  const response = await api.delete(`/api/scraper/cache/${bookId}`)
  return response.data
}
