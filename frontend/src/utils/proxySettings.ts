import api from '@/utils/api'
import type { MihomoCatalog } from '@/utils/crawler'

export interface SystemProxyConfig {
  id: number
  name: string
  url: string
  enabled: boolean
  priority: number
  proxyType: 'SIMPLE' | 'MIHOMO'
  controllerUrl?: string
  secretConfigured: boolean
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
  proxyType?: 'SIMPLE' | 'MIHOMO'
  controllerUrl?: string
  secret?: string
  clearSecret?: boolean
}

export interface MihomoNodeGroup {
  id:number
  systemProxyId:number
  name:string
  controlGroup:string
  proxyUrl:string
  nodes:string[]
}
export type MihomoNodeGroupPayload = Omit<MihomoNodeGroup, 'id'|'systemProxyId'>

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
  retryBackoffMaxMillis: number
  maxInlineRetryDelayMillis: number
  maxResponseSizeMb: number
  maxRedirects: number
  maxOriginConcurrency: number
  adaptiveDelayMaxMillis: number
  circuitCooldownSeconds: number
  accessDeniedCooldownSeconds: number
  robotsCacheMinutes: number
  robotsErrorCacheMinutes: number
  softBlockDetectionEnabled: boolean
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
  mihomoCatalog: (id:number|undefined,config:SystemProxyPayload) => api.post<MihomoCatalog>('/api/proxy-settings/system/mihomo/catalog',{id,config}).then(r => r.data),
  testMihomo: (id:number|undefined,config:SystemProxyPayload) => api.post<{controllerAvailable:boolean;proxyAvailable:boolean;message:string;delay:number|null}>('/api/proxy-settings/system/mihomo/test',{id,config}).then(r => r.data),
  mihomoGroups: (id:number) => api.get<MihomoNodeGroup[]>(`/api/proxy-settings/system/${id}/mihomo/groups`).then(r => r.data),
  createMihomoGroup: (id:number,payload:MihomoNodeGroupPayload) => api.post<MihomoNodeGroup>(`/api/proxy-settings/system/${id}/mihomo/groups`,payload).then(r => r.data),
  updateMihomoGroup: (id:number,groupId:number,payload:MihomoNodeGroupPayload) => api.put<MihomoNodeGroup>(`/api/proxy-settings/system/${id}/mihomo/groups/${groupId}`,payload).then(r => r.data),
  deleteMihomoGroup: (id:number,groupId:number) => api.delete(`/api/proxy-settings/system/${id}/mihomo/groups/${groupId}`),
  mihomoDelay: (id:number,name:string) => api.post<{name:string;alive:boolean;delay:number|null;checkedAt:string}>(`/api/proxy-settings/system/${id}/mihomo/delay`,{name}).then(r => r.data),
  crawlerList: () => api.get<CrawlerProxyConfig[]>('/api/proxy-settings/crawler').then(response => response.data),
  createCrawler: (payload: CrawlerProxyPayload) => api.post<CrawlerProxyConfig>('/api/proxy-settings/crawler', payload).then(response => response.data),
  updateCrawler: (id: number, payload: CrawlerProxyPayload) => api.put<CrawlerProxyConfig>(`/api/proxy-settings/crawler/${id}`, payload).then(response => response.data),
  reorderCrawler: (ids: number[]) => api.put<CrawlerProxyConfig[]>('/api/proxy-settings/crawler/order', { ids }).then(response => response.data),
  deleteCrawler: (id: number) => api.delete(`/api/proxy-settings/crawler/${id}`),
  crawlerSettings: () => api.get<CrawlerRequestSettings>('/api/crawler-settings').then(response => response.data),
  updateCrawlerSettings: (payload: CrawlerRequestSettings) => api.put<CrawlerRequestSettings>('/api/crawler-settings', payload).then(response => response.data),
}
