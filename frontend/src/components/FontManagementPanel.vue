<template>
  <div class="font-management">
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
          <select
            class="select-input"
            :value="preferencesStore.uiFontId ?? ''"
            @change="handleUiFontChange"
          >
            <option value="">系统默认</option>
            <option v-for="font in fontStore.availableFonts" :key="font.id" :value="font.id">
              {{ font.displayName }}
            </option>
          </select>
          <small>应用于导航、书库、设置和详情页面。</small>
        </label>
        <label class="preference-field">
          <span>默认阅读字体</span>
          <select
            class="select-input"
            :value="preferencesStore.readerFontId ?? ''"
            @change="handleReaderFontChange"
          >
            <option value="">阅读器默认</option>
            <option v-for="font in fontStore.availableFonts" :key="font.id" :value="font.id">
              {{ font.displayName }}
            </option>
          </select>
          <small>应用于 TXT、Markdown 和 EPUB 正文。</small>
        </label>
      </div>
    </div>

    <div class="card glass">
      <div class="card-header">
        <div>
          <strong>字体扫描目录</strong>
          <p>填写 Jenkins 已映射到后端容器内的路径，例如 /fontfolder。</p>
        </div>
        <button class="btn btn-primary" :disabled="fontStore.scanning" @click="handleScan">
          {{ fontStore.scanning ? '扫描中…' : '扫描字体' }}
        </button>
      </div>
      <div class="directory-editor">
        <input
          v-model.trim="newDirectory"
          class="input"
          placeholder="/fontfolder"
          @keyup.enter="handleAddDirectory"
        />
        <button class="btn" :disabled="addingDirectory || !newDirectory" @click="handleAddDirectory">
          {{ addingDirectory ? '添加中…' : '添加目录' }}
        </button>
      </div>
      <div v-if="fontStore.directories.length" class="font-directory-list">
        <div v-for="directory in fontStore.directories" :key="directory.id" class="font-directory-row">
          <div>
            <div class="directory-path">{{ directory.path }}</div>
            <small>
              {{ directory.lastScanAt ? `上次扫描：${formatTime(directory.lastScanAt)}` : '尚未扫描' }}
            </small>
            <small v-if="directory.lastError" class="directory-error">{{ directory.lastError }}</small>
          </div>
          <button class="btn btn-text btn-danger" @click="handleRemoveDirectory(directory)">
            删除配置
          </button>
        </div>
      </div>
      <div v-else class="inline-empty">暂无字体扫描目录，仍可直接上传字体。</div>
    </div>

    <div class="card glass">
      <div class="card-header">
        <div>
          <strong>字体文件</strong>
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
      <div v-else class="font-list">
        <article
          v-for="font in fontStore.fonts"
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
              <span class="tag" :class="font.enabled && font.available ? 'tag-success' : 'tag-info'">
                {{ !font.available ? '文件不可用' : font.enabled ? '已启用' : '已停用' }}
              </span>
              <span class="tag tag-info">{{ font.sourceType === 'UPLOADED' ? '上传' : '目录扫描' }}</span>
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { confirm, message } from '@/utils/message'
import { formatChinaDateTime } from '@/utils/dateTime'
import { usePreferencesStore } from '@/stores/preferences'
import { useFontStore, type FontAsset, type FontScanDirectory } from '@/stores/font'

const preferencesStore = usePreferencesStore()
const fontStore = useFontStore()
const newDirectory = ref('/fontfolder')
const addingDirectory = ref(false)
const uploading = ref(false)

const readSelectedId = (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  return value ? Number(value) : null
}

const handleUiFontChange = async (event: Event) => {
  const id = readSelectedId(event)
  preferencesStore.setUiFontId(id)
  message.success(id == null ? '已恢复系统默认字体' : '系统字体已更新')
}

const handleReaderFontChange = (event: Event) => {
  const id = readSelectedId(event)
  preferencesStore.setReaderFontId(id)
  message.success(id == null ? '已恢复阅读器默认字体' : '默认阅读字体已更新')
}

const handleAddDirectory = async () => {
  if (!newDirectory.value.startsWith('/')) {
    message.warning('请输入容器内的绝对路径')
    return
  }
  addingDirectory.value = true
  try {
    await fontStore.addDirectory(newDirectory.value)
    message.success('字体扫描目录已添加')
  } catch (error: any) {
    message.error(error.response?.data?.message || '添加字体目录失败')
  } finally {
    addingDirectory.value = false
  }
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

onMounted(async () => {
  try {
    await Promise.all([
      preferencesStore.hydrate(),
      fontStore.fetchFonts(),
      fontStore.fetchDirectories(),
    ])
  } catch (error) {
    console.error('Failed to initialize font management:', error)
  }
})
</script>

<style scoped>
.font-management {
  display: grid;
  gap: var(--spacing-lg);
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

.font-preference-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
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

.directory-editor {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}

.directory-editor .input {
  flex: 1;
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

@media (max-width: 800px) {
  .font-preference-grid {
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
}

@media (max-width: 560px) {
  .card-header,
  .directory-editor,
  .font-directory-row {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
