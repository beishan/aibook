<template>
  <div class="card glass favicon-card">
    <div class="favicon-header">
      <div>
        <div class="panel-title">🌐 网站图标</div>
        <p class="header-hint">设置浏览器标签页和收藏夹中显示的网站图标</p>
      </div>
    </div>

    <div v-if="loading" class="favicon-state">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else class="favicon-content">
      <div class="favicon-preview-wrap">
        <div class="favicon-preview">
          <img :src="previewUrl" alt="当前网站图标" />
        </div>
        <div>
          <strong>{{ status.hasCustom ? '当前自定义图标' : '当前默认图标' }}</strong>
          <p>推荐使用正方形 PNG 或 ICO，尺寸不小于 64×64 像素。</p>
        </div>
      </div>

      <div class="favicon-actions">
        <input
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif,image/x-icon,.ico"
          hidden
          @change="handleFileSelected"
        />
        <button class="btn btn-primary" :disabled="saving" @click="fileInput?.click()">
          {{ saving ? '上传中...' : '上传新图标' }}
        </button>
        <button
          v-if="status.hasCustom"
          class="btn btn-text btn-danger"
          :disabled="saving"
          @click="restoreDefault"
        >
          恢复默认
        </button>
      </div>
      <p class="upload-hint">支持 JPG、PNG、WebP、GIF 和 ICO，文件不能超过 2MB。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/utils/api'
import { confirm, message } from '@/utils/message'
import {
  applySiteFavicon,
  faviconUrl,
  type SiteFaviconStatus,
} from '@/utils/siteFavicon'

const emptyStatus = (): SiteFaviconStatus => ({ hasCustom: false, version: 0 })
const loading = ref(true)
const saving = ref(false)
const status = ref<SiteFaviconStatus>(emptyStatus())
const fileInput = ref<HTMLInputElement | null>(null)
const previewUrl = computed(() => faviconUrl(status.value))

const applyStatus = (value: SiteFaviconStatus) => {
  status.value = value
  applySiteFavicon(faviconUrl(value))
}

const loadStatus = async () => {
  loading.value = true
  try {
    const { data } = await api.get<SiteFaviconStatus>('/api/site/favicon/status')
    applyStatus(data)
  } catch (error: any) {
    message.error(error.response?.data?.message || '网站图标加载失败')
  } finally {
    loading.value = false
  }
}

const handleFileSelected = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''
  if (file.size > 2 * 1024 * 1024) {
    message.warning('网站图标不能超过 2MB')
    return
  }
  const supportedType = [
    'image/jpeg',
    'image/png',
    'image/webp',
    'image/gif',
    'image/x-icon',
    'image/vnd.microsoft.icon',
  ].includes(file.type)
  const supportedExtension = /\.(jpe?g|png|webp|gif|ico)$/i.test(file.name)
  if (!supportedType && !supportedExtension) {
    message.warning('仅支持 JPG、PNG、WebP、GIF 或 ICO 图片')
    return
  }

  saving.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post<SiteFaviconStatus>('/api/site/favicon', form)
    applyStatus(data)
    message.success('网站图标已更新')
  } catch (error: any) {
    message.error(error.response?.data?.message || '网站图标上传失败')
  } finally {
    saving.value = false
  }
}

const restoreDefault = async () => {
  if (!await confirm('确定恢复系统默认网站图标吗？')) return
  saving.value = true
  try {
    const { data } = await api.delete<SiteFaviconStatus>('/api/site/favicon')
    applyStatus(data)
    message.success('已恢复默认网站图标')
  } catch (error: any) {
    message.error(error.response?.data?.message || '恢复默认图标失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => void loadStatus())
</script>

<style scoped>
.favicon-card {
  overflow: hidden;
}

.favicon-header {
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}

.panel-title {
  color: var(--text-primary);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.header-hint {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.favicon-state {
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.favicon-content {
  padding: var(--spacing-xl);
}

.favicon-preview-wrap {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}

.favicon-preview {
  width: 96px;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  padding: 12px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-lg);
  background: var(--surface-hover);
}

.favicon-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.favicon-preview-wrap p,
.upload-hint {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.favicon-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-xl);
}

@media (max-width: 640px) {
  .favicon-content {
    padding: var(--spacing-lg);
  }

  .favicon-preview-wrap {
    align-items: flex-start;
    flex-direction: column;
  }

  .favicon-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
