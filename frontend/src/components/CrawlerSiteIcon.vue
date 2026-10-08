<template>
  <div :class="['crawler-site-icon', { 'is-editable': editable }]">
    <span class="icon-preview" :style="{ '--icon-color': color || '#009688' }" :aria-label="`${name || '网站'}图标`">
      <img
        v-if="preview || state?.dataUrl"
        v-show="!imageFailed"
        :src="preview || state?.dataUrl || ''"
        alt=""
        @error="imageFailed = true"
      />
      <span v-if="imageFailed || !(preview || state?.dataUrl)">{{ name?.slice(0, 1) || '站' }}</span>
    </span>
    <div v-if="editable" class="icon-controls">
      <div class="icon-actions">
        <el-button :loading="busy" :disabled="loading || busy" @click="input?.click()">上传图标</el-button>
        <el-button v-if="siteId" :loading="loading" :disabled="busy || loading" @click="refresh">重新采集</el-button>
        <el-button v-if="!siteId && preview" text @click="clearPending">取消上传</el-button>
      </div>
      <small v-if="loading">正在采集网站标签页图标…</small>
      <small v-else-if="preview">保存网站时上传所选图标</small>
      <small v-else-if="state?.error">{{ state.error }}</small>
      <small v-else-if="siteId">{{ state?.source === 'CUSTOM' ? '自定义图标' : '自动采集标签页图标' }} · 图标操作立即保存</small>
      <small v-else>保存网站后自动采集，也可先选择自定义图标</small>
      <small>支持 PNG、JPG、GIF、WebP、ICO，最大 2MB</small>
      <input ref="input" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/x-icon,image/vnd.microsoft.icon,.ico" hidden @change="selectFile" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import api from '@/utils/api'
import { message } from '@/utils/message'

interface IconView {
  dataUrl: string | null
  source: 'AUTO' | 'CUSTOM' | null
  error: string | null
}
const props = defineProps<{ siteId?: number; name?: string; color?: string; editable?: boolean }>()
const emit = defineEmits<{ selected: [file: File | undefined]; changed: [] }>()
const state = ref<IconView>()
const loading = ref(false)
const busy = ref(false)
const imageFailed = ref(false)
const input = ref<HTMLInputElement>()
const preview = ref('')
let requestVersion = 0

function clearPending() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
  imageFailed.value = false
  emit('selected', undefined)
}

watch(() => props.siteId, async id => {
  const version = ++requestVersion
  state.value = undefined
  imageFailed.value = false
  clearPending()
  if (!id) return
  loading.value = true
  try {
    const response = await api.get<IconView>(`/api/crawler/sites/${id}/icon`, {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    if (version === requestVersion) state.value = response.data
  } catch {
    if (version === requestVersion) state.value = { dataUrl: null, source: null, error: '图标加载失败，可重新采集或上传' }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}, { immediate: true })

async function refresh() {
  if (!props.siteId) return
  const id = props.siteId
  const version = requestVersion
  loading.value = true
  try {
    const result = (await api.post<IconView>(`/api/crawler/sites/${id}/icon/refresh`)).data
    if (version === requestVersion) {
      state.value = result
      imageFailed.value = false
    }
    message.success('网站图标已重新采集')
    emit('changed')
  } finally {
    loading.value = false
  }
}

async function selectFile(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    message.warning('图标不能超过 2MB')
    return
  }
  if (!props.siteId) {
    clearPending()
    preview.value = URL.createObjectURL(file)
    emit('selected', file)
    return
  }
  const id = props.siteId
  const version = requestVersion
  busy.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    const result = (await api.post<IconView>(`/api/crawler/sites/${id}/icon`, data)).data
    if (version === requestVersion) {
      state.value = result
      imageFailed.value = false
    }
    message.success('自定义网站图标已保存')
    emit('changed')
  } finally {
    busy.value = false
  }
}

onBeforeUnmount(() => {
  requestVersion++
  if (preview.value) URL.revokeObjectURL(preview.value)
})
</script>

<style scoped>
.crawler-site-icon { display: flex; align-items: center; gap: 16px; }
.icon-preview {
  display: inline-flex;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 12px;
  background: color-mix(in srgb, var(--icon-color) 12%, transparent);
  color: var(--icon-color);
  font-size: 20px;
  font-weight: 700;
}
.icon-preview img { width: 28px; height: 28px; object-fit: contain; }
.is-editable .icon-preview { width: 64px; height: 64px; }
.is-editable .icon-preview img { width: 44px; height: 44px; }
.icon-controls { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.icon-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.icon-actions :deep(.el-button + .el-button) { margin-left: 0; }
.icon-controls small { color: var(--el-text-color-secondary); line-height: 1.5; }
</style>
