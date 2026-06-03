<template>
  <el-dialog
    :model-value="visible"
    title="批量刮削元数据"
    width="600px"
    :close-on-click-modal="false"
    :close-on-press-escape="phase !== 'running'"
    @close="handleClose"
  >
    <!-- 空闲状态：确认开始 -->
    <div v-if="phase === 'idle'" class="batch-scrape-idle">
      <el-icon :size="48" color="#409eff"><InfoFilled /></el-icon>
      <p v-if="mode === 'selected'">
        选中了 <strong>{{ bookIds.length }}</strong> 本书
      </p>
      <p v-else>
        将查找所有缺少元数据的书籍
      </p>
      <p class="hint">
        系统将自动从豆瓣、Open Library等来源获取书籍信息
      </p>
      <div class="force-update-option">
        <label class="checkbox-label">
          <input type="checkbox" v-model="forceUpdate" />
          <span>强制更新已有信息</span>
        </label>
        <p class="hint">
          {{ forceUpdate
            ? '将重新刮削所有选中的书籍，覆盖已有信息'
            : '默认只刮削缺少作者或简介的书籍' }}
        </p>
      </div>
    </div>

    <!-- 启动中 -->
    <div v-else-if="phase === 'starting'" class="batch-scrape-loading">
      <el-icon class="is-loading" :size="48"><Loading /></el-icon>
      <p>正在启动刮削任务...</p>
    </div>

    <!-- 运行中 -->
    <div v-else-if="phase === 'running'" class="batch-scrape-running">
      <div class="progress-header">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        <span>正在刮削...</span>
      </div>

      <!-- 进度条 -->
      <el-progress
        :percentage="progressPercentage"
        :status="progressStatus"
        :stroke-width="20"
        text-inside
      />

      <!-- 当前书名 -->
      <p v-if="taskStatus?.currentBookTitle" class="current-book">
        当前：《{{ taskStatus.currentBookTitle }}》
      </p>

      <!-- 统计信息 -->
      <div class="stats">
        <span class="stat-item success">
          <el-icon><Check /></el-icon>
          {{ taskStatus?.completedBooks || 0 }} 成功
        </span>
        <span class="stat-item danger">
          <el-icon><Close /></el-icon>
          {{ taskStatus?.failedBooks || 0 }} 失败
        </span>
        <span class="stat-item">
          共 {{ taskStatus?.totalBooks || 0 }} 本
        </span>
      </div>

      <!-- 结果列表 -->
      <div v-if="taskStatus?.results && taskStatus.results.length > 0" class="results-list">
        <div
          v-for="result in taskStatus.results"
          :key="result.bookId"
          class="result-item"
          :class="{ success: result.success, error: !result.success }"
        >
          <el-icon v-if="result.success" color="#67c23a"><Check /></el-icon>
          <el-icon v-else color="#f56c6c"><Close /></el-icon>
          <span class="book-title">{{ result.title }}</span>
          <span v-if="!result.success && result.error" class="error-msg">{{ result.error }}</span>
        </div>
      </div>
    </div>

    <!-- 完成状态 -->
    <div v-else-if="phase === 'done'" class="batch-scrape-done">
      <el-result
        :icon="taskStatus?.status === 'COMPLETED' ? 'success' : taskStatus?.status === 'FAILED' ? 'error' : 'warning'"
        :title="doneTitle"
        :sub-title="doneSubTitle"
      >
        <template #extra>
          <div class="done-stats">
            <p>✅ 成功：{{ taskStatus?.completedBooks || 0 }} 本</p>
            <p>❌ 失败：{{ taskStatus?.failedBooks || 0 }} 本</p>
          </div>
        </template>
      </el-result>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="phase === 'error'" class="batch-scrape-error">
      <el-result icon="error" title="任务启动失败" :sub-title="errorMessage">
        <template #extra>
          <el-button type="primary" @click="retry">重试</el-button>
        </template>
      </el-result>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button v-if="phase === 'idle'" @click="handleClose">取消</el-button>
        <el-button v-if="phase === 'idle'" type="primary" @click="startScrape">
          开始刮削
        </el-button>

        <el-button v-if="phase === 'running'" @click="cancelTask">取消任务</el-button>

        <el-button v-if="phase === 'done' || phase === 'error'" type="primary" @click="handleClose">
          完成
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled, Loading, Check, Close } from '@element-plus/icons-vue'
import {
  batchScrape,
  scrapeAllIncomplete,
  getScrapeTask,
  cancelScrapeTask,
  createScrapeSSE,
  getAuthToken,
  type TaskStatus
} from '@/utils/scraper'

const props = defineProps<{
  visible: boolean
  bookIds: number[]
  mode: 'selected' | 'all-incomplete'
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'complete'): void
}>()

// 状态
const phase = ref<'idle' | 'starting' | 'running' | 'done' | 'error'>('idle')
const taskId = ref<string | null>(null)
const taskStatus = ref<TaskStatus | null>(null)
const errorMessage = ref('')
const eventSource = ref<EventSource | null>(null)
let pollingTimer: ReturnType<typeof setInterval> | null = null
const forceUpdate = ref(false)

// 计算属性
const progressPercentage = computed(() => {
  if (!taskStatus.value) return 0
  const { completedBooks, failedBooks, totalBooks } = taskStatus.value
  return Math.round(((completedBooks + failedBooks) / totalBooks) * 100)
})

const progressStatus = computed(() => {
  if (!taskStatus.value) return undefined
  if (taskStatus.value.failedBooks > 0) return 'exception'
  if (taskStatus.value.status === 'COMPLETED') return 'success'
  return undefined
})

const doneTitle = computed(() => {
  if (taskStatus.value?.status === 'COMPLETED') return '刮削完成'
  if (taskStatus.value?.status === 'FAILED') return '任务失败'
  if (taskStatus.value?.status === 'CANCELLED') return '任务已取消'
  return '任务结束'
})

const doneSubTitle = computed(() => {
  if (taskStatus.value?.status === 'CANCELLED') return '任务已被用户取消'
  if (taskStatus.value?.errorMessage) return taskStatus.value.errorMessage
  return ''
})

// 监听visible变化，重置状态
watch(() => props.visible, (newVal) => {
  if (newVal) {
    resetState()
  } else {
    cleanup()
  }
})

// 重置状态
function resetState() {
  phase.value = 'idle'
  taskId.value = null
  taskStatus.value = null
  errorMessage.value = ''
  cleanup()
}

// 清理资源
function cleanup() {
  if (eventSource.value) {
    eventSource.value.close()
    eventSource.value = null
  }
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// 开始刮削
async function startScrape() {
  phase.value = 'starting'

  try {
    let result: { taskId: string }

    if (props.mode === 'selected') {
      result = await batchScrape(props.bookIds, forceUpdate.value)
    } else {
      result = await scrapeAllIncomplete(forceUpdate.value)
    }

    taskId.value = result.taskId
    phase.value = 'running'

    // 建立SSE连接
    connectSSE(result.taskId)

  } catch (error: any) {
    console.error('启动刮削任务失败:', error)
    phase.value = 'error'
    errorMessage.value = error.response?.data?.message || error.message || '启动失败，请重试'
  }
}

// 建立SSE连接
function connectSSE(id: string) {
  const token = getAuthToken()
  if (!token) {
    phase.value = 'error'
    errorMessage.value = '未找到认证令牌'
    return
  }

  eventSource.value = createScrapeSSE(
    id,
    token,
    (status) => {
      taskStatus.value = status

      // 检查是否完成
      if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(status.status)) {
        cleanup()
        phase.value = 'done'
        emit('complete')
      }
    },
    (error) => {
      console.error('SSE连接错误，切换到轮询模式:', error)
      // 降级到轮询
      startPolling(id)
    }
  )
}

// 轮询模式（SSE失败时的降级方案）
function startPolling(id: string) {
  if (pollingTimer) return

  pollingTimer = setInterval(async () => {
    try {
      const status = await getScrapeTask(id)
      taskStatus.value = status

      if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(status.status)) {
        cleanup()
        phase.value = 'done'
        emit('complete')
      }
    } catch (error) {
      console.error('轮询任务状态失败:', error)
    }
  }, 2000)
}

// 取消任务
async function cancelTask() {
  if (!taskId.value) return

  try {
    await cancelScrapeTask(taskId.value)
    ElMessage.success('任务已取消')
    cleanup()
    phase.value = 'done'
    emit('complete')
  } catch (error: any) {
    console.error('取消任务失败:', error)
    ElMessage.error('取消任务失败')
  }
}

// 重试
function retry() {
  resetState()
}

// 关闭对话框
function handleClose() {
  // 如果任务还在运行，提示用户
  if (phase.value === 'running') {
    if (confirm('任务正在运行中，确定要关闭吗？任务将在后台继续运行。')) {
      cleanup()
      emit('close')
    }
  } else {
    cleanup()
    emit('close')
  }
}

// 组件卸载时清理
onUnmounted(() => {
  cleanup()
})
</script>

<style scoped>
.batch-scrape-idle,
.batch-scrape-loading,
.batch-scrape-running,
.batch-scrape-done,
.batch-scrape-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  text-align: center;
}

.batch-scrape-idle p,
.batch-scrape-loading p {
  margin: 16px 0;
  font-size: 16px;
}

.hint {
  color: #909399;
  font-size: 14px;
}

.progress-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 16px;
  color: #409eff;
}

.current-book {
  margin: 12px 0;
  font-size: 14px;
  color: #606266;
}

.stats {
  display: flex;
  gap: 24px;
  margin: 16px 0;
  font-size: 14px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #909399;
}

.stat-item.success {
  color: #67c23a;
}

.stat-item.danger {
  color: #f56c6c;
}

.results-list {
  width: 100%;
  max-height: 200px;
  overflow-y: auto;
  margin-top: 16px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.result-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
}

.result-item:last-child {
  border-bottom: none;
}

.result-item.success {
  background-color: #f0f9eb;
}

.result-item.error {
  background-color: #fef0f0;
}

.book-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-msg {
  color: #f56c6c;
  font-size: 12px;
}

.done-stats {
  text-align: left;
}

.done-stats p {
  margin: 8px 0;
  font-size: 14px;
}

.force-update-option {
  margin-top: 20px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.hint {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>
