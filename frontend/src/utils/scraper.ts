import api from './api'

export interface ScrapeResult {
/**
 * 刮削书籍元数据
 * 从豆瓣/Open Library/Google Books 获取书籍信息
 */
export async function scrapeBook(bookId: number) {
  const response = await api.post(`/api/books/${bookId}/scrape`)
  return response.data
}

/**
 * 下载书籍封面
 */
export async function downloadCover(bookId: number) {
  const response = await api.post(`/api/books/${bookId}/cover`)
  return response.data
}

// ==================== 批量刮削相关 ====================

/**
 * 单本书的刮削结果
 */
export interface BookScrapeResult {
  bookId: number
  title: string
  success: boolean
  updatedFields?: string[]
  updatedFields: string[]
  error?: string
}

export interface ScrapeResponse {
  success: boolean
  message: string
  book?: any
  results?: ScrapeResult[]
  count?: number
/**
 * 批量刮削任务状态
 */
export interface TaskStatus {
  taskId: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  totalBooks: number
  completedBooks: number
  failedBooks: number
  currentBookTitle: string | null
  results: BookScrapeResult[]
  startTime: number
  endTime: number
  errorMessage?: string
}

/**
 * 刮削单本书籍
 * 批量刮削指定书籍
 * @param bookIds 书籍ID列表
 * @param forceUpdate 是否强制更新已有字段
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
export async function batchScrape(bookIds: number[], forceUpdate: boolean = false): Promise<{ taskId: string }> {
  const response = await api.post('/api/books/batch-scrape', { bookIds, forceUpdate })
  return response.data
}

/**
 * 刮削所有缺少元数据的书籍
 * @param forceUpdate 是否强制更新已有字段
 */
export async function scrapeAllIncomplete(): Promise<ScrapeResponse> {
  const response = await api.post('/api/scraper/scrape-all')
export async function scrapeAllIncomplete(forceUpdate: boolean = false): Promise<{ taskId: string }> {
  const response = await api.post(`/api/books/scrape-all-incomplete?forceUpdate=${forceUpdate}`)
  return response.data
}

/**
 * 下载书籍封面
 * 查询刮削任务状态
 */
export async function downloadCover(bookId: number): Promise<ScrapeResponse> {
  const response = await api.post(`/api/scraper/covers/${bookId}`)
export async function getScrapeTask(taskId: string): Promise<TaskStatus> {
  const response = await api.get(`/api/books/scrape-task/${taskId}`)
  return response.data
}

/**
 * 批量下载缺失封面
 * 取消刮削任务
 */
export async function cancelScrapeTask(taskId: string): Promise<{ cancelled: boolean }> {
  const response = await api.post(`/api/books/scrape-task/${taskId}/cancel`)
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

/**
 * 创建SSE连接接收刮削进度
 * @param taskId 任务ID
 * @param token JWT token（用于SSE认证）
 * @param onMessage 收到进度更新时的回调
 * @param onError 错误时的回调
 * @returns EventSource实例，用于关闭连接
 */
export function createScrapeSSE(
  taskId: string,
  token: string,
  onMessage: (status: TaskStatus) => void,
  onError: (error: Event) => void
): EventSource {
  const eventSource = new EventSource(
    `/api/books/scrape-task/${taskId}/stream?token=${encodeURIComponent(token)}`
  )

  eventSource.addEventListener('scrape-progress', (event) => {
    try {
      const status = JSON.parse(event.data) as TaskStatus
      onMessage(status)
    } catch (e) {
      console.error('解析SSE数据失败:', e)
    }
  })

  eventSource.onerror = (error) => {
    console.error('SSE连接错误:', error)
    onError(error)
  }

  return eventSource
}

/**
 * 从localStorage获取JWT token
 */
export function getAuthToken(): string | null {
  return localStorage.getItem('token')
}
