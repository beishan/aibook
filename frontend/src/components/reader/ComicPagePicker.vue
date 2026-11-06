<template>
  <dialog ref="dialog" class="page-picker" aria-labelledby="comic-picker-title" @close="emit('close')">
    <header>
      <h2 id="comic-picker-title">页面缩略图</h2>
      <button type="button" @click="dialog?.close()">关闭</button>
    </header>

    <div class="page-picker-grid">
      <button
        v-for="page in pages"
        :key="page"
        type="button"
        class="page-picker-card"
        :aria-current="page === currentPage ? 'page' : undefined"
        :aria-label="`跳到第 ${page} 页${previews[page] === null ? '，预览不可用' : ''}`"
        @click="emit('select', page)"
      >
        <img v-if="previews[page]" :src="previews[page] || undefined" alt="" />
        <span v-else class="page-picker-placeholder">
          {{ previews[page] === null ? '预览不可用' : '加载中' }}
        </span>
        <span>第 {{ page }} 页</span>
      </button>
    </div>

    <footer>
      <button type="button" :disabled="group === 0" @click="group--">上一组</button>
      <span aria-live="polite">{{ pages[0] }}–{{ pages[pages.length - 1] }} / {{ pageCount }} 页</span>
      <button type="button" :disabled="group >= groupCount - 1" @click="group++">下一组</button>
    </footer>
  </dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import api from '@/utils/api'

const props = defineProps<{
  endpoint: string
  revision: string
  pageCount: number
  currentPage: number
}>()
const emit = defineEmits<{
  close: []
  select: [page: number]
  'file-changed': []
}>()

const groupSize = 8
const dialog = ref<HTMLDialogElement>()
const group = ref(Math.floor((props.currentPage - 1) / groupSize))
const groupCount = computed(() => Math.ceil(props.pageCount / groupSize))
const pages = computed(() => {
  const first = group.value * groupSize + 1
  return Array.from({ length: Math.min(groupSize, props.pageCount - first + 1) },
    (_, index) => first + index)
})
const previews = ref<Record<number, string | null>>({})

onMounted(() => dialog.value?.showModal())

watch(pages, (batch, _previous, onCleanup) => {
  const request = new AbortController()
  const urls: string[] = []
  previews.value = {}
  if (dialog.value) dialog.value.scrollTop = 0
  onCleanup(() => {
    request.abort()
    urls.forEach(url => URL.revokeObjectURL(url))
  })
  void loadBatch(batch, request.signal, urls)
}, { immediate: true })

async function loadBatch(batch: number[], signal: AbortSignal, urls: string[]) {
  for (const page of batch) {
    if (signal.aborted) return
    try {
      const { data } = await api.get<Blob>(`${props.endpoint}/pages/${page}`, {
        params: { revision: props.revision },
        headers: { 'X-Suppress-Error-Toast': 'true' },
        responseType: 'blob',
        signal,
      })
      if (signal.aborted) return
      const preview = await makePreview(data)
      if (signal.aborted) return
      const url = URL.createObjectURL(preview)
      urls.push(url)
      previews.value[page] = url
    } catch (error) {
      if (signal.aborted) return
      if ((error as { response?: { status?: number } }).response?.status === 409) {
        emit('file-changed')
        return
      }
      previews.value[page] = null
    }
  }
}

async function makePreview(source: Blob): Promise<Blob> {
  const bitmap = await createImageBitmap(source)
  try {
    const ratio = Math.min(1, 240 / bitmap.width, 320 / bitmap.height)
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(bitmap.width * ratio))
    canvas.height = Math.max(1, Math.round(bitmap.height * ratio))
    const context = canvas.getContext('2d', { alpha: false })
    if (!context) throw new Error('无法生成缩略图')
    context.fillStyle = '#fff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    return await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(blob => {
        if (blob) resolve(blob)
        else reject(new Error('无法生成缩略图'))
      }, 'image/jpeg', 0.82)
    })
  } finally {
    bitmap.close()
  }
}
</script>

<style scoped>
.page-picker {
  position: fixed;
  inset: 0;
  width: min(680px, calc(100% - 24px));
  max-height: calc(100dvh - 24px);
  margin: auto;
  padding: 14px;
  overflow: auto;
  color: #f1f3f6;
  background: #20242a;
  border: 1px solid #626b78;
  border-radius: 12px;
}

.page-picker::backdrop {
  background: rgb(0 0 0 / 65%);
}

.page-picker header,
.page-picker footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.page-picker h2 {
  font-size: 1.1rem;
}

.page-picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(110px, 42%), 1fr));
  gap: 8px;
  margin-block: 12px;
}

.page-picker-card {
  display: grid;
  gap: 4px;
  justify-items: center;
  padding: 6px;
}

.page-picker-card img,
.page-picker-placeholder {
  width: 100%;
  height: 112px;
  object-fit: contain;
}

.page-picker-placeholder {
  display: grid;
  place-items: center;
  font-size: 0.85rem;
}

.page-picker button {
  min-height: 36px;
  padding-inline: 8px;
  color: inherit;
  background: #272c35;
  border: 1px solid #626b78;
  border-radius: 7px;
  cursor: pointer;
}

.page-picker button[aria-current='page'] {
  border-color: #8bc7ff;
  background: #364b63;
}

.page-picker button:disabled {
  opacity: 0.5;
  cursor: default;
}

.page-picker button:focus-visible {
  outline: 2px solid #8bc7ff;
  outline-offset: -2px;
}
</style>
