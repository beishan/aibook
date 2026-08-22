<template>
  <section class="cover-library card glass">
    <header class="library-header">
      <div>
        <div class="library-title-row">
          <div class="library-title">🖼️ 书籍封面库</div>
          <el-tooltip
            :trigger="['hover', 'click']"
            placement="bottom-start"
            popper-class="random-cover-library-help-tooltip"
            teleported
          >
            <template #content>
              <span>支持 JPG、PNG、WebP、GIF，单张最大 10MB。<br />删除库内素材不会影响已使用该素材的书籍。</span>
            </template>
            <button class="library-help-button" type="button" aria-label="查看封面库素材要求">
              <el-icon aria-hidden="true"><QuestionFilled /></el-icon>
            </button>
          </el-tooltip>
        </div>
        <p>导入或扫描到没有封面的新书时，将从这里自动随机选择一张封面。</p>
      </div>
      <button class="btn btn-primary" type="button" :disabled="uploading" @click="fileInput?.click()">
        <span>＋</span>
        <span>{{ uploading ? '上传中...' : '添加封面' }}</span>
      </button>
      <input
        ref="fileInput"
        class="file-input"
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        multiple
        :disabled="uploading"
        @change="uploadFiles"
      />
    </header>

    <div v-if="loading" class="library-state">
      <div class="loading-spinner-small"></div>
      <span>正在加载封面库...</span>
    </div>
    <div v-else-if="covers.length === 0" class="library-state empty-state">
      <span class="empty-icon">🖼️</span>
      <strong>封面库还是空的</strong>
      <p>添加一些封面后，新导入且没有封面的书籍会自动获得随机封面。</p>
    </div>
    <div v-else class="library-content">
      <div class="library-toolbar">
        <span class="toolbar-label">封面展示方式</span>
        <div class="cover-view-switch" role="group" aria-label="封面展示方式">
          <button
            class="cover-view-button"
            type="button"
            :aria-pressed="coverViewMode === 'standard'"
            title="标准卡片"
            @click="setCoverViewMode('standard')"
          >
            标准卡片
          </button>
          <button
            class="cover-view-button"
            type="button"
            :aria-pressed="coverViewMode === 'compact'"
            title="紧凑卡片"
            @click="setCoverViewMode('compact')"
          >
            紧凑卡片
          </button>
        </div>
      </div>
      <div class="cover-grid" :class="{ 'cover-grid--compact': coverViewMode === 'compact' }">
        <article v-for="cover in paginatedCovers" :key="cover.id" class="cover-item">
          <div class="cover-preview">
            <img :src="getCoverUrl(cover.coverUrl)" :alt="cover.name" loading="lazy" />
            <button
              class="delete-cover"
              type="button"
              :disabled="deletingId === cover.id"
              :aria-label="`删除封面 ${cover.name}`"
              title="删除封面"
              @click="removeCover(cover)"
            >
              {{ deletingId === cover.id ? '…' : '×' }}
            </button>
          </div>
          <div class="cover-meta">
            <strong :title="cover.name">{{ cover.name }}</strong>
            <small>{{ formatFileSize(cover.fileSize) }}</small>
          </div>
        </article>
      </div>
      <div class="library-pagination">
        <span class="pagination-summary">共 {{ covers.length }} 张</span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="pageSizeOptions"
          :total="covers.length"
          layout="sizes, prev, pager, next"
          background
          @size-change="handlePageSizeChange"
        />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import api from '@/utils/api'
import { getCoverUrl } from '@/utils/cover'
import { confirm, message } from '@/utils/message'

interface RandomBookCover {
  id: number
  name: string
  coverUrl: string
  contentType: string
  fileSize: number
  createdAt: string
}

const COVER_VIEW_MODE_STORAGE_KEY = 'aibook-random-cover-library-view-mode'
const coverViewModes = ['standard', 'compact'] as const
type CoverViewMode = typeof coverViewModes[number]

const isCoverViewMode = (value: string | null): value is CoverViewMode =>
  coverViewModes.includes(value as CoverViewMode)

const readCoverViewMode = (): CoverViewMode => {
  try {
    const savedMode = localStorage.getItem(COVER_VIEW_MODE_STORAGE_KEY)
    return isCoverViewMode(savedMode) ? savedMode : 'standard'
  } catch {
    return 'standard'
  }
}

const covers = ref<RandomBookCover[]>([])
const loading = ref(true)
const uploading = ref(false)
const deletingId = ref<number | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const coverViewMode = ref<CoverViewMode>(readCoverViewMode())
const currentPage = ref(1)
const pageSize = ref(12)
const pageSizeOptions = [12, 24, 48]
const pageCount = computed(() => Math.max(1, Math.ceil(covers.value.length / pageSize.value)))
const paginatedCovers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return covers.value.slice(start, start + pageSize.value)
})

const setCoverViewMode = (mode: CoverViewMode) => {
  coverViewMode.value = mode
  try {
    localStorage.setItem(COVER_VIEW_MODE_STORAGE_KEY, mode)
  } catch {
    // 浏览器禁止本地存储时仍可在当前页面切换展示方式。
  }
}

const loadCovers = async () => {
  loading.value = true
  try {
    const { data } = await api.get<RandomBookCover[]>('/api/random-book-covers', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    covers.value = data
    currentPage.value = 1
  } catch (error: any) {
    message.error(error.response?.data?.message || '封面库加载失败')
  } finally {
    loading.value = false
  }
}

const uploadFiles = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (files.length === 0) return
  const invalid = files.find(file =>
    !['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)
    || file.size > 10 * 1024 * 1024
  )
  if (invalid) {
    message.warning('请选择 JPG、PNG、WebP 或 GIF 图片，且单张不能超过10MB')
    input.value = ''
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    const { data } = await api.post<RandomBookCover[]>('/api/random-book-covers', formData, {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    covers.value = [...data, ...covers.value]
    currentPage.value = 1
    message.success(`已添加 ${data.length} 张随机封面`)
  } catch (error: any) {
    message.error(error.response?.data?.message || '封面上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

const removeCover = async (cover: RandomBookCover) => {
  if (!await confirm(`确定从封面库删除“${cover.name}”吗？`)) return
  deletingId.value = cover.id
  try {
    await api.delete(`/api/random-book-covers/${cover.id}`, {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    covers.value = covers.value.filter(item => item.id !== cover.id)
    message.success('封面已删除')
  } catch (error: any) {
    message.error(error.response?.data?.message || '封面删除失败')
  } finally {
    deletingId.value = null
  }
}

const formatFileSize = (bytes: number) => {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const handlePageSizeChange = () => {
  currentPage.value = 1
}

watch(pageCount, count => {
  if (currentPage.value > count) currentPage.value = count
})

onMounted(loadCovers)
</script>

<style scoped>
.cover-library { overflow: hidden; }
.library-header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--spacing-lg); padding: var(--spacing-xl); border-bottom: 1px solid var(--border-color-light); }
.library-title-row { display: inline-flex; align-items: center; gap: 6px; }
.library-title { color: var(--text-primary); font-size: var(--font-size-lg); font-weight: 700; }
.library-help-button { display: inline-grid; width: 24px; height: 24px; place-items: center; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--text-tertiary); cursor: help; font-size: 16px; line-height: 1; transition: background-color .18s ease, color .18s ease; }
.library-help-button:hover { background: var(--primary-alpha-10); color: var(--primary); }
.library-help-button:focus-visible, .cover-view-button:focus-visible, .delete-cover:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.library-header p { margin: 7px 0 0; color: var(--text-secondary); font-size: var(--font-size-sm); }
.file-input { display: none; }
.library-state { display: flex; min-height: 260px; align-items: center; justify-content: center; gap: 10px; color: var(--text-secondary); }
.empty-state { flex-direction: column; padding: var(--spacing-xl); text-align: center; }
.empty-state .empty-icon { font-size: 42px; opacity: .72; }
.empty-state strong { color: var(--text-primary); font-size: var(--font-size-lg); }
.empty-state p { max-width: 440px; margin: 0; color: var(--text-secondary); }
.library-content { min-width: 0; }
.library-toolbar { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); padding: var(--spacing-lg) var(--spacing-xl) 0; }
.toolbar-label { color: var(--text-secondary); font-size: var(--font-size-sm); }
.cover-view-switch { display: inline-flex; padding: 3px; border: 1px solid var(--border-color-light); border-radius: var(--radius-md); background: var(--surface-hover); }
.cover-view-button { min-height: 30px; padding: 0 10px; border: 0; border-radius: calc(var(--radius-md) - 3px); background: transparent; color: var(--text-secondary); cursor: pointer; font-size: 12px; font-weight: 600; transition: background-color .18s ease, color .18s ease; }
.cover-view-button[aria-pressed="true"] { background: var(--surface-card); box-shadow: var(--shadow-sm); color: var(--primary); }
.cover-view-button:hover:not([aria-pressed="true"]) { color: var(--text-primary); }
.cover-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(138px, 1fr)); gap: var(--spacing-lg); padding: var(--spacing-xl); }
.cover-item { min-width: 0; padding: 9px; border: 1px solid var(--border-color-light); border-radius: var(--radius-lg); background: var(--surface-card); }
.cover-preview { position: relative; aspect-ratio: 3 / 4; overflow: hidden; border-radius: var(--radius-md); background: var(--surface-hover); }
.cover-preview img { width: 100%; height: 100%; object-fit: cover; }
.delete-cover { position: absolute; top: 7px; right: 7px; display: grid; width: 27px; height: 27px; place-items: center; border: 1px solid rgba(255,255,255,.45); border-radius: 50%; background: rgba(24,24,27,.72); color: white; cursor: pointer; font-size: 19px; line-height: 1; backdrop-filter: blur(8px); }
.delete-cover:hover:not(:disabled) { background: var(--danger); }
.cover-meta { display: grid; min-width: 0; margin-top: 9px; }
.cover-meta strong { overflow: hidden; color: var(--text-primary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.cover-meta small { margin-top: 3px; color: var(--text-tertiary); }
.cover-grid--compact { grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); gap: 10px; padding: var(--spacing-lg) var(--spacing-xl) var(--spacing-xl); }
.cover-grid--compact .cover-item { padding: 6px; border-radius: var(--radius-md); }
.cover-grid--compact .cover-preview { border-radius: var(--radius-sm); }
.cover-grid--compact .delete-cover { top: 5px; right: 5px; }
.cover-grid--compact .cover-meta { margin-top: 6px; }
.cover-grid--compact .cover-meta strong { font-size: 11px; }
.cover-grid--compact .cover-meta small { margin-top: 2px; font-size: 11px; }
.library-pagination { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-lg); padding: 0 var(--spacing-xl) var(--spacing-xl); border-top: 1px solid var(--border-color-light); padding-top: var(--spacing-lg); }
.pagination-summary { flex: none; color: var(--text-secondary); font-size: var(--font-size-sm); }
.library-pagination :deep(.el-pagination) { min-width: 0; }
@media (max-width: 640px) { .library-header { flex-direction: column; }.library-header .btn { width: 100%; justify-content: center; }.library-toolbar { padding: var(--spacing-lg) var(--spacing-lg) 0; }.cover-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); padding: var(--spacing-lg); }.cover-grid--compact { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }.library-pagination { align-items: flex-start; flex-direction: column; padding: var(--spacing-lg); }.library-pagination :deep(.el-pagination) { flex-wrap: wrap; justify-content: flex-start; gap: 6px 0; } }
@media (max-width: 360px) { .library-toolbar { align-items: flex-start; flex-direction: column; }.cover-grid--compact { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (prefers-reduced-motion: reduce) { .cover-view-button { transition: none; } }
</style>

<style>
.random-cover-library-help-tooltip {
  max-width: min(320px, calc(100vw - 32px));
  line-height: 1.6;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}
</style>
