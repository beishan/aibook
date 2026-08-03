<template>
  <div class="card glass operation-log-card">
    <div class="log-header">
      <div>
        <div class="panel-title">📋 操作日志</div>
        <p class="header-hint">关键操作按时间倒序展示</p>
      </div>
      <button class="btn btn-primary" :disabled="loading" @click="loadLogs(currentPage - 1)">
        {{ loading ? '刷新中...' : '刷新' }}
      </button>
    </div>

    <div v-if="loading && logs.length === 0" class="log-state">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="logs.length === 0" class="log-state">
      <div class="empty-icon">📝</div>
      <p>暂无操作日志</p>
    </div>

    <div v-else class="log-table-wrap">
      <table class="log-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>操作类型</th>
            <th>操作内容</th>
            <th>详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td class="time-cell">{{ formatChinaDateTime(log.createdAt) }}</td>
            <td>
              <span class="tag" :class="actionMeta(log.action).className">
                {{ actionMeta(log.action).label }}
              </span>
            </td>
            <td class="description-cell">{{ log.description }}</td>
            <td class="details-cell">{{ log.details || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > 0" class="log-pagination">
      <span class="log-total">共 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/utils/api'
import { formatChinaDateTime } from '@/utils/dateTime'
import { message } from '@/utils/message'

interface OperationLog {
  id: number
  action: string
  description: string
  details?: string
  createdAt: string
}

const logs = ref<OperationLog[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const actionLabels: Record<string, { label: string; className: string }> = {
  IMPORT_BOOK: { label: '导入书籍', className: 'tag-success' },
  OPEN_BOOK: { label: '打开书籍', className: 'tag-primary' },
  DELETE_BOOK: { label: '删除书籍', className: 'tag-warning' },
  PERMANENTLY_DELETE_BOOK: { label: '永久删除', className: 'tag-danger' },
  RESTORE_BOOK: { label: '恢复书籍', className: 'tag-info' },
  CREATE_USER: { label: '新增用户', className: 'tag-success' },
  UPDATE_USER: { label: '修改用户', className: 'tag-primary' },
  DELETE_USER: { label: '删除用户', className: 'tag-danger' },
  RESET_PASSWORD: { label: '重置密码', className: 'tag-warning' },
  UPDATE_PROFILE: { label: '个人资料', className: 'tag-primary' },
  UPDATE_AVATAR: { label: '个人头像', className: 'tag-primary' },
  UPDATE_SITE_FAVICON: { label: '网站图标', className: 'tag-primary' },
}

const actionMeta = (action: string) =>
  actionLabels[action] || { label: action, className: 'tag-info' }

const loadLogs = async (page = currentPage.value - 1) => {
  loading.value = true
  try {
    const { data } = await api.get('/api/operation-logs', {
      params: { page, size: pageSize.value },
    })
    logs.value = data.content || []
    total.value = data.totalElements || 0
    currentPage.value = (data.number || 0) + 1
  } catch (error: any) {
    message.error(error.response?.data?.message || '操作日志加载失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => void loadLogs(page - 1)

const handleSizeChange = () => {
  currentPage.value = 1
  void loadLogs(0)
}

onMounted(() => void loadLogs())
</script>

<style scoped>
.operation-log-card {
  overflow: hidden;
}

.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}

.panel-title {
  color: var(--text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.log-header .btn {
  flex: 0 0 auto;
  margin-left: auto;
}

.header-hint {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.log-state {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.log-table-wrap {
  overflow-x: auto;
}

.log-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 760px;
}

.log-table th,
.log-table td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color-light);
  text-align: left;
  vertical-align: middle;
}

.log-table th {
  color: var(--text-secondary);
  background: var(--surface-muted, var(--primary-alpha-10));
  font-size: var(--font-size-sm);
  font-weight: 600;
  white-space: nowrap;
}

.log-table tbody tr:last-child td {
  border-bottom: 0;
}

.time-cell {
  width: 180px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.description-cell {
  color: var(--text-primary);
  font-weight: 500;
}

.details-cell {
  color: var(--text-secondary);
}

.tag-primary {
  background: var(--primary-alpha-10);
  color: var(--primary);
}

.tag-warning {
  background: rgba(245, 158, 11, 0.14);
  color: #d97706;
}

.tag-danger {
  background: rgba(239, 68, 68, 0.14);
  color: #dc2626;
}

.log-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--border-color-light);
}

.log-total {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

@media (max-width: 640px) {
  .log-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
