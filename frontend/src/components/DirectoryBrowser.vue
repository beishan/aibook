<template>
  <div class="directory-browser">
    <section class="browser-panel" aria-label="目录浏览">
      <div class="browser-toolbar">
        <nav class="breadcrumbs" aria-label="当前目录路径">
          <button type="button" @click="openRoot">根目录</button>
          <template v-for="(column, index) in columns.slice(1)" :key="column.path">
            <span aria-hidden="true">/</span>
            <button type="button" @click="openBreadcrumb(index + 1)">{{ column.name }}</button>
          </template>
        </nav>
        <button type="button" class="refresh-button" aria-label="刷新当前目录" @click="refreshCurrent">
          ↻
        </button>
      </div>
      <p class="browser-hint">选择文件夹查看下一级，横向滑动可查看各级目录。</p>
      <div ref="columnsContainer" class="columns" aria-label="目录分栏">
        <section v-for="(column, index) in columns" :key="column.path" class="column">
          <h3 :title="column.path">{{ column.name }}</h3>
          <div class="column-content">
            <p v-if="column.loading" class="column-message" role="status">正在读取…</p>
            <div v-else-if="column.error" class="column-message" role="alert">
              <p>无法读取目录，请检查挂载和读取权限。</p>
              <button type="button" class="retry-button" @click="loadColumn(index)">重试</button>
            </div>
            <p v-else-if="!column.items.length" class="column-message">没有可显示的子目录</p>
            <button
              v-for="item in column.items"
              v-else
              :key="item.path"
              type="button"
              class="folder-row"
              :class="{ active: selectedPath === item.path || columns[index + 1]?.path === item.path }"
              :aria-current="selectedPath === item.path ? 'true' : undefined"
              :title="item.path"
              @click="selectFolder(item, index)"
            >
              <span class="folder-icon" aria-hidden="true">📁</span>
              <span class="folder-name">{{ item.name }}</span>
              <span class="folder-arrow" aria-hidden="true">›</span>
            </button>
          </div>
        </section>
      </div>
    </section>

    <aside class="selection-panel" aria-label="目录选择摘要">
      <div class="selection-summary">
        <h3>已选择的目录</h3>
        <p v-if="selectedPath" class="selected-path">{{ selectedPath }}</p>
        <p v-else class="selection-placeholder">请从左侧选择目录</p>
      </div>
      <slot />
      <div class="manual-input">
        <label for="scan-manual-path">手动输入路径</label>
        <div class="input-group">
          <input
            id="scan-manual-path"
            v-model="manualPath"
            type="text"
            class="input"
            placeholder="例如：/scanfolder/有声书"
            @keyup.enter="handleManualInput"
          />
          <button type="button" class="btn" @click="handleManualInput">确定</button>
        </div>
        <p>请输入服务端可见的绝对路径。</p>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { message } from '@/utils/message'
import api from '@/utils/api'

interface DirectoryItem {
  name: string
  path: string
  isDirectory: boolean
  accessible: boolean
}

interface DirectoryColumn {
  name: string
  path: string
  items: DirectoryItem[]
  loading: boolean
  error: boolean
}

const emit = defineEmits<{ (event: 'select', path: string): void }>()
const selectedPath = ref('')
const manualPath = ref('')
const columns = ref<DirectoryColumn[]>([])
const columnsContainer = ref<HTMLElement | null>(null)

function createColumn(name: string, path: string): DirectoryColumn {
  return { name, path, items: [], loading: false, error: false }
}

async function loadColumn(index: number) {
  const column = columns.value[index]
  if (!column) return
  column.loading = true
  column.error = false
  try {
    const response = await api.get('/api/files/browse', { params: { path: column.path } })
    const items: DirectoryItem[] = Array.isArray(response.data)
      ? response.data
      : (response.data.directories || [])
    column.items = items.filter((item) => item.isDirectory && item.accessible !== false)
  } catch {
    column.items = []
    column.error = true
  } finally {
    column.loading = false
  }
}

async function selectFolder(item: DirectoryItem, index: number) {
  selectedPath.value = item.path
  manualPath.value = item.path
  emit('select', item.path)
  columns.value.splice(index + 1, columns.value.length, createColumn(item.name, item.path))
  await nextTick()
  columnsContainer.value?.scrollTo({
    left: columnsContainer.value.scrollWidth,
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
  })
  await loadColumn(index + 1)
}

function openRoot() {
  columns.value = [createColumn('根目录', '/')]
  selectedPath.value = '/'
  manualPath.value = '/'
  emit('select', '/')
  void loadColumn(0)
}

function openBreadcrumb(index: number) {
  const column = columns.value[index]
  if (!column) return
  columns.value.splice(index + 1)
  selectedPath.value = column.path
  manualPath.value = column.path
  emit('select', column.path)
  void loadColumn(index)
}

function refreshCurrent() {
  void loadColumn(columns.value.length - 1)
}

function handleManualInput() {
  const path = manualPath.value.trim()
  if (!path.startsWith('/')) {
    message.warning('请输入以 / 开头的绝对路径')
    return
  }
  selectedPath.value = path
  emit('select', path)
}

onMounted(() => {
  columns.value = [createColumn('根目录', '/')]
  void loadColumn(0)
})
</script>

<style scoped>
.directory-browser {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
}
.browser-panel, .selection-panel {
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
}
.browser-panel {
  overflow: hidden;
}
.browser-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
}
.breadcrumbs {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
  overflow-x: auto;
  white-space: nowrap;
}
.breadcrumbs button, .refresh-button, .retry-button {
  border: 0;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
  font: inherit;
}
.breadcrumbs button {
  padding: 5px;
  border-radius: 5px;
}
.refresh-button {
  font-size: 22px;
  line-height: 1;
}
.browser-hint {
  margin: 0;
  padding: 8px 12px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}
.columns {
  display: flex;
  min-height: 260px;
  height: min(350px, 42dvh);
  overflow-x: auto;
  overscroll-behavior: contain;
}
.column {
  flex: 0 0 190px;
  min-width: 0;
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}
.column h3 {
  margin: 0;
  padding: 10px 12px;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-bottom: 1px solid var(--border-color);
}
.column-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 5px;
  -webkit-overflow-scrolling: touch;
}
.folder-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  font: inherit;
}
.folder-row:hover, .folder-row.active {
  background: color-mix(in srgb, var(--primary) 14%, transparent);
  color: var(--primary);
}
.folder-row:focus-visible, .breadcrumbs button:focus-visible, .refresh-button:focus-visible, .retry-button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.folder-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.folder-arrow {
  font-size: 20px;
}
.column-message {
  padding: 12px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}
.selection-panel {
  padding: 18px;
}
.selection-summary {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}
.selection-summary h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.selected-path {
  margin: 0;
  color: var(--primary);
  overflow-wrap: anywhere;
  font-weight: 600;
}
.selection-placeholder, .manual-input p {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}
.manual-input {
  margin-top: 18px;
}
.manual-input label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
}
.input-group {
  display: flex;
  gap: 8px;
}
.input-group .input {
  min-width: 0;
  flex: 1;
}
.manual-input p {
  margin-bottom: 0;
}
@media (max-width: 760px) {
  .directory-browser {
    grid-template-columns: 1fr;
  }
  .columns {
    min-height: 210px;
    height: 30dvh;
  }
  .selection-panel {
    padding: 14px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .columns {
    scroll-behavior: auto;
  }
}
</style>
