<template>
  <div class="settings-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">系统设置</h1>
        <p class="page-subtitle">管理您的书库配置</p>
      </div>
    </div>

    <!-- 选项卡 -->
    <div class="tabs">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="selectTab(tab.key)"
      >
        <span class="tab-icon">{{ tab.icon }}</span>
        <span>{{ tab.label }}</span>
      </div>
    </div>

    <!-- 主题设置 -->
    <div v-show="activeTab === 'theme'" class="tab-content">
      <div class="card glass">
        <div class="card-header">
          <span>🎨 主题风格</span>
        </div>

        <div class="theme-grid">
          <div
            v-for="theme in themes"
            :key="theme.id"
            class="theme-card"
            :class="{ active: themeStore.currentTheme === theme.id }"
            @click="handleThemeChange(theme.id)"
          >
            <div class="theme-preview" :class="`theme-preview-${theme.id}`">
              <div class="preview-sidebar" :class="`sidebar-${theme.id}`"></div>
              <div class="preview-content">
                <div class="preview-header" :class="`header-${theme.id}`"></div>
                <div class="preview-cards">
                  <div class="preview-card" :class="`card-${theme.id}`"></div>
                  <div class="preview-card" :class="`card-${theme.id}`"></div>
                </div>
              </div>
            </div>
            <div class="theme-info">
              <div class="theme-name">
                <span class="theme-icon">{{ theme.icon }}</span>
                <span>{{ theme.name }}</span>
              </div>
              <div class="theme-desc">{{ theme.description }}</div>
              <div class="theme-layout">
                <span class="layout-badge">{{ getLayoutName(theme.layout) }}</span>
              </div>
            </div>
            <div v-if="themeStore.currentTheme === theme.id" class="theme-check">✓</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 字体管理 -->
    <div v-show="activeTab === 'fonts'" class="tab-content">
      <FontManagementPanel />
    </div>

    <!-- 扫描目录 -->
    <div v-show="activeTab === 'directories'" class="tab-content">
      <div class="card glass">
        <div class="card-header">
          <span>📁 监控目录配置</span>
          <div class="directory-header-actions">
            <button class="btn" @click="showScanHistory = true">
              <span>📋</span>
              <span>扫描记录</span>
            </button>
            <button class="btn btn-primary" @click="showAddDialog = true">
              <span>➕</span>
              <span>添加目录</span>
            </button>
          </div>
        </div>

        <div class="scan-performance-settings">
          <div>
            <div class="form-label">扫描并发线程数</div>
            <div class="form-hint">
              同时处理的书籍文件数量，范围 1–16；NAS 建议设置为 2–4。
            </div>
          </div>
          <div class="scan-thread-control">
            <input
              v-model.number="scanThreadCountDraft"
              type="number"
              min="1"
              max="16"
              step="1"
              class="input scan-thread-input"
            />
            <button
              class="btn btn-primary"
              :disabled="savingScanSettings"
              @click="handleSaveScanSettings"
            >
              {{ savingScanSettings ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>

        <div v-if="loading" class="loading">
          <div class="loading-spinner"></div>
          <p>加载中...</p>
        </div>

        <div v-else-if="directories.length === 0" class="empty">
          <div class="empty-icon">📂</div>
          <p>暂无扫描目录</p>
        </div>

        <div v-else class="directories-list">
          <div v-for="row in directories" :key="row.id" class="directory-item">
            <div class="directory-info">
              <div class="directory-path">{{ row.path }}</div>
              <div class="directory-meta">
                <span class="tag" :class="row.enabled ? 'tag-success' : 'tag-info'">
                  {{ row.enabled ? '启用' : '禁用' }}
                </span>
                <span class="meta-text">{{ row.bookCount }} 本书</span>
                <span class="meta-text">
                  默认分类：{{ row.defaultCategoryName || '未分类' }}
                </span>
                <span class="meta-text">{{ row.lastScanTime ? formatTime(row.lastScanTime) : '未扫描' }}</span>
              </div>
            </div>
            <div class="directory-actions">
              <select
                class="select-input directory-category-select"
                :value="row.defaultCategoryId || ''"
                @change="handleDefaultCategoryChange(row, $event)"
              >
                <option value="">新书不分类</option>
                <option
                  v-for="category in categoryStore.flatTree"
                  :key="category.id"
                  :value="category.id"
                >
                  {{ category.path }}
                </option>
              </select>
              <button class="btn btn-text" @click="handleScan(row)" :disabled="row._scanning">
                {{ row._scanning ? `${row._scanProgress?.progress || 0}%` : '立即扫描' }}
              </button>
              <button class="btn btn-text" @click="handleToggle(row)">
                {{ row.enabled ? '禁用' : '启用' }}
              </button>
              <button class="btn btn-text btn-danger" @click="handleRemove(row)">删除</button>
            </div>
            <div
              v-if="row._scanProgress && row._scanProgress.status !== 'IDLE'"
              class="scan-progress-panel"
            >
              <div class="scan-progress-header">
                <span>{{ getScanStatusText(row._scanProgress.status) }}</span>
                <strong>{{ row._scanProgress.progress }}%</strong>
              </div>
              <el-progress
                :percentage="row._scanProgress.progress"
                :stroke-width="8"
                :show-text="false"
                :status="getScanProgressStatus(row._scanProgress.status)"
              />
              <div class="scan-progress-stats">
                <span>总数 {{ row._scanProgress.totalCount }}</span>
                <span>
                  已扫描
                  {{ row._scanProgress.scannedCount }}/{{ row._scanProgress.totalCount }}
                </span>
                <span>新增 {{ row._scanProgress.newBooks || 0 }}</span>
                <span>跳过 {{ row._scanProgress.skippedBooks || 0 }}</span>
                <span>失败 {{ row._scanProgress.failedBooks || 0 }}</span>
              </div>
              <div
                v-if="row._scanning && row._scanProgress.currentFile"
                class="scan-current-file"
              >
                当前：{{ formatScanFile(row._scanProgress.currentFile) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- OPDS 与第三方客户端连接 -->
    <div v-show="activeTab === 'connections'" class="tab-content">
      <ConnectionsView embedded />
    </div>

    <!-- 定时任务 -->
    <div v-show="activeTab === 'scheduler'" class="tab-content">
      <div class="card glass">
        <div class="card-header">
          <span>⏰ 定时扫描配置</span>
        </div>

        <div class="settings-form">
          <div class="form-group">
            <div class="form-row">
              <label class="form-label">启用定时扫描</label>
              <label class="switch">
                <input type="checkbox" v-model="schedulerConfig.enabled" />
                <span class="switch-slider"></span>
              </label>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">扫描时间</label>
            <input type="time" v-model="schedulerTime" class="input" />
          </div>

          <button class="btn btn-primary" @click="handleSaveScheduler">
            <span>💾</span>
            <span>保存配置</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 操作日志 -->
    <div v-show="activeTab === 'logs'" class="tab-content">
      <OperationLogPanel />
    </div>

    <!-- 用户管理（仅管理员） -->
    <div v-if="isAdmin && activeTab === 'users'" class="tab-content">
      <UserManagementPanel />
    </div>

    <!-- 系统信息 -->
    <div v-show="activeTab === 'info'" class="tab-content">
      <div class="card glass">
        <div class="card-header">
          <span>ℹ️ 系统信息</span>
        </div>

        <div class="info-list grouped-list">
          <div class="info-item list-item">
            <span class="info-label">系统版本</span>
            <span class="info-value">1.0.0</span>
          </div>
          <div class="info-item list-item">
            <span class="info-label">运行状态</span>
            <span class="info-value">
              <span class="tag tag-success">正常运行</span>
            </span>
          </div>
          <div class="info-item list-item">
            <span class="info-label">数据库状态</span>
            <span class="info-value">
              <span class="tag tag-success">已连接</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 添加目录对话框 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showAddDialog" class="dialog-overlay" @click.self="showAddDialog = false">
          <div class="dialog">
            <div class="dialog-header">
              <span>📂 添加扫描目录</span>
              <button class="dialog-close" @click="showAddDialog = false">✕</button>
            </div>

            <div class="dialog-body">
              <DirectoryBrowser @select="handleDirectorySelect" />
              <div class="form-group default-category-field">
                <label class="form-label">新书默认分类</label>
                <select v-model="selectedDefaultCategoryId" class="select-input">
                  <option value="">暂不分类</option>
                  <option
                    v-for="category in categoryStore.flatTree"
                    :key="category.id"
                    :value="String(category.id)"
                  >
                    {{ category.path }}
                  </option>
                </select>
                <p class="field-hint">只影响之后新扫描入库的书，不覆盖已有书籍分类。</p>
              </div>
            </div>

            <div class="dialog-footer">
              <button class="btn" @click="showAddDialog = false">取消</button>
              <button
                class="btn btn-primary"
                @click="handleAddDirectory"
                :disabled="adding || !selectedPath"
              >
                <span v-if="adding" class="loading-spinner-small"></span>
                <span>{{ adding ? '添加中...' : '添加目录' }}</span>
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <ScanHistoryDialog
      :visible="showScanHistory"
      :directories="directories"
      @close="showScanHistory = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, confirm } from '@/utils/message'
import { formatChinaDateTime } from '@/utils/dateTime'
import api from '@/utils/api'
import DirectoryBrowser from '@/components/DirectoryBrowser.vue'
import ScanHistoryDialog from '@/components/ScanHistoryDialog.vue'
import ConnectionsView from '@/views/ConnectionsView.vue'
import FontManagementPanel from '@/components/FontManagementPanel.vue'
import OperationLogPanel from '@/components/OperationLogPanel.vue'
import UserManagementPanel from '@/components/UserManagementPanel.vue'
import { useThemeStore } from '@/stores/theme'
import { usePreferencesStore } from '@/stores/preferences'
import { useCategoryStore } from '@/stores/category'
import { useUserStore } from '@/stores/user'
import { THEMES, type ThemeId } from '@/types/theme'

const themeStore = useThemeStore()
const route = useRoute()
const router = useRouter()
const preferencesStore = usePreferencesStore()
const categoryStore = useCategoryStore()
const userStore = useUserStore()
const themes = THEMES

const baseTabs = [
  { key: 'theme', label: '主题风格', icon: '🎨' },
  { key: 'fonts', label: '字体管理', icon: '🔤' },
  { key: 'directories', label: '扫描目录', icon: '📂' },
  { key: 'connections', label: 'OPDS 连接', icon: '🔗' },
  { key: 'scheduler', label: '定时任务', icon: '⏰' },
  { key: 'logs', label: '操作日志', icon: '📋' },
  { key: 'info', label: '系统信息', icon: 'ℹ️' },
]
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const tabs = computed(() => isAdmin.value
  ? [
      ...baseTabs.slice(0, -1),
      { key: 'users', label: '用户管理', icon: '👥' },
      baseTabs[baseTabs.length - 1],
    ]
  : baseTabs
)

const getLayoutName = (layout: string) => {
  const names: Record<string, string> = {
    sidebar: '侧边栏',
    topbar: '顶部栏',
    dock: '底部 Dock'
  }
  return names[layout] || layout
}

const handleThemeChange = (id: ThemeId) => {
  preferencesStore.setTheme(id)
  message.success(`已切换到「${themes.find(t => t.id === id)?.name}」主题`)
}

const tabKeys = computed(() => new Set(tabs.value.map(tab => tab.key)))
const activeTab = ref('theme')

const syncActiveTab = (tab: unknown) => {
  activeTab.value =
    typeof tab === 'string' && tabKeys.value.has(tab) ? tab : 'theme'
}

const selectTab = (tab: string) => {
  if (!tabKeys.value.has(tab)) return
  activeTab.value = tab
  void router.replace({
    query: {
      ...route.query,
      tab: tab === 'theme' ? undefined : tab,
    },
  })
}

watch(
  [() => route.query.tab, isAdmin],
  ([tab]) => syncActiveTab(tab)
)
const loading = ref(false)
const adding = ref(false)
const showAddDialog = ref(false)
const showScanHistory = ref(false)
const directories = ref<any[]>([])
const scanThreadCountDraft = ref(preferencesStore.scanThreadCount)
const savingScanSettings = ref(false)
const scanPollTimers = new Map<number, ReturnType<typeof setTimeout>>()

const newDir = reactive({ path: '' })
const selectedPath = ref('')
const selectedDefaultCategoryId = ref('')

const schedulerConfig = reactive({
  enabled: true,
  time: '02:00',
})

const schedulerTime = computed({
  get: () => schedulerConfig.time,
  set: (value: string) => {
    schedulerConfig.time = value
  },
})

const loadDirectories = async (showLoading = true) => {
  if (showLoading) loading.value = true
  try {
    const res = await api.get('/api/scan-directories')
    const existingRows = new Map(directories.value.map(row => [row.id, row]))
    directories.value = res.data.map((directory: any) => {
      const existing = existingRows.get(directory.id)
      if (existing) {
        return Object.assign(existing, directory)
      }
      return {
        ...directory,
        _scanning: false,
        _scanProgress: null,
        _scanPollFailures: 0,
      }
    })
  } catch (error) {
    console.error('Failed to load directories:', error)
  } finally {
    if (showLoading) loading.value = false
  }
}

const isScanActive = (status: string) =>
  status === 'PENDING' || status === 'RUNNING'

const stopScanPolling = (directoryId: number) => {
  const timer = scanPollTimers.get(directoryId)
  if (timer) clearTimeout(timer)
  scanPollTimers.delete(directoryId)
}

const scheduleScanPoll = (row: any) => {
  stopScanPolling(row.id)
  scanPollTimers.set(
    row.id,
    setTimeout(() => void pollScanProgress(row), 800)
  )
}

const pollScanProgress = async (row: any) => {
  stopScanPolling(row.id)
  const wasActive = row._scanning
  try {
    const { data } = await api.get(`/api/scan-directories/${row.id}/scan-progress`)
    row._scanProgress = data
    row._scanning = isScanActive(data.status)
    row._scanPollFailures = 0

    if (row._scanning) {
      scheduleScanPoll(row)
      return
    }

    if (wasActive) {
      if (data.status === 'COMPLETED') {
        message.success(
          `扫描完成：共 ${data.totalCount} 本，新增 ${data.newBooks || 0} 本，失败 ${data.failedBooks || 0} 本`
        )
      } else if (data.status === 'FAILED') {
        message.error(data.message || '扫描失败')
      }
      await loadDirectories(false)
    }
  } catch (error) {
    row._scanPollFailures = (row._scanPollFailures || 0) + 1
    if (row._scanning && row._scanPollFailures <= 3) {
      scheduleScanPoll(row)
      return
    }
    row._scanning = false
    message.error('获取扫描进度失败')
  }
}

const restoreScanProgress = async () => {
  await Promise.all(
    directories.value.map(async row => {
      try {
        const { data } = await api.get(
          `/api/scan-directories/${row.id}/scan-progress`
        )
        if (data.status === 'IDLE') return
        row._scanProgress = data
        row._scanning = isScanActive(data.status)
        if (row._scanning) scheduleScanPoll(row)
      } catch (error) {
        console.error(`Failed to restore scan progress for ${row.id}:`, error)
      }
    })
  )
}

const handleSaveScanSettings = async () => {
  const value = Number(scanThreadCountDraft.value)
  if (!Number.isInteger(value) || value < 1 || value > 16) {
    message.warning('扫描线程数必须是 1 到 16 之间的整数')
    return
  }

  savingScanSettings.value = true
  try {
    await api.put('/api/user/preferences', { scanThreadCount: value })
    preferencesStore.setScanThreadCount(value, false)
    message.success('扫描线程数已保存')
  } catch (error: any) {
    message.error(error.response?.data?.message || '扫描线程数保存失败')
  } finally {
    savingScanSettings.value = false
  }
}

const handleDirectorySelect = (path: string) => {
  selectedPath.value = path
  newDir.path = path
}

const handleAddDirectory = async () => {
  const pathToAdd = selectedPath.value || newDir.path.trim()
  if (!pathToAdd) {
    message.warning('请选择或输入目录路径')
    return
  }
  adding.value = true
  try {
    await api.post('/api/scan-directories', {
      path: pathToAdd,
      defaultCategoryId: selectedDefaultCategoryId.value
        ? Number(selectedDefaultCategoryId.value)
        : null,
    })
    message.success('添加成功')
    newDir.path = ''
    selectedPath.value = ''
    selectedDefaultCategoryId.value = ''
    showAddDialog.value = false
    await loadDirectories()
  } catch (error: any) {
    message.error(error.response?.data?.message || '添加失败')
  } finally {
    adding.value = false
  }
}

const handleDefaultCategoryChange = async (row: any, event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  try {
    await api.put(`/api/scan-directories/${row.id}/default-category`, {
      categoryId: value ? Number(value) : null,
    })
    message.success('默认分类已更新')
    await loadDirectories()
  } catch {
    message.error('默认分类更新失败')
  }
}

const handleScan = async (row: any) => {
  stopScanPolling(row.id)
  row._scanning = true
  try {
    const res = await api.post(`/api/scan-directories/${row.id}/scan`)
    row._scanProgress = res.data
    row._scanning = isScanActive(res.data.status)
    if (row._scanning) {
      scheduleScanPoll(row)
    } else if (res.data.status === 'COMPLETED') {
      message.success(
        `扫描完成：共 ${res.data.totalCount} 本，新增 ${res.data.newBooks || 0} 本`
      )
      await loadDirectories(false)
    }
  } catch (error: any) {
    row._scanning = false
    message.error(error.response?.data?.message || '扫描失败')
  }
}

const handleToggle = async (row: any) => {
  try {
    await api.put(`/api/scan-directories/${row.id}/toggle`)
    message.success(row.enabled ? '已禁用' : '已启用')
    await loadDirectories()
  } catch (error) {
    message.error('操作失败')
  }
}

const handleRemove = async (row: any) => {
  const result = await confirm(`确定要删除目录 ${row.path} 吗？`)
  if (result) {
    try {
      stopScanPolling(row.id)
      await api.delete(`/api/scan-directories/${row.id}`)
      message.success('删除成功')
      await loadDirectories()
    } catch (error) {
      message.error('删除失败')
    }
  }
}

const handleSaveScheduler = () => {
  message.success('配置已保存')
}

const formatTime = (timeStr: string) => {
  return formatChinaDateTime(timeStr)
}

const formatScanFile = (path: string) => path.split(/[\\/]/).pop() || path

const getScanStatusText = (status: string) => {
  const labels: Record<string, string> = {
    PENDING: '等待扫描',
    RUNNING: '正在扫描',
    COMPLETED: '扫描完成',
    FAILED: '扫描失败',
  }
  return labels[status] || status
}

const getScanProgressStatus = (
  status: string
): 'success' | 'exception' | undefined => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'exception'
  return undefined
}

onMounted(async () => {
  await userStore.hydrate()
  syncActiveTab(route.query.tab)
  await preferencesStore.hydrate()
  scanThreadCountDraft.value = preferencesStore.scanThreadCount
  await loadDirectories()
  await restoreScanProgress()
  await categoryStore.refresh()
})

onUnmounted(() => {
  scanPollTimers.forEach(timer => clearTimeout(timer))
  scanPollTimers.clear()
})
</script>

<style scoped>
.settings-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

/* 页面头部 */
.page-header {
  margin-bottom: var(--spacing-xl);
}

.page-title {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  color: var(--text-on-page-bg);
  text-shadow: var(--text-on-page-bg-shadow);
  margin-bottom: var(--spacing-sm);
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: var(--text-on-page-bg-secondary);
}

/* 卡片 */
.card {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  font-weight: 600;
  font-size: var(--font-size-lg);
}

.directory-header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.scan-performance-settings {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-xl);
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  background: var(--surface-hover);
}

.form-hint {
  margin-top: var(--spacing-xs);
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
  line-height: 1.5;
}

.scan-thread-control {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: var(--spacing-sm);
}

.scan-thread-input {
  width: 96px;
}

/* 加载中和空状态 */
.loading,
.empty {
  text-align: center;
  color: var(--text-secondary);
  padding: var(--spacing-xl);
}

.loading-spinner {
  display: inline-block;
  width: 32px;
  height: 32px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: var(--spacing-md);
}

.loading-spinner-small {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-right: var(--spacing-sm);
  vertical-align: middle;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-icon {
  font-size: 48px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

/* 目录列表 */
.directories-list {
  padding: var(--spacing-md);
}

.directory-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md) var(--spacing-lg);
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  transition: background var(--transition-fast);
}

.directory-item:last-child {
  border-bottom: none;
}

.directory-item:hover {
  background: var(--surface-hover);
}

.directory-info {
  flex: 1;
  min-width: 0;
}

.directory-path {
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: var(--spacing-sm);
  word-break: break-all;
}

.directory-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

.meta-text {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.scan-progress-panel {
  grid-column: 1 / -1;
  width: 100%;
  max-width: none;
  box-sizing: border-box;
  padding: var(--spacing-md);
  background: var(--surface-hover);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
}

.scan-progress-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--spacing-sm);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.scan-progress-header strong {
  color: var(--primary);
}

.scan-progress-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm) var(--spacing-md);
  margin-top: var(--spacing-sm);
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
}

.scan-current-file {
  margin-top: var(--spacing-xs);
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.directory-category-select {
  max-width: 180px;
}

.default-category-field {
  margin-top: var(--spacing-lg);
}

.default-category-field .select-input {
  width: 100%;
}

.field-hint {
  margin-top: var(--spacing-xs);
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

.btn-danger {
  color: var(--danger) !important;
}

/* 设置表单 */
.settings-form {
  padding: var(--spacing-lg);
  max-width: 400px;
}

.form-group {
  margin-bottom: var(--spacing-lg);
}

.form-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-label {
  font-weight: 500;
  color: var(--text-secondary);
}

/* 信息列表 */
.info-list {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin: var(--spacing-md);
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

.info-value {
  color: var(--text-primary);
  font-weight: 500;
}

/* 主题选择 */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-lg);
  padding: var(--spacing-lg);
}

.theme-card {
  position: relative;
  border: 2px solid var(--border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-normal);
}

.theme-card:hover {
  border-color: var(--primary);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.theme-card.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-alpha-20);
}

.theme-preview {
  height: 120px;
  display: flex;
  overflow: hidden;
}

/* 现代简约预览 */
.theme-preview-modern {
  background: #f5f5f5;
}

.sidebar-modern {
  width: 40px;
  background: #ffffff;
  border-right: 1px solid #e5e5e5;
}

.header-modern {
  height: 20px;
  background: #ffffff;
  border-bottom: 1px solid #e5e5e5;
}

.card-modern {
  background: #ffffff;
  border: 1px solid #e5e5e5;
  border-radius: 4px;
}

/* 暖色文艺预览 */
.theme-preview-warm {
  background: #faf6f1;
}

.sidebar-warm {
  width: 0;
}

.header-warm {
  height: 24px;
  background: #fffbf5;
  border-bottom: 1px solid #e8ddd0;
}

.card-warm {
  background: #fffbf5;
  border: 1px solid #e8ddd0;
  border-radius: 10px;
}

/* 自然清新预览 */
.theme-preview-natural {
  background: linear-gradient(135deg, #e8f5e9 0%, #e0f2f1 100%);
}

.sidebar-natural {
  width: 0;
}

.header-natural {
  height: 20px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(10px);
}

.card-natural {
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(200, 230, 210, 0.3);
  border-radius: 8px;
}

.preview-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.preview-cards {
  flex: 1;
  display: flex;
  gap: 8px;
  padding: 8px;
}

.preview-card {
  flex: 1;
}

.theme-info {
  padding: var(--spacing-md);
}

.theme-name {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
}

.theme-icon {
  font-size: 18px;
}

.theme-desc {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
}

.theme-layout {
  display: flex;
}

.layout-badge {
  display: inline-flex;
  padding: 2px 8px;
  background: var(--primary-alpha-10);
  color: var(--primary);
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.theme-check {
  position: absolute;
  top: var(--spacing-sm);
  right: var(--spacing-sm);
  width: 24px;
  height: 24px;
  border-radius: var(--radius-full);
  background: var(--primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

/* 响应式 */
@media (max-width: 768px) {
  .theme-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .scan-performance-settings {
    align-items: stretch;
    flex-direction: column;
    gap: var(--spacing-md);
  }

  .scan-thread-control {
    width: 100%;
  }

  .scan-thread-input {
    flex: 1;
    width: auto;
  }

  .directory-item {
    grid-template-columns: 1fr;
    align-items: flex-start;
    gap: var(--spacing-md);
  }

  .directory-actions {
    grid-column: 1;
    width: 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
  }

  .scan-progress-panel {
    grid-column: 1;
  }

  .directory-header-actions {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
