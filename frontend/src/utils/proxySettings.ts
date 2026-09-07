import api from '@/utils/api'

export interface SystemProxyConfig {
  id: number
  name: string
  url: string
  enabled: boolean
  priority: number
  createdAt?: string
  updatedAt?: string
}

export interface CrawlerProxyConfig {
  id: number
  sourceType: 'SYSTEM' | 'CUSTOM'
  name?: string
  url?: string
  systemProxyId?: number
  systemProxyName?: string
  enabled: boolean
  sourceAvailable: boolean
  effectiveEnabled: boolean
  priority: number
  createdAt?: string
  updatedAt?: string
}

export interface SystemProxyPayload {
  name: string
  url: string
  enabled: boolean
  priority: number
}

export interface CrawlerProxyPayload {
  name?: string
  url?: string
  systemProxyId?: number
  enabled: boolean
  priority: number
}

export interface CrawlerRequestSettings {
  timeoutMillis: number
  retryCount: number
  maxConsecutiveFailures: number
  userAgent: string
  cookie: string
  headersJson: string
}

export const proxySettingsApi = {
  systemList: () => api.get<SystemProxyConfig[]>('/api/proxy-settings/system').then(response => response.data),
  createSystem: (payload: SystemProxyPayload) => api.post<SystemProxyConfig>('/api/proxy-settings/system', payload).then(response => response.data),
  updateSystem: (id: number, payload: SystemProxyPayload) => api.put<SystemProxyConfig>(`/api/proxy-settings/system/${id}`, payload).then(response => response.data),
  reorderSystem: (ids: number[]) => api.put<SystemProxyConfig[]>('/api/proxy-settings/system/order', { ids }).then(response => response.data),
  deleteSystem: (id: number) => api.delete(`/api/proxy-settings/system/${id}`),
  crawlerList: () => api.get<CrawlerProxyConfig[]>('/api/proxy-settings/crawler').then(response => response.data),
  createCrawler: (payload: CrawlerProxyPayload) => api.post<CrawlerProxyConfig>('/api/proxy-settings/crawler', payload).then(response => response.data),
  updateCrawler: (id: number, payload: CrawlerProxyPayload) => api.put<CrawlerProxyConfig>(`/api/proxy-settings/crawler/${id}`, payload).then(response => response.data),
  reorderCrawler: (ids: number[]) => api.put<CrawlerProxyConfig[]>('/api/proxy-settings/crawler/order', { ids }).then(response => response.data),
  deleteCrawler: (id: number) => api.delete(`/api/proxy-settings/crawler/${id}`),
  crawlerSettings: () => api.get<CrawlerRequestSettings>('/api/crawler-settings').then(response => response.data),
  updateCrawlerSettings: (payload: CrawlerRequestSettings) => api.put<CrawlerRequestSettings>('/api/crawler-settings', payload).then(response => response.data),
}
