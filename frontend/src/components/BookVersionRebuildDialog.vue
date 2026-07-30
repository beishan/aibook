<template>
  <el-dialog
    :model-value="visible"
    title="重建书籍多版本"
    width="640px"
    :close-on-click-modal="false"
    :close-on-press-escape="phase !== 'running'"
    @close="handleClose"
  >
    <div v-if="phase === 'idle'" class="rebuild-intro">
      <div class="intro-icon">🧩</div>
      <h3>自动聚合同一本书的不同文件版本</h3>
      <p>
        系统会检查 ISBN、书名、作者和原始文件名，并进行保守的相似度匹配。
      </p>
      <ul>
        <li>被聚合的重复书籍将不再单独显示</li>
        <li>NAS 上的原始文件不会被移动或删除</li>
        <li>各文件版本的阅读进度会分别保留</li>
      </ul>
    </div>

    <div v-else-if="phase === 'starting'" class="rebuild-center">
      <div class="dialog-spinner"></div>
      <p>正在创建重建任务...</p>
    </div>

    <div v-else class="rebuild-progress">
      <div class="status-heading">
        <div>
          <span class="status-label">{{ task?.message || '正在准备' }}</span>
          <p v-if="task?.currentBookTitle">当前：《{{ task.currentBookTitle }}》</p>
        </div>
        <strong>{{ progressPercentage }}%</strong>
      </div>

      <el-progress
        :percentage="progressPercentage"
        :stroke-width="16"
        :status="progressStatus"
      />

      <div class="progress-summary">
        <div class="summary-card">
          <span>书籍总数</span>
          <strong>{{ task?.totalBooks || 0 }}</strong>
        </div>
        <div class="summary-card">
          <span>已处理</span>
          <strong>{{ task?.processedBooks || 0 }}</strong>
        </div>
        <div class="summary-card success">
          <span>成功聚合</span>
          <strong>{{ task?.mergedBooks || 0 }}</strong>
        </div>
        <div class="summary-card">
          <span>形成版本</span>
          <strong>{{ task?.aggregatedVersions || 0 }}</strong>
        </div>
        <div class="summary-card">
          <span>匹配分组</span>
          <strong>{{ task?.completedGroups || 0 }} / {{ task?.matchedGroups || 0 }}</strong>
        </div>
        <div class="summary-card" :class="{ danger: (task?.failedBooks || 0) > 0 }">
          <span>失败</span>
          <strong>{{ task?.failedBooks || 0 }}</strong>
        </div>
      </div>

      <div class="elapsed-row">
        <span>⏱️ 已耗时</span>
        <strong>{{ formattedElapsed }}</strong>
      </div>

      <div v-if="phase === 'done'" class="done-banner" :class="{ warning: hasErrors }">
        <strong>{{ doneTitle }}</strong>
        <span>
          共检查 {{ task?.totalBooks || 0 }} 本书，聚合 {{ task?.mergedBooks || 0 }} 本，
          生成 {{ task?.aggregatedVersions || 0 }} 个版本，跳过 {{ task?.skippedBooks || 0 }} 本。
        </span>
      </div>

      <div v-if="task?.errors?.length" class="error-list">
        <strong>失败详情</strong>
        <div v-for="(error, index) in task.errors" :key="index">{{ error }}</div>
      </div>
    </div>

    <template #footer>
      <el-button v-if="phase === 'idle'" @click="handleClose">取消</el-button>
      <el-button v-if="phase === 'idle'" type="primary" @click="startRebuild">
        开始重建
      </el-button>
      <el-button v-if="phase === 'running'" @click="handleClose">后台运行</el-button>
      <el-button v-if="phase === 'done' || phase === 'error'" type="primary" @click="handleClose">
        完成
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import {
  useBookStore,
  type BookVersionRebuildTask,
} from '@/stores/book'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  (event: 'close'): void
  (event: 'complete'): void
}>()

const bookStore = useBookStore()
const phase = ref<'idle' | 'starting' | 'running' | 'done' | 'error'>('idle')
const task = ref<BookVersionRebuildTask | null>(null)
let pollingTimer: ReturnType<typeof setInterval> | null = null

const finishedStatuses = ['COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED']
const progressPercentage = computed(() => {
  const total = task.value?.totalBooks || 0
  if (total === 0) return 0
  return Math.min(100, Math.round(((task.value?.processedBooks || 0) / total) * 100))
})
const hasErrors = computed(() =>
  task.value?.status === 'COMPLETED_WITH_ERRORS'
  || task.value?.status === 'FAILED'
  || (task.value?.failedBooks || 0) > 0,
)
const progressStatus = computed<'success' | 'exception' | undefined>(() => {
  if (phase.value !== 'done') return undefined
  return hasErrors.value ? 'exception' : 'success'
})
const formattedElapsed = computed(() => {
  const seconds = Math.floor((task.value?.elapsedMs || 0) / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainingSeconds = seconds % 60
  if (hours > 0) return `${hours} 小时 ${minutes} 分 ${remainingSeconds} 秒`
  if (minutes > 0) return `${minutes} 分 ${remainingSeconds} 秒`
  return `${remainingSeconds} 秒`
})
const doneTitle = computed(() => {
  if (task.value?.status === 'FAILED') return '重建失败'
  if (hasErrors.value) return '重建完成，部分书籍处理失败'
  return '多版本重建成功'
})

watch(
  () => props.visible,
  visible => {
    if (visible) {
      reset()
    } else {
      stopPolling()
    }
  },
)

async function startRebuild() {
  phase.value = 'starting'
  try {
    task.value = await bookStore.startBookVersionRebuild()
    updatePhase()
    if (phase.value === 'running') {
      startPolling()
    }
  } catch {
    phase.value = 'error'
  }
}

function startPolling() {
  stopPolling()
  pollingTimer = setInterval(async () => {
    if (!task.value?.taskId) return
    try {
      task.value = await bookStore.getBookVersionRebuildTask(task.value.taskId)
      updatePhase()
    } catch {
      phase.value = 'error'
      stopPolling()
    }
  }, 1000)
}

function updatePhase() {
  if (!task.value) return
  if (finishedStatuses.includes(task.value.status)) {
    phase.value = task.value.status === 'FAILED' ? 'error' : 'done'
    stopPolling()
    emit('complete')
  } else {
    phase.value = 'running'
  }
}

function reset() {
  stopPolling()
  phase.value = 'idle'
  task.value = null
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

function handleClose() {
  stopPolling()
  emit('close')
}

onUnmounted(stopPolling)
</script>

<style scoped>
.rebuild-intro,
.rebuild-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 12px 24px 20px;
}

.intro-icon {
  font-size: 48px;
}

.rebuild-intro h3 {
  margin: 12px 0 6px;
}

.rebuild-intro p,
.rebuild-intro li {
  color: var(--text-secondary);
}

.rebuild-intro ul {
  width: 100%;
  margin: 18px 0 0;
  padding: 16px 20px 16px 38px;
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  text-align: left;
}

.dialog-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.status-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.status-heading p {
  margin: 5px 0 0;
  color: var(--text-tertiary);
  font-size: 13px;
}

.status-heading > strong {
  color: var(--primary);
  font-size: 22px;
}

.progress-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 20px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: var(--surface-card);
}

.summary-card span {
  color: var(--text-tertiary);
  font-size: 12px;
}

.summary-card strong {
  font-size: 18px;
}

.summary-card.success strong {
  color: var(--success);
}

.summary-card.danger strong {
  color: var(--danger);
}

.elapsed-row {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  background: var(--bg-primary);
}

.done-banner {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 16px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--success) 35%, transparent);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--success) 8%, transparent);
}

.done-banner.warning {
  border-color: color-mix(in srgb, var(--warning) 35%, transparent);
  background: color-mix(in srgb, var(--warning) 8%, transparent);
}

.done-banner span {
  color: var(--text-secondary);
  font-size: 13px;
}

.error-list {
  max-height: 140px;
  margin-top: 14px;
  padding: 12px;
  overflow-y: auto;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--danger) 7%, transparent);
  color: var(--danger);
  font-size: 12px;
}

.error-list div {
  margin-top: 6px;
}

@media (max-width: 640px) {
  .progress-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
