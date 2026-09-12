import api from '@/utils/api'

export interface CrawlerRule {
  titleSelector: string; authorSelector?: string; coverSelector?: string
  descriptionSelector?: string; categorySelector?: string; tagsSelector?: string; statusSelector?: string
  latestChapterSelector?: string; chapterListUrlSelector?: string
  chapterItemSelector: string; chapterTitleSelector?: string; chapterUrlSelector: string
  contentTitleSelector?: string; contentSelector: string; removeSelectors?: string
  regexReplacementsJson?: string; minChapterLength?: number
  discoveryItemSelector?: string; discoveryUrlSelector?: string; discoveryTitleSelector?: string
  discoveryAuthorSelector?: string; discoveryCoverSelector?: string; discoveryCategorySelector?: string
  discoveryLatestChapterSelector?: string; discoveryNextPageSelector?: string
  xpathRemoveSelectors?:string; stringReplacementsJson?:string
  removeBlankLines?:boolean; saveOriginalHtml?:boolean
}
export interface CrawlerProxy { name:string; url:string; enabled:boolean }
export interface CrawlerSitePayload {
  siteName: string; siteCode: string; baseUrl: string; homeUrl?: string; enabled: boolean
  autoScan: boolean; autoCrawl: boolean; autoUpdate: boolean; autoImportLibrary: boolean
  requestIntervalMillis: number; randomDelayMillis: number; maxConcurrency: number
  encoding: string; proxies:CrawlerProxy[]; scanIntervalMinutes:number
  updateIntervalMinutes:number; maxDiscoveryPages:number; autoImportFormat:'TXT'|'EPUB'|'BOTH'; contentFailureMarkers:string[]
}
export interface CrawlerSite extends CrawlerSitePayload { id: number; status: string; bookCount: number; proxy?:string; rule?:CrawlerRule; ruleVersion?:number; activeRuleId?:number; ruleCount:number; lastScanAt?:string; lastUpdateAt?:string; lastHealthCheckAt?:string; healthMessage?:string; createdAt: string }
export interface CrawlerDiscoveryPagePayload { pageName:string; pageUrl:string; autoScanEnabled:boolean; scanIntervalMinutes:number; maxPages:number }
export interface CrawlerDiscoveryPage extends CrawlerDiscoveryPagePayload { id:number; siteId:number; lastScanAt?:string; createdAt:string }
export interface CrawlerBook { id:number; siteId:number; siteName:string; externalBookId:string; bookUrl:string; bookName:string; author?:string; coverUrl?:string; description?:string; category?:string; tags:string[]; bookStatus?:string; latestChapter?:string; discoveryPageId?:number; discoveryPageName?:string; chapterCount:number; crawledChapterCount:number; failedChapterCount:number; crawlStatus:string; discoveryStatus:string; importStatus:string; autoUpdateEnabled:boolean; autoSyncLibrary:boolean; libraryBookId?:number; discoverTime:string; lastCrawlStartedAt?:string; lastCrawlTime?:string; createdAt?:string }
export interface CrawlerTask { id:string; type:string; status:string; priority:string; siteId:number; siteName:string; discoveryPageId?:number; discoveryPageName?:string; scanMaxPages?:number; scannedPageCount:number; progressPercent:number; bookId?:number; bookName?:string; totalCount:number; successCount:number; newBookCount:number; duplicateCount:number; failedCount:number; waitingCount:number; currentChapter?:string; averageRequestMillis:number; errorMessage?:string; startedAt?:string; finishedAt?:string; createdAt:string }
export interface CrawlerTaskQueueSettings { maxConcurrentTasks:number; runningCount:number; queuedCount:number }
export interface CrawlerScanResult { id:number; bookId?:number; bookName:string; bookUrl:string; resultStatus:'NEW'|'DUPLICATE'|'BLACKLISTED'|'FAILED'; errorMessage?:string; createdAt:string }
export interface CrawlerChapter { id:number; chapterIndex:number; chapterName:string; chapterUrl:string; wordCount:number; crawlStatus:string; accessStatus:string; retryCount:number; errorMessage?:string; crawlTime?:string; createdAt?:string }
export interface CrawlerChapterFocus { chapter:CrawlerChapter; page:number }
export interface CrawlerLog { id:number; description:string; details?:string; createdAt:string }
export interface CrawlerExport { id:number; format:string; fileSize:number; fileHash:string; createdAt:string }
export interface CrawlerDashboard { siteCount:number; enabledSiteCount:number; bookCount:number; completedBookCount:number; crawlingBookCount:number; failedBookCount:number; todayNewBooks:number; todayNewChapters:number; readyToImportCount:number; importedCount:number; recentTasks:CrawlerTask[] }
export interface CrawlerRuleTest { success:boolean; title?:string; author?:string; description?:string; coverUrl?:string; category?:string; tags:string[]; bookStatus?:string; chapterListUrl?:string; chapterCount:number; sampleChapter?:string; contentLength:number; contentPreview?:string; durationMillis:number; errorMessage?:string }
export interface CrawlerRuleVersion { id:number; version:number; changeSummary:string; enabled:boolean; rule:CrawlerRule; createdAt:string; updatedAt?:string }
export interface CrawlerRuleSave { version:number; changeSummary:string; rule:CrawlerRule; enabled:boolean }
export interface CrawlerRuleExport { schemaVersion:number; siteCode:string; version:number; changeSummary:string; rule:CrawlerRule; enabled?:boolean }
export interface PageResult<T> { content:T[]; totalElements:number; totalPages:number; number:number; size:number; first:boolean; last:boolean }
export interface CrawlerDiscoveryQuery { page:number; size:number; keyword?:string; siteId?:number; sort:string }
export interface CrawlerBookQuery { page:number; size:number; keyword?:string; siteId?:number; crawlStatus?:string; importStatus?:string; sort:string }
export interface CrawlerChapterQuery { page:number; size:number; sort:'INDEX_ASC'|'INDEX_DESC'|'CREATED_DESC' }
export interface CrawlerTaskQuery { page:number; size:number; failedOnly?:boolean }

export const crawlerApi = {
  dashboard: () => api.get<CrawlerDashboard>('/api/crawler/dashboard').then(r => r.data),
  sites: () => api.get<CrawlerSite[]>('/api/crawler/sites').then(r => r.data),
  createSite: (data:CrawlerSitePayload) => api.post<CrawlerSite>('/api/crawler/sites', data).then(r => r.data),
  updateSite: (id:number, data:CrawlerSitePayload) => api.put<CrawlerSite>(`/api/crawler/sites/${id}`, data).then(r => r.data),
  deleteSite: (id:number) => api.delete(`/api/crawler/sites/${id}`),
  crawlUrl: (siteId:number, url:string) => api.post<CrawlerTask>(`/api/crawler/sites/${siteId}/crawl`, { url }).then(r => r.data),
  scanSite: (siteId:number) => api.post<CrawlerTask>(`/api/crawler/sites/${siteId}/scan`).then(r => r.data),
  discoveryPages: () => api.get<CrawlerDiscoveryPage[]>('/api/crawler/discovery-pages').then(r => r.data),
  createDiscoveryPage: (siteId:number,data:CrawlerDiscoveryPagePayload) => api.post<CrawlerDiscoveryPage>(`/api/crawler/sites/${siteId}/discovery-pages`,data).then(r => r.data),
  updateDiscoveryPage: (id:number,data:CrawlerDiscoveryPagePayload) => api.put<CrawlerDiscoveryPage>(`/api/crawler/discovery-pages/${id}`,data).then(r => r.data),
  deleteDiscoveryPage: (id:number) => api.delete(`/api/crawler/discovery-pages/${id}`),
  scanDiscoveryPage: (id:number) => api.post<CrawlerTask>(`/api/crawler/discovery-pages/${id}/scan`).then(r => r.data),
  testRule: (siteId:number, url:string, rule?:CrawlerRule) => api.post<CrawlerRuleTest>(`/api/crawler/sites/${siteId}/rules/test`, {url,rule}).then(r => r.data),
  checkRule: (siteId:number) => api.post<CrawlerRuleTest>(`/api/crawler/sites/${siteId}/rules/health`).then(r => r.data),
  rules: (siteId:number) => api.get<CrawlerRuleVersion[]>(`/api/crawler/sites/${siteId}/rules`).then(r => r.data),
  createRule: (siteId:number, data:CrawlerRuleSave) => api.post<CrawlerRuleVersion>(`/api/crawler/sites/${siteId}/rules`,data).then(r => r.data),
  updateRule: (siteId:number, ruleId:number, data:CrawlerRuleSave) => api.put<CrawlerRuleVersion>(`/api/crawler/sites/${siteId}/rules/${ruleId}`,data).then(r => r.data),
  deleteRule: (siteId:number, ruleId:number) => api.delete(`/api/crawler/sites/${siteId}/rules/${ruleId}`),
  setRuleStatus: (siteId:number, ruleId:number, enabled:boolean) => api.put<CrawlerRuleVersion>(`/api/crawler/sites/${siteId}/rules/${ruleId}/status`,{enabled}).then(r => r.data),
  exportRule: (siteId:number, ruleId:number) => api.get<CrawlerRuleExport>(`/api/crawler/sites/${siteId}/rules/${ruleId}/export`).then(r => r.data),
  importRule: (siteId:number, data:CrawlerRuleExport & {enabled:boolean}) => api.post<CrawlerRuleVersion>(`/api/crawler/sites/${siteId}/rules/import`,data).then(r => r.data),
  books: (params:CrawlerBookQuery) => api.get<PageResult<CrawlerBook>>('/api/crawler/books', { params }).then(r => r.data),
  book: (bookId:number) => api.get<CrawlerBook>(`/api/crawler/books/${bookId}`).then(r => r.data),
  discoveredBooks: (params:CrawlerDiscoveryQuery) => api.get<PageResult<CrawlerBook>>('/api/crawler/books/discovered', {params}).then(r => r.data),
  chapters: (bookId:number, params:CrawlerChapterQuery) => api.get<PageResult<CrawlerChapter>>(`/api/crawler/books/${bookId}/chapters`, {params}).then(r => r.data),
  currentChapter: (bookId:number, size:number) => api.get<CrawlerChapterFocus|undefined>(`/api/crawler/books/${bookId}/chapters/current`, {params:{size}}).then(r => r.data||undefined),
  logs: (bookId:number) => api.get<CrawlerLog[]>(`/api/crawler/books/${bookId}/logs`, {params:{limit:100}}).then(r => r.data),
  chapter: (bookId:number, chapterId:number) => api.get<{title:string;url:string;content:string;errorMessage:string}>(`/api/crawler/books/${bookId}/chapters/${chapterId}`).then(r => r.data),
  continueBook: (bookId:number) => api.post<CrawlerTask>(`/api/crawler/books/${bookId}/continue`).then(r => r.data),
  retryFailures: (bookId:number) => api.post<CrawlerTask>(`/api/crawler/books/${bookId}/retry-failures`).then(r => r.data),
  checkUpdates: (bookId:number) => api.post<CrawlerTask>(`/api/crawler/books/${bookId}/check-updates`).then(r => r.data),
  setBookStatus: (bookId:number, status:string, autoUpdateEnabled:boolean) => api.put<CrawlerBook>(`/api/crawler/books/${bookId}/crawl-status`, {status,autoUpdateEnabled}).then(r => r.data),
  setLibrarySync: (bookId:number, enabled:boolean) => api.put<CrawlerBook>(`/api/crawler/books/${bookId}/library-sync`, {enabled}).then(r => r.data),
  batchCrawl: (bookIds:number[]) => api.post<CrawlerTask[]>('/api/crawler/books/batch/crawl', {bookIds}).then(r => r.data),
  setDiscoveryStatus: (bookIds:number[], status:'ACTIVE'|'IGNORED'|'BLACKLISTED') => api.put<CrawlerBook[]>('/api/crawler/books/batch/discovery-status', {bookIds,status}).then(r => r.data),
  generate: (bookId:number, formats:string[]) => api.post<CrawlerExport[]>(`/api/crawler/books/${bookId}/exports`, { formats }).then(r => r.data),
  exports: (bookId:number) => api.get<CrawlerExport[]>(`/api/crawler/books/${bookId}/exports`).then(r => r.data),
  importBook: (bookId:number, formats:string[]) => api.post<{bookId:number}>(`/api/crawler/books/${bookId}/import`, { formats }).then(r => r.data),
  tasks: (params:CrawlerTaskQuery) => api.get<PageResult<CrawlerTask>>('/api/crawler/tasks', { params }).then(r => r.data),
  queuedTasks: () => api.get<CrawlerTask[]>('/api/crawler/tasks/queued').then(r => r.data),
  task: (id:string) => api.get<CrawlerTask>(`/api/crawler/tasks/${id}`).then(r => r.data),
  taskQueueSettings: () => api.get<CrawlerTaskQueueSettings>('/api/crawler/tasks/queue-settings').then(r => r.data),
  updateTaskQueueSettings: (maxConcurrentTasks:number) => api.put<CrawlerTaskQueueSettings>('/api/crawler/tasks/queue-settings',{maxConcurrentTasks}).then(r => r.data),
  scanResults: (id:string,page:number,size:number) => api.get<PageResult<CrawlerScanResult>>(`/api/crawler/tasks/${id}/scan-results`,{params:{page,size}}).then(r=>r.data),
  updateTask: (id:string, priority:'LOW'|'NORMAL'|'HIGH') => api.put<CrawlerTask>(`/api/crawler/tasks/${id}`, {priority}).then(r => r.data),
  deleteTask: (id:string) => api.delete(`/api/crawler/tasks/${id}`),
  taskCommand: (id:string, command:'pause'|'resume'|'cancel') => api.post<CrawlerTask>(`/api/crawler/tasks/${id}/${command}`).then(r => r.data),
}
