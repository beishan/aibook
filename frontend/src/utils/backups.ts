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
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  contents: string
  details: string
  outputPath: string | null
  errorMessage: string | null
  fileSizeBytes: number | null
  fileCount: number | null
  startedAt: string | null
  finishedAt: string | null
}

export interface BackupPath {
  path: string
  exists: boolean
  writable: boolean
}

export const backupApi = {
  path: () => api.get<BackupPath>('/api/system/backups/path').then(({ data }) => data),
  tasks: () => api.get<BackupTask[]>('/api/system/backups/tasks').then(({ data }) => data),
  executions: () => api
    .get<BackupExecution[]>('/api/system/backups/executions')
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
