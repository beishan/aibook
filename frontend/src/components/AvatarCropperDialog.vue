<template>
  <el-dialog
    :model-value="visible"
    title="编辑头像"
    width="min(560px, 94vw)"
    class="avatar-crop-dialog"
    top="4vh"
    append-to-body
    destroy-on-close
    @opened="handleOpened"
    @close="handleClose"
  >
    <div class="crop-editor">
      <p class="crop-hint">拖动图片选择头像区域，使用滑块放大或缩小。</p>
      <div
        ref="stageRef"
        class="crop-stage"
        @pointerdown="startDragging"
        @pointermove="dragImage"
        @pointerup="stopDragging"
        @pointercancel="stopDragging"
      >
        <img
          ref="imageRef"
          :src="imageUrl"
          :style="imageStyle"
          alt="待裁剪头像"
          draggable="false"
          @load="handleImageLoaded"
          @error="handleImageError"
        />
        <div class="crop-guide" aria-hidden="true"></div>
      </div>

      <div class="zoom-controls">
        <span>🔍</span>
        <el-slider
          v-model="zoom"
          :min="1"
          :max="3"
          :step="0.01"
          :show-tooltip="false"
          @input="clampOffset"
        />
        <span class="zoom-value">{{ Math.round(zoom * 100) }}%</span>
        <button type="button" class="btn btn-text" @click="resetCrop">重置</button>
      </div>
    </div>

    <template #footer>
      <button type="button" class="btn" :disabled="processing" @click="emit('close')">
        取消
      </button>
      <button
        type="button"
        class="btn btn-primary"
        :disabled="!imageReady || processing"
        @click="confirmCrop"
      >
        {{ processing ? '处理中...' : '确认并上传' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { message } from '@/utils/message'

const OUTPUT_SIZE = 512

const props = defineProps<{
  visible: boolean
  imageUrl: string
  originalName?: string
}>()

const emit = defineEmits<{
  close: []
  confirm: [file: File]
}>()

const stageRef = ref<HTMLElement | null>(null)
const imageRef = ref<HTMLImageElement | null>(null)
const stageSize = ref(360)
const naturalWidth = ref(0)
const naturalHeight = ref(0)
const zoom = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const processing = ref(false)
const dragging = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)
const dragOriginX = ref(0)
const dragOriginY = ref(0)
let resizeObserver: ResizeObserver | null = null

const imageReady = computed(() => naturalWidth.value > 0 && naturalHeight.value > 0)
const baseScale = computed(() => {
  if (!imageReady.value) return 1
  return Math.max(
    stageSize.value / naturalWidth.value,
    stageSize.value / naturalHeight.value
  )
})
const renderedWidth = computed(() => naturalWidth.value * baseScale.value * zoom.value)
const renderedHeight = computed(() => naturalHeight.value * baseScale.value * zoom.value)
const imageStyle = computed(() => ({
  width: `${renderedWidth.value}px`,
  height: `${renderedHeight.value}px`,
  transform: `translate(-50%, -50%) translate(${offsetX.value}px, ${offsetY.value}px)`,
}))

const syncStageSize = () => {
  const size = stageRef.value?.getBoundingClientRect().width
  if (size) stageSize.value = size
  clampOffset()
}

const clampOffset = () => {
  const maxX = Math.max(0, (renderedWidth.value - stageSize.value) / 2)
  const maxY = Math.max(0, (renderedHeight.value - stageSize.value) / 2)
  offsetX.value = Math.min(maxX, Math.max(-maxX, offsetX.value))
  offsetY.value = Math.min(maxY, Math.max(-maxY, offsetY.value))
}

const resetCrop = () => {
  zoom.value = 1
  offsetX.value = 0
  offsetY.value = 0
}

const handleOpened = async () => {
  await nextTick()
  syncStageSize()
  resizeObserver?.disconnect()
  resizeObserver = new ResizeObserver(syncStageSize)
  if (stageRef.value) resizeObserver.observe(stageRef.value)
}

const handleImageLoaded = () => {
  naturalWidth.value = imageRef.value?.naturalWidth || 0
  naturalHeight.value = imageRef.value?.naturalHeight || 0
  resetCrop()
  syncStageSize()
}

const handleImageError = () => {
  message.error('无法读取该图片，请选择其他图片')
  handleClose()
}

const handleClose = () => {
  dragging.value = false
  resizeObserver?.disconnect()
  emit('close')
}

const startDragging = (event: PointerEvent) => {
  if (!imageReady.value) return
  dragging.value = true
  dragStartX.value = event.clientX
  dragStartY.value = event.clientY
  dragOriginX.value = offsetX.value
  dragOriginY.value = offsetY.value
  stageRef.value?.setPointerCapture(event.pointerId)
}

const dragImage = (event: PointerEvent) => {
  if (!dragging.value) return
  offsetX.value = dragOriginX.value + event.clientX - dragStartX.value
  offsetY.value = dragOriginY.value + event.clientY - dragStartY.value
  clampOffset()
}

const stopDragging = (event: PointerEvent) => {
  if (!dragging.value) return
  dragging.value = false
  if (stageRef.value?.hasPointerCapture(event.pointerId)) {
    stageRef.value.releasePointerCapture(event.pointerId)
  }
}

const canvasBlob = (canvas: HTMLCanvasElement) => new Promise<Blob>((resolve, reject) => {
  canvas.toBlob(
    blob => blob ? resolve(blob) : reject(new Error('头像生成失败')),
    'image/jpeg',
    0.92
  )
})

const confirmCrop = async () => {
  if (!imageRef.value || !imageReady.value) return
  processing.value = true
  try {
    syncStageSize()
    const displayScale = baseScale.value * zoom.value
    const sourceSize = stageSize.value / displayScale
    const sourceX = (naturalWidth.value - sourceSize) / 2 - offsetX.value / displayScale
    const sourceY = (naturalHeight.value - sourceSize) / 2 - offsetY.value / displayScale
    const canvas = document.createElement('canvas')
    canvas.width = OUTPUT_SIZE
    canvas.height = OUTPUT_SIZE
    const context = canvas.getContext('2d')
    if (!context) throw new Error('浏览器不支持图片裁剪')
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE)
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = 'high'
    context.drawImage(
      imageRef.value,
      sourceX,
      sourceY,
      sourceSize,
      sourceSize,
      0,
      0,
      OUTPUT_SIZE,
      OUTPUT_SIZE
    )
    const blob = await canvasBlob(canvas)
    const baseName = (props.originalName || 'avatar').replace(/\.[^.]+$/, '')
    emit('confirm', new File([blob], `${baseName}-avatar.jpg`, { type: 'image/jpeg' }))
  } catch (error) {
    message.error(error instanceof Error ? error.message : '头像处理失败')
  } finally {
    processing.value = false
  }
}

watch(() => props.imageUrl, () => {
  naturalWidth.value = 0
  naturalHeight.value = 0
  resetCrop()
})

onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<style scoped>
.crop-editor {
  display: grid;
  justify-items: center;
  gap: var(--spacing-md);
}

.crop-hint {
  width: 100%;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  text-align: center;
}

.crop-stage {
  position: relative;
  width: min(360px, 78vw);
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: var(--radius-md);
  background:
    linear-gradient(45deg, #d9d9d9 25%, transparent 25%),
    linear-gradient(-45deg, #d9d9d9 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #d9d9d9 75%),
    linear-gradient(-45deg, transparent 75%, #d9d9d9 75%),
    #f2f2f2;
  background-position: 0 0, 0 8px, 8px -8px, -8px 0;
  background-size: 16px 16px;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.crop-stage:active {
  cursor: grabbing;
}

.crop-stage img {
  position: absolute;
  top: 50%;
  left: 50%;
  max-width: none;
  pointer-events: none;
  user-select: none;
}

.crop-guide {
  position: absolute;
  inset: 0;
  border: 2px solid rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  box-shadow: 0 0 0 999px rgba(0, 0, 0, 0.42);
  pointer-events: none;
}

.zoom-controls {
  display: flex;
  width: min(420px, 100%);
  align-items: center;
  gap: var(--spacing-md);
}

.zoom-controls :deep(.el-slider) {
  flex: 1;
}

.zoom-value {
  width: 44px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  text-align: right;
}

:global(.avatar-crop-dialog) {
  display: flex;
  max-height: 92vh;
  flex-direction: column;
  overflow: hidden;
}

:global(.avatar-crop-dialog .el-dialog__body) {
  overflow-y: auto;
}
</style>
