import api from '@/utils/api'

// ==================== 类型定义 ====================

export interface RepairTask {
  id: number
  bookId: number
  bookTitle: string
  versionId?: number
  templateId?: number
  repairMode: 'SAFE' | 'STANDARD' | 'DEEP'
  status: string
  originalContentVersion?: string
  repairedContentVersion?: string
  optionsJson?: string
  reportJson?: string
  totalIssueCount: number
  detectedChapterCount: number
  pendingIssueCount: number
  acceptedIssueCount: number
  rejectedIssueCount: number
  ignoredIssueCount: number
  appliedIssueCount: number
  userId: number
  createdAt: string
  updatedAt: string
  issueTypeCounts?: Record<string, number>
  issueStatusCounts?: Record<string, number>
}

export interface RepairIssue {
  id: number
  taskId: number
  chapterIndex: number
  chapterTitle?: string
  type: string
  startOffset?: number
  endOffset?: number
  originalText?: string
  suggestedText?: string
  reason?: string
  ruleId?: string
  confidence?: number
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'IGNORED' | 'APPLIED' | 'REVERTED'
  source: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  metadata?: Record<string, unknown>
  candidates?: string[]
  createdAt: string
  updatedAt: string
}

export interface EncodingDetectResult {
  encoding: string
  confidence: number
  anomalyCount: number
  hasBom: boolean
  bomType: string
  previewText?: string
  hasGarbled: boolean
  garbledType?: string
  candidateEncodings?: string[]
}

export interface RepairPreview {
  originalText: string
  repairedText: string
  diffLines: DiffLine[]
  issueIds: number[]
}

export interface DiffLine {
  type: 'ADDED' | 'REMOVED' | 'UNCHANGED' | 'MODIFIED'
  originalLine: string
  repairedLine: string
}

export interface RepairRule {
  id: number
  name: string
  type: string
  pattern: string
  matchScope: string
  action: string
  replacement?: string
  riskLevel: string
  enabled: boolean
  systemRule: boolean
  whitelist: boolean
  scope: string
  templateId?: number
  bookId?: number
  userId?: number
}

export interface RepairTemplate {
  id: number
  name: string
  description?: string
  repairMode: 'SAFE' | 'STANDARD' | 'DEEP'
  enabledItemsJson?: string
  chapterFormat: string
  indentStyle: string
  blankLineCount: number
  punctuationNormalize: boolean
  traditionalSimplified: string
  minChapterWords: number
  maxChapterWords: number
  autoApplyThreshold: number
  systemTemplate: boolean
  userId?: number
}

export interface RepairReport {
  detectedChapters: number
  fixedEncoding: number
  removedAds: number
  normalizedChapters: number
  fixedLineBreaks: number
  removedDuplicates: number
  cleanedInvisibleChars: number
  normalizedPunctuation: number
  anomalies: { type: string; description: string; count: number }[]
  unconfirmedCount: number
}

// ==================== API 调用 ====================

// 修复任务
export function createRepairTask(
  bookId: number,
  repairMode: string,
  versionId?: number,
  templateId?: number,
  optionsJson?: string,
) {
  return api.post<RepairTask>('/api/text-repair/tasks', {
    bookId,
    repairMode,
    versionId,
    templateId,
    optionsJson,
  })
}

export function getRepairTasks(page = 0, size = 10) {
  return api.get<{ content: RepairTask[]; totalElements: number }>('/api/text-repair/tasks', {
    params: { page, size },
  })
}

export function getRepairRecords(page = 0, size = 10) {
  return api.get<{ content: RepairTask[]; totalElements: number }>('/api/text-repair/records', {
    params: { page, size },
  })
}

export function getRepairTask(taskId: number) {
  return api.get<RepairTask>(`/api/text-repair/tasks/${taskId}`)
}

export function rescanRepairTask(taskId: number) {
  return api.post<RepairTask>(`/api/text-repair/tasks/${taskId}/rescan`)
}

export function deleteRepairTask(taskId: number) {
  return api.delete(`/api/text-repair/tasks/${taskId}`)
}

export function getBookRepairTasks(bookId: number) {
  return api.get<RepairTask[]>(`/api/text-repair/books/${bookId}/tasks`)
}

// 修复问题
export function getRepairIssues(
  taskId: number,
  params: { type?: string; status?: string; page?: number; size?: number } = {},
) {
  return api.get<{ content: RepairIssue[]; totalElements: number }>(
    `/api/text-repair/tasks/${taskId}/issues`,
    { params: { page: 0, size: 100, ...params } },
  )
}

export function updateRepairIssue(
  issueId: number,
  data: { status: string; manualText?: string; applyToAll?: boolean },
) {
  return api.put<RepairIssue>(`/api/text-repair/issues/${issueId}`, data)
}

export function batchUpdateIssues(taskId: number, issueIds: number[], status: string) {
  return api.post(`/api/text-repair/tasks/${taskId}/issues/batch`, { issueIds, status })
}

export function acceptHighConfidence(taskId: number, threshold = 0.8) {
  return api.post<{ acceptedCount: number }>(`/api/text-repair/tasks/${taskId}/accept-high`, null, {
    params: { threshold },
  })
}

export function revertAllRepairs(taskId: number) {
  return api.post(`/api/text-repair/tasks/${taskId}/revert`)
}

export function restoreOriginalRepairVersion(taskId: number) {
  return api.post(`/api/text-repair/tasks/${taskId}/restore-original`)
}

// 编码检测
export function detectEncoding(bookId: number, versionId?: number) {
  return api.get<EncodingDetectResult>(`/api/text-repair/books/${bookId}/encoding`, {
    params: { versionId },
  })
}

export function previewEncoding(bookId: number, encoding: string, versionId?: number) {
  return api.post<{ preview: string }>(`/api/text-repair/books/${bookId}/encoding/preview`, {
    bookId,
    encoding,
    versionId,
  })
}

// 修复预览与应用
export function previewRepair(taskId: number) {
  return api.get<RepairPreview>(`/api/text-repair/tasks/${taskId}/preview`)
}

export function applyRepair(taskId: number, acceptedOnly = true) {
  return api.post(`/api/text-repair/apply`, { taskId, acceptedOnly })
}

// 规则管理
export function getRepairRules() {
  return api.get<RepairRule[]>('/api/text-repair/rules')
}

export function createRepairRule(data: Partial<RepairRule>) {
  return api.post<RepairRule>('/api/text-repair/rules', data)
}

export function updateRepairRule(ruleId: number, data: Partial<RepairRule>) {
  return api.put<RepairRule>(`/api/text-repair/rules/${ruleId}`, data)
}

export function deleteRepairRule(ruleId: number) {
  return api.delete(`/api/text-repair/rules/${ruleId}`)
}

// 模板管理
export function getRepairTemplates() {
  return api.get<RepairTemplate[]>('/api/text-repair/templates')
}

export function createRepairTemplate(data: Partial<RepairTemplate>) {
  return api.post<RepairTemplate>('/api/text-repair/templates', data)
}

export function updateRepairTemplate(templateId: number, data: Partial<RepairTemplate>) {
  return api.put<RepairTemplate>(`/api/text-repair/templates/${templateId}`, data)
}

export function deleteRepairTemplate(templateId: number) {
  return api.delete(`/api/text-repair/templates/${templateId}`)
}
