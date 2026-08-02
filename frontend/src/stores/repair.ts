import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  RepairTask,
  RepairIssue,
  EncodingDetectResult,
  RepairPreview,
  RepairRule,
  RepairTemplate,
} from '@/utils/repair'
import {
  createRepairTask,
  getRepairTask,
  getRepairRecords,
  rescanRepairTask,
  deleteRepairTask,
  getBookRepairTasks,
  getRepairIssues,
  updateRepairIssue,
  acceptHighConfidence,
  revertAllRepairs,
  restoreOriginalRepairVersion,
  detectEncoding,
  previewEncoding,
  previewRepair,
  applyRepair,
  getRepairRules,
  createRepairRule,
  updateRepairRule,
  deleteRepairRule,
  getRepairTemplates,
  createRepairTemplate,
  updateRepairTemplate,
  deleteRepairTemplate,
} from '@/utils/repair'

export const useRepairStore = defineStore('repair', () => {
  const currentTask = ref<RepairTask | null>(null)
  const tasks = ref<RepairTask[]>([])
  const records = ref<RepairTask[]>([])
  const recordsTotal = ref(0)
  const issues = ref<RepairIssue[]>([])
  const issuesTotal = ref(0)
  const loading = ref(false)
  const encodingResult = ref<EncodingDetectResult | null>(null)
  const preview = ref<RepairPreview | null>(null)
  const rules = ref<RepairRule[]>([])
  const templates = ref<RepairTemplate[]>([])

  const pendingCount = computed(() => issues.value.filter((i) => i.status === 'PENDING').length)
  const acceptedCount = computed(() => issues.value.filter((i) => i.status === 'ACCEPTED').length)
  const rejectedCount = computed(() => issues.value.filter((i) => i.status === 'REJECTED').length)

  // 任务管理
  async function createTask(
    bookId: number,
    repairMode: string,
    versionId?: number,
    templateId?: number,
    optionsJson?: string,
  ) {
    loading.value = true
    try {
      const response = await createRepairTask(
        bookId, repairMode, versionId, templateId, optionsJson,
      )
      currentTask.value = response.data
      await loadIssues(response.data.id)
      return response.data
    } finally {
      loading.value = false
    }
  }

  async function loadTask(taskId: number) {
    loading.value = true
    try {
      const response = await getRepairTask(taskId)
      currentTask.value = response.data
      return response.data
    } finally {
      loading.value = false
    }
  }

  async function loadBookTasks(bookId: number) {
    const response = await getBookRepairTasks(bookId)
    tasks.value = response.data
    return response.data
  }

  async function loadRecords(page = 0, size = 10) {
    loading.value = true
    try {
      const response = await getRepairRecords(page, size)
      records.value = response.data.content
      recordsTotal.value = response.data.totalElements
      return response.data
    } finally {
      loading.value = false
    }
  }

  async function rescanTask(taskId: number) {
    loading.value = true
    try {
      const response = await rescanRepairTask(taskId)
      currentTask.value = response.data
      await loadIssues(response.data.id)
      return response.data
    } finally {
      loading.value = false
    }
  }

  async function removeTask(taskId: number) {
    await deleteRepairTask(taskId)
    records.value = records.value.filter((task) => task.id !== taskId)
    recordsTotal.value = Math.max(0, recordsTotal.value - 1)
    tasks.value = tasks.value.filter((task) => task.id !== taskId)
    if (currentTask.value?.id === taskId) currentTask.value = null
  }

  // 问题管理
  async function loadIssues(
    taskId: number,
    filters: { type?: string; status?: string } = {},
  ) {
    const response = await getRepairIssues(taskId, filters)
    issues.value = response.data.content
    issuesTotal.value = response.data.totalElements
    return response.data
  }

  async function updateIssue(
    issueId: number,
    data: { status: string; manualText?: string; applyToAll?: boolean },
  ) {
    const response = await updateRepairIssue(issueId, data)
    const index = issues.value.findIndex((i) => i.id === issueId)
    if (index !== -1) {
      issues.value[index] = response.data
    }
    // 刷新任务计数
    if (currentTask.value) {
      await loadTask(currentTask.value.id)
    }
    return response.data
  }

  async function acceptHighConfidenceIssues(taskId: number, threshold = 0.8) {
    const response = await acceptHighConfidence(taskId, threshold)
    await loadIssues(taskId)
    await loadTask(taskId)
    return response.data.acceptedCount
  }

  async function revertAll(taskId: number) {
    await revertAllRepairs(taskId)
    await loadIssues(taskId)
    await loadTask(taskId)
  }

  async function restoreOriginal(taskId: number) {
    await restoreOriginalRepairVersion(taskId)
    await loadTask(taskId)
    await loadIssues(taskId)
  }

  // 编码检测
  async function loadEncoding(bookId: number, versionId?: number) {
    const response = await detectEncoding(bookId, versionId)
    encodingResult.value = response.data
    return response.data
  }

  async function switchEncodingPreview(bookId: number, encoding: string, versionId?: number) {
    const response = await previewEncoding(bookId, encoding, versionId)
    return response.data.preview
  }

  // 修复预览与应用
  async function loadPreview(taskId: number) {
    const response = await previewRepair(taskId)
    preview.value = response.data
    return response.data
  }

  async function executeRepair(taskId: number, acceptedOnly = true) {
    const response = await applyRepair(taskId, acceptedOnly)
    return response.data
  }

  // 规则管理
  async function loadRules() {
    const response = await getRepairRules()
    rules.value = response.data
    return response.data
  }

  async function addRule(data: Partial<RepairRule>) {
    const response = await createRepairRule(data)
    rules.value.push(response.data)
    return response.data
  }

  async function editRule(ruleId: number, data: Partial<RepairRule>) {
    const response = await updateRepairRule(ruleId, data)
    const index = rules.value.findIndex((r) => r.id === ruleId)
    if (index !== -1) {
      rules.value[index] = response.data
    }
    return response.data
  }

  async function removeRule(ruleId: number) {
    await deleteRepairRule(ruleId)
    rules.value = rules.value.filter((r) => r.id !== ruleId)
  }

  // 模板管理
  async function loadTemplates() {
    const response = await getRepairTemplates()
    templates.value = response.data
    return response.data
  }

  async function addTemplate(data: Partial<RepairTemplate>) {
    const response = await createRepairTemplate(data)
    templates.value.push(response.data)
    return response.data
  }

  async function editTemplate(templateId: number, data: Partial<RepairTemplate>) {
    const response = await updateRepairTemplate(templateId, data)
    const index = templates.value.findIndex((t) => t.id === templateId)
    if (index !== -1) {
      templates.value[index] = response.data
    }
    return response.data
  }

  async function removeTemplate(templateId: number) {
    await deleteRepairTemplate(templateId)
    templates.value = templates.value.filter((t) => t.id !== templateId)
  }

  function reset() {
    currentTask.value = null
    tasks.value = []
    issues.value = []
    issuesTotal.value = 0
    encodingResult.value = null
    preview.value = null
  }

  return {
    currentTask,
    tasks,
    records,
    recordsTotal,
    issues,
    issuesTotal,
    loading,
    encodingResult,
    preview,
    rules,
    templates,
    pendingCount,
    acceptedCount,
    rejectedCount,
    createTask,
    loadTask,
    loadBookTasks,
    loadRecords,
    rescanTask,
    removeTask,
    loadIssues,
    updateIssue,
    acceptHighConfidenceIssues,
    revertAll,
    restoreOriginal,
    loadEncoding,
    switchEncodingPreview,
    loadPreview,
    executeRepair,
    loadRules,
    addRule,
    editRule,
    removeRule,
    loadTemplates,
    addTemplate,
    editTemplate,
    removeTemplate,
    reset,
  }
})
