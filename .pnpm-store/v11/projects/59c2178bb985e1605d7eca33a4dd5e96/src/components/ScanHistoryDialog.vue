<template>
  <el-dialog
    :model-value="visible"
    title="扫描记录"
    width="min(1080px, 95vw)"
    destroy-on-close
    @open="loadHistory(0)"
    @close="emit('close')"
  >
    <div class="history-toolbar">
      <el-select
        v-model="directoryId"
        clearable
        placeholder="全部目录"
        class="history-filter"
        @change="loadHistory(0)"
      >
        <el-option
          v-for="directory in directories"
          :key="directory.id"
          :label="directory.path"
          :value="directory.id"
        />
      </el-select>
      <el-select
        v-model="status"
        clearable
        placeholder="全部状态"
        class="status-filter"
        @change="loadHistory(0)"
      >
        <el-option label="等待扫描" value="PENDING" />
        <el-option label="正在扫描" value="RUNNING" />
        <el-option label="扫描完成" value="COMPLETED" />
        <el-option label="扫描失败" value="FAILED" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="loadHistory(currentPage - 1)">
        刷新
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="records"
      row-key="id"
      max-height="540"
      empty-text="暂无扫描记录"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="record-detail">
            <div><span>任务 ID</span>{{ row.taskId }}</div>
            <div><span>完整路径</span>{{ row.directoryPath }}</div>
            <div><span>线程数</span>{{ row.threadCount || '-' }}</div>
            <div><span>开始时间</span>{{ formatDateTime(row.startedAt) }}</div>
            <div><span>结束时间</span>{{ formatDateTime(row.finishedAt) }}</div>
            <div v-if="row.message"><span>执行信息</span>{{ row.message }}</div>
            <div v-if="row.errorDetails" class="error-detail">
              <span>错误详情</span>{{ row.errorDetails }}
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="扫描目录" min-width="245" show-overflow-tooltip prop="directoryPath" />
      <el-table-column label="状态" width="102">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="light">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="扫描结果" min-width="260">
        <template #default="{ row }">
          <div class="result-summary">
            <span>总数 {{ row.totalCount || 0 }}</span>
            <span>已扫描 {{ row.scannedCount || 0 }}</span>
            <span class="new-count">新增 {{ row.newBooks || 0 }}</span>
            <span>跳过 {{ row.skippedBooks || 0 }}</span>
            <span :class="{ 'failed-count': row.failedBooks > 0 }">
              失败 {{ row.failedBooks || 0 }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="95">
        <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column label="开始时间" width="168">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
      </el-table-column>
    </el-table>

    <div v-if="total > 0" class="history-pagination">
      <span>共 {{ total }} 条记录</span>
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="page => loadHistory(page - 1)"
      />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/utils/api'
import { message } from '@/utils/message'
import { formatChinaDateTime as formatDateTimeInChina } from '@/utils/dateTime'

interface ScanDirectoryOption {
  id: number
  path: string
}

interface ScanRecord {
  id: number
  taskId: string
  directoryId: number
  directoryPath: string
  status: string
  message?: string
  totalCount: number
  scannedCount: number
  newBooks: number
  skippedBooks: number
  failedBooks: number
  threadCount: number
  durationMs?: number
  startedAt?: string
  finishedAt?: string
  errorDetails?: string
}

defineProps<{
  visible: boolean
  directories: ScanDirectoryOption[]
}>()

const emit = defineEmits<{ close: [] }>()
const records = ref<ScanRecord[]>([])
const loading = ref(false)
const directoryId = ref<number | undefined>()
const status = ref<string | undefined>()
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)

const loadHistory = async (page = currentPage.value - 1) => {
  loading.value = true
  try {
    const { data } = await api.get('/api/scan-directories/scan-history', {
      params: {
        page,
        size: pageSize,
        directoryId: directoryId.value,
        status: status.value,
      },
    })
    records.value = data.content
    total.value = data.totalElements
    currentPage.value = data.number + 1
  } catch {
    message.error('加载扫描记录失败')
  } finally {
    loading.value = false
  }
}

const statusText = (value: string) => {
  const labels: Record<string, string> = {
    PENDING: '等待扫描',
    RUNNING: '正在扫描',
    COMPLETED: '扫描完成',
    FAILED: '扫描失败',
  }
  return labels[value] || value
}

const statusType = (value: string): 'success' | 'danger' | 'warning' | 'info' => {
  if (value === 'COMPLETED') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'RUNNING') return 'warning'
  return 'info'
}

const formatDateTime = (value?: string) =>
  value ? formatDateTimeInChina(value) : '-'

const formatDuration = (value?: number) => {
  if (value == null) return '进行中'
  if (value < 1000) return `${value} 毫秒`
  if (value < 60_000) return `${(value / 1000).toFixed(1)} 秒`
  const minutes = Math.floor(value / 60_000)
  const seconds = Math.round((value % 60_000) / 1000)
  return `${minutes}分${seconds}秒`
}
</script>

<style scoped>
.history-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.history-filter {
  width: min(360px, 45%);
}

.status-filter {
  width: 150px;
}

.result-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.new-count {
  color: var(--el-color-success);
}

.failed-count {
  color: var(--el-color-danger);
}

.record-detail {
  display: grid;
  gap: 9px;
  padding: 4px 40px 14px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  word-break: break-all;
}

.record-detail div {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr);
  gap: 12px;
}

.record-detail span {
  color: var(--el-text-color-secondary);
}

.error-detail {
  color: var(--el-color-danger);
}

.history-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 700px) {
  .history-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .history-filter,
  .status-filter {
    flex: 1;
    min-width: 150px;
    width: auto;
  }
}
</style>
