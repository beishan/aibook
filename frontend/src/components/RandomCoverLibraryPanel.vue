<template>
  <section class="cover-library card glass">
    <header class="library-header">
      <div>
        <div class="library-title">🖼️ 书籍封面库</div>
        <p>导入或扫描到没有封面的新书时，将从这里自动随机选择一张封面。</p>
      </div>
      <button class="btn btn-primary" :disabled="uploading" @click="fileInput?.click()">
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

    <div class="library-note">
      支持 JPG、PNG、WebP、GIF，单张最大 10MB。删除库内素材不会影响已使用该素材的书籍。
    </div>

    <div v-if="loading" class="library-state">
      <div class="loading-spinner-small"></div>
      <span>正在加载封面库...</span>
    </div>
    <div v-else-if="covers.length === 0" class="library-state empty-state">
      <span class="empty-icon">🖼️</span>
      <strong>封面库还是空的</strong>
      <p>添加一些封面后，新导入且没有封面的书籍会自动获得随机封面。</p>
    </div>
    <div v-else class="cover-grid">
      <article v-for="cover in covers" :key="cover.id" class="cover-item">
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
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

const covers = ref<RandomBookCover[]>([])
const loading = ref(true)
const uploading = ref(false)
const deletingId = ref<number | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const loadCovers = async () => {
  loading.value = true
  try {
    const { data } = await api.get<RandomBookCover[]>('/api/random-book-covers', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    covers.value = data
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

onMounted(loadCovers)
</script>

<style scoped>
.cover-library { overflow: hidden; }
.library-header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--spacing-lg); padding: var(--spacing-xl); border-bottom: 1px solid var(--border-color-light); }
.library-title { color: var(--text-primary); font-size: var(--font-size-lg); font-weight: 700; }
.library-header p { margin: 7px 0 0; color: var(--text-secondary); font-size: var(--font-size-sm); }
.file-input { display: none; }
.library-note { margin: var(--spacing-lg) var(--spacing-xl) 0; padding: 12px 14px; border: 1px solid var(--primary-alpha-20); border-radius: var(--radius-md); background: var(--primary-alpha-10); color: var(--text-secondary); font-size: var(--font-size-sm); }
.library-state { display: flex; min-height: 260px; align-items: center; justify-content: center; gap: 10px; color: var(--text-secondary); }
.empty-state { flex-direction: column; padding: var(--spacing-xl); text-align: center; }
.empty-state .empty-icon { font-size: 42px; opacity: .72; }
.empty-state strong { color: var(--text-primary); font-size: var(--font-size-lg); }
.empty-state p { max-width: 440px; margin: 0; color: var(--text-secondary); }
.cover-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(138px, 1fr)); gap: var(--spacing-lg); padding: var(--spacing-xl); }
.cover-item { min-width: 0; padding: 9px; border: 1px solid var(--border-color-light); border-radius: var(--radius-lg); background: var(--surface-card); }
.cover-preview { position: relative; aspect-ratio: 3 / 4; overflow: hidden; border-radius: var(--radius-md); background: var(--surface-hover); }
.cover-preview img { width: 100%; height: 100%; object-fit: cover; }
.delete-cover { position: absolute; top: 7px; right: 7px; display: grid; width: 27px; height: 27px; place-items: center; border: 1px solid rgba(255,255,255,.45); border-radius: 50%; background: rgba(24,24,27,.72); color: white; cursor: pointer; font-size: 19px; line-height: 1; backdrop-filter: blur(8px); }
.delete-cover:hover:not(:disabled) { background: var(--danger); }
.cover-meta { display: grid; min-width: 0; margin-top: 9px; }
.cover-meta strong { overflow: hidden; color: var(--text-primary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.cover-meta small { margin-top: 3px; color: var(--text-tertiary); }
@media (max-width: 640px) { .library-header { flex-direction: column; }.library-header .btn { width: 100%; justify-content: center; }.cover-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); padding: var(--spacing-lg); } }
</style>
