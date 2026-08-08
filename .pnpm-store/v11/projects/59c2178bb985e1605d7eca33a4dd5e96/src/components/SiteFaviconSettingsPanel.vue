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

  <Teleport to="body">
    <Transition name="crop-dialog">
      <div
        v-if="cropOpen"
        class="crop-overlay"
        role="presentation"
        @click.self="closeCropper"
      >
        <section
          ref="cropDialog"
          class="crop-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="crop-dialog-title"
        >
          <header class="crop-header">
            <div>
              <span class="crop-kicker">图标编辑器</span>
              <h2 id="crop-dialog-title">选择图像区域</h2>
              <p>拖动图片调整位置，缩放后保留方框内的内容。</p>
            </div>
            <button
              class="crop-close"
              type="button"
              aria-label="关闭图标编辑器"
              :disabled="saving"
              @click="closeCropper"
            >
              ×
            </button>
          </header>

          <div class="crop-body">
            <div class="crop-workbench">
              <div class="crop-stage">
                <canvas
                  ref="cropCanvas"
                  class="crop-canvas"
                  width="512"
                  height="512"
                  tabindex="0"
                  aria-label="图标裁剪区域，可拖动图片或使用方向键调整位置"
                  @pointerdown="startDrag"
                  @pointermove="moveDrag"
                  @pointerup="endDrag"
                  @pointercancel="endDrag"
                  @wheel.prevent="handleWheel"
                  @keydown="handleCropKeydown"
                />
                <div class="crop-grid" aria-hidden="true">
                  <i></i><i></i><i></i><i></i>
                </div>
                <span class="crop-corner crop-corner-tl" aria-hidden="true"></span>
                <span class="crop-corner crop-corner-tr" aria-hidden="true"></span>
                <span class="crop-corner crop-corner-bl" aria-hidden="true"></span>
                <span class="crop-corner crop-corner-br" aria-hidden="true"></span>
              </div>
              <p class="crop-drag-hint">按住并拖动图片 · 滚轮也可缩放</p>
            </div>

            <aside class="crop-panel">
              <div class="result-preview">
                <canvas ref="previewCanvas" width="64" height="64" aria-hidden="true" />
                <div>
                  <strong>图标预览</strong>
                  <span>浏览器标签页效果</span>
                </div>
              </div>

              <div class="source-meta">
                <span>原图</span>
                <strong>{{ cropImageWidth }} × {{ cropImageHeight }}</strong>
              </div>

              <div class="zoom-control">
                <div class="zoom-label">
                  <label for="favicon-zoom">缩放</label>
                  <output for="favicon-zoom">{{ zoomPercent }}%</output>
                </div>
                <div class="zoom-row">
                  <button
                    type="button"
                    aria-label="缩小图片"
                    :disabled="zoom <= MIN_ZOOM"
                    @click="adjustZoom(-0.1)"
                  >−</button>
                  <input
                    id="favicon-zoom"
                    v-model.number="zoom"
                    type="range"
                    :min="MIN_ZOOM"
                    :max="MAX_ZOOM"
                    step="0.01"
                    aria-label="图片缩放比例"
                    @input="updateZoom"
                  />
                  <button
                    type="button"
                    aria-label="放大图片"
                    :disabled="zoom >= MAX_ZOOM"
                    @click="adjustZoom(0.1)"
                  >+</button>
                </div>
              </div>

              <button class="reset-crop" type="button" @click="resetCrop">
                居中并重置缩放
              </button>
            </aside>
          </div>

          <footer class="crop-footer">
            <span>将生成 512 × 512 PNG 图标</span>
            <div>
              <button class="btn btn-text" type="button" :disabled="saving" @click="closeCropper">
                取消
              </button>
              <button class="btn btn-primary" type="button" :disabled="saving" @click="uploadCroppedIcon">
                {{ saving ? '正在上传...' : '应用并上传' }}
              </button>
            </div>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
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
const cropOpen = ref(false)
const cropDialog = ref<HTMLElement | null>(null)
const cropCanvas = ref<HTMLCanvasElement | null>(null)
const previewCanvas = ref<HTMLCanvasElement | null>(null)
const cropImageWidth = ref(0)
const cropImageHeight = ref(0)
const zoom = ref(1)
const MIN_ZOOM = 1
const MAX_ZOOM = 4
const CROP_SIZE = 512
const zoomPercent = computed(() => Math.round(zoom.value * 100))

let cropImage: HTMLImageElement | null = null
let cropObjectUrl: string | null = null
let cropFileName = 'favicon'
let offsetX = 0
let offsetY = 0
let dragging = false
let dragPointerId: number | null = null
let dragStartX = 0
let dragStartY = 0
let dragStartOffsetX = 0
let dragStartOffsetY = 0

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

const cleanupCropper = () => {
  cropOpen.value = false
  cropImage = null
  cropImageWidth.value = 0
  cropImageHeight.value = 0
  if (cropObjectUrl) {
    URL.revokeObjectURL(cropObjectUrl)
    cropObjectUrl = null
  }
}

const closeCropper = () => {
  if (!saving.value) cleanupCropper()
}

const clampOffsets = () => {
  if (!cropImage) return
  const baseScale = Math.max(CROP_SIZE / cropImage.width, CROP_SIZE / cropImage.height)
  const scale = baseScale * zoom.value
  const maxX = Math.max(0, (cropImage.width * scale - CROP_SIZE) / 2)
  const maxY = Math.max(0, (cropImage.height * scale - CROP_SIZE) / 2)
  offsetX = Math.max(-maxX, Math.min(maxX, offsetX))
  offsetY = Math.max(-maxY, Math.min(maxY, offsetY))
}

const renderCrop = () => {
  const canvas = cropCanvas.value
  if (!canvas || !cropImage) return
  clampOffsets()
  const context = canvas.getContext('2d')
  if (!context) return

  const baseScale = Math.max(CROP_SIZE / cropImage.width, CROP_SIZE / cropImage.height)
  const scale = baseScale * zoom.value
  const width = cropImage.width * scale
  const height = cropImage.height * scale
  context.clearRect(0, 0, CROP_SIZE, CROP_SIZE)
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(
    cropImage,
    (CROP_SIZE - width) / 2 + offsetX,
    (CROP_SIZE - height) / 2 + offsetY,
    width,
    height,
  )

  const preview = previewCanvas.value
  const previewContext = preview?.getContext('2d')
  if (preview && previewContext) {
    previewContext.clearRect(0, 0, preview.width, preview.height)
    previewContext.drawImage(canvas, 0, 0, preview.width, preview.height)
  }
}

const resetCrop = () => {
  zoom.value = MIN_ZOOM
  offsetX = 0
  offsetY = 0
  renderCrop()
}

const updateZoom = () => {
  zoom.value = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom.value))
  renderCrop()
}

const adjustZoom = (amount: number) => {
  zoom.value = Math.round(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom.value + amount)) * 100) / 100
  renderCrop()
}

const handleWheel = (event: WheelEvent) => {
  adjustZoom(event.deltaY < 0 ? 0.08 : -0.08)
}

const startDrag = (event: PointerEvent) => {
  const canvas = cropCanvas.value
  if (!canvas) return
  dragging = true
  dragPointerId = event.pointerId
  dragStartX = event.clientX
  dragStartY = event.clientY
  dragStartOffsetX = offsetX
  dragStartOffsetY = offsetY
  canvas.setPointerCapture(event.pointerId)
}

const moveDrag = (event: PointerEvent) => {
  const canvas = cropCanvas.value
  if (!dragging || !canvas || dragPointerId !== event.pointerId) return
  const displayScale = CROP_SIZE / canvas.getBoundingClientRect().width
  offsetX = dragStartOffsetX + (event.clientX - dragStartX) * displayScale
  offsetY = dragStartOffsetY + (event.clientY - dragStartY) * displayScale
  renderCrop()
}

const endDrag = (event: PointerEvent) => {
  if (dragPointerId !== event.pointerId) return
  dragging = false
  dragPointerId = null
  cropCanvas.value?.releasePointerCapture(event.pointerId)
}

const handleCropKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    closeCropper()
    return
  }
  const movement: Record<string, [number, number]> = {
    ArrowLeft: [-4, 0],
    ArrowRight: [4, 0],
    ArrowUp: [0, -4],
    ArrowDown: [0, 4],
  }
  const delta = movement[event.key]
  if (!delta) return
  event.preventDefault()
  offsetX += delta[0]
  offsetY += delta[1]
  renderCrop()
}

const openCropper = (file: File) => {
  cleanupCropper()
  const objectUrl = URL.createObjectURL(file)
  cropObjectUrl = objectUrl
  cropFileName = file.name.replace(/\.[^.]+$/, '') || 'favicon'
  const image = new Image()
  image.onload = async () => {
    if (cropObjectUrl !== objectUrl) return
    cropImage = image
    cropImageWidth.value = image.naturalWidth
    cropImageHeight.value = image.naturalHeight
    zoom.value = MIN_ZOOM
    offsetX = 0
    offsetY = 0
    cropOpen.value = true
    await nextTick()
    renderCrop()
    cropCanvas.value?.focus()
  }
  image.onerror = () => {
    if (cropObjectUrl === objectUrl) cleanupCropper()
    message.error('无法读取这张图片，请尝试 PNG、JPG 或 WebP 格式')
  }
  image.src = objectUrl
}

const handleFileSelected = (event: Event) => {
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

  openCropper(file)
}

const canvasToBlob = (canvas: HTMLCanvasElement) => new Promise<Blob>((resolve, reject) => {
  canvas.toBlob((blob) => {
    if (blob) resolve(blob)
    else reject(new Error('图标生成失败'))
  }, 'image/png')
})

const uploadCroppedIcon = async () => {
  const canvas = cropCanvas.value
  if (!canvas || !cropImage) return
  saving.value = true
  try {
    const blob = await canvasToBlob(canvas)
    if (blob.size > 2 * 1024 * 1024) {
      message.warning('裁剪后的图标超过 2MB，请适当缩小后重试')
      return
    }
    const file = new File([blob], `${cropFileName}.png`, { type: 'image/png' })
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post<SiteFaviconStatus>('/api/site/favicon', form)
    applyStatus(data)
    cleanupCropper()
    message.success('网站图标已裁剪并更新')
  } catch (error: any) {
    message.error(error.response?.data?.message || error.message || '网站图标上传失败')
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
onBeforeUnmount(cleanupCropper)
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

.crop-overlay {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(7, 17, 13, 0.72);
  backdrop-filter: blur(10px);
}

.crop-dialog {
  width: min(880px, 100%);
  max-height: calc(100vh - 48px);
  overflow: auto;
  color: var(--text-primary);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-xl);
  background: var(--surface-elevated);
  box-shadow: var(--shadow-xl), 0 30px 80px rgba(0, 0, 0, 0.24);
}

.crop-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px 20px;
  border-bottom: 1px solid var(--border-color-light);
}

.crop-kicker {
  display: block;
  margin-bottom: 4px;
  color: var(--primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.crop-header h2 {
  margin: 0;
  font-size: 22px;
  line-height: 1.25;
}

.crop-header p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.crop-close {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--text-secondary);
  font: inherit;
  font-size: 25px;
  line-height: 1;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 50%;
  background: transparent;
  transition: 160ms ease;
}

.crop-close:hover:not(:disabled) {
  color: var(--text-primary);
  border-color: var(--border-color);
  background: var(--surface-hover);
}

.crop-body {
  display: grid;
  grid-template-columns: minmax(300px, 1fr) 240px;
  gap: 30px;
  padding: 28px;
}

.crop-workbench {
  min-width: 0;
}

.crop-stage {
  position: relative;
  width: min(100%, 440px);
  aspect-ratio: 1;
  margin: 0 auto;
  overflow: hidden;
  cursor: grab;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: 6px;
  background-color: #17201c;
  background-image:
    linear-gradient(45deg, rgba(255, 255, 255, 0.035) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(255, 255, 255, 0.035) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(255, 255, 255, 0.035) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(255, 255, 255, 0.035) 75%);
  background-position: 0 0, 0 8px, 8px -8px, -8px 0;
  background-size: 16px 16px;
  box-shadow: 0 18px 44px rgba(8, 20, 14, 0.2);
  touch-action: none;
  user-select: none;
}

.crop-stage:active {
  cursor: grabbing;
}

.crop-canvas {
  display: block;
  width: 100%;
  height: 100%;
  outline: none;
}

.crop-canvas:focus-visible {
  box-shadow: inset 0 0 0 3px var(--primary-light);
}

.crop-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.crop-grid i {
  position: absolute;
  display: block;
  background: rgba(255, 255, 255, 0.3);
}

.crop-grid i:nth-child(1),
.crop-grid i:nth-child(2) {
  top: 0;
  bottom: 0;
  width: 1px;
}

.crop-grid i:nth-child(1) { left: 33.333%; }
.crop-grid i:nth-child(2) { left: 66.666%; }

.crop-grid i:nth-child(3),
.crop-grid i:nth-child(4) {
  right: 0;
  left: 0;
  height: 1px;
}

.crop-grid i:nth-child(3) { top: 33.333%; }
.crop-grid i:nth-child(4) { top: 66.666%; }

.crop-corner {
  position: absolute;
  width: 22px;
  height: 22px;
  pointer-events: none;
  border-color: white;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.45));
}

.crop-corner-tl { top: 8px; left: 8px; border-top: 3px solid; border-left: 3px solid; }
.crop-corner-tr { top: 8px; right: 8px; border-top: 3px solid; border-right: 3px solid; }
.crop-corner-bl { bottom: 8px; left: 8px; border-bottom: 3px solid; border-left: 3px solid; }
.crop-corner-br { right: 8px; bottom: 8px; border-right: 3px solid; border-bottom: 3px solid; }

.crop-drag-hint {
  margin: 12px 0 0;
  color: var(--text-tertiary);
  font-size: 12px;
  text-align: center;
}

.crop-panel {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 4px 0;
}

.result-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color-light);
}

.result-preview canvas {
  width: 64px;
  height: 64px;
  flex: 0 0 auto;
  border: 1px solid var(--border-color-light);
  border-radius: 14px;
  background: var(--surface-hover);
  box-shadow: var(--shadow-sm);
}

.result-preview strong,
.result-preview span {
  display: block;
}

.result-preview strong {
  font-size: 14px;
}

.result-preview span {
  margin-top: 3px;
  color: var(--text-tertiary);
  font-size: 11px;
}

.source-meta,
.zoom-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 12px;
}

.source-meta strong,
.zoom-label output {
  color: var(--text-primary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.zoom-label label {
  font-weight: 600;
}

.zoom-row {
  display: grid;
  grid-template-columns: 34px 1fr 34px;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.zoom-row button {
  width: 34px;
  height: 34px;
  padding: 0;
  color: var(--text-primary);
  font: inherit;
  font-size: 18px;
  cursor: pointer;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--surface-card);
}

.zoom-row button:hover:not(:disabled) {
  color: var(--primary);
  border-color: var(--primary);
}

.zoom-row button:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.zoom-row input {
  width: 100%;
  accent-color: var(--primary);
  cursor: pointer;
}

.reset-crop {
  align-self: flex-start;
  padding: 0;
  color: var(--primary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  border: 0;
  background: transparent;
}

.reset-crop:hover {
  text-decoration: underline;
}

.crop-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 28px;
  border-top: 1px solid var(--border-color-light);
}

.crop-footer > span {
  color: var(--text-tertiary);
  font-size: 12px;
}

.crop-footer > div {
  display: flex;
  gap: 10px;
}

.crop-dialog-enter-active,
.crop-dialog-leave-active {
  transition: opacity 180ms ease;
}

.crop-dialog-enter-active .crop-dialog,
.crop-dialog-leave-active .crop-dialog {
  transition: transform 180ms ease, opacity 180ms ease;
}

.crop-dialog-enter-from,
.crop-dialog-leave-to {
  opacity: 0;
}

.crop-dialog-enter-from .crop-dialog,
.crop-dialog-leave-to .crop-dialog {
  opacity: 0;
  transform: translateY(10px) scale(0.985);
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

  .crop-overlay {
    align-items: end;
    padding: 0;
  }

  .crop-dialog {
    width: 100%;
    max-height: 94vh;
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  }

  .crop-header,
  .crop-body,
  .crop-footer {
    padding-right: 18px;
    padding-left: 18px;
  }

  .crop-body {
    grid-template-columns: 1fr;
    gap: 22px;
  }

  .crop-stage {
    width: min(100%, 360px);
  }

  .crop-panel {
    gap: 16px;
  }

  .result-preview {
    display: none;
  }

  .crop-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .crop-footer > span {
    text-align: center;
  }

  .crop-footer > div {
    display: grid;
    grid-template-columns: 1fr 1.4fr;
  }
}
</style>
