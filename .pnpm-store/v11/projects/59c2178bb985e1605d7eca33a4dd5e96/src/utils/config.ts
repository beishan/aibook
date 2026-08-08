import api from './api'

/**
 * 获取刮削配置
 */
export async function getScraperConfig(): Promise<Record<string, string>> {
  const response = await api.get('/api/config/scraper')
  return response.data
}

/**
 * 更新刮削配置
 */
export async function updateScraperConfig(configs: Record<string, string>): Promise<void> {
  await api.put('/api/config/scraper', configs)
}

/**
 * 获取刮削器状态
 */
export interface ScraperStatus {
  name: string
  configKey: string
  enabled: boolean
  priority: number
  needsApiKey: boolean
  hasApiKey?: boolean
}

export async function getScraperStatus(): Promise<ScraperStatus[]> {
  const response = await api.get('/api/scraper/status')
  return response.data
}
