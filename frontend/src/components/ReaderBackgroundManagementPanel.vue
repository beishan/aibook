<template>
  <section class="background-library card glass">
    <header class="library-header">
      <div>
        <div class="library-title">🌄 阅读背景图片</div>
        <p>管理阅读器可选的纸张纹理和背景壁纸。内置背景始终可用，上传背景仅当前账号可见。</p>
      </div>
      <button class="btn btn-primary" type="button" :disabled="uploading" @click="fileInput?.click()">
        <span>＋</span>
        <span>{{ uploading ? '上传中...' : '上传背景' }}</span>
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

    <div class="background-section">
      <div class="section-heading">
        <div><strong>默认壁纸</strong><span>随应用提供，不占用上传空间</span></div>
        <span class="count-pill">{{ BUILT_IN_READER_BACKGROUNDS.length }} 张</span>
      </div>
      <div class="background-grid">
        <article v-for="background in BUILT_IN_READER_BACKGROUNDS" :key="background.id" class="background-card">
          <img :src="background.imageUrl" :alt="background.name" />
          <div class="background-meta"><strong>{{ background.name }}</strong><small>内置</small></div>
        </article>
      </div>
    </div>

    <div class="background-section custom-section">
      <div class="section-heading">
        <div><strong>我的背景</strong><span>支持 JPG、PNG、WebP、GIF，单张最大 15MB</span></div>
        <span class="count-pill">{{ backgrounds.length }} 张</span>
      </div>
      <div v-if="loading" class="library-state"><div class="loading-spinner-small"></div><span>正在加载...</span></div>
      <div v-else-if="backgrounds.length === 0" class="library-state empty-state">
        <span class="empty-icon">🏞️</span>
        <strong>还没有上传背景</strong>
        <p>上传后即可在阅读设置中选择。</p>
      </div>
      <div v-else class="background-grid">
        <article v-for="background in backgrounds" :key="background.id" class="background-card">
          <div class="custom-preview">
            <img :src="background.imageUrl" :alt="background.name" loading="lazy" />
            <button
              class="delete-background"
              type="button"
              :disabled="deletingId === background.customId"
              :aria-label="`删除背景 ${background.name}`"
              title="删除背景"
              @click="removeBackground(background)"
            >{{ deletingId === background.customId ? '…' : '×' }}</button>
          </div>
          <div class="background-meta">
            <strong :title="background.name">{{ background.name }}</strong>
            <small>{{ formatFileSize(background.fileSize) }}</small>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/utils/api'
import { confirm, message } from '@/utils/message'
import {
  BUILT_IN_READER_BACKGROUNDS,
  toReaderBackgroundOption,
  type ReaderBackgroundDto,
  type ReaderBackgroundOption,
} from '@/utils/readerBackground'

const fileInput = ref<HTMLInputElement | null>(null)
const backgrounds = ref<ReaderBackgroundOption[]>([])
const loading = ref(false)
const uploading = ref(false)
const deletingId = ref<number | null>(null)

const loadBackgrounds = async () => {
  loading.value = true
  try {
    const { data } = await api.get<ReaderBackgroundDto[]>('/api/reader-backgrounds')
    backgrounds.value = data.map(toReaderBackgroundOption)
  } catch (error: any) {
    message.error(error.response?.data?.message || '阅读背景加载失败')
  } finally {
    loading.value = false
  }
}

const uploadFiles = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (files.length === 0) return
  uploading.value = true
  try {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    const { data } = await api.post<ReaderBackgroundDto[]>('/api/reader-backgrounds', formData, {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    backgrounds.value = [...data.map(toReaderBackgroundOption), ...backgrounds.value]
    message.success(`已添加 ${data.length} 张阅读背景`)
  } catch (error: any) {
    message.error(error.response?.data?.message || '阅读背景上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

const removeBackground = async (background: ReaderBackgroundOption) => {
  if (background.customId == null || !await confirm(`确定删除阅读背景“${background.name}”吗？`)) return
  deletingId.value = background.customId
  try {
    await api.delete(`/api/reader-backgrounds/${background.customId}`, {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    backgrounds.value = backgrounds.value.filter(item => item.id !== background.id)
    window.dispatchEvent(new CustomEvent('reader-background-deleted', { detail: background.id }))
    message.success('阅读背景已删除')
  } catch (error: any) {
    message.error(error.response?.data?.message || '阅读背景删除失败')
  } finally {
    deletingId.value = null
  }
}

const formatFileSize = (size?: number) => {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onMounted(loadBackgrounds)
</script>

<style scoped>
.background-library { padding: 24px; }
.library-header, .section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.library-title { margin-bottom: 6px; color: var(--text-primary); font-size: 20px; font-weight: 700; }
.library-header p, .section-heading span, .empty-state p { margin: 0; color: var(--text-secondary); font-size: 13px; }
.file-input { display: none; }
.background-section { margin-top: 28px; }
.custom-section { padding-top: 24px; border-top: 1px solid var(--border-color); }
.section-heading > div { display: grid; gap: 4px; }
.section-heading strong { font-size: 16px; }
.count-pill { padding: 4px 9px; border-radius: var(--radius-full); background: var(--primary-alpha-10); color: var(--primary) !important; }
.background-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 16px; margin-top: 16px; }
.background-card { overflow: hidden; border: 1px solid var(--border-color); border-radius: 16px; background: var(--surface-elevated); box-shadow: var(--shadow-sm); }
.background-card > img, .custom-preview img { display: block; width: 100%; aspect-ratio: 16 / 10; object-fit: cover; }
.custom-preview { position: relative; }
.background-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 11px 12px; }
.background-meta strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.background-meta small { flex: 0 0 auto; color: var(--text-tertiary); }
.delete-background { position: absolute; top: 8px; right: 8px; display: grid; width: 28px; height: 28px; place-items: center; border: 1px solid rgba(255,255,255,.65); border-radius: 50%; background: rgba(24,28,34,.65); color: #fff; cursor: pointer; backdrop-filter: blur(8px); }
.delete-background:hover { background: var(--danger); }
.library-state { display: flex; min-height: 150px; align-items: center; justify-content: center; gap: 10px; margin-top: 16px; border: 1px dashed var(--border-color); border-radius: 16px; color: var(--text-secondary); }
.empty-state { flex-direction: column; text-align: center; }
.empty-icon { font-size: 36px; }
@media (max-width: 640px) { .library-header, .section-heading { align-items: flex-start; flex-direction: column; } .library-header .btn { width: 100%; } .background-grid { grid-template-columns: 1fr 1fr; gap: 10px; } }
</style>
