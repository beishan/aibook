<template>
  <div class="settings-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">系统设置</h1>
        <p class="page-subtitle">管理您的书库配置</p>
      </div>
    </div>

    <div class="settings-layout">
      <nav class="settings-nav" aria-label="设置导航">
        <div v-for="group in tabGroups" :key="group.label" class="settings-nav-group">
          <div class="settings-nav-title">{{ group.label }}</div>
          <button
            v-for="tab in group.items"
            :key="tab.key"
            type="button"
            class="settings-nav-item"
            :class="{ active: activeTab === tab.key }"
            :aria-current="activeTab === tab.key ? 'page' : undefined"
            @click="selectTab(tab.key)"
          >
            <span class="settings-nav-icon">{{ tab.icon }}</span>
            <span>{{ tab.label }}</span>
          </button>
        </div>
      </nav>

      <main class="settings-content">

    <!-- 当前用户设置 -->
    <div v-if="activeTab === 'profile'" class="tab-content">
      <CurrentUserSettingsPanel />
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
          <span>📁 书籍扫描目录配置</span>
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
                <span class="tag" :class="row.libraryVisible !== false ? 'tag-success' : 'tag-info'">
                  {{ row.libraryVisible !== false ? '书库显示' : '书库隐藏' }}
                </span>
                <span class="meta-text">{{ row.bookCount }} 本书</span>
                <span class="meta-text">
                  默认分类：{{ row.defaultCategoryName || '未分类' }}
                </span>
                <span class="meta-text">{{ row.lastScanTime ? formatTime(row.lastScanTime) : '未扫描' }}</span>
              </div>
            </div>
            <div class="directory-actions">
              <el-select
                class="directory-category-select"
                :model-value="row.defaultCategoryId || ''"
                placeholder="新书不分类"
                @change="value => handleDefaultCategoryChange(row, value)"
              >
                <el-option label="新书不分类" value="" />
                <el-option
                  v-for="category in categoryStore.flatTree"
                  :key="category.id"
                  :label="category.path"
                  :value="category.id"
                />
              </el-select>
              <button
                class="btn btn-text"
                :disabled="row._scanning || !row.enabled"
                :title="row.enabled ? '立即扫描' : '请先启用该目录'"
                @click="handleScan(row)"
              >
                {{ row._scanning ? `${row._scanProgress?.progress || 0}%` : '立即扫描' }}
              </button>
              <button
                class="btn btn-text"
                :disabled="row._toggling"
                @click="handleToggle(row)"
              >
                {{ row._toggling ? '处理中...' : row.enabled ? '禁用' : '启用' }}
              </button>
              <button
                class="btn btn-text"
                :disabled="row._visibilityUpdating"
                @click="handleLibraryVisibility(row)"
              >
                {{ row._visibilityUpdating
                  ? '处理中...'
                  : row.libraryVisible !== false ? '书库隐藏' : '书库显示' }}
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

    <!-- 网站图标（仅管理员） -->
    <div v-if="isAdmin && activeTab === 'favicon'" class="tab-content">
      <SiteFaviconSettingsPanel />
    </div>

    <!-- 系统信息 -->
    <div v-show="activeTab === 'info'" class="tab-content">
      <div class="card glass">
        <div class="card-header">
          <span>ℹ️ 系统信息</span>
          <button
            v-if="isAdmin"
            class="btn system-resource-refresh"
            :disabled="resourceLoading"
            @click="loadSystemResources(true)"
          >
            <span :class="{ 'refresh-spinning': resourceLoading }">↻</span>
            <span>{{ resourceLoading ? '采集中...' : '刷新' }}</span>
          </button>
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
          <div class="info-item list-item">
            <span class="info-label">当前运行总时长</span>
            <span class="info-value">{{ formattedUptime }}</span>
          </div>
          <div class="info-item list-item">
            <span class="info-label">最近启动时间</span>
            <span class="info-value">
              {{ systemRuntime?.startedAt ? formatChinaDateTime(systemRuntime.startedAt) : '加载中...' }}
            </span>
          </div>
        </div>

        <section v-if="isAdmin" class="system-resources" aria-label="系统资源占用">
          <div class="system-resources-heading">
            <div>
              <div class="system-resources-title">
                <h2>系统资源</h2>
                <span v-if="systemResources" class="tag" :class="resourceStatusClass">
                  {{ resourceStatusText }}
                </span>
              </div>
              <p>{{ resourceScopeDescription }}</p>
            </div>
            <span class="resource-collected-at">
              最近采集：{{ systemResources?.collectedAt ? formatChinaDateTime(systemResources.collectedAt) : '--' }}
            </span>
          </div>

          <div class="resource-grid">
            <article class="resource-card">
              <div class="resource-card-title"><span>CPU</span><span>{{ formatPercent(systemResources?.cpu.usagePercent) }}</span></div>
              <el-progress
                :percentage="progressPercent(systemResources?.cpu.usagePercent)"
                :show-text="false"
                :color="progressColor(systemResources?.cpu.usagePercent)"
              />
              <p>{{ resourceMetricDescription(systemResources?.cpu, 'CPU 首次采样将在下一次刷新后显示') }}</p>
            </article>
            <article class="resource-card">
              <div class="resource-card-title"><span>内存</span><span>{{ formatPercent(systemResources?.memory.usagePercent) }}</span></div>
              <el-progress
                :percentage="progressPercent(systemResources?.memory.usagePercent)"
                :show-text="false"
                :color="progressColor(systemResources?.memory.usagePercent)"
              />
              <p>{{ formatUsage(systemResources?.memory) }}</p>
            </article>
            <article class="resource-card">
              <div class="resource-card-title"><span>磁盘</span><span>{{ formatPercent(systemResources?.disk.usagePercent) }}</span></div>
              <el-progress
                :percentage="progressPercent(systemResources?.disk.usagePercent)"
                :show-text="false"
                :color="progressColor(systemResources?.disk.usagePercent)"
              />
              <p>{{ formatUsage(systemResources?.disk) }}</p>
            </article>
          </div>

          <div v-if="systemResources?.disks?.length" class="disk-volumes">
            <span v-for="volume in systemResources.disks" :key="volume.label">
              {{ volume.label }}：{{ formatBytes(volume.usedBytes) }} / {{ formatBytes(volume.totalBytes) }}
            </span>
          </div>
        </section>
      </div>
    </div>
      </main>
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
                <el-select
                  v-model="selectedDefaultCategoryId"
                  class="default-category-select"
                  placeholder="暂不分类"
                >
                  <el-option label="暂不分类" value="" />
                  <el-option
                    v-for="category in categoryStore.flatTree"
                    :key="category.id"
                    :label="category.path"
                    :value="String(category.id)"
                  />
                </el-select>
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
import CurrentUserSettingsPanel from '@/components/CurrentUserSettingsPanel.vue'
import SiteFaviconSettingsPanel from '@/components/SiteFaviconSettingsPanel.vue'
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

interface SystemRuntime {
  startedAt: string
  uptimeMillis: number
}

type ResourceStatus = 'AVAILABLE' | 'PARTIAL' | 'UNAVAILABLE'
type ResourceScope = 'CONTAINER' | 'HOST' | 'UNKNOWN'

interface ResourceMetric {
  usagePercent: number | null
  usedBytes: number | null
  totalBytes: number | null
  status: ResourceStatus
}

interface SystemResources {
  scope: ResourceScope
  status: ResourceStatus
  collectedAt: string
  cpu: ResourceMetric
  memory: ResourceMetric
  disk: ResourceMetric
  disks: Array<{
    label: string
    usedBytes: number | null
    totalBytes: number | null
    status: ResourceStatus
  }>
}

const systemRuntime = ref<SystemRuntime | null>(null)
const runtimeLoadedAt = ref(0)
const runtimeClock = ref(Date.now())
let runtimeClockTimer: ReturnType<typeof setInterval> | null = null
const systemResources = ref<SystemResources | null>(null)
const resourceLoading = ref(false)
let resourceRefreshTimer: ReturnType<typeof setInterval> | null = null
let resourceRequest: Promise<void> | null = null
let resourceErrorShown = false

const formatDuration = (milliseconds: number) => {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000))
  const totalDays = Math.floor(totalSeconds / 86400)
  const years = Math.floor(totalDays / 365)
  const remainingAfterYears = totalDays % 365
  const months = Math.floor(remainingAfterYears / 30)
  const days = remainingAfterYears % 30
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${years}年 ${months}月 ${days}日 ${hours}时 ${minutes}分 ${seconds}秒`
}

const formattedUptime = computed(() => {
  if (!systemRuntime.value) return '加载中...'
  const elapsedAfterLoad = runtimeClock.value - runtimeLoadedAt.value
  return formatDuration(systemRuntime.value.uptimeMillis + elapsedAfterLoad)
})

const loadSystemRuntime = async () => {
  try {
    const { data } = await api.get<SystemRuntime>('/api/system/runtime')
    systemRuntime.value = data
    runtimeLoadedAt.value = Date.now()
    runtimeClock.value = runtimeLoadedAt.value
  } catch (error: any) {
    message.error(error.response?.data?.message || '系统运行信息加载失败')
  }
}

const loadSystemResources = async (manual = false) => {
  if (!isAdmin.value || resourceRequest) return resourceRequest
  resourceLoading.value = true
  resourceRequest = api.get<SystemResources>('/api/system/resources')
    .then(({ data }) => {
      systemResources.value = data
      resourceErrorShown = false
    })
    .catch((error: any) => {
      if (manual || !resourceErrorShown) {
        message.error(error.response?.data?.message || '系统资源信息加载失败')
        resourceErrorShown = true
      }
    })
    .finally(() => {
      resourceLoading.value = false
      resourceRequest = null
    })
  return resourceRequest
}

const formatBytes = (value: number | null | undefined) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return '--'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let amount = Math.max(0, value)
  let unit = 0
  while (amount >= 1024 && unit < units.length - 1) {
    amount /= 1024
    unit += 1
  }
  return `${amount >= 10 || unit === 0 ? amount.toFixed(0) : amount.toFixed(1)} ${units[unit]}`
}

const formatPercent = (value: number | null | undefined) =>
  value === null || value === undefined || !Number.isFinite(value) ? '--' : `${value.toFixed(1)}%`

const progressPercent = (value: number | null | undefined) =>
  value === null || value === undefined || !Number.isFinite(value) ? 0 : Math.min(100, Math.max(0, value))

const progressColor = (value: number | null | undefined) => {
  if (value === null || value === undefined) return 'var(--border-color)'
  if (value >= 90) return 'var(--danger)'
  if (value >= 75) return 'var(--warning)'
  return 'var(--success)'
}

const formatUsage = (metric: ResourceMetric | undefined) =>
  metric?.usedBytes === null || metric?.usedBytes === undefined || metric?.totalBytes === null || metric?.totalBytes === undefined
    ? '--'
    : `${formatBytes(metric.usedBytes)} / ${formatBytes(metric.totalBytes)}`

const resourceMetricDescription = (metric: ResourceMetric | undefined, emptyText: string) =>
  metric?.usagePercent === null || metric?.usagePercent === undefined ? emptyText : '当前 CPU 使用率'

const resourceScopeDescription = computed(() => {
  switch (systemResources.value?.scope) {
    case 'CONTAINER': return '容器范围：CPU 与内存优先按本容器 cgroup 限额统计，已用内存不含可回收文件缓存。'
    case 'HOST': return '宿主机范围：显示应用可见的系统资源，已用内存按系统可用量计算。'
    default: return '可见范围未知：显示应用当前可读取的资源信息。'
  }
})

const resourceStatusText = computed(() => {
  switch (systemResources.value?.status) {
    case 'AVAILABLE': return '指标正常'
    case 'PARTIAL': return '部分指标不可用'
    case 'UNAVAILABLE': return '指标不可用'
    default: return '等待采集'
  }
})

const resourceStatusClass = computed(() =>
  systemResources.value?.status === 'AVAILABLE' ? 'tag-success' : 'tag-info'
)

const refreshResourcesWhenVisible = () => {
  if (activeTab.value === 'info' && document.visibilityState === 'visible') {
    void loadSystemResources()
  }
}

const updateResourceRefreshTimer = () => {
  if (resourceRefreshTimer) {
    clearInterval(resourceRefreshTimer)
    resourceRefreshTimer = null
  }
  if (isAdmin.value && activeTab.value === 'info') {
    resourceRefreshTimer = setInterval(refreshResourcesWhenVisible, 10_000)
  }
}

const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const tabGroups = computed(() => [
  {
    label: '个人',
    items: [{ key: 'profile', label: '个人设置', icon: '👤' }],
  },
  {
    label: '外观',
    items: [
      { key: 'theme', label: '主题风格', icon: '🎨' },
      { key: 'fonts', label: '字体管理', icon: '🔤' },
    ],
  },
  {
    label: '书库',
    items: [
      { key: 'directories', label: '扫描目录', icon: '📂' },
      { key: 'scheduler', label: '定时任务', icon: '⏰' },
    ],
  },
  {
    label: '连接',
    items: [{ key: 'connections', label: 'OPDS 连接', icon: '🔗' }],
  },
  {
    label: '管理',
    items: [
      ...(isAdmin.value ? [{ key: 'users', label: '用户管理', icon: '👥' }] : []),
      { key: 'logs', label: '操作日志', icon: '📋' },
    ],
  },
  {
    label: '系统',
    items: [
      ...(isAdmin.value ? [{ key: 'favicon', label: '网站图标', icon: '🌐' }] : []),
      { key: 'info', label: '系统信息', icon: 'ℹ️' },
    ],
  },
])
const tabs = computed(() => tabGroups.value.flatMap(group => group.items))

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
watch([activeTab, isAdmin], ([tab, admin]) => {
  updateResourceRefreshTimer()
  if (tab === 'info' && admin) void loadSystemResources()
})
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

const handleDefaultCategoryChange = async (row: any, value: number | string) => {
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
  row._toggling = true
  try {
    const { data } = await api.put(`/api/scan-directories/${row.id}/toggle`)
    Object.assign(row, data)
    message.success(data.enabled ? '已启用' : '已禁用')
  } catch (error: any) {
    message.error(error.response?.data?.message || '操作失败')
  } finally {
    row._toggling = false
  }
}

const handleLibraryVisibility = async (row: any) => {
  row._visibilityUpdating = true
  const visible = row.libraryVisible === false
  try {
    const { data } = await api.put(`/api/scan-directories/${row.id}/library-visibility`, {
      visible,
    })
    Object.assign(row, data)
    message.success(visible ? '该目录书籍已在书库显示' : '该目录书籍已从书库隐藏')
  } catch (error: any) {
    message.error(error.response?.data?.message || '书库显示状态更新失败')
  } finally {
    row._visibilityUpdating = false
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
  await loadSystemRuntime()
  runtimeClockTimer = setInterval(() => {
    runtimeClock.value = Date.now()
  }, 1000)
  document.addEventListener('visibilitychange', refreshResourcesWhenVisible)
  updateResourceRefreshTimer()
  if (activeTab.value === 'info' && isAdmin.value) await loadSystemResources()
})

onUnmounted(() => {
  scanPollTimers.forEach(timer => clearTimeout(timer))
  scanPollTimers.clear()
  if (runtimeClockTimer) clearInterval(runtimeClockTimer)
  if (resourceRefreshTimer) clearInterval(resourceRefreshTimer)
  document.removeEventListener('visibilitychange', refreshResourcesWhenVisible)
})
</script>

<style scoped>
.settings-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

.settings-layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  align-items: start;
  gap: var(--spacing-xl);
}

.settings-nav {
  position: sticky;
  top: var(--spacing-lg);
  padding: var(--spacing-sm);
  background: var(--surface-card);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
}

.settings-nav-group + .settings-nav-group {
  margin-top: var(--spacing-sm);
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--border-color-light);
}

.settings-nav-title {
  padding: var(--spacing-xs) var(--spacing-md);
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.settings-nav-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 10px var(--spacing-md);
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
  transition: all var(--transition-fast);
}

.settings-nav-item:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.settings-nav-item.active {
  background: var(--primary-gradient);
  box-shadow: var(--shadow-md);
  color: white;
}

.settings-nav-icon {
  width: 20px;
  flex: 0 0 20px;
  text-align: center;
}

.settings-content {
  min-width: 0;
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
  width: 180px;
  max-width: 180px;
}

.default-category-field {
  margin-top: var(--spacing-lg);
}

.default-category-select {
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

.system-resource-refresh {
  margin-left: auto;
}

.refresh-spinning {
  display: inline-block;
  animation: spin 0.8s linear infinite;
}

.system-resources {
  padding: var(--spacing-lg);
  border-top: 1px solid var(--border-color-light);
}

.system-resources-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-md);
}

.system-resources-heading h2 {
  margin: 0 0 var(--spacing-xs);
  color: var(--text-primary);
  font-size: var(--font-size-base);
}

.system-resources-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.system-resources-title h2 {
  margin-bottom: 0;
}

.system-resources-heading p,
.resource-collected-at,
.resource-card p,
.disk-volumes {
  margin: 0;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.resource-collected-at {
  flex: 0 0 auto;
  text-align: right;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-md);
}

.resource-card {
  min-width: 0;
  padding: var(--spacing-md);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  background: var(--surface-hover);
}

.resource-card-title {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.resource-card p {
  min-height: 20px;
  margin-top: var(--spacing-sm);
}

.disk-volumes {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs) var(--spacing-md);
  margin-top: var(--spacing-md);
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
@media (max-width: 900px) {
  .settings-layout {
    display: block;
  }

  .settings-nav {
    position: static;
    display: flex;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-xl);
    overflow-x: auto;
    scrollbar-width: thin;
  }

  .settings-nav-group {
    display: contents;
  }

  .settings-nav-title {
    display: none;
  }

  .settings-nav-item {
    width: auto;
    flex: 0 0 auto;
    padding: 10px 14px;
  }
}

@media (max-width: 768px) {
  .theme-grid {
    grid-template-columns: 1fr;
  }

  .resource-grid {
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

  .system-resources-heading {
    flex-direction: column;
    gap: var(--spacing-xs);
  }

  .resource-collected-at {
    text-align: left;
  }
}
</style>
