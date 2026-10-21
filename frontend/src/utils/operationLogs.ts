import api from '@/utils/api'

export interface OperationLog {
  id: number
  action: string
  description: string
  details?: string
  bookTitle?: string
  createdAt: string
  retained: boolean
}

export interface LogRetentionRule {
  action: string
  label: string
  retentionDays: number | null
}

export interface LogRetentionSettings {
  rules: LogRetentionRule[]
}

export const retentionOptions = [
  { value: null, label: '未配置（不清理）' },
  { value: 2, label: '最近两天' },
  { value: 7, label: '最近一周' },
  { value: 14, label: '最近两周' },
  { value: 30, label: '最近 30 天' },
  { value: 90, label: '最近 90 天' },
  { value: 180, label: '最近半年（180 天）' },
  { value: 365, label: '最近一年（365 天）' },
  { value: 0, label: '永久留存' },
] as const

export const retentionKey = (rules: LogRetentionRule[]) => JSON.stringify(
  rules.map(rule => [rule.action, rule.retentionDays]),
)

export const operationLogsApi = {
  list: (params: { page: number; size: number; action?: string; keyword?: string }) =>
    api.get<{ content: OperationLog[]; totalElements: number; number: number }>(
      '/api/operation-logs', { params },
    ).then(response => response.data),
  settings: () => api.get<LogRetentionSettings>('/api/operation-logs/retention')
    .then(response => response.data),
  saveSettings: (settings: LogRetentionSettings) =>
    api.put<LogRetentionSettings>('/api/operation-logs/retention', settings)
      .then(response => response.data),
  cleanup: () => api.post<{ deletedCount: number }>('/api/operation-logs/cleanup')
    .then(response => response.data),
  retain: (id: number) => api.post(`/api/operation-logs/${id}/retain`),
  delete: (id: number) => api.delete(`/api/operation-logs/${id}`),
}
