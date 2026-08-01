<template>
  <div class="font-management">
    <el-tabs v-model="activeSection" class="font-section-tabs">
      <el-tab-pane label="字体使用" name="usage">
        <div class="card glass">
          <div class="card-header">
            <div>
              <strong>字体使用</strong>
              <p>系统界面与阅读正文可以使用不同字体。</p>
            </div>
          </div>
          <div class="font-preference-grid">
            <label class="preference-field">
              <span>系统界面字体</span>
              <div class="font-selector-row">
                <el-select
                  class="font-select"
                  filterable
                  placeholder="系统默认"
                  :model-value="preferencesStore.uiFontId ?? ''"
                  @change="handleUiFontChange"
                  @visible-change="preloadFontOptions"
                >
                  <el-option label="系统默认" value="">
                    <div class="font-option">
                      <span class="font-option-name">系统默认</span>
                      <span class="font-option-sample">默认效果 Aa 中文</span>
                    </div>
                  </el-option>
                  <el-option
                    v-for="font in fontStore.availableFonts"
                    :key="font.id"
                    :label="font.displayName"
                    :value="font.id"
                  >
                    <div class="font-option" @mouseenter="loadPreview(font)">
                      <span class="font-option-name">{{ font.displayName }}</span>
                      <span
                        class="font-option-sample"
                        :style="{ fontFamily: fontStore.cssFamily(font.id) }"
                      >
                        字体效果 Aa 中文
                      </span>
                    </div>
                  </el-option>
                </el-select>
                <div
                  class="current-font-preview"
                  :style="previewStyle(selectedUiFont)"
                  @mouseenter="loadSelectedPreview(selectedUiFont)"
                >
                  <span>当前字体效果</span>
                  <strong>汗牛充栋 · Aa 123</strong>
                </div>
              </div>
              <small>应用于导航、书库、设置和详情页面。</small>
            </label>
            <label class="preference-field">
              <span>默认阅读字体</span>
              <div class="font-selector-row">
                <el-select
                  class="font-select"
                  filterable
                  placeholder="阅读器默认"
                  :model-value="preferencesStore.readerFontId ?? ''"
                  @change="handleReaderFontChange"
                  @visible-change="preloadFontOptions"
                >
                  <el-option label="阅读器默认" value="">
                    <div class="font-option">
                      <span class="font-option-name">阅读器默认</span>
                      <span class="font-option-sample reader-default-sample">
                        默认效果 Aa 中文
                      </span>
                    </div>
                  </el-option>
                  <el-option
                    v-for="font in fontStore.availableFonts"
                    :key="font.id"
                    :label="font.displayName"
                    :value="font.id"
                  >
                    <div class="font-option" @mouseenter="loadPreview(font)">
                      <span class="font-option-name">{{ font.displayName }}</span>
                      <span
                        class="font-option-sample"
                        :style="{ fontFamily: fontStore.cssFamily(font.id) }"
                      >
                        字体效果 Aa 中文
                      </span>
                    </div>
                  </el-option>
                </el-select>
                <div
                  class="current-font-preview reader-preview"
                  :style="previewStyle(selectedReaderFont)"
                  @mouseenter="loadSelectedPreview(selectedReaderFont)"
                >
                  <span>当前字体效果</span>
                  <strong>书山有路勤为径 · Aa 123</strong>
                </div>
              </div>
              <small>应用于 TXT、Markdown 和 EPUB 正文。</small>
            </label>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="字体扫描配置" name="scan">
        <div class="card glass">
          <div class="card-header">
            <div>
              <strong>字体扫描配置</strong>
              <p>从 Jenkins 映射到 /fontfolder 的目录中选择扫描范围。</p>
            </div>
            <div class="header-actions">
              <button class="btn" @click="openDirectoryDialog">新增目录</button>
              <button class="btn btn-primary" :disabled="fontStore.scanning" @click="handleScan">
                {{ fontStore.scanning ? '扫描中…' : '扫描字体' }}
              </button>
            </div>
          </div>
          <div v-if="fontStore.directories.length" class="font-directory-list">
            <div
              v-for="directory in fontStore.directories"
              :key="directory.id"
              class="font-directory-row"
            >
              <div>
                <div class="directory-path">{{ directory.path }}</div>
                <small>
                  {{ directory.lastScanAt ? `上次扫描：${formatTime(directory.lastScanAt)}` : '尚未扫描' }}
                </small>
                <small v-if="directory.lastError" class="directory-error">
                  {{ directory.lastError }}
                </small>
              </div>
              <button class="btn btn-text btn-danger" @click="handleRemoveDirectory(directory)">
                删除配置
              </button>
            </div>
          </div>
          <div v-else class="inline-empty">暂无字体扫描目录，请点击“新增目录”进行配置。</div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="字体列表" name="list">
        <div class="card glass">
          <div class="card-header">
            <div>
              <strong>字体列表</strong>
              <p>支持 TTF、OTF、WOFF、WOFF2；上传字体会保存到持久卷。</p>
            </div>
            <label class="btn btn-primary upload-button">
              <input
                type="file"
                accept=".ttf,.otf,.woff,.woff2,font/ttf,font/otf,font/woff,font/woff2"
                multiple
                @change="handleUpload"
              />
              {{ uploading ? '上传中…' : '上传字体' }}
            </label>
          </div>

          <div v-if="fontStore.loading" class="inline-empty">正在加载字体列表…</div>
          <div v-else-if="!fontStore.fonts.length" class="inline-empty">
            还没有字体，请上传字体或扫描字体目录。
          </div>
          <template v-else>
            <div class="font-list">
              <article
                v-for="font in pagedFonts"
                :key="font.id"
                class="font-row"
                :class="{ unavailable: !font.available || !font.enabled }"
                @mouseenter="loadPreview(font)"
              >
                <div class="font-preview" :style="{ fontFamily: fontStore.cssFamily(font.id) }">
                  汗牛充栋 · 阅读让思想自由生长
                </div>
                <div class="font-detail">
                  <div class="font-name-line">
                    <strong>{{ font.displayName }}</strong>
                    <span
                      class="tag"
                      :class="font.enabled && font.available ? 'tag-success' : 'tag-info'"
                    >
                      {{ !font.available ? '文件不可用' : font.enabled ? '已启用' : '已停用' }}
                    </span>
                    <span class="tag tag-info">
                      {{ font.sourceType === 'UPLOADED' ? '上传' : '目录扫描' }}
                    </span>
                  </div>
                  <div class="font-meta">
                    <span>{{ font.fontFamily || '未识别字体族' }}</span>
                    <span>{{ font.format.toUpperCase() }}</span>
                    <span v-if="font.fileSize">{{ formatFileSize(font.fileSize) }}</span>
                    <span v-if="font.filePath" :title="font.filePath">{{ font.filePath }}</span>
                  </div>
                </div>
                <div class="font-actions">
                  <button class="btn btn-text" @click="handleRename(font)">重命名</button>
                  <button class="btn btn-text" :disabled="!font.available" @click="handleToggle(font)">
                    {{ font.enabled ? '停用' : '启用' }}
                  </button>
                  <button class="btn btn-text btn-danger" @click="handleRemoveFont(font)">删除</button>
                </div>
              </article>
            </div>
            <div class="font-pagination">
              <el-pagination
                v-model:current-page="fontPage"
                v-model:page-size="fontPageSize"
                :page-sizes="fontPageSizeOptions"
                :total="fontStore.fonts.length"
                layout="total, sizes, prev, pager, next"
              />
            </div>
          </template>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="directoryDialogVisible"
      title="新增字体扫描目录"
      width="min(640px, 92vw)"
      destroy-on-close
    >
      <p class="dialog-hint">浏览字体根目录，选择要加入扫描配置的目录。</p>
      <div class="directory-tree-panel">
        <el-tree
          :key="directoryTreeKey"
          node-key="path"
          lazy
          highlight-current
          :load="loadDirectoryTree"
          :props="{ label: 'name', isLeaf: 'leaf' }"
          @node-click="handleDirectorySelect"
        >
          <template #default="{ data }">
            <span class="directory-tree-node">
              <span>📁 {{ data.name }}</span>
              <el-tag
                v-if="configuredDirectoryPaths.has(data.path)"
                size="small"
                type="success"
                effect="plain"
              >
                已配置
              </el-tag>
            </span>
          </template>
        </el-tree>
      </div>
      <div class="selected-directory">
        <span>已选择</span>
        <strong>{{ selectedDirectory?.path || '请选择一个目录' }}</strong>
      </div>
      <template #footer>
        <el-button @click="directoryDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="addingDirectory"
          :disabled="!canAddSelectedDirectory"
          @click="handleAddDirectory"
        >
          添加扫描目录
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { confirm, message } from '@/utils/message'
import { formatChinaDateTime } from '@/utils/dateTime'
import { usePreferencesStore } from '@/stores/preferences'
import {
  useFontStore,
  type FontAsset,
  type FontDirectoryNode,
  type FontScanDirectory,
} from '@/stores/font'

const preferencesStore = usePreferencesStore()
const fontStore = useFontStore()
const activeSection = ref('usage')
const addingDirectory = ref(false)
const uploading = ref(false)
const directoryDialogVisible = ref(false)
const directoryTreeKey = ref(0)
const selectedDirectory = ref<FontDirectoryNode | null>(null)
const fontPage = ref(1)
const fontPageSize = ref(10)
const fontPageSizeOptions = [10, 20, 50]
let fontOptionPreviewTask: Promise<void> | null = null

const configuredDirectoryPaths = computed(
  () => new Set(fontStore.directories.map(directory => directory.path))
)
const canAddSelectedDirectory = computed(() =>
  selectedDirectory.value != null
  && !configuredDirectoryPaths.value.has(selectedDirectory.value.path)
)
const pagedFonts = computed(() => {
  const start = (fontPage.value - 1) * fontPageSize.value
  return fontStore.fonts.slice(start, start + fontPageSize.value)
})
const selectedUiFont = computed(() => fontStore.getFont(preferencesStore.uiFontId))
const selectedReaderFont = computed(() => fontStore.getFont(preferencesStore.readerFontId))

const readSelectedId = (value: number | string) => {
  return value ? Number(value) : null
}

const handleUiFontChange = async (value: number | string) => {
  const id = readSelectedId(value)
  if (id == null) {
    preferencesStore.setUiFontId(null)
    message.success('已恢复系统默认字体')
    return
  }
  const font = fontStore.getFont(id)
  try {
    await fontStore.loadFont(id)
    preferencesStore.setUiFontId(id)
    message.success('系统字体已更新')
  } catch {
    // 不支持的字体会由字体仓库标记为不可用并从选项中移除。
  }
}

const handleReaderFontChange = async (value: number | string) => {
  const id = readSelectedId(value)
  if (id == null) {
    preferencesStore.setReaderFontId(null)
    message.success('已恢复阅读器默认字体')
    return
  }
  const font = fontStore.getFont(id)
  try {
    await fontStore.loadFont(id)
    preferencesStore.setReaderFontId(id)
    message.success('默认阅读字体已更新')
  } catch {
    // 不支持的字体会由字体仓库标记为不可用并从选项中移除。
  }
}

const loadSelectedPreview = async (font: FontAsset | undefined) => {
  if (!font?.enabled || !font.available) return
  try {
    await fontStore.loadFont(font)
  } catch {
    if (preferencesStore.uiFontId === font.id) preferencesStore.setUiFontId(null)
    if (preferencesStore.readerFontId === font.id) preferencesStore.setReaderFontId(null)
  }
}

const previewStyle = (font: FontAsset | undefined) => ({
  fontFamily: font ? fontStore.cssFamily(font.id) : undefined,
})

const preloadFontOptions = (visible: boolean) => {
  if (!visible || fontOptionPreviewTask) return
  fontOptionPreviewTask = (async () => {
    for (const font of fontStore.availableFonts) {
      try {
        await fontStore.loadFont(font)
      } catch {
        // 单个字体不可预览时继续加载其他选项。
      }
    }
  })().finally(() => {
    fontOptionPreviewTask = null
  })
}

const handleAddDirectory = async () => {
  if (!canAddSelectedDirectory.value || !selectedDirectory.value) return
  addingDirectory.value = true
  try {
    await fontStore.addDirectory(selectedDirectory.value.path)
    message.success('字体扫描目录已添加')
    directoryDialogVisible.value = false
  } catch (error: any) {
    message.error(error.response?.data?.message || '添加字体目录失败')
  } finally {
    addingDirectory.value = false
  }
}

const openDirectoryDialog = () => {
  selectedDirectory.value = null
  directoryTreeKey.value += 1
  directoryDialogVisible.value = true
}

const loadDirectoryTree = async (
  node: any,
  resolve: (nodes: FontDirectoryNode[]) => void
) => {
  try {
    const path = node.level === 0 ? undefined : node.data.path
    resolve(await fontStore.browseDirectories(path))
  } catch (error: any) {
    resolve([])
    message.error(error.response?.data?.message || '字体目录加载失败')
  }
}

const handleDirectorySelect = (directory: FontDirectoryNode) => {
  selectedDirectory.value = directory
}

const handleRemoveDirectory = async (directory: FontScanDirectory) => {
  if (!await confirm(`确定删除字体扫描目录配置 ${directory.path} 吗？\n不会删除 NAS 原文件。`)) {
    return
  }
  try {
    await fontStore.removeDirectory(directory.id)
    if (
      preferencesStore.uiFontId != null
      && !fontStore.getFont(preferencesStore.uiFontId)
    ) {
      preferencesStore.setUiFontId(null)
    }
    if (
      preferencesStore.readerFontId != null
      && !fontStore.getFont(preferencesStore.readerFontId)
    ) {
      preferencesStore.setReaderFontId(null)
    }
    message.success('目录配置已删除')
  } catch (error: any) {
    message.error(error.response?.data?.message || '删除目录失败')
  }
}

const handleScan = async () => {
  try {
    const result = await fontStore.scanFonts()
    const added = result?.added ?? result?.newFonts ?? 0
    const updated = result?.updated ?? result?.updatedFonts ?? 0
    const failed = result?.failed ?? result?.failedFonts ?? 0
    message.success(`字体扫描完成：新增 ${added}，更新 ${updated}，失败 ${failed}`)
  } catch (error: any) {
    message.error(error.response?.data?.message || '字体扫描失败')
  }
}

const handleUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length) return

  uploading.value = true
  let succeeded = 0
  try {
    for (const file of files) {
      try {
        await fontStore.uploadFont(file)
        succeeded += 1
      } catch (error: any) {
        message.error(`${file.name}：${error.response?.data?.message || '上传失败'}`)
      }
    }
    if (succeeded) message.success(`成功上传 ${succeeded} 个字体`)
  } finally {
    uploading.value = false
    input.value = ''
  }
}

const loadPreview = (font: FontAsset) => {
  if (font.enabled && font.available) {
    void fontStore.loadFont(font).catch(() => undefined)
  }
}

const handleRename = async (font: FontAsset) => {
  try {
    const result = await ElMessageBox.prompt('请输入字体显示名称', '重命名字体', {
      inputValue: font.displayName,
      inputPattern: /\S+/,
      inputErrorMessage: '字体名称不能为空',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    await fontStore.updateFont(font.id, { displayName: result.value.trim() })
    message.success('字体名称已更新')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    message.error(error.response?.data?.message || '字体名称更新失败')
  }
}

const handleToggle = async (font: FontAsset) => {
  try {
    await fontStore.updateFont(font.id, { enabled: !font.enabled })
    if (font.enabled) {
      if (preferencesStore.uiFontId === font.id) preferencesStore.setUiFontId(null)
      if (preferencesStore.readerFontId === font.id) preferencesStore.setReaderFontId(null)
    }
    message.success(font.enabled ? '字体已停用' : '字体已启用')
  } catch (error: any) {
    message.error(error.response?.data?.message || '字体状态更新失败')
  }
}

const handleRemoveFont = async (font: FontAsset) => {
  const sourceHint = font.sourceType === 'SCANNED'
    ? '只删除系统记录，不会删除 NAS 原文件。'
    : '上传的字体文件也会一并删除。'
  if (!await confirm(`确定删除字体“${font.displayName}”吗？\n${sourceHint}`)) return

  try {
    await fontStore.removeFont(font.id)
    if (preferencesStore.uiFontId === font.id) preferencesStore.setUiFontId(null)
    if (preferencesStore.readerFontId === font.id) preferencesStore.setReaderFontId(null)
    message.success('字体已删除')
  } catch (error: any) {
    message.error(error.response?.data?.message || '字体删除失败')
  }
}

const formatTime = (value: string) => formatChinaDateTime(value)

const formatFileSize = (bytes: number) => {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

watch(
  [() => fontStore.fonts.length, fontPageSize],
  () => {
    const totalPages = Math.max(1, Math.ceil(fontStore.fonts.length / fontPageSize.value))
    if (fontPage.value > totalPages) fontPage.value = totalPages
  }
)

onMounted(async () => {
  try {
    await Promise.all([
      preferencesStore.hydrate(),
      fontStore.fetchFonts(),
      fontStore.fetchDirectories(),
    ])
    await Promise.all([
      loadSelectedPreview(selectedUiFont.value),
      loadSelectedPreview(selectedReaderFont.value),
    ])
  } catch (error) {
    console.error('Failed to initialize font management:', error)
  }
})
</script>

<style scoped>
.font-management {
  display: block;
}

.font-section-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--spacing-lg);
}

.font-section-tabs :deep(.el-tabs__item) {
  color: var(--text-secondary);
  font-size: var(--font-size-base);
  font-weight: 500;
}

.font-section-tabs :deep(.el-tabs__item.is-active) {
  color: var(--primary);
}

.card {
  overflow: hidden;
  background: var(--surface-card);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-lg);
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}

.card-header p {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
  font-weight: 400;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.font-preference-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--spacing-lg);
  padding: var(--spacing-lg);
}

.preference-field {
  display: grid;
  gap: var(--spacing-sm);
  color: var(--text-secondary);
  font-weight: 500;
}

.preference-field small,
.font-directory-row small {
  color: var(--text-tertiary);
  font-weight: 400;
}

.font-selector-row {
  display: grid;
  grid-template-columns: minmax(240px, 0.85fr) minmax(260px, 1.15fr);
  align-items: stretch;
  gap: var(--spacing-md);
}

.font-select {
  width: 100%;
}

.font-select :deep(.el-select__wrapper) {
  min-height: 46px;
}

.font-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-lg);
  width: 100%;
}

.font-option-name,
.font-option-sample {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.font-option-name {
  color: var(--text-primary);
  font-weight: 500;
}

.font-option-sample {
  color: var(--text-secondary);
  font-size: 16px;
  text-align: right;
}

.current-font-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  min-width: 0;
  padding: 9px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
}

.current-font-preview span {
  flex: 0 0 auto;
  color: var(--text-tertiary);
  font-family: var(--font-family);
  font-size: var(--font-size-xs);
  font-weight: 400;
}

.current-font-preview strong {
  min-width: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reader-preview,
.reader-default-sample {
  font-family: Georgia, 'Times New Roman', serif;
}

.font-directory-list {
  padding: 0 var(--spacing-lg);
}

.font-directory-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-md) 0;
  border-bottom: 1px solid var(--border-color-light);
}

.font-directory-row:last-child {
  border-bottom: 0;
}

.directory-path {
  color: var(--text-primary);
  font-weight: 500;
  word-break: break-all;
}

.directory-error {
  display: block;
  margin-top: 4px;
  color: var(--danger) !important;
}

.upload-button {
  cursor: pointer;
}

.upload-button input {
  display: none;
}

.inline-empty {
  padding: var(--spacing-xl);
  color: var(--text-tertiary);
  text-align: center;
}

.font-list {
  padding: var(--spacing-md);
}

.font-pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--spacing-md) var(--spacing-lg) var(--spacing-lg);
  border-top: 1px solid var(--border-color-light);
}

.font-row {
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(260px, 1fr) auto;
  align-items: center;
  gap: var(--spacing-lg);
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}

.font-row:last-child {
  border-bottom: 0;
}

.font-row.unavailable {
  opacity: 0.68;
}

.font-preview {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.font-detail {
  min-width: 0;
}

.font-name-line,
.font-meta,
.font-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.font-name-line {
  flex-wrap: wrap;
}

.font-meta {
  margin-top: var(--spacing-xs);
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
}

.font-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.font-actions {
  justify-content: flex-end;
}

.btn-danger {
  color: var(--danger) !important;
}

.dialog-hint {
  margin: 0 0 var(--spacing-md);
  color: var(--text-secondary);
}

.directory-tree-panel {
  min-height: 260px;
  max-height: 420px;
  padding: var(--spacing-sm);
  overflow: auto;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
}

.directory-tree-panel :deep(.el-tree) {
  background: transparent;
  color: var(--text-primary);
}

.directory-tree-node {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  min-width: 0;
  padding-right: var(--spacing-sm);
}

.selected-directory {
  display: grid;
  gap: 4px;
  margin-top: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
}

.selected-directory span {
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
}

.selected-directory strong {
  overflow-wrap: anywhere;
  color: var(--text-primary);
}

@media (max-width: 800px) {
  .font-preference-grid {
    grid-template-columns: 1fr;
  }

  .font-selector-row {
    grid-template-columns: 1fr;
  }

  .font-row {
    grid-template-columns: 1fr;
    gap: var(--spacing-sm);
  }

  .font-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .font-pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}

@media (max-width: 560px) {
  .card-header,
  .font-directory-row {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .btn {
    flex: 1;
  }
}
</style>
