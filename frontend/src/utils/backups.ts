import api from '@/utils/api'

export interface BackupTask {
  id: number
  name: string
  databaseEnabled: boolean
  booksEnabled: boolean
  uploadsEnabled: boolean
  crawlerDataEnabled: boolean
  scheduleEnabled: boolean
  enabled: boolean
  cronExpression: string
  nextRunAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export type BackupTaskInput = Omit<BackupTask, 'id' | 'nextRunAt' | 'createdAt' | 'updatedAt'>

export interface BackupExecution {
  id: number
  taskId: number | null
  taskName: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'DELETED'
  executionStatus: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  deletedAt: string | null
  deletionReason: string | null
  contents: string
  details: string
  currentStage: string | null
  progressPercent: number | null
  progressDetail: string | null
  outputPath: string | null
  errorMessage: string | null
  fileSizeBytes: number | null
  fileCount: number | null
  startedAt: string | null
  finishedAt: string | null
  durationMillis?: number | null
}

export interface BackupExecutionPage {
  content: BackupExecution[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  active: boolean
}

export interface BackupPath {
  path: string
  exists: boolean
  writable: boolean
}

export interface BackupRetentionSettings {
  enabled: boolean
  recentDays: number
  weeklyWeeks: number
  monthlyMonths: number
}

export const backupApi = {
  path: () => api.get<BackupPath>('/api/system/backups/path').then(({ data }) => data),
  retention: () => api
    .get<BackupRetentionSettings>('/api/system/backups/retention')
    .then(({ data }) => data),
  updateRetention: (settings: BackupRetentionSettings) => api
    .put<BackupRetentionSettings>('/api/system/backups/retention', settings)
    .then(({ data }) => data),
  tasks: () => api.get<BackupTask[]>('/api/system/backups/tasks').then(({ data }) => data),
  executions: () => api
    .get<BackupExecution[]>('/api/system/backups/executions')
    .then(({ data }) => data),
  executionPage: (page: number, size = 20) => api
    .get<BackupExecutionPage>('/api/system/backups/executions/page', { params: { page, size } })
    .then(({ data }) => data),
  createTask: (input: BackupTaskInput) => api
    .post<BackupTask>('/api/system/backups/tasks', input)
    .then(({ data }) => data),
  updateTask: (id: number, input: BackupTaskInput) => api
    .put<BackupTask>(`/api/system/backups/tasks/${id}`, input)
    .then(({ data }) => data),
  deleteTask: (id: number) => api.delete(`/api/system/backups/tasks/${id}`),
  runTask: (id: number) => api
    .post<BackupExecution>(`/api/system/backups/tasks/${id}/run`)
    .then(({ data }) => data),
  runImmediately: (input: BackupTaskInput) => api
    .post<BackupExecution>('/api/system/backups/run', input)
    .then(({ data }) => data),
}

export function formatBackupDuration(execution: BackupExecution): string {
  const status = execution.executionStatus || execution.status
  if (status === 'QUEUED') return '待执行'
  if (status === 'RUNNING') return '执行中'
  const duration = execution.durationMillis
  if (duration == null || !Number.isFinite(duration) || duration < 0) return '—'
  if (duration < 1000) return `${Math.floor(duration)} 毫秒`
  const seconds = Math.floor(duration / 1000)
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分 ${seconds % 60} 秒`
  const hours = Math.floor(minutes / 60)
  return `${hours} 小时 ${minutes % 60} 分 ${seconds % 60} 秒`
}
