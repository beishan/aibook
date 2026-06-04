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
        @click="activeTab = tab.key"
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

    <!-- 扫描目录 -->
    <div v-show="activeTab === 'directories'" class="tab-content">
      <div class="card glass">
        <div class="card-header">
          <span>📁 监控目录配置</span>
          <button class="btn btn-primary" @click="showAddDialog = true">
            <span>➕</span>
            <span>添加目录</span>
          </button>
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
                <span class="meta-text">{{ row.lastScanTime ? formatTime(row.lastScanTime) : '未扫描' }}</span>
              </div>
            </div>
            <div class="directory-actions">
              <button class="btn btn-text" @click="handleScan(row)" :disabled="row._scanning">
                {{ row._scanning ? '扫描中...' : '立即扫描' }}
              </button>
              <button class="btn btn-text" @click="handleToggle(row)">
                {{ row.enabled ? '禁用' : '启用' }}
              </button>
              <button class="btn btn-text btn-danger" @click="handleRemove(row)">删除</button>
            </div>
          </div>
        </div>
      </div>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, confirm } from '@/utils/message'
import api from '@/utils/api'
import DirectoryBrowser from '@/components/DirectoryBrowser.vue'
import { useThemeStore } from '@/stores/theme'
import { THEMES, type ThemeId } from '@/types/theme'

const themeStore = useThemeStore()
const themes = THEMES

const tabs = [
  { key: 'theme', label: '主题风格', icon: '🎨' },
  { key: 'directories', label: '扫描目录', icon: '📂' },
  { key: 'scheduler', label: '定时任务', icon: '⏰' },
  { key: 'info', label: '系统信息', icon: 'ℹ️' },
]

const getLayoutName = (layout: string) => {
  const names: Record<string, string> = {
    sidebar: '侧边栏',
    topbar: '顶部栏',
    dock: '底部 Dock'
  }
  return names[layout] || layout
}

const handleThemeChange = (id: ThemeId) => {
  themeStore.setTheme(id)
  message.success(`已切换到「${themes.find(t => t.id === id)?.name}」主题`)
}

const activeTab = ref('directories')
const loading = ref(false)
const adding = ref(false)
const showAddDialog = ref(false)
const directories = ref<any[]>([])

const newDir = reactive({ path: '' })
const selectedPath = ref('')

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

const loadDirectories = async () => {
  loading.value = true
  try {
    const res = await api.get('/api/scan-directories')
    directories.value = res.data.map((d: any) => ({ ...d, _scanning: false }))
  } catch (error) {
    console.error('Failed to load directories:', error)
  } finally {
    loading.value = false
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
    await api.post('/api/scan-directories', { path: pathToAdd })
    message.success('添加成功')
    newDir.path = ''
    selectedPath.value = ''
    showAddDialog.value = false
    await loadDirectories()
  } catch (error: any) {
    message.error(error.response?.data?.message || '添加失败')
  } finally {
    adding.value = false
  }
}

const handleScan = async (row: any) => {
  row._scanning = true
  try {
    const res = await api.post(`/api/scan-directories/${row.id}/scan`)
    if (res.data.success) {
      message.success(`扫描完成，找到 ${res.data.bookCount} 本书籍文件`)
      await loadDirectories()
    } else {
      message.error(res.data.message)
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '扫描失败')
  } finally {
    row._scanning = false
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
  if (!timeStr) return ''
  return new Date(timeStr).toLocaleString('zh-CN')
}

onMounted(loadDirectories)
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
  display: flex;
  justify-content: space-between;
  align-items: center;
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
  gap: var(--spacing-md);
}

.meta-text {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.directory-actions {
  display: flex;
  gap: var(--spacing-sm);
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
  .directory-item {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-md);
  }

  .directory-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
