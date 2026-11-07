<template>
    <el-dialog
      v-model="quickReaderOpen"
      class="book-quick-reader-dialog"
      modal-class="book-quick-reader-overlay"
      :style="quickReaderDialogStyle"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      append-to-body
      destroy-on-close
      @closed="resetQuickReader"
    >
      <iframe
        v-if="quickReaderOpen && quickReaderSrc"
        ref="quickReaderFrame"
        class="book-quick-reader-frame"
        :src="quickReaderSrc"
        :title="`小窗阅读：${displayBookName(quickReaderBook?.title) || ''}`"
        allow="fullscreen"
      />
      <button
        class="book-quick-reader-resize"
        type="button"
        aria-label="拖动调整阅读窗口大小，也可使用方向键"
        title="拖动调整窗口大小"
        @pointerdown="startQuickReaderResize"
        @pointermove="updateQuickReaderResize"
        @pointerup="stopQuickReaderResize"
        @pointercancel="stopQuickReaderResize"
        @lostpointercapture="stopQuickReaderResize"
        @keydown="resizeQuickReaderWithKeyboard"
      />
    </el-dialog>
</template>

<script setup lang="ts">
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { usePreferencesStore } from '@/stores/preferences'
import type { Book } from '@/stores/book'

const { displayBookName } = useBookNameDisplay()

const emit = defineEmits<{ (event:'closed', bookId:number):void }>()
const router = useRouter()
const preferencesStore = usePreferencesStore()
const { quickReaderWindow } = storeToRefs(preferencesStore)
const quickReaderOpen = ref(false)
const quickReaderBook = ref<Book | null>(null)
const quickReaderVersionId = ref<number | null>(null)
const quickReaderFrame = ref<HTMLIFrameElement | null>(null)
const quickReaderSize = ref({ width: 0, height: 0, left: 0, top: 0 })
const overlayTransparency = computed(() => quickReaderWindow.value?.overlayTransparency ?? 50)
const quickReaderMaskColor = computed(() => `rgba(0, 0, 0, ${1 - overlayTransparency.value / 100})`)

watch([quickReaderFrame, quickReaderMaskColor], ([frame, color]) => {
  const overlay = frame?.closest<HTMLElement>('.book-quick-reader-overlay')
  overlay?.style.setProperty('--el-overlay-color-lighter', color)
  frame?.contentWindow?.postMessage({
    type: 'aibook:quick-reader-window-state',
    overlayTransparency: overlayTransparency.value,
  }, window.location.origin)
}, { flush: 'post' })

const setOverlayTransparency = (transparency: number) => {
  if (!Number.isInteger(transparency) || transparency < 0 || transparency > 100) return
  saveQuickReaderWindow(true, transparency)
}

const quickReaderResizeStart = ref<{
  pointerId: number
  x: number
  y: number
  width: number
  height: number
} | null>(null)

const quickReaderSrc = computed(() => {
  if (!quickReaderBook.value) return ''
  return router.resolve({
    name: 'Reader',
    params: { id: quickReaderBook.value.id },
    query: {
      quickWindow: '1',
      ...(quickReaderVersionId.value ? { versionId: String(quickReaderVersionId.value) } : {}),
    },
  }).href
})
const quickReaderDialogStyle = computed(() => ({
  width: `${quickReaderSize.value.width}px`,
  height: `${quickReaderSize.value.height}px`,
  margin: `${quickReaderSize.value.top}px 0 0 ${quickReaderSize.value.left}px`,
}))

const openQuickReader = (book: Book, versionId: number | null = null) => {
  const maxWidth = Math.max(120, window.innerWidth - 24)
  const maxHeight = Math.max(160, window.innerHeight - 24)
  const defaultWidth = Math.min(
    maxWidth,
    Math.max(Math.min(560, maxWidth), window.innerWidth * 0.96),
  )
  const defaultHeight = Math.min(
    maxHeight,
    Math.max(Math.min(360, maxHeight), window.innerHeight * 0.94),
  )
  const minWidth = Math.min(520, maxWidth)
  const minHeight = Math.min(320, maxHeight)
  const width = Math.min(
    maxWidth,
    Math.max(minWidth, quickReaderWindow.value?.width ?? defaultWidth),
  )
  const height = Math.min(
    maxHeight,
    Math.max(minHeight, quickReaderWindow.value?.height ?? defaultHeight),
  )
  const maxLeft = Math.max(12, window.innerWidth - width - 12)
  const maxTop = Math.max(12, window.innerHeight - height - 12)
  const left = Math.min(
    maxLeft,
    Math.max(12, quickReaderWindow.value?.left ?? (window.innerWidth - width) / 2),
  )
  const top = Math.min(
    maxTop,
    Math.max(12, quickReaderWindow.value?.top ?? (window.innerHeight - height) / 2),
  )

  quickReaderSize.value = {
    width: Math.round(width),
    height: Math.round(height),
    left: Math.round(left),
    top: Math.round(top),
  }
  quickReaderVersionId.value = versionId
  quickReaderBook.value = book
  quickReaderOpen.value = true
}

const saveQuickReaderWindow = (
  debounceRemote = true,
  transparency = overlayTransparency.value,
) => {
  preferencesStore.setQuickReaderWindow(
    {
      overlayTransparency: transparency,
      width: Math.round(quickReaderSize.value.width),
      height: Math.round(quickReaderSize.value.height),
      left: Math.round(quickReaderSize.value.left),
      top: Math.round(quickReaderSize.value.top),
    },
    true,
    debounceRemote,
  )
}

const constrainQuickReaderSize = (width: number, height: number) => {
  const { left, top } = quickReaderSize.value
  const maxWidth = Math.max(120, window.innerWidth - left - 12)
  const maxHeight = Math.max(160, window.innerHeight - top - 12)
  const minWidth = Math.min(520, maxWidth)
  const minHeight = Math.min(320, maxHeight)

  quickReaderSize.value = {
    ...quickReaderSize.value,
    width: Math.round(Math.min(maxWidth, Math.max(minWidth, width))),
    height: Math.round(Math.min(maxHeight, Math.max(minHeight, height))),
  }
}

const constrainQuickReaderPosition = (left: number, top: number) => {
  const { width, height } = quickReaderSize.value
  const maxLeft = Math.max(12, window.innerWidth - width - 12)
  const maxTop = Math.max(12, window.innerHeight - height - 12)

  quickReaderSize.value = {
    ...quickReaderSize.value,
    left: Math.round(Math.min(maxLeft, Math.max(12, left))),
    top: Math.round(Math.min(maxTop, Math.max(12, top))),
  }
}

const startQuickReaderResize = (event: PointerEvent) => {
  if (event.button !== 0 || !quickReaderOpen.value) return
  const handle = event.currentTarget
  if (!(handle instanceof HTMLElement)) return

  event.preventDefault()
  handle.setPointerCapture(event.pointerId)
  quickReaderResizeStart.value = {
    pointerId: event.pointerId,
    x: event.clientX,
    y: event.clientY,
    width: quickReaderSize.value.width,
    height: quickReaderSize.value.height,
  }
}

const updateQuickReaderResize = (event: PointerEvent) => {
  const start = quickReaderResizeStart.value
  if (!start || event.pointerId !== start.pointerId) return
  constrainQuickReaderSize(
    start.width + event.clientX - start.x,
    start.height + event.clientY - start.y,
  )
}

const stopQuickReaderResize = (event: PointerEvent) => {
  if (quickReaderResizeStart.value?.pointerId === event.pointerId) {
    quickReaderResizeStart.value = null
    saveQuickReaderWindow()
  }
}

const resizeQuickReaderWithKeyboard = (event: KeyboardEvent) => {
  const step = event.shiftKey ? 48 : 16
  const deltas: Record<string, [number, number]> = {
    ArrowRight: [step, 0],
    ArrowLeft: [-step, 0],
    ArrowDown: [0, step],
    ArrowUp: [0, -step],
  }
  const delta = deltas[event.key]
  if (!delta) return

  event.preventDefault()
  constrainQuickReaderSize(
    quickReaderSize.value.width + delta[0],
    quickReaderSize.value.height + delta[1],
  )
  saveQuickReaderWindow()
}

const handleQuickReaderViewportResize = () => {
  if (!quickReaderOpen.value) return
  const size = quickReaderSize.value
  const maxWidth = Math.max(120, window.innerWidth - 24)
  const maxHeight = Math.max(160, window.innerHeight - 24)
  const width = Math.min(size.width, maxWidth)
  const height = Math.min(size.height, maxHeight)

  quickReaderSize.value = {
    width,
    height,
    left: Math.max(12, Math.min(size.left, window.innerWidth - width - 12)),
    top: Math.max(12, Math.min(size.top, window.innerHeight - height - 12)),
  }
}

const handleQuickReaderWindowControl = (data: Record<string, unknown>) => {
  if (!quickReaderOpen.value) return
  if (data.action === 'ready') {
    quickReaderFrame.value?.contentWindow?.postMessage({
      type: 'aibook:quick-reader-window-state',
      overlayTransparency: overlayTransparency.value,
    }, window.location.origin)
  } else if (data.action === 'mask' && typeof data.value === 'number') {
    setOverlayTransparency(data.value)
  } else if (data.action === 'move') {
    if (typeof data.dx !== 'number' || typeof data.dy !== 'number'
        || !Number.isFinite(data.dx) || !Number.isFinite(data.dy)) return
    constrainQuickReaderPosition(
      quickReaderSize.value.left + data.dx,
      quickReaderSize.value.top + data.dy,
    )
    if (data.persist === true) saveQuickReaderWindow()
  } else if (data.action === 'move-end') {
    saveQuickReaderWindow()
  } else if (data.action === 'flush') {
    void preferencesStore.flushQuickReaderWindow()
  }
}

const handleQuickReaderMessage = (event: MessageEvent) => {
  if (event.origin !== window.location.origin) return
  if (event.source !== quickReaderFrame.value?.contentWindow) return
  if (!event.data || typeof event.data !== 'object') return
  if (event.data.type === 'aibook:quick-reader-window-control') {
    handleQuickReaderWindowControl(event.data)
  } else if (event.data.type === 'aibook:quick-reader-close') {
    quickReaderOpen.value = false
  }
}

const resetQuickReader = () => {
  void preferencesStore.flushQuickReaderWindow()
  if (quickReaderBook.value) emit('closed', quickReaderBook.value.id)
  quickReaderBook.value = null
  quickReaderVersionId.value = null
  quickReaderResizeStart.value = null
}


onMounted(() => {
  window.addEventListener('message', handleQuickReaderMessage)
  window.addEventListener('resize', handleQuickReaderViewportResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('message', handleQuickReaderMessage)
  window.removeEventListener('resize', handleQuickReaderViewportResize)
  if (quickReaderOpen.value) void preferencesStore.flushQuickReaderWindow()
})
defineExpose({ open:openQuickReader })
</script>

<style scoped>
.book-quick-reader-frame {
  display: block;
  width: 100%;
  height: 0;
  min-height: 0;
  flex: 1 1 auto;
  border: 0;
  background: var(--surface-card);
}

:global(.book-quick-reader-dialog.el-dialog) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  border-radius: 16px;
}

:global(.book-quick-reader-dialog .el-dialog__header) {
  display: none;
}

:global(.book-quick-reader-dialog .el-dialog__body) {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  overflow: hidden;
  padding: 0;
  border-radius: inherit;
}

.book-quick-reader-resize {
  position: absolute;
  right: 8px;
  bottom: 8px;
  z-index: 2;
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  padding: 0;
  border: 1px solid var(--border-color-light, rgba(80, 90, 100, 0.2));
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface-card, #fff) 88%, transparent);
  color: var(--text-secondary, #667085);
  cursor: nwse-resize;
  touch-action: none;
  user-select: none;
}

.book-quick-reader-resize::before {
  width: 11px;
  height: 11px;
  background: repeating-linear-gradient(
    135deg,
    transparent 0 3px,
    currentColor 3px 4px
  );
  clip-path: polygon(100% 0, 100% 100%, 0 100%);
  content: '';
}

.book-quick-reader-resize:focus-visible {
  outline: 2px solid var(--primary, #409eff);
  outline-offset: 2px;
}

</style>
