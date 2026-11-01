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
      <header class="book-quick-reader-window-toolbar" aria-label="小窗窗口控制">
        <span class="book-quick-reader-window-label">小窗阅读</span>
        <div class="book-quick-reader-mask-controls">
          <button
            class="book-quick-reader-mask-toggle"
            type="button"
            :aria-expanded="maskSettingsOpen"
            aria-controls="quick-reader-mask-settings"
            @click="maskSettingsOpen = !maskSettingsOpen"
          >
            蒙板
          </button>
          <section
            v-if="maskSettingsOpen"
            id="quick-reader-mask-settings"
            class="book-quick-reader-mask-settings"
            aria-label="小窗外部蒙板设置"
          >
            <label for="quick-reader-mask-transparency">
              蒙板透明度 <output>{{ overlayTransparency }}%</output>
            </label>
            <input
              id="quick-reader-mask-transparency"
              type="range"
              min="0"
              max="100"
              step="1"
              :value="overlayTransparency"
              @input="setOverlayTransparency"
              @change="preferencesStore.flushQuickReaderWindow()"
            />
            <p>0% 完全遮黑，100% 完全透明</p>
          </section>
        </div>
        <button
          class="book-quick-reader-drag"
          type="button"
          aria-label="窗口拖动手柄：拖动调整位置，也可使用方向键"
          title="拖动移动窗口，方向键微调位置"
          @pointerdown="startQuickReaderMove"
          @pointermove="updateQuickReaderMove"
          @pointerup="stopQuickReaderMove"
          @pointercancel="stopQuickReaderMove"
          @lostpointercapture="stopQuickReaderMove"
          @keydown="moveQuickReaderWithKeyboard"
        />
      </header>
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
const quickReaderFrame = ref<HTMLIFrameElement | null>(null)
const quickReaderSize = ref({ width: 0, height: 0, left: 0, top: 0 })
const maskSettingsOpen = ref(false)
const overlayTransparency = computed(() => quickReaderWindow.value?.overlayTransparency ?? 50)
const quickReaderMaskColor = computed(() => `rgba(0, 0, 0, ${1 - overlayTransparency.value / 100})`)

watch([quickReaderFrame, quickReaderMaskColor], ([frame, color]) => {
  const overlay = frame?.closest<HTMLElement>('.book-quick-reader-overlay')
  overlay?.style.setProperty('--el-overlay-color-lighter', color)
}, { flush: 'post' })

const setOverlayTransparency = (event: Event) => {
  const transparency = Number((event.target as HTMLInputElement).value)
  if (!Number.isInteger(transparency) || transparency < 0 || transparency > 100) return
  saveQuickReaderWindow(true, transparency)
}

const quickReaderMoveStart = ref<{
  pointerId: number
  x: number
  y: number
  left: number
  top: number
} | null>(null)
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
    query: { quickWindow: '1' },
  }).href
})
const quickReaderDialogStyle = computed(() => ({
  width: `${quickReaderSize.value.width}px`,
  height: `${quickReaderSize.value.height}px`,
  margin: `${quickReaderSize.value.top}px 0 0 ${quickReaderSize.value.left}px`,
}))

const openQuickReader = (book: Book) => {
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

const startQuickReaderMove = (event: PointerEvent) => {
  if (event.button !== 0 || !quickReaderOpen.value) return
  const handle = event.currentTarget
  if (!(handle instanceof HTMLElement)) return

  event.preventDefault()
  handle.setPointerCapture(event.pointerId)
  quickReaderMoveStart.value = {
    pointerId: event.pointerId,
    x: event.clientX,
    y: event.clientY,
    left: quickReaderSize.value.left,
    top: quickReaderSize.value.top,
  }
}

const updateQuickReaderMove = (event: PointerEvent) => {
  const start = quickReaderMoveStart.value
  if (!start || event.pointerId !== start.pointerId) return
  constrainQuickReaderPosition(
    start.left + event.clientX - start.x,
    start.top + event.clientY - start.y,
  )
}

const stopQuickReaderMove = (event: PointerEvent) => {
  if (quickReaderMoveStart.value?.pointerId === event.pointerId) {
    quickReaderMoveStart.value = null
    saveQuickReaderWindow()
  }
}

const moveQuickReaderWithKeyboard = (event: KeyboardEvent) => {
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
  constrainQuickReaderPosition(
    quickReaderSize.value.left + delta[0],
    quickReaderSize.value.top + delta[1],
  )
  saveQuickReaderWindow()
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

const handleQuickReaderMessage = (event: MessageEvent) => {
  if (event.origin !== window.location.origin) return
  if (event.source !== quickReaderFrame.value?.contentWindow) return
  if (!event.data || typeof event.data !== 'object') return
  if (event.data.type === 'aibook:quick-reader-close') {
    quickReaderOpen.value = false
  }
}

const resetQuickReader = () => {
  maskSettingsOpen.value = false
  void preferencesStore.flushQuickReaderWindow()
  if (quickReaderBook.value) emit('closed', quickReaderBook.value.id)
  quickReaderBook.value = null
  quickReaderMoveStart.value = null
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
.book-quick-reader-window-toolbar {
  position: relative;
  z-index: 3;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-height: 52px;
  box-sizing: border-box;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-color-light);
  background: var(--surface-card);
}

.book-quick-reader-window-label {
  min-width: 0;
  margin-right: auto;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-quick-reader-mask-controls {
  position: relative;
  flex: none;
}

.book-quick-reader-mask-toggle {
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--border-color-light);
  border-radius: 10px;
  background: var(--surface-card);
  color: var(--text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.book-quick-reader-mask-toggle:hover,
.book-quick-reader-mask-toggle[aria-expanded="true"] {
  background: var(--surface-hover);
  color: var(--primary);
}

.book-quick-reader-mask-toggle:focus-visible,
.book-quick-reader-mask-settings input:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.book-quick-reader-mask-settings {
  position: absolute;
  top: 44px;
  right: 0;
  width: min(240px, calc(100vw - 100px));
  padding: 14px;
  border: 1px solid var(--border-color-light);
  border-radius: 12px;
  background: var(--surface-card);
  color: var(--text-primary);
}

.book-quick-reader-mask-settings label {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.book-quick-reader-mask-settings input {
  width: 100%;
  margin: 14px 0 8px;
  accent-color: var(--primary);
}

.book-quick-reader-mask-settings p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 11px;
}

.book-quick-reader-frame {
  display: block;
  width: 100%;
  height: 0;
  min-height: 0;
  flex: 1 1 auto;
  border: 0;
  background: var(--surface-card);
}

.book-quick-reader-drag {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  place-items: center;
  padding: 0;
  border: 1px solid var(--border-color-light, rgba(80, 90, 100, 0.2));
  border-radius: 10px;
  background: color-mix(in srgb, var(--surface-card, #fff) 88%, transparent);
  color: var(--text-secondary, #667085);
  cursor: move;
  touch-action: none;
  user-select: none;
}

.book-quick-reader-drag::before {
  width: 12px;
  height: 18px;
  background-image: radial-gradient(circle, currentColor 1.3px, transparent 1.6px);
  background-size: 6px 6px;
  content: '';
  opacity: 0.75;
}

.book-quick-reader-drag:hover {
  background: var(--surface-card, #fff);
  color: var(--primary, #409eff);
}

.book-quick-reader-drag:active {
  cursor: grabbing;
}

.book-quick-reader-drag:focus-visible {
  outline: 2px solid var(--primary, #409eff);
  outline-offset: -2px;
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
