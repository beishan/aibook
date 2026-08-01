<template>
  <div class="repair-view">
    <!-- 返回按钮 -->
    <div class="page-header">
      <button class="back-btn" @click="$router.back()">
        <span>‹</span>
        <span>返回</span>
      </button>
      <div v-if="bookInfo">
        <h1 class="page-title">内容修复 - {{ bookInfo.title }}</h1>
        <p class="page-subtitle">
          {{ bookInfo.author || '未知作者' }} · {{ bookInfo.format?.toUpperCase() }}
        </p>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>{{ loadingText }}</p>
    </div>

    <template v-else>
      <!-- 未创建任务：模式选择 -->
      <div v-if="!repairStore.currentTask" class="repair-mode-section glass">
        <div v-if="bookRepairRecords.length && !showScanSetup" class="resume-panel">
          <div class="resume-heading">
            <div>
              <span class="eyebrow">已找到检测结果</span>
              <h2>继续上次处理，还是重新检测？</h2>
              <p>处理状态已保存，可从待处理问题继续，不需要重复扫描。</p>
            </div>
            <div class="resume-actions">
              <button class="btn btn-primary" @click="handleOpenTask(bookRepairRecords[0].id)">继续最近记录</button>
              <button class="btn" @click="handleRescanTask(bookRepairRecords[0].id)">重新检测</button>
              <button class="btn btn-text" @click="showScanSetup = true">使用其他配置</button>
            </div>
          </div>
          <div class="task-history">
            <h3>本书检测记录</h3>
          <button
            v-for="item in bookRepairRecords"
            :key="item.id"
            class="history-task"
            @click="handleOpenTask(item.id)"
          >
              <span class="history-main">
                <b>{{ getStatusText(item.status) }} · {{ getModeText(item.repairMode) }}</b>
                <small>{{ new Date(item.createdAt).toLocaleString() }}</small>
              </span>
              <span class="history-progress">待处理 {{ item.pendingIssueCount }} / 共 {{ item.totalIssueCount }}</span>
          </button>
          </div>
        </div>
        <div v-if="showScanSetup || !bookRepairRecords.length" class="scan-setup">
        <h2>选择修复模式</h2>
        <div class="mode-cards">
          <div
            v-for="mode in repairModes"
            :key="mode.value"
            class="mode-card"
            :class="{ active: selectedMode === mode.value }"
            @click="selectedMode = mode.value"
          >
            <div class="mode-icon">{{ mode.icon }}</div>
            <h3>{{ mode.label }}</h3>
            <p>{{ mode.description }}</p>
            <ul class="mode-features">
              <li v-for="feature in mode.features" :key="feature">{{ feature }}</li>
            </ul>
          </div>
        </div>

        <div class="template-selector">
          <label for="repair-template">修复模板</label>
          <select id="repair-template" v-model="selectedTemplateId" @change="handleTemplateChange">
            <option :value="undefined">使用通用设置</option>
            <option v-for="item in repairStore.templates" :key="item.id" :value="item.id">
              {{ item.name }}{{ item.systemTemplate ? '（系统）' : '' }}
            </option>
          </select>
        </div>

        <!-- 编码检测 -->
        <div class="encoding-section">
          <button class="btn" @click="handleDetectEncoding">
            🔍 检测编码
          </button>
          <div v-if="repairStore.encodingResult" class="encoding-result">
            <div class="encoding-info">
              <span class="tag tag-info">编码: {{ repairStore.encodingResult.encoding }}</span>
              <span class="tag" :class="repairStore.encodingResult.confidence > 0.8 ? 'tag-success' : 'tag-warning'">
                置信度: {{ (repairStore.encodingResult.confidence * 100).toFixed(0) }}%
              </span>
              <span v-if="repairStore.encodingResult.anomalyCount > 0" class="tag tag-danger">
                异常字符: {{ repairStore.encodingResult.anomalyCount }}处
              </span>
              <span v-if="repairStore.encodingResult.hasBom" class="tag tag-info">
                BOM: {{ repairStore.encodingResult.bomType }}
              </span>
            </div>
            <div v-if="repairStore.encodingResult.hasGarbled" class="garbled-warning">
              ⚠️ 检测到乱码: {{ repairStore.encodingResult.garbledType }}
            </div>
            <!-- 手动切换编码 -->
            <div class="encoding-switch">
              <span>手动切换编码预览:</span>
              <select v-model="selectedEncoding" @change="handleSwitchEncoding">
                <option value="AUTO">自动检测</option>
                <option value="UTF-8">UTF-8</option>
                <option value="GBK">GBK</option>
                <option value="GB18030">GB18030</option>
                <option value="Big5">Big5</option>
                <option value="UTF-16LE">UTF-16 LE</option>
                <option value="UTF-16BE">UTF-16 BE</option>
              </select>
            </div>
            <div v-if="encodingPreview" class="encoding-preview-text">
              <pre>{{ encodingPreview }}</pre>
            </div>
          </div>
        </div>

        <div class="start-repair">
          <button class="btn btn-primary" @click="handleCreateTask">
            🚀 开始扫描
          </button>
          <button v-if="bookRepairRecords.length" class="btn" @click="showScanSetup = false">返回已有记录</button>
        </div>
        </div>
      </div>

      <!-- 任务已创建：三栏布局 -->
      <div v-else class="repair-workspace">
        <!-- 任务状态栏 -->
        <div class="task-status-bar glass">
          <div class="task-info">
            <span class="tag" :class="getStatusClass(repairStore.currentTask.status)">
              {{ getStatusText(repairStore.currentTask.status) }}
            </span>
            <span class="mode-label">模式: {{ getModeText(repairStore.currentTask.repairMode) }}</span>
          </div>
          <div class="task-counts">
            <span class="count-item">
              总计: {{ repairStore.currentTask.totalIssueCount }}
            </span>
            <span class="count-item pending">待处理: {{ repairStore.currentTask.pendingIssueCount }}</span>
            <span class="count-item accepted">已接受: {{ repairStore.currentTask.acceptedIssueCount }}</span>
            <span class="count-item rejected">已拒绝: {{ repairStore.currentTask.rejectedIssueCount }}</span>
            <span class="count-item applied">已应用: {{ repairStore.currentTask.appliedIssueCount }}</span>
          </div>
          <div class="task-actions">
            <button class="btn btn-sm" @click="handleRescanTask(repairStore.currentTask.id)">
              🔄 重新检测
            </button>
            <button class="btn btn-sm" @click="handleAcceptHighConfidence" :disabled="repairStore.currentTask.pendingIssueCount === 0">
              ✅ 接受高置信度
            </button>
            <button class="btn btn-sm" @click="handlePreview" :disabled="repairStore.currentTask.acceptedIssueCount === 0">
              👁️ 预览修复
            </button>
            <button class="btn btn-sm btn-primary" @click="handleApply" :disabled="repairStore.currentTask.acceptedIssueCount === 0">
              💾 执行修复
            </button>
            <button
              v-if="repairStore.currentTask.status === 'SCANNED'"
              class="btn btn-sm"
              @click="handleRevertAll"
            >
              ↩️ 撤销全部
            </button>
            <button
              v-if="repairStore.currentTask.status === 'COMPLETED'"
              class="btn btn-sm btn-danger"
              @click="handleRestoreOriginal"
            >
              🛟 恢复修复前版本
            </button>
          </div>
        </div>

        <div v-if="repairReport" class="repair-report glass">
          <h3>修复结果报告</h3>
          <div class="report-counts">
            <span>检测章节：{{ repairReport.detectedChapters }}</span>
            <span>删除广告：{{ repairReport.removedAds }}</span>
            <span>统一章节：{{ repairReport.normalizedChapters }}</span>
            <span>段落修复：{{ repairReport.fixedLineBreaks }}</span>
            <span>删除重复：{{ repairReport.removedDuplicates }}</span>
            <span>未确认：{{ repairReport.unconfirmedCount }}</span>
          </div>
          <ul v-if="repairReport.anomalies?.length">
            <li v-for="(item, index) in repairReport.anomalies" :key="index">
              {{ item.description }}
            </li>
          </ul>
        </div>

        <!-- 三栏布局 -->
        <div class="three-column">
          <!-- 左栏：问题分类与列表 -->
          <div class="left-panel glass">
            <div class="panel-header">
              <h3>问题列表</h3>
              <select v-model="filterType" @change="loadFilteredIssues" class="filter-select">
                <option value="">全部类型</option>
                <option v-for="(label, value) in issueTypes" :key="value" :value="value">
                  {{ label }}
                </option>
              </select>
              <select v-model="filterStatus" @change="loadFilteredIssues" class="filter-select">
                <option value="">全部状态</option>
                <option v-for="(label, value) in issueStatuses" :key="value" :value="value">
                  {{ label }}
                </option>
              </select>
            </div>
            <div class="issue-list">
              <div
                v-for="issue in repairStore.issues"
                :key="issue.id"
                class="issue-item"
                :class="{ active: selectedIssueId === issue.id, [issue.status.toLowerCase()]: true }"
                @click="selectIssue(issue)"
              >
                <div class="issue-header">
                  <span class="issue-type-tag" :class="issue.type.toLowerCase()">
                    {{ getIssueTypeText(issue.type) }}
                  </span>
                  <span class="issue-confidence">
                    {{ issue.confidence ? (issue.confidence * 100).toFixed(0) + '%' : '-' }}
                  </span>
                </div>
                <div class="issue-text">{{ truncate(issue.originalText, 60) }}</div>
                <div class="issue-footer">
                  <span v-if="issue.riskLevel === 'HIGH'" class="risk-tag high">高风险</span>
                  <span v-else-if="issue.riskLevel === 'MEDIUM'" class="risk-tag medium">中风险</span>
                  <span v-else class="risk-tag low">低风险</span>
                  <span class="issue-status">{{ getIssueStatusText(issue.status) }}</span>
                </div>
              </div>
              <div v-if="repairStore.issues.length === 0" class="empty-issues">
                <p>暂无问题</p>
              </div>
            </div>
          </div>

          <!-- 中栏：原始内容 -->
          <div class="middle-panel glass">
            <div class="panel-header">
              <h3>原始内容</h3>
            </div>
            <div class="content-view">
              <div v-if="selectedIssue && isBlankLineIssue(selectedIssue)" class="blank-line-preview">
                <div class="context-line">
                  <span class="context-label">上文</span>
                  <p>{{ blankLineContext(selectedIssue, 'contextBefore') }}</p>
                </div>
                <div class="blank-run-indicator" :style="blankRunStyle(selectedIssue)">
                  <span>连续 {{ blankLineCount(selectedIssue) }} 个空行</span>
                </div>
                <div class="context-line">
                  <span class="context-label">下文</span>
                  <p>{{ blankLineContext(selectedIssue, 'contextAfter') }}</p>
                </div>
              </div>
              <div v-else-if="selectedIssue && hasSamplePreview(selectedIssue)" class="sample-preview-card">
                <span class="sample-caption">检测样例</span>
                <pre>{{ issueMetadataText(selectedIssue, 'previewOriginal') }}</pre>
                <p v-if="issueMetadataText(selectedIssue, 'previewNote')" class="sample-note">
                  {{ issueMetadataText(selectedIssue, 'previewNote') }}
                </p>
              </div>
              <pre v-else-if="selectedIssue">{{ selectedIssue.originalText || '（无内容）' }}</pre>
              <div v-else class="content-placeholder">
                <p>选择左侧问题查看原始内容</p>
              </div>
            </div>
          </div>

          <!-- 右栏：修复后内容 -->
          <div class="right-panel glass">
            <div class="panel-header">
              <h3>修复后内容</h3>
              <div v-if="selectedIssue" class="issue-actions">
                <button
                  class="btn btn-sm btn-success"
                  :disabled="!isIssueActionable(selectedIssue)"
                  :title="isIssueActionable(selectedIssue) ? '接受这项修复' : '该问题仅用于检测提示'"
                  @click="handleAcceptIssue(selectedIssue)"
                >
                  ✅ 接受
                </button>
                <button class="btn btn-sm btn-danger" @click="handleRejectIssue(selectedIssue)">
                  ❌ 拒绝
                </button>
                <button class="btn btn-sm" @click="handleIgnoreIssue(selectedIssue)">
                  🚫 忽略
                </button>
                <button
                  v-if="canManualEdit(selectedIssue)"
                  class="btn btn-sm"
                  @click="handleManualEdit(selectedIssue)"
                >
                  ✏️ 手动编辑
                </button>
                <button
                  v-if="isIssueActionable(selectedIssue)"
                  class="btn btn-sm"
                  @click="handleApplyToAll(selectedIssue)"
                >
                  📚 应用同类
                </button>
                <button
                  v-if="selectedIssue.type === 'AD'"
                  class="btn btn-sm"
                  @click="handleAddAdRule(selectedIssue, false)"
                >
                  ➕ 加入广告规则
                </button>
                <button
                  v-if="selectedIssue.type === 'AD'"
                  class="btn btn-sm"
                  @click="handleAddAdRule(selectedIssue, true)"
                >
                  🛡️ 加入白名单
                </button>
              </div>
            </div>
            <div class="content-view">
              <div v-if="selectedIssue && isBlankLineIssue(selectedIssue)" class="blank-line-preview repaired">
                <div class="context-line">
                  <span class="context-label">上文</span>
                  <p>{{ blankLineContext(selectedIssue, 'contextBefore') }}</p>
                </div>
                <div class="blank-run-indicator fixed" :style="repairedBlankRunStyle(selectedIssue)">
                  <span>{{ repairedBlankLineText(selectedIssue) }}</span>
                </div>
                <div class="context-line">
                  <span class="context-label">下文</span>
                  <p>{{ blankLineContext(selectedIssue, 'contextAfter') }}</p>
                </div>
              </div>
              <div v-else-if="selectedIssue && !isIssueActionable(selectedIssue)" class="repair-state-card advisory">
                <span class="repair-state-icon">i</span>
                <div>
                  <strong>仅检测提示，不会自动修改</strong>
                  <p>该问题需要人工判断，可选择忽略、拒绝或手动编辑。</p>
                </div>
              </div>
              <div v-else-if="selectedIssue && hasSamplePreview(selectedIssue)" class="sample-preview-card repaired">
                <span class="sample-caption">处理后样例</span>
                <pre>{{ issueMetadataText(selectedIssue, 'previewSuggested') || '（该样例内容将被删除）' }}</pre>
                <p v-if="issueMetadataText(selectedIssue, 'previewNote')" class="sample-note">
                  {{ issueMetadataText(selectedIssue, 'previewNote') }}
                </p>
              </div>
              <div v-else-if="selectedIssue && isDeletionSuggestion(selectedIssue)" class="repair-state-card deletion">
                <span class="repair-state-icon">−</span>
                <div>
                  <strong>删除这段内容</strong>
                  <p>接受后将删除左侧展示的完整内容。</p>
                </div>
              </div>
              <pre v-else-if="selectedIssue">{{ selectedIssue.suggestedText }}</pre>
              <div v-else class="content-placeholder">
                <p>选择左侧问题查看修复建议</p>
              </div>
            </div>
            <div v-if="selectedIssue" class="issue-detail">
              <div class="detail-row">
                <span class="detail-label">原因:</span>
                <span class="detail-value">{{ selectedIssue.reason }}</span>
              </div>
              <div v-if="selectedIssue.candidates && selectedIssue.candidates.length > 0" class="detail-row">
                <span class="detail-label">候选结果:</span>
                <div class="candidates">
                  <button
                    v-for="(candidate, idx) in selectedIssue.candidates"
                    :key="idx"
                    class="candidate-btn"
                    @click="handleSelectCandidate(selectedIssue, candidate)"
                  >
                    {{ idx + 1 }}. {{ candidate }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 修复预览弹窗 -->
        <div v-if="repairStore.preview" class="preview-modal" @click.self="repairStore.preview = null">
          <div class="preview-content glass">
            <div class="preview-header">
              <h3>修复预览</h3>
              <button class="btn" @click="repairStore.preview = null">✕</button>
            </div>
            <div class="preview-diff">
              <div
                v-for="(line, idx) in repairStore.preview.diffLines"
                :key="idx"
                class="diff-line"
                :class="line.type.toLowerCase()"
              >
                <span class="diff-marker">
                  {{ line.type === 'ADDED' ? '+' : line.type === 'REMOVED' ? '-' : line.type === 'MODIFIED' ? '~' : ' ' }}
                </span>
                <span class="diff-original">{{ line.originalLine }}</span>
                <span v-if="line.type === 'MODIFIED' || line.type === 'ADDED'" class="diff-arrow">→</span>
                <span v-if="line.type === 'MODIFIED' || line.type === 'ADDED'" class="diff-repaired">{{ line.repairedLine }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { message, confirm } from '@/utils/message'
import { useRepairStore } from '@/stores/repair'
import { useBookStore } from '@/stores/book'
import type { RepairIssue, RepairReport } from '@/utils/repair'

const route = useRoute()
const repairStore = useRepairStore()
const bookStore = useBookStore()

const bookId = Number(route.params.id)
const loading = ref(false)
const loadingText = ref('')
const bookInfo = ref<any>(null)
const selectedMode = ref('STANDARD')
const selectedTemplateId = ref<number | undefined>(undefined)
const selectedEncoding = ref('AUTO')
const encodingPreview = ref('')
const selectedIssueId = ref<number | null>(null)
const filterType = ref('')
const filterStatus = ref('')
const showScanSetup = ref(false)

const selectedIssue = computed(() =>
  repairStore.issues.find((i) => i.id === selectedIssueId.value) || null,
)

const bookRepairRecords = computed(() =>
  repairStore.tasks.filter((task) => task.status === 'SCANNED' || task.status === 'COMPLETED'),
)

const repairReport = computed<RepairReport | null>(() => {
  const json = repairStore.currentTask?.reportJson
  if (!json) return null
  try {
    return JSON.parse(json) as RepairReport
  } catch {
    return null
  }
})

const repairModes = [
  {
    value: 'SAFE',
    label: '安全修复',
    icon: '🛡️',
    description: '仅处理低风险问题',
    features: ['编码检测', '明确乱码检测', '换行符统一', '不可见字符清理', '明确网址广告', '多余空行清理', '高置信度章节识别'],
  },
  {
    value: 'STANDARD',
    label: '标准修复',
    icon: '🔧',
    description: '安全修复 + 常见广告清理、章节统一等',
    features: ['安全修复全部功能', '常见广告清理', '章节标题统一', '章节编号检查', '重复章节检查', '高置信度错误换行修复', '段首缩进统一'],
  },
  {
    value: 'DEEP',
    label: '深度修复',
    icon: '🔬',
    description: '标准修复 + 模糊广告识别、章节粘连检测等',
    features: ['标准修复全部功能', '模糊广告识别', '章节粘连检测', '近似重复段落检测', '全书段落重新分析'],
  },
]

const issueTypes: Record<string, string> = {
  ENCODING: '乱码',
  AD: '广告',
  CHAPTER: '章节',
  CHAPTER_ANOMALY: '章节异常',
  PARAGRAPH: '段落',
  PUNCTUATION: '标点',
  DUPLICATE: '重复内容',
  INVISIBLE_CHAR: '不可见字符',
  CHAPTER_ADHESION: '章节粘连',
  TRADITIONAL_SIMPLIFIED: '繁简转换',
  AI_SUGGESTION: 'AI 建议',
}

const issueStatuses: Record<string, string> = {
  PENDING: '待处理',
  ACCEPTED: '已接受',
  REJECTED: '已拒绝',
  IGNORED: '已忽略',
  APPLIED: '已应用',
  REVERTED: '已撤销',
}

function isBlankLineIssue(issue: RepairIssue): boolean {
  return issue.type === 'PARAGRAPH'
    && Boolean(issue.reason?.includes('多余空行'))
    && typeof issue.metadata?.blankLineCount === 'number'
}

function issueMetadataText(issue: RepairIssue, key: string): string {
  const value = issue.metadata?.[key]
  return typeof value === 'string' ? value : ''
}

function hasSamplePreview(issue: RepairIssue): boolean {
  return issueMetadataText(issue, 'previewOriginal').length > 0
}

function isIssueActionable(issue: RepairIssue): boolean {
  if (issue.suggestedText == null) return false
  if (issue.startOffset != null && issue.endOffset != null) return true
  if (issue.type === 'PUNCTUATION' || issue.type === 'INVISIBLE_CHAR') return true
  if (issue.type === 'PARAGRAPH') {
    return Boolean(issue.reason?.includes('换行符') || issue.reason?.includes('段首缩进'))
  }
  if (issue.type === 'ENCODING') {
    return issueMetadataText(issue, 'garbledPattern').length > 0
  }
  return false
}

function isDeletionSuggestion(issue: RepairIssue): boolean {
  return issue.suggestedText === ''
    || ((issue.type === 'AD' || issue.type === 'DUPLICATE')
      && issue.suggestedText === '[已删除]')
}

function canManualEdit(issue: RepairIssue): boolean {
  const hasConcreteRange = issue.startOffset != null && issue.endOffset != null
  const isPatternReplacement = issue.type === 'ENCODING'
    && issueMetadataText(issue, 'garbledPattern').length > 0
  return isIssueActionable(issue) && (hasConcreteRange || isPatternReplacement)
}

function blankLineContext(issue: RepairIssue, key: 'contextBefore' | 'contextAfter'): string {
  const value = issue.metadata?.[key]
  return typeof value === 'string' && value.length > 0 ? value : '（文件边界）'
}

function blankLineCount(issue: RepairIssue): number {
  const count = issue.metadata?.blankLineCount
  return typeof count === 'number' ? count : 0
}

function repairedBlankLineCount(issue: RepairIssue): number {
  return issue.suggestedText == null
    ? 0
    : [...issue.suggestedText].filter((char) => char === '\n').length
}

function repairedBlankLineText(issue: RepairIssue): string {
  const count = repairedBlankLineCount(issue)
  return count === 0 ? '移除全部空行' : `保留 ${count} 个空行`
}

function blankRunStyle(issue: RepairIssue): Record<string, string> {
  const height = Math.min(128, 38 + blankLineCount(issue) * 12)
  return { minHeight: `${height}px` }
}

function repairedBlankRunStyle(issue: RepairIssue): Record<string, string> {
  const height = Math.min(80, 32 + repairedBlankLineCount(issue) * 10)
  return { minHeight: `${height}px` }
}

onMounted(async () => {
  repairStore.reset()
  const savedSettings = localStorage.getItem('textRepairSettings')
  if (savedSettings) {
    try {
      const settings = JSON.parse(savedSettings)
      if (['SAFE', 'STANDARD', 'DEEP'].includes(settings.defaultMode)) {
        selectedMode.value = settings.defaultMode
      }
    } catch {
      // 无效的本地设置不影响页面打开。
    }
  }
  await Promise.all([
    loadBookInfo(),
    repairStore.loadTemplates(),
    repairStore.loadBookTasks(bookId),
  ])
  const requestedTaskId = Number(route.query.taskId)
  if (Number.isFinite(requestedTaskId) && requestedTaskId > 0) {
    try {
      await handleOpenTask(requestedTaskId)
    } catch {
      message.error('检测记录不存在或已被删除')
    }
  } else {
    showScanSetup.value = bookRepairRecords.value.length === 0
  }
})

async function handleOpenTask(taskId: number) {
  await repairStore.loadTask(taskId)
  await repairStore.loadIssues(taskId)
}

async function handleRescanTask(taskId: number) {
  try {
    const confirmed = await confirm('重新检测会创建一条新记录，原检测结果仍会保留。确认继续？')
    if (!confirmed) return
    loading.value = true
    loadingText.value = '正在重新检测内容...'
    const task = await repairStore.rescanTask(taskId)
    await repairStore.loadBookTasks(bookId)
    message.success(`重新检测完成，发现 ${task.totalIssueCount} 个问题`)
  } catch {
    message.error('重新检测失败')
  } finally {
    loading.value = false
  }
}

function handleTemplateChange() {
  const template = repairStore.templates.find((item) => item.id === selectedTemplateId.value)
  if (template) selectedMode.value = template.repairMode
}

async function loadBookInfo() {
  try {
    loading.value = true
    loadingText.value = '加载书籍信息...'
    await bookStore.fetchBookById(bookId)
    bookInfo.value = bookStore.currentBook
  } catch {
    message.error('加载书籍信息失败')
  } finally {
    loading.value = false
  }
}

async function handleDetectEncoding() {
  try {
    loading.value = true
    loadingText.value = '检测编码中...'
    await repairStore.loadEncoding(bookId)
  } catch {
    message.error('编码检测失败')
  } finally {
    loading.value = false
  }
}

async function handleSwitchEncoding() {
  if (selectedEncoding.value === 'AUTO') return
  try {
    encodingPreview.value = await repairStore.switchEncodingPreview(bookId, selectedEncoding.value)
  } catch {
    message.error('编码切换预览失败')
  }
}

async function handleCreateTask() {
  try {
    loading.value = true
    loadingText.value = '扫描内容中...'
    const optionsJson = selectedTemplateId.value
      ? undefined
      : localStorage.getItem('textRepairSettings') || undefined
    await repairStore.createTask(
      bookId,
      selectedMode.value,
      undefined,
      selectedTemplateId.value,
      optionsJson,
    )
    message.success('扫描完成，发现 ' + repairStore.currentTask?.totalIssueCount + ' 个问题')
  } catch (error: any) {
    message.error(error.response?.data?.message || '创建修复任务失败')
  } finally {
    loading.value = false
  }
}

async function loadFilteredIssues() {
  if (!repairStore.currentTask) return
  await repairStore.loadIssues(repairStore.currentTask.id, {
    type: filterType.value || undefined,
    status: filterStatus.value || undefined,
  })
}

function selectIssue(issue: RepairIssue) {
  selectedIssueId.value = issue.id
}

async function handleAcceptIssue(issue: RepairIssue) {
  if (!isIssueActionable(issue)) return
  try {
    await repairStore.updateIssue(issue.id, { status: 'ACCEPTED' })
    message.success('已接受修复')
  } catch {
    message.error('操作失败')
  }
}

async function handleRejectIssue(issue: RepairIssue) {
  try {
    await repairStore.updateIssue(issue.id, { status: 'REJECTED' })
    message.success('已拒绝修复')
  } catch {
    message.error('操作失败')
  }
}

async function handleIgnoreIssue(issue: RepairIssue) {
  try {
    await repairStore.updateIssue(issue.id, { status: 'IGNORED' })
    message.success('已忽略')
  } catch {
    message.error('操作失败')
  }
}

async function handleManualEdit(issue: RepairIssue) {
  if (!canManualEdit(issue)) return
  const value = window.prompt(
    '请输入修复后的文本',
    issue.suggestedText ?? issue.originalText ?? '',
  )
  if (value === null) return
  await repairStore.updateIssue(issue.id, {
    status: 'ACCEPTED',
    manualText: value,
  })
  message.success('已保存手动修改')
}

async function handleApplyToAll(issue: RepairIssue) {
  if (!isIssueActionable(issue)) return
  await repairStore.updateIssue(issue.id, {
    status: 'ACCEPTED',
    applyToAll: true,
  })
  await repairStore.loadTask(issue.taskId)
  message.success('已应用到全部同类问题')
}

async function handleAddAdRule(issue: RepairIssue, whitelist: boolean) {
  const content = issue.originalText?.trim()
  if (!content) return
  const pattern = content.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  await repairStore.addRule({
    name: whitelist ? `白名单：${content.slice(0, 20)}` : `广告：${content.slice(0, 20)}`,
    type: 'AD',
    pattern,
    matchScope: 'LINE',
    action: whitelist ? 'MARK_ONLY' : 'DELETE_LINE',
    riskLevel: whitelist ? 'LOW' : 'MEDIUM',
    enabled: true,
    whitelist,
    scope: 'CURRENT_BOOK',
    bookId,
  })
  message.success(whitelist ? '已加入白名单' : '已加入广告规则')
}

function handleSelectCandidate(issue: RepairIssue, candidate: string) {
  repairStore.updateIssue(issue.id, { status: 'ACCEPTED', manualText: candidate })
}

async function handleAcceptHighConfidence() {
  if (!repairStore.currentTask) return
  try {
    const count = await repairStore.acceptHighConfidenceIssues(repairStore.currentTask.id)
    message.success(`已接受 ${count} 个高置信度问题`)
  } catch {
    message.error('操作失败')
  }
}

async function handlePreview() {
  if (!repairStore.currentTask) return
  try {
    loading.value = true
    loadingText.value = '生成预览中...'
    await repairStore.loadPreview(repairStore.currentTask.id)
  } catch {
    message.error('生成预览失败')
  } finally {
    loading.value = false
  }
}

async function handleApply() {
  if (!repairStore.currentTask) return
  try {
    await confirm('确认执行修复？将保存为新的书籍版本，原始内容不会被修改。')
    loading.value = true
    loadingText.value = '执行修复中...'
    await repairStore.executeRepair(repairStore.currentTask.id)
    message.success('修复完成，已保存为新版本')
    await repairStore.loadTask(repairStore.currentTask.id)
  } catch (error: any) {
    if (error !== 'cancel') {
      message.error(error.response?.data?.message || '执行修复失败')
    }
  } finally {
    loading.value = false
  }
}

async function handleRevertAll() {
  if (!repairStore.currentTask) return
  try {
    await confirm('确认撤销全部修改？')
    await repairStore.revertAll(repairStore.currentTask.id)
    message.success('已撤销全部修改')
  } catch (error: any) {
    if (error !== 'cancel') {
      message.error('撤销失败')
    }
  }
}

async function handleRestoreOriginal() {
  if (!repairStore.currentTask) return
  try {
    await confirm('确认删除本次生成的修复版本并恢复到修复前状态？')
    await repairStore.restoreOriginal(repairStore.currentTask.id)
    message.success('已恢复到修复前版本')
  } catch (error: any) {
    if (error !== 'cancel') {
      message.error(error.response?.data?.message || '恢复失败')
    }
  }
}

function getStatusClass(status: string) {
  switch (status) {
    case 'SCANNING': return 'tag-warning'
    case 'SCANNED': return 'tag-info'
    case 'REPAIRING': return 'tag-primary'
    case 'COMPLETED': return 'tag-success'
    case 'REVERTED': return 'tag-info'
    case 'FAILED': return 'tag-danger'
    default: return 'tag-info'
  }
}

function getStatusText(status: string) {
  switch (status) {
    case 'SCANNING': return '扫描中'
    case 'SCANNED': return '已扫描'
    case 'REPAIRING': return '修复中'
    case 'COMPLETED': return '已完成'
    case 'REVERTED': return '已恢复原始版本'
    case 'FAILED': return '失败'
    default: return status
  }
}

function getModeText(mode: string) {
  return repairModes.find((m) => m.value === mode)?.label || mode
}

function getIssueTypeText(type: string) {
  return issueTypes[type] || type
}

function getIssueStatusText(status: string) {
  return issueStatuses[status] || status
}

function truncate(text: string | undefined, max: number) {
  if (!text) return ''
  return text.length > max ? text.substring(0, max) + '...' : text
}
</script>

<style scoped>
.repair-view {
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: var(--glass-bg);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-primary);
}

.back-btn:hover {
  background: var(--glass-hover);
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 4px 0 0 0;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
  color: var(--text-secondary);
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--border-color);
  border-top-color: var(--accent-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 12px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 模式选择 */
.repair-mode-section {
  padding: 24px;
  border-radius: 12px;
}

.resume-panel { display: grid; gap: 22px; }
.resume-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; padding: 20px; border-radius: 10px; background: var(--accent-bg); }
.resume-heading h2 { margin: 5px 0 6px; }
.resume-heading p { margin: 0; color: var(--text-secondary); }
.eyebrow { color: var(--accent-color); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.resume-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.btn-text { border-color: transparent; background: transparent; }
.task-history h3 { margin: 0 0 10px; }
.history-task { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 12px 14px; margin-top: 8px; border: 1px solid var(--border-color); border-radius: 8px; background: transparent; color: var(--text-primary); text-align: left; cursor: pointer; }
.history-task:hover { border-color: var(--accent-color); background: var(--accent-bg); }
.history-main { display: grid; gap: 3px; }
.history-main small,
.history-progress { color: var(--text-secondary); font-size: 12px; }
.scan-setup { margin-top: 4px; }

.mode-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
  margin: 20px 0;
}

.mode-card {
  padding: 20px;
  border: 2px solid var(--border-color);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-card:hover {
  border-color: var(--accent-color);
}

.mode-card.active {
  border-color: var(--accent-color);
  background: var(--accent-bg);
}

.mode-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.mode-card h3 {
  margin: 0 0 8px;
  color: var(--text-primary);
}

.mode-card p {
  color: var(--text-secondary);
  font-size: 13px;
  margin: 0 0 12px;
}

.mode-features {
  list-style: none;
  padding: 0;
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.mode-features li {
  padding: 2px 0;
}

.mode-features li::before {
  content: '✓ ';
  color: var(--success-color);
}

/* 编码检测 */
.encoding-section {
  margin: 20px 0;
  padding: 16px;
  border-top: 1px solid var(--border-color);
}

.encoding-result {
  margin-top: 12px;
}

.encoding-info {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.garbled-warning {
  padding: 8px 12px;
  background: var(--warning-bg);
  border-radius: 6px;
  color: var(--warning-color);
  font-size: 13px;
  margin-bottom: 8px;
}

.encoding-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
}

.encoding-switch select {
  padding: 4px 8px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--input-bg);
  color: var(--text-primary);
}

.encoding-preview-text pre {
  max-height: 200px;
  overflow-y: auto;
  padding: 12px;
  background: var(--code-bg);
  border-radius: 6px;
  font-size: 13px;
  white-space: pre-wrap;
  color: var(--text-primary);
}

.start-repair {
  text-align: center;
  margin-top: 20px;
}

/* 任务工作区 */
.repair-workspace {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 10px;
  flex-wrap: wrap;
  gap: 8px;
}

.task-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.task-counts {
  display: flex;
  gap: 12px;
  font-size: 13px;
}

.count-item {
  color: var(--text-secondary);
}

.count-item.pending { color: var(--warning-color); }
.count-item.accepted { color: var(--success-color); }
.count-item.rejected { color: var(--danger-color); }
.count-item.applied { color: var(--accent-color); }

.task-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}

/* 三栏布局 */
.three-column {
  display: grid;
  grid-template-columns: 320px 1fr 1fr;
  gap: 12px;
  min-height: 500px;
}

.left-panel,
.middle-panel,
.right-panel {
  display: flex;
  flex-direction: column;
  border-radius: 10px;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
  flex-wrap: wrap;
}

.panel-header h3 {
  margin: 0;
  font-size: 14px;
  color: var(--text-primary);
}

.filter-select {
  padding: 2px 6px;
  font-size: 12px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--input-bg);
  color: var(--text-primary);
}

.issue-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.issue-item {
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  border: 1px solid transparent;
  transition: all 0.15s;
}

.issue-item:hover {
  background: var(--glass-hover);
}

.issue-item.active {
  border-color: var(--accent-color);
  background: var(--accent-bg);
}

.issue-item.accepted {
  border-left: 3px solid var(--success-color);
}

.issue-item.rejected {
  border-left: 3px solid var(--danger-color);
  opacity: 0.6;
}

.issue-item.ignored {
  opacity: 0.4;
}

.issue-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.issue-type-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--tag-bg);
  color: var(--text-secondary);
}

.issue-confidence {
  font-size: 11px;
  color: var(--text-secondary);
}

.issue-text {
  font-size: 13px;
  color: var(--text-primary);
  margin-bottom: 4px;
  line-height: 1.4;
}

.issue-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.risk-tag {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
}

.risk-tag.high { background: var(--danger-bg); color: var(--danger-color); }
.risk-tag.medium { background: var(--warning-bg); color: var(--warning-color); }
.risk-tag.low { background: var(--info-bg); color: var(--text-secondary); }

.issue-status {
  font-size: 11px;
  color: var(--text-secondary);
}

.empty-issues {
  text-align: center;
  padding: 40px;
  color: var(--text-secondary);
  font-size: 14px;
}

.content-view {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.content-view pre {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  color: var(--text-primary);
  margin: 0;
}

.blank-line-preview {
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--glass-bg);
}

.context-line {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  padding: 12px;
  background: var(--surface-card);
}

.context-line p {
  margin: 0;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.context-label {
  padding-top: 2px;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
}

.blank-run-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 12px;
  border-top: 1px dashed var(--danger);
  border-bottom: 1px dashed var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}

.blank-run-indicator span {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--surface-card);
  color: var(--danger);
  font-size: 12px;
  font-weight: 600;
}

.blank-run-indicator.fixed {
  border-color: var(--success);
  background: var(--success-alpha-15);
}

.blank-run-indicator.fixed span {
  color: var(--success);
}

.sample-preview-card {
  position: relative;
  overflow: hidden;
  padding: 38px 14px 14px;
  border: 1px solid color-mix(in srgb, var(--warning) 35%, var(--border-color));
  border-radius: 8px;
  background: color-mix(in srgb, var(--warning) 7%, var(--surface-card));
}

.sample-preview-card.repaired {
  border-color: color-mix(in srgb, var(--success) 35%, var(--border-color));
  background: color-mix(in srgb, var(--success) 7%, var(--surface-card));
}

.sample-caption {
  position: absolute;
  top: 0;
  left: 0;
  padding: 6px 10px;
  border-bottom-right-radius: 7px;
  background: color-mix(in srgb, var(--warning) 16%, var(--surface-card));
  color: var(--warning);
  font-size: 11px;
  font-weight: 700;
}

.sample-preview-card.repaired .sample-caption {
  background: color-mix(in srgb, var(--success) 16%, var(--surface-card));
  color: var(--success);
}

.sample-preview-card pre {
  overflow-wrap: anywhere;
}

.sample-note {
  margin: 10px 0 0;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.5;
}

.repair-state-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-card);
}

.repair-state-card.deletion {
  border-color: color-mix(in srgb, var(--danger) 35%, var(--border-color));
  background: color-mix(in srgb, var(--danger) 7%, var(--surface-card));
}

.repair-state-card.advisory {
  border-color: color-mix(in srgb, var(--warning) 35%, var(--border-color));
  background: color-mix(in srgb, var(--warning) 7%, var(--surface-card));
}

.repair-state-icon {
  display: inline-flex;
  flex: 0 0 26px;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--surface-card);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 800;
}

.repair-state-card strong {
  color: var(--text-primary);
  font-size: 13px;
}

.repair-state-card p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.content-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-secondary);
  font-size: 14px;
}

.issue-actions {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.btn-success {
  background: var(--success-color);
  color: white;
  border-color: var(--success-color);
}

.btn-danger {
  background: var(--danger-color);
  color: white;
  border-color: var(--danger-color);
}

.issue-detail {
  padding: 10px 12px;
  border-top: 1px solid var(--border-color);
  font-size: 12px;
}

.detail-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}

.detail-label {
  color: var(--text-secondary);
  min-width: 60px;
}

.detail-value {
  color: var(--text-primary);
  flex: 1;
}

.candidates {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.candidate-btn {
  text-align: left;
  padding: 4px 8px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--glass-bg);
  cursor: pointer;
  font-size: 12px;
  color: var(--text-primary);
}

.candidate-btn:hover {
  background: var(--glass-hover);
}

/* 预览弹窗 */
.preview-modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.preview-content {
  width: 90%;
  max-width: 900px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
}

.preview-diff {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.diff-line {
  display: flex;
  gap: 8px;
  padding: 2px 0;
  font-size: 13px;
  font-family: monospace;
}

.diff-line.added { background: var(--success-bg); }
.diff-line.removed { background: var(--danger-bg); }
.diff-line.modified { background: var(--warning-bg); }

.diff-marker {
  min-width: 20px;
  color: var(--text-secondary);
}

.diff-original {
  color: var(--danger-color);
  text-decoration: line-through;
}

.diff-arrow {
  color: var(--text-secondary);
}

.diff-repaired {
  color: var(--success-color);
}

/* 响应式 */
@media (max-width: 1024px) {
  .three-column {
    grid-template-columns: 1fr;
  }
}
</style>
