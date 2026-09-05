import api from '@/utils/api'

export interface CrawlerRule {
  titleSelector: string; authorSelector?: string; coverSelector?: string
  descriptionSelector?: string; categorySelector?: string; statusSelector?: string
  latestChapterSelector?: string; chapterListUrlSelector?: string
  chapterItemSelector: string; chapterTitleSelector?: string; chapterUrlSelector: string
  contentTitleSelector?: string; contentSelector: string; removeSelectors?: string
  regexReplacementsJson?: string; minChapterLength?: number
}
export interface CrawlerSitePayload {
  siteName: string; siteCode: string; baseUrl: string; homeUrl?: string; enabled: boolean
  autoScan: boolean; autoCrawl: boolean; autoUpdate: boolean; autoImportLibrary: boolean
  requestIntervalMillis: number; randomDelayMillis: number; maxConcurrency: number
  timeoutMillis: number; retryCount: number; encoding: string; userAgent?: string
  cookie?: string; headersJson?: string; proxy?: string; rule: CrawlerRule
}
export interface CrawlerSite extends CrawlerSitePayload { id: number; status: string; bookCount: number; createdAt: string }
export interface CrawlerBook { id:number; siteId:number; siteName:string; externalBookId:string; bookUrl:string; bookName:string; author?:string; coverUrl?:string; description?:string; category?:string; bookStatus?:string; latestChapter?:string; chapterCount:number; crawledChapterCount:number; failedChapterCount:number; crawlStatus:string; importStatus:string; libraryBookId?:number; discoverTime:string; lastCrawlTime?:string }
export interface CrawlerTask { id:string; type:string; status:string; priority:string; siteId:number; siteName:string; bookId?:number; bookName?:string; totalCount:number; successCount:number; failedCount:number; waitingCount:number; currentChapter?:string; averageRequestMillis:number; errorMessage?:string; startedAt?:string; finishedAt?:string; createdAt:string }
export interface CrawlerChapter { id:number; chapterIndex:number; chapterName:string; chapterUrl:string; wordCount:number; crawlStatus:string; accessStatus:string; retryCount:number; errorMessage?:string; crawlTime?:string }
export interface CrawlerExport { id:number; format:string; fileSize:number; fileHash:string; createdAt:string }
export interface CrawlerDashboard { siteCount:number; enabledSiteCount:number; bookCount:number; completedBookCount:number; crawlingBookCount:number; failedBookCount:number; todayNewBooks:number; todayNewChapters:number; readyToImportCount:number; importedCount:number; recentTasks:CrawlerTask[] }

export const crawlerApi = {
  dashboard: () => api.get<CrawlerDashboard>('/api/crawler/dashboard').then(r => r.data),
  sites: () => api.get<CrawlerSite[]>('/api/crawler/sites').then(r => r.data),
  createSite: (data:CrawlerSitePayload) => api.post<CrawlerSite>('/api/crawler/sites', data).then(r => r.data),
  updateSite: (id:number, data:CrawlerSitePayload) => api.put<CrawlerSite>(`/api/crawler/sites/${id}`, data).then(r => r.data),
  deleteSite: (id:number) => api.delete(`/api/crawler/sites/${id}`),
  crawlUrl: (siteId:number, url:string) => api.post<CrawlerTask>(`/api/crawler/sites/${siteId}/crawl`, { url }).then(r => r.data),
  books: () => api.get<{content:CrawlerBook[]}>('/api/crawler/books', { params:{ size:100 } }).then(r => r.data.content),
  chapters: (bookId:number) => api.get<CrawlerChapter[]>(`/api/crawler/books/${bookId}/chapters`).then(r => r.data),
  chapter: (bookId:number, chapterId:number) => api.get<{title:string;url:string;content:string;errorMessage:string}>(`/api/crawler/books/${bookId}/chapters/${chapterId}`).then(r => r.data),
  continueBook: (bookId:number) => api.post<CrawlerTask>(`/api/crawler/books/${bookId}/continue`).then(r => r.data),
  retryFailures: (bookId:number) => api.post<CrawlerTask>(`/api/crawler/books/${bookId}/retry-failures`).then(r => r.data),
  generate: (bookId:number, formats:string[]) => api.post<CrawlerExport[]>(`/api/crawler/books/${bookId}/exports`, { formats }).then(r => r.data),
  exports: (bookId:number) => api.get<CrawlerExport[]>(`/api/crawler/books/${bookId}/exports`).then(r => r.data),
  importBook: (bookId:number, format:string) => api.post<{bookId:number}>(`/api/crawler/books/${bookId}/import`, { format }).then(r => r.data),
  tasks: () => api.get<CrawlerTask[]>('/api/crawler/tasks').then(r => r.data),
  taskCommand: (id:string, command:'pause'|'resume'|'cancel') => api.post<CrawlerTask>(`/api/crawler/tasks/${id}/${command}`).then(r => r.data),
}
