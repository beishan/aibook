<template>
  <div class="pdf-reader" :aria-busy="loading">
    <div class="pdf-toolbar glass" role="toolbar" aria-label="PDF 阅读工具">
      <button type="button" :disabled="!canPrevious || loading" aria-label="上一页" @click="previous">
        ‹
      </button>
      <label class="pdf-page-control">
        <span class="sr-only">当前页</span>
        <input
          v-model.number="pageInput"
          type="number"
          min="1"
          :max="totalPages || 1"
          :disabled="loading"
          @change="commitPageInput"
          @keydown.enter.prevent="commitPageInput"
        />
        <span>/ {{ totalPages || 1 }}</span>
      </label>
      <span class="pdf-toolbar-separator" aria-hidden="true"></span>
      <button type="button" :disabled="loading" aria-label="缩小" @click="zoomOut">−</button>
      <span class="pdf-zoom-value">{{ Math.round(zoom * 100) }}%</span>
      <button type="button" :disabled="loading" aria-label="放大" @click="zoomIn">＋</button>
      <button type="button" :disabled="loading" @click="fitWidth">适宽</button>
      <button type="button" :disabled="!canNext || loading" aria-label="下一页" @click="next">
        ›
      </button>
    </div>

    <div ref="viewportElement" class="pdf-viewport">
      <div v-if="errorMessage" class="pdf-state" role="alert">
        <span aria-hidden="true">⚠️</span>
        <p>{{ errorMessage }}</p>
        <button type="button" @click="loadDocument">重新加载 PDF</button>
      </div>
      <div v-else-if="loading" class="pdf-state" role="status">
        <span class="pdf-spinner" aria-hidden="true"></span>
        <p>{{ documentLoading ? '正在解析 PDF…' : '正在渲染页面…' }}</p>
      </div>
      <div
        v-show="!errorMessage"
        class="pdf-spread"
        :class="{ 'pdf-spread--double': isDoublePage && visiblePages.length > 1 }"
      >
        <div
          v-for="page in visiblePages"
          :key="page"
          :ref="element => setPageElement(page, element)"
          class="pdf-page"
          :class="{ 'pdf-page--active': page === currentPage }"
          @click="selectPage(page)"
        >
          <canvas :ref="element => setCanvasElement(page, element)" :aria-label="`PDF 第 ${page} 页`"></canvas>
          <div :ref="element => setTextLayerElement(page, element)" class="textLayer pdf-text-layer"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import type {
  PDFDocumentLoadingTask,
  PDFDocumentProxy,
  PDFPageProxy,
  RenderTask,
  TextLayer,
} from 'pdfjs-dist'

const props = withDefaults(defineProps<{
  source: { url: string; httpHeaders?: Record<string, string> } | null
  initialPage?: number
  displayMode?: 'single' | 'double'
}>(), {
  initialPage: 1,
  displayMode: 'single',
})

const emit = defineEmits<{
  (event: 'page-change', value: { page: number; total: number; progress: number }): void
  (event: 'outline', value: Array<{ title: string; page: number; level: number }>): void
  (event: 'error', value: string): void
}>()

const viewportElement = ref<HTMLElement>()
const currentPage = ref(1)
const pageInput = ref(1)
const totalPages = ref(0)
const zoom = ref(1)
const loading = ref(false)
const documentLoading = ref(false)
const errorMessage = ref('')

let pdfDocument: PDFDocumentProxy | null = null
let loadingTask: PDFDocumentLoadingTask | null = null
const pageElements = new Map<number, HTMLElement>()
const canvasElements = new Map<number, HTMLCanvasElement>()
const textLayerElements = new Map<number, HTMLElement>()
const renderTasks = new Map<number, RenderTask>()
const textLayers = new Map<number, TextLayer>()
let resizeObserver: ResizeObserver | null = null
let renderSequence = 0
let documentSequence = 0
let searchSequence = 0

const clampPage = (page: number) => Math.min(Math.max(1, Math.round(page || 1)), totalPages.value || 1)
const isDoublePage = computed(() => props.displayMode === 'double')
const spreadStart = (page: number) => page <= 1 ? 1 : page - (page % 2)
const visiblePages = computed(() => {
  if (!isDoublePage.value || totalPages.value <= 1) return [currentPage.value]
  const start = spreadStart(currentPage.value)
  if (start === 1) return [1]
  return [start, start + 1].filter(page => page <= totalPages.value)
})
const canPrevious = computed(() => isDoublePage.value
  ? spreadStart(currentPage.value) > 1
  : currentPage.value > 1)
const canNext = computed(() => isDoublePage.value
  ? spreadStart(currentPage.value) + visiblePages.value.length <= totalPages.value
  : currentPage.value < totalPages.value)

type TemplateElement = Element | ComponentPublicInstance | null
const setElement = <T extends Element>(map: Map<number, T>, page: number, element: TemplateElement) => {
  if (element instanceof Element) map.set(page, element as T)
  else map.delete(page)
}
const setPageElement = (page: number, element: TemplateElement) => setElement(pageElements, page, element)
const setCanvasElement = (page: number, element: TemplateElement) => setElement(canvasElements, page, element)
const setTextLayerElement = (page: number, element: TemplateElement) => setElement(textLayerElements, page, element)

const reportPage = () => {
  const progress = totalPages.value <= 1
    ? 100
    : Math.round(((currentPage.value - 1) / (totalPages.value - 1)) * 100)
  emit('page-change', { page: currentPage.value, total: totalPages.value, progress })
}

const cancelRendering = () => {
  renderTasks.forEach(task => task.cancel())
  textLayers.forEach(layer => layer.cancel())
  renderTasks.clear()
  textLayers.clear()
}

const renderPdfPage = async (pageNumber: number, sequence: number) => {
  if (!pdfDocument || !viewportElement.value) return
  const canvas = canvasElements.get(pageNumber)
  const layer = textLayerElements.get(pageNumber)
  const pageElement = pageElements.get(pageNumber)
  if (!canvas || !layer || !pageElement) return
  const pdfjs = await import('pdfjs-dist')
  const page: PDFPageProxy = await pdfDocument.getPage(pageNumber)
  if (sequence !== renderSequence) return
  const originalViewport = page.getViewport({ scale: 1 })
  const horizontalPadding = isDoublePage.value ? 72 : 48
  const pageGap = isDoublePage.value && visiblePages.value.length > 1 ? 20 : 0
  const pageSlots = isDoublePage.value && visiblePages.value.length > 1 ? 2 : 1
  const availableWidth = Math.max(240, (viewportElement.value.clientWidth - horizontalPadding - pageGap) / pageSlots)
  const fitScale = availableWidth / originalViewport.width
  const viewport = page.getViewport({ scale: fitScale * zoom.value })
  const outputScale = Math.min(window.devicePixelRatio || 1, 2)
  const context = canvas.getContext('2d', { alpha: false })
  if (!context) throw new Error('浏览器无法创建 PDF 画布')

  canvas.width = Math.floor(viewport.width * outputScale)
  canvas.height = Math.floor(viewport.height * outputScale)
  canvas.style.width = `${Math.floor(viewport.width)}px`
  canvas.style.height = `${Math.floor(viewport.height)}px`
  pageElement.style.width = canvas.style.width
  pageElement.style.height = canvas.style.height

  const task = page.render({
    canvas,
    canvasContext: context,
    viewport,
    transform: outputScale === 1 ? undefined : [outputScale, 0, 0, outputScale, 0, 0],
  })
  renderTasks.set(pageNumber, task)
  await task.promise
  if (sequence !== renderSequence) return

  layer.replaceChildren()
  layer.style.width = canvas.style.width
  layer.style.height = canvas.style.height
  layer.style.setProperty('--total-scale-factor', String(viewport.scale))
  const renderedTextLayer = new pdfjs.TextLayer({
    textContentSource: page.streamTextContent({ includeMarkedContent: true }),
    container: layer,
    viewport,
  })
  textLayers.set(pageNumber, renderedTextLayer)
  await renderedTextLayer.render()
}

const renderPage = async () => {
  if (!pdfDocument || !viewportElement.value) return
  const sequence = ++renderSequence
  loading.value = true
  errorMessage.value = ''
  cancelRendering()

  try {
    await nextTick()
    await Promise.all(visiblePages.value.map(page => renderPdfPage(page, sequence)))
    if (sequence !== renderSequence) return
    pageInput.value = currentPage.value
    reportPage()
  } catch (error: any) {
    if (sequence !== renderSequence) return
    if (error?.name === 'RenderingCancelledException') return
    const message = error?.name === 'PasswordException'
      ? '该 PDF 受密码保护，暂时无法在线阅读'
      : 'PDF 页面渲染失败'
    errorMessage.value = message
    emit('error', message)
    console.error('Failed to render PDF page:', error)
  } finally {
    if (sequence === renderSequence) loading.value = false
  }
}

const destroyDocument = async () => {
  renderSequence += 1
  searchSequence += 1
  cancelRendering()
  if (loadingTask) {
    await loadingTask.destroy().catch(() => undefined)
    loadingTask = null
  }
  pdfDocument = null
}

const loadOutline = async () => {
  const document = pdfDocument
  if (!document) return
  const outline = await document.getOutline()
  const result: Array<{ title: string; page: number; level: number }> = []
  const visit = async (items: any[] = [], level = 0) => {
    for (const item of items) {
      try {
        const destination = typeof item.dest === 'string'
          ? await document.getDestination(item.dest)
          : item.dest
        const reference = destination?.[0]
        const page = typeof reference === 'object'
          ? (await document.getPageIndex(reference)) + 1
          : Number(reference) + 1
        if (Number.isFinite(page)) result.push({ title: item.title || `第 ${page} 页`, page, level })
      } catch (error) {
        console.warn('Failed to resolve PDF outline item:', item?.title, error)
      }
      if (item.items?.length) await visit(item.items, level + 1)
    }
  }
  await visit(outline || [])
  if (pdfDocument === document) emit('outline', result)
}

const loadDocument = async () => {
  const sequence = ++documentSequence
  await destroyDocument()
  if (sequence !== documentSequence) return
  if (!props.source?.url) return
  documentLoading.value = true
  loading.value = true
  errorMessage.value = ''
  try {
    const pdfjs = await import('pdfjs-dist')
    pdfjs.GlobalWorkerOptions.workerSrc = workerUrl
    const task = pdfjs.getDocument({
      url: props.source.url,
      httpHeaders: props.source.httpHeaders,
      cMapUrl: '/pdfjs-assets/cmaps/',
      cMapPacked: true,
      iccUrl: '/pdfjs-assets/iccs/',
      standardFontDataUrl: '/pdfjs-assets/standard_fonts/',
      wasmUrl: '/pdfjs-assets/wasm/',
    })
    loadingTask = task
    const loadedDocument = await task.promise
    if (sequence !== documentSequence) {
      await task.destroy().catch(() => undefined)
      return
    }
    pdfDocument = loadedDocument
    totalPages.value = pdfDocument.numPages
    currentPage.value = Math.min(Math.max(1, props.initialPage), totalPages.value)
    pageInput.value = currentPage.value
    await nextTick()
    await renderPage()
    void loadOutline().catch(error => console.warn('Failed to load PDF outline:', error))
  } catch (error: any) {
    if (sequence !== documentSequence) return
    const message = error?.name === 'PasswordException'
      ? '该 PDF 受密码保护，暂时无法在线阅读'
      : 'PDF 文件解析失败'
    errorMessage.value = message
    emit('error', message)
    console.error('Failed to load PDF:', error)
  } finally {
    if (sequence === documentSequence) {
      documentLoading.value = false
      if (!pdfDocument) loading.value = false
    }
  }
}

const goToPage = async (page: number) => {
  const target = clampPage(page)
  if (!pdfDocument || target === currentPage.value) {
    pageInput.value = currentPage.value
    return
  }
  currentPage.value = target
  viewportElement.value?.scrollTo({ top: 0, left: 0 })
  await renderPage()
}

const selectPage = (page: number) => {
  if (page === currentPage.value) return
  currentPage.value = page
  pageInput.value = page
  reportPage()
}
const previous = () => void goToPage(isDoublePage.value
  ? (spreadStart(currentPage.value) <= 2 ? 1 : spreadStart(currentPage.value) - 2)
  : currentPage.value - 1)
const next = () => void goToPage(isDoublePage.value
  ? (spreadStart(currentPage.value) === 1 ? 2 : spreadStart(currentPage.value) + 2)
  : currentPage.value + 1)
const commitPageInput = () => void goToPage(pageInput.value)
const zoomOut = () => {
  zoom.value = Math.max(0.5, Number((zoom.value - 0.1).toFixed(1)))
  void renderPage()
}
const zoomIn = () => {
  zoom.value = Math.min(3, Number((zoom.value + 0.1).toFixed(1)))
  void renderPage()
}
const fitWidth = () => {
  zoom.value = 1
  void renderPage()
}

const search = async (query: string, limit = 200) => {
  const sequence = ++searchSequence
  const document = pdfDocument
  const normalizedQuery = query.trim().toLocaleLowerCase()
  if (!normalizedQuery) return []
  if (!document) throw new Error('PDF 尚未加载完成')
  const results: Array<{ page: number; excerpt: string }> = []
  for (let pageNumber = 1; pageNumber <= document.numPages && results.length < limit; pageNumber += 1) {
    if (sequence !== searchSequence || document !== pdfDocument) return []
    const page = await document.getPage(pageNumber)
    const textContent = await page.getTextContent()
    const text = textContent.items
      .map(item => 'str' in item ? item.str : '')
      .join(' ')
      .replace(/\s+/g, ' ')
      .trim()
    const normalizedText = text.toLocaleLowerCase()
    let fromIndex = 0
    while (results.length < limit) {
      const index = normalizedText.indexOf(normalizedQuery, fromIndex)
      if (index < 0) break
      const start = Math.max(0, index - 36)
      const end = Math.min(text.length, index + query.trim().length + 56)
      results.push({
        page: pageNumber,
        excerpt: `${start > 0 ? '…' : ''}${text.slice(start, end)}${end < text.length ? '…' : ''}`,
      })
      fromIndex = index + Math.max(1, normalizedQuery.length)
    }
  }
  return results
}

const cancelSearch = () => {
  searchSequence += 1
}

watch(() => props.source, () => void loadDocument(), { deep: true })
watch(() => props.initialPage, page => {
  if (pdfDocument && page !== currentPage.value) void goToPage(page)
})
watch(() => props.displayMode, () => {
  if (pdfDocument) void renderPage()
})

onMounted(() => {
  resizeObserver = new ResizeObserver(() => {
    if (pdfDocument && !loading.value) void renderPage()
  })
  if (viewportElement.value) resizeObserver.observe(viewportElement.value)
  void loadDocument()
})

onBeforeUnmount(() => {
  documentSequence += 1
  resizeObserver?.disconnect()
  resizeObserver = null
  void destroyDocument()
})

defineExpose({ previous, next, goToPage, search, cancelSearch })
</script>

<style scoped>
.pdf-reader {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.pdf-toolbar {
  position: absolute;
  z-index: 20;
  top: 14px;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--border-color-light);
  border-radius: 14px;
  transform: translateX(-50%);
  background: var(--surface-elevated);
  box-shadow: var(--shadow-md);
}

.pdf-toolbar button,
.pdf-page-control input {
  height: 32px;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: var(--surface-card);
  color: var(--text-primary);
}

.pdf-toolbar button {
  min-width: 34px;
  padding: 0 10px;
  cursor: pointer;
}

.pdf-toolbar button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.pdf-page-control {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 13px;
}

.pdf-page-control input {
  width: 58px;
  padding: 0 6px;
  text-align: center;
}

.pdf-toolbar-separator {
  width: 1px;
  height: 22px;
  background: var(--border-color);
}

.pdf-zoom-value {
  min-width: 46px;
  color: var(--text-secondary);
  text-align: center;
  font-size: 12px;
}

.pdf-viewport {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  overflow: auto;
  padding: 68px 24px 32px;
  text-align: center;
}

.pdf-page {
  position: relative;
  display: inline-block;
  overflow: hidden;
  background: white;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.22);
  line-height: 1;
  text-align: initial;
}

.pdf-spread {
  display: inline-flex;
  align-items: flex-start;
  justify-content: center;
  gap: 20px;
  min-width: min-content;
}

.pdf-page--active {
  outline: 2px solid color-mix(in srgb, var(--primary) 55%, transparent);
  outline-offset: 3px;
}

.pdf-page canvas {
  display: block;
}

.pdf-text-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  opacity: 1;
  line-height: 1;
  text-size-adjust: none;
  forced-color-adjust: none;
  transform-origin: 0 0;
  caret-color: CanvasText;
  --min-font-size: 1;
  --text-scale-factor: calc(var(--total-scale-factor) * var(--min-font-size));
  --min-font-size-inv: calc(1 / var(--min-font-size));
}

.pdf-text-layer :deep(span),
.pdf-text-layer :deep(br) {
  position: absolute;
  color: transparent;
  white-space: pre;
  transform-origin: 0 0;
  cursor: text;
  user-select: text;
}

.pdf-text-layer :deep(> :not(.markedContent)),
.pdf-text-layer :deep(.markedContent span:not(.markedContent)) {
  z-index: 1;
  font-size: calc(var(--text-scale-factor) * var(--font-height, 0));
  transform: rotate(var(--rotate, 0deg)) scaleX(var(--scale-x, 1)) scale(var(--min-font-size-inv));
}

.pdf-text-layer :deep(.markedContent) {
  display: contents;
}

.pdf-text-layer :deep(.endOfContent) {
  position: absolute;
  z-index: 0;
  display: block;
  inset: 100% 0 0;
  cursor: default;
  user-select: none;
}

.pdf-text-layer :deep(::selection) {
  background: color-mix(in srgb, var(--primary) 35%, transparent);
}

.pdf-state {
  position: absolute;
  z-index: 10;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: var(--surface-elevated);
  color: var(--text-secondary);
}

.pdf-state button {
  padding: 8px 14px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--surface-card);
  color: var(--text-primary);
  cursor: pointer;
}

.pdf-spinner {
  width: 30px;
  height: 30px;
  border: 3px solid var(--primary-alpha-20);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: pdf-spin 0.8s linear infinite;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

@keyframes pdf-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 640px) {
  .pdf-toolbar {
    top: 8px;
    width: calc(100% - 20px);
    justify-content: center;
    gap: 5px;
  }

  .pdf-toolbar button {
    padding: 0 7px;
  }

  .pdf-toolbar-separator,
  .pdf-zoom-value {
    display: none;
  }

  .pdf-viewport {
    padding: 58px 10px 20px;
  }

  .pdf-spread--double {
    gap: 10px;
  }
}
</style>
