<template>
  <section class="comic-reader" aria-label="漫画阅读器" @keydown="handleKey">
    <header class="comic-header" :class="{ compact: !toolbar }">
      <button type="button" @click="returnToBook">返回详情</button>
      <strong class="comic-title">{{ manifest?.title || '漫画阅读' }}</strong>
      <button type="button" :aria-expanded="toolbar" @click="toolbar = !toolbar">
        {{ toolbar ? '收起工具栏' : '展开工具栏' }}
      </button>
    </header>

    <div v-if="manifest && toolbar" class="comic-tools">
      <button type="button" :disabled="previousPage < 1" @click="goToPage(previousPage)">上一页</button>
      <form class="comic-jump" novalidate @submit.prevent="jump">
        <input v-model="jumpText" type="text" inputmode="numeric" aria-label="跳转页码"
          :aria-invalid="Boolean(jumpError)" @input="jumpError = ''" />
        <span>/ {{ manifest.pageCount }} 页</span>
        <button type="submit">跳转</button>
      </form>
      <button type="button" :disabled="nextPage > manifest.pageCount" @click="goToPage(nextPage)">下一页</button>
      <button type="button" aria-haspopup="dialog" :disabled="fileChanged || loading"
        @click="pagePickerOpen = true">页面缩略图</button>
      <button type="button" :aria-expanded="settingsOpen" @click="settingsOpen = !settingsOpen">阅读设置</button>
      <button v-if="!scrollMode" type="button" :aria-pressed="zoomed" @click="toggleZoom">
        {{ zoomed ? '恢复适配' : '放大' }}
      </button>
      <small v-if="jumpError" role="alert">{{ jumpError }}</small>
    </div>

    <div v-if="manifest && toolbar && settingsOpen" class="comic-settings" :aria-busy="scopeSaving">
      <button
        type="button"
        :aria-pressed="bookSettings"
        :disabled="settingsSaving || settingsDirty || scopeSaving"
        @click="toggleBookSettings"
      >
        仅本册
      </button>
      <div>阅读模式
        <ReaderBlueGraySegmented :model-value="preferences.mode" :options="modeOptions"
          label="阅读模式" @update:model-value="changePreference('mode', $event)" />
      </div>
      <div v-if="!scrollMode">翻页方向
        <ReaderBlueGraySegmented :model-value="preferences.direction" :options="directionOptions"
          label="翻页方向" @update:model-value="changePreference('direction', $event)" />
      </div>
      <div v-if="!scrollMode">页面适配
        <ReaderBlueGraySegmented :model-value="preferences.fit" :options="fitOptions"
          label="页面适配" @update:model-value="changePreference('fit', $event)" />
      </div>
    </div>

    <div v-if="notice || syncError || preferenceError || fileChanged" class="comic-notice" role="status">
      <span v-if="notice">{{ notice }}</span>
      <template v-if="fileChanged">
        <span>漫画文件已更新。</span>
        <button type="button" @click="initialize">重新加载</button>
      </template>
      <template v-if="syncError && !fileChanged">
        <span>{{ conflict ? '另一台设备已更新阅读位置。' : '阅读进度尚未同步。' }}</span>
        <button v-if="conflict" type="button" @click="resolveConflict(false)">载入已同步位置</button>
        <button v-if="conflict" type="button" @click="resolveConflict(true)">保存当前位置</button>
        <button v-else type="button" @click="flushProgress()">重试同步</button>
      </template>
      <template v-if="preferenceError">
        <span>阅读设置尚未同步。</span>
        <button type="button" @click="savePreferences">重试设置</button>
      </template>
    </div>

    <div ref="viewport" class="comic-viewport" :class="{ 'is-scroll': scrollMode }"
      tabindex="0" aria-label="漫画页面" @scroll.passive="onScroll" @click="handlePageClick"
      @pointerdown="startPan" @pointermove="movePan" @pointerup="endPan"
      @pointercancel="endPan" @lostpointercapture="endPan">
      <div v-if="loading" class="comic-status" role="status">正在打开漫画…</div>
      <div v-else-if="loadError" class="comic-status" role="alert">
        <span>{{ loadError }}</span>
        <button type="button" @click="initialize">重试</button>
      </div>
      <template v-else-if="manifest">
        <div v-if="scrollMode" class="comic-strip" :style="{ height: stripHeight + 'px' }">
          <div v-for="page in visiblePages" :key="page" class="comic-strip-page"
            :style="{ top: offsets[page - 1] + 'px', height: rowHeight(page) + 'px' }">
            <img v-if="pageStates[page]?.url" :src="pageStates[page].url" :alt="'第 ' + page + ' 页'"
              draggable="false" @load="showPage(page, $event)" @error="decodeError(page, $event)" />
            <div v-else class="comic-status">
              <span>{{ pageStates[page]?.error || '正在加载第 ' + page + ' 页…' }}</span>
              <button v-if="pageStates[page]?.error" type="button" @click.stop="retryPage(page)">重试</button>
              <button v-if="pageStates[page]?.error && page < manifest.pageCount"
                type="button" @click.stop="goToPage(page + 1)">跳过此页</button>
            </div>
          </div>
        </div>
        <div v-else-if="doubleMode" class="comic-spread"
          :class="{ rtl: preferences.direction === 'rtl', 'fit-width': preferences.fit === 'width', zoomed }">
          <div v-for="page in spreadPages" :key="page" class="comic-spread-page">
            <img v-if="pageStates[page]?.url" :src="pageStates[page].url" :alt="'第 ' + page + ' 页'"
              draggable="false" @load="showPage(page, $event)" @error="decodeError(page, $event)" />
            <div v-else class="comic-status">
              <span>{{ pageStates[page]?.error || '正在加载第 ' + page + ' 页…' }}</span>
              <button v-if="pageStates[page]?.error" type="button" @click.stop="retryPage(page)">重试</button>
            </div>
          </div>
        </div>
        <div v-else class="comic-single" :class="{ 'fit-width': preferences.fit === 'width', zoomed }">
          <img v-if="pageStates[currentPage]?.url" :key="currentPage" :src="pageStates[currentPage].url"
            :alt="'第 ' + currentPage + ' 页'" draggable="false"
            @load="showPage(currentPage, $event)" @error="decodeError(currentPage, $event)" />
          <div v-else class="comic-status" role="status">
            <span>{{ pageStates[currentPage]?.error || '正在加载第 ' + currentPage + ' 页…' }}</span>
            <button v-if="pageStates[currentPage]?.error" type="button" @click.stop="retryPage(currentPage)">重试</button>
            <button v-if="pageStates[currentPage]?.error && currentPage < manifest.pageCount"
              type="button" @click.stop="goToPage(currentPage + 1)">跳过此页</button>
          </div>
        </div>
      </template>
    </div>
    <ComicPagePicker
      v-if="manifest && pagePickerOpen && !fileChanged"
      :endpoint="endpoint"
      :revision="manifest.revision"
      :page-count="manifest.pageCount"
      :current-page="currentPage"
      @close="pagePickerOpen = false"
      @select="selectPreview"
      @file-changed="markFileChanged"
    />
    <footer v-if="manifest" class="comic-footer">
      <span aria-live="polite">第 {{ displayedPageLabel }} / {{ manifest.pageCount }} 页{{ finished ? ' · 已读完' : '' }}</span>
      <button v-if="finished && nextPage > manifest.pageCount && manifest.pageCount > 1"
        type="button" @click="goToPage(1)">回到首页</button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ComicPagePicker from '@/components/reader/ComicPagePicker.vue'
import ReaderBlueGraySegmented from '@/components/reader/ReaderBlueGraySegmented.vue'
import api from '@/utils/api'

interface Manifest {
  title: string
  versionId: number
  revision: string
  pageCount: number
  bookPreferences?: Preferences | null
}

interface Position {
  page: number
  offset: number
  finished: boolean
  revision: string | null
  token: string | null
}

interface Preferences {
  mode: string
  direction: string
  fit: string
}

interface PageState {
  url?: string
  error?: string
  shown?: boolean
}

const route = useRoute()
const router = useRouter()
const bookId = Number(route.params.id)
const versionId = Number(route.params.versionId)
const libraryQuery = { libraryId: route.query.libraryId, libraryReturn: route.query.libraryReturn }
const endpoint = `/api/books/${bookId}/versions/${versionId}/comic`
const sessionToken = localStorage.getItem('token')
const preferencesEndpoint = '/api/user/preferences/comic'
const bookPreferencesEndpoint = endpoint + '/preferences'
const quiet = { headers: { 'X-Suppress-Error-Toast': 'true' } }
const modeOptions = [
  { value: 'single', label: '单页翻页' },
  { value: 'double', label: '双页翻页' },
  { value: 'scroll', label: '纵向滚动' },
]
const directionOptions = [{ value: 'ltr', label: '从左往右' }, { value: 'rtl', label: '从右往左' }]
const fitOptions = [{ value: 'page', label: '整页适配' }, { value: 'width', label: '适宽' }]

const preferences = reactive<Preferences>({ mode: 'single', direction: 'ltr', fit: 'page' })
const manifest = ref<Manifest>()
const viewport = ref<HTMLElement>()
const viewportWidth = ref(0)
const viewportHeight = ref(0)
const currentPage = ref(1)
const jumpText = ref('1')
const jumpError = ref('')
const toolbar = ref(true)
const settingsOpen = ref(false)
const bookSettings = ref(false)
const scopeSaving = ref(false)
const settingsDirty = ref(false)
const settingsSaving = ref(false)
const pagePickerOpen = ref(false)
const zoomed = ref(false)
const finished = ref(false)
const loading = ref(true)
const loadError = ref('')
const notice = ref('')
const syncError = ref(false)
const preferenceError = ref(false)
const conflict = ref(false)
const fileChanged = ref(false)
const scrollMode = computed(() => preferences.mode === 'scroll')
const doubleMode = computed(() => preferences.mode === 'double')
const spreadStart = computed(() => currentPage.value === 1 ? 1 : currentPage.value - currentPage.value % 2)
const spreadPages = computed(() => {
  const start = spreadStart.value
  return start === 1 || start >= (manifest.value?.pageCount ?? 0) ? [start] : [start, start + 1]
})
const previousPage = computed(() => {
  if (!doubleMode.value) return currentPage.value - 1
  if (spreadStart.value <= 2) return spreadStart.value - 1
  return spreadStart.value - 2
})
const nextPage = computed(() => doubleMode.value
  ? spreadStart.value + spreadPages.value.length : currentPage.value + 1)
const displayedPageLabel = computed(() => doubleMode.value ? spreadPages.value.join('–') : String(currentPage.value))
const pageStates = reactive<Record<number, PageState>>({})
const dimensions = reactive<Record<number, number>>({})
const active = new Map<number, AbortController>()
const windowStart = ref(1)
const windowEnd = ref(1)
const imageWidth = computed(() => Math.min(viewportWidth.value || 600, 1000))
let disposed = false
let generation = 0
let token: string | null = null
let offset = 0
let scrollOffsetPage = 1
let scrollOffset = 0
let scrollTarget: number | undefined
let resizeObserver: ResizeObserver | undefined
let frame = 0
let progressTimer: ReturnType<typeof setTimeout> | undefined
let pending: Omit<Position, 'token'> | undefined
let lastSaved: Omit<Position, 'token'> | undefined
let saving = false
let pan: { id: number; x: number; y: number; left: number; top: number } | undefined
let dragged = false

const rowHeight = (page: number) => Math.max(80, imageWidth.value * (dimensions[page] || 1.42))
const offsets = computed(() => {
  const values = [0]
  for (let page = 1; page <= (manifest.value?.pageCount || 0); page++) {
    values.push(values[page - 1] + rowHeight(page))
  }
  return values
})
const stripHeight = computed(() => offsets.value[offsets.value.length - 1])
const visiblePages = computed(() => Array.from(
  { length: Math.max(0, windowEnd.value - windowStart.value + 1) },
  (_, index) => windowStart.value + index,
))

function pageAt(top: number) {
  let low = 1
  let high = manifest.value?.pageCount || 1
  while (low < high) {
    const mid = Math.floor((low + high) / 2)
    if (offsets.value[mid] <= top) low = mid + 1
    else high = mid
  }
  return low
}

function updateWindow(top = viewport.value?.scrollTop || 0) {
  if (!manifest.value) return
  windowStart.value = Math.max(1, pageAt(top) - 2)
  windowEnd.value = Math.min(manifest.value.pageCount, pageAt(top + viewportHeight.value) + 2)
}

function clearPage(page: number) {
  const url = pageStates[page]?.url
  if (url) URL.revokeObjectURL(url)
  delete pageStates[page]
}

function resetImages() {
  active.forEach(controller => controller.abort())
  active.clear()
  Object.keys(pageStates).forEach(page => clearPage(Number(page)))
  Object.keys(dimensions).forEach(page => delete dimensions[Number(page)])
}

async function initialize() {
  if (saving || scopeSaving.value) return
  const keepLocalSettings = settingsDirty.value || settingsSaving.value
  pagePickerOpen.value = false
  const request = ++generation
  clearTimeout(progressTimer)
  resetImages()
  pending = undefined
  lastSaved = undefined
  manifest.value = undefined
  scrollTarget = undefined
  fileChanged.value = false
  syncError.value = false
  conflict.value = false
  loading.value = true
  loadError.value = ''
  notice.value = ''
  try {
    const [book, position, settings] = await Promise.all([
      api.get<Manifest>(endpoint, quiet),
      api.get<Position>(endpoint + '/progress', quiet),
      api.get<Preferences>(preferencesEndpoint, quiet),
    ])
    if (disposed || request !== generation) return
    if (!keepLocalSettings) {
      bookSettings.value = Boolean(book.data.bookPreferences)
      Object.assign(preferences, book.data.bookPreferences ?? settings.data)
    }
    manifest.value = book.data
    token = position.data.token
    const { token: _token, ...stored } = position.data
    lastSaved = stored
    if (position.data.revision && position.data.revision !== book.data.revision) {
      notice.value = '漫画文件已更新，已恢复到新文件的有效页码。'
    }
    await restorePosition(position.data)
    void api.post(`/api/books/${bookId}/open`, undefined, {
      ...quiet, params: { versionId },
    }).catch(() => undefined)
  } catch (error) {
    if (!disposed && request === generation) {
      loadError.value = await errorText(error, '暂时无法打开漫画')
    }
  } finally {
    if (!disposed && request === generation) {
      loading.value = false
      await nextTick()
      if (scrollMode.value && manifest.value) alignScroll()
      requestPages()
      if (document.activeElement === document.body) viewport.value?.focus({ preventScroll: true })
    }
  }
}

function statusOf(error: unknown): number | undefined {
  return (error as { response?: { status?: number } }).response?.status
}

async function errorText(error: unknown, fallback: string) {
  const data = (error as { response?: { data?: { message?: string } | Blob } }).response?.data
  if (data instanceof Blob) {
    try {
      return (JSON.parse(await data.text()) as { message?: string }).message || fallback
    } catch {
      return fallback
    }
  }
  return data?.message || fallback
}

function requestPages() {
  const book = manifest.value
  if (!book || loading.value || fileChanged.value || disposed) return
  const first = doubleMode.value ? spreadStart.value : currentPage.value
  const start = scrollMode.value ? windowStart.value : Math.max(1, first - 1)
  const end = scrollMode.value ? windowEnd.value : Math.min(book.pageCount, nextPage.value)
  const wanted = Array.from({ length: end - start + 1 }, (_, index) => start + index)
    .sort((a, b) => Math.abs(a - currentPage.value) - Math.abs(b - currentPage.value))
  const keep = new Set(wanted)
  for (const [page, request] of active) {
    if (!keep.has(page)) {
      active.delete(page)
      request.abort()
    }
  }
  for (const page of Object.keys(pageStates).map(Number)) {
    if (!keep.has(page)) clearPage(page)
  }
  for (const page of wanted) {
    if (active.size >= 2) break
    if (!active.has(page) && !pageStates[page]) void loadPage(page, book.revision)
  }
}

async function loadPage(page: number, revision: string) {
  const controller = new AbortController()
  active.set(page, controller)
  try {
    const response = await api.get<Blob>(endpoint + '/pages/' + page, {
      ...quiet,
      params: { revision },
      responseType: 'blob',
      signal: controller.signal,
    })
    if (controller.signal.aborted || disposed) return
    const width = Number(response.headers['x-image-width'])
    const height = Number(response.headers['x-image-height'])
    if (width > 0 && height > 0 && dimensions[page] !== height / width) {
      dimensions[page] = height / width
      if (scrollMode.value) {
        await nextTick()
        if (!controller.signal.aborted) alignScroll()
      }
    }
    if (controller.signal.aborted || disposed) return
    pageStates[page] = { url: URL.createObjectURL(response.data) }
  } catch (error) {
    if (!controller.signal.aborted && !disposed) {
      if (statusOf(error) === 409) {
        fileChanged.value = true
      } else {
        const text = await errorText(error, '图片加载失败，请重试')
        if (!controller.signal.aborted && !disposed) pageStates[page] = { error: text }
      }
    }
  } finally {
    if (active.get(page) === controller) {
      active.delete(page)
      requestPages()
    }
  }
}

function retryPage(page: number) {
  clearPage(page)
  requestPages()
}

function showPage(page: number, event: Event) {
  const state = pageStates[page]
  if (state?.url && (event.target as HTMLImageElement).src === state.url) {
    state.shown = true
    if (doubleMode.value ? spreadPages.value.includes(page) : page === currentPage.value) queueProgress()
  }
}

function decodeError(page: number, event: Event) {
  if ((event.target as HTMLImageElement).src !== pageStates[page]?.url) return
  clearPage(page)
  pageStates[page] = { error: '图片已损坏或浏览器无法解码' }
}

function setPage(page: number, fraction = 0) {
  if (!scrollMode.value && page !== currentPage.value) {
    scrollOffsetPage = page
    scrollOffset = 0
  }
  currentPage.value = page
  jumpText.value = String(page)
  jumpError.value = ''
  offset = fraction
  if (scrollMode.value) {
    scrollOffset = fraction
    scrollOffsetPage = page
  }
}

function anchorLine() {
  return Math.min(100, viewportHeight.value * 0.15)
}

function alignScroll() {
  if (!viewport.value || !manifest.value) return
  const top = offsets.value[currentPage.value - 1] + rowHeight(currentPage.value) * offset
  viewport.value.scrollTop = Math.max(0, top - anchorLine())
  scrollTarget = viewport.value.scrollTop
  updateWindow(scrollTarget)
}

function onScroll() {
  if (!scrollMode.value || !viewport.value || !manifest.value || frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    if (!viewport.value || !manifest.value || !scrollMode.value) return
    const top = viewport.value.scrollTop
    if (scrollTarget !== undefined && Math.abs(top - scrollTarget) < 1) return
    scrollTarget = undefined
    const bottom = top > 0 && top + viewportHeight.value >= viewport.value.scrollHeight - 2
    const anchor = top === 0 ? 0 : top + anchorLine()
    const page = bottom ? manifest.value.pageCount : pageAt(anchor)
    const fraction = bottom ? 1 : Math.max(0, Math.min(1, (anchor - offsets.value[page - 1]) / rowHeight(page)))
    setPage(page, fraction)
    updateWindow(top)
    requestPages()
    queueProgress()
  })
}

async function goToPage(page: number) {
  if (!manifest.value || page < 1 || page > manifest.value.pageCount) return
  const fraction = page === currentPage.value ? offset : 0
  setPage(page, fraction)
  zoomed.value = false
  await nextTick()
  if (scrollMode.value) alignScroll()
  else if (viewport.value) viewport.value.scrollTo(0, 0)
  requestPages()
  queueProgress()
}

async function selectPreview(page: number) {
  pagePickerOpen.value = false
  await goToPage(page)
  viewport.value?.focus({ preventScroll: true })
}

function markFileChanged() {
  pagePickerOpen.value = false
  fileChanged.value = true
}

function jump() {
  const value = jumpText.value.trim()
  const page = Number(value)
  if (!manifest.value || !/^\d+$/.test(value) || page < 1 || page > manifest.value.pageCount) {
    jumpError.value = `请输入 1 至 ${manifest.value?.pageCount || 1} 的整数页码`
    return
  }
  void goToPage(page)
}

function handleKey(event: KeyboardEvent) {
  const target = event.target as HTMLElement
  if (pagePickerOpen.value || event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey
      || target.closest('input, button, select, textarea, [contenteditable], [role="radiogroup"]')) return
  if (!scrollMode.value && !zoomed.value && ['ArrowLeft', 'ArrowRight'].includes(event.key)) {
    event.preventDefault()
    const forward = (event.key === 'ArrowRight') === (preferences.direction === 'ltr')
    void goToPage(forward ? nextPage.value : previousPage.value)
  }
}

function handlePageClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!manifest.value || !viewport.value || target.closest('button') || dragged) return
  const bounds = viewport.value.getBoundingClientRect()
  const x = (event.clientX - bounds.left) / bounds.width
  if (x >= 0.3 && x <= 0.7) {
    toolbar.value = !toolbar.value
  } else if (!scrollMode.value && !zoomed.value) {
    const forward = (x > 0.7) === (preferences.direction === 'ltr')
    void goToPage(forward ? nextPage.value : previousPage.value)
  }
}

function toggleZoom() {
  zoomed.value = !zoomed.value
  if (!zoomed.value) viewport.value?.scrollTo(0, 0)
}

function startPan(event: PointerEvent) {
  dragged = false
  if (!zoomed.value || !viewport.value || event.pointerType !== 'mouse' || event.button !== 0
      || (event.target as HTMLElement).closest('button')) return
  pan = { id: event.pointerId, x: event.clientX, y: event.clientY,
    left: viewport.value.scrollLeft, top: viewport.value.scrollTop }
  viewport.value.setPointerCapture(event.pointerId)
}

function movePan(event: PointerEvent) {
  if (!pan || pan.id !== event.pointerId || !viewport.value) return
  const dx = event.clientX - pan.x
  const dy = event.clientY - pan.y
  if (Math.abs(dx) + Math.abs(dy) > 4) dragged = true
  viewport.value.scrollTo(pan.left - dx, pan.top - dy)
}

function endPan(event: PointerEvent) {
  if (pan?.id === event.pointerId) pan = undefined
}

async function applyPreferences(next: Preferences) {
  const modeChanged = preferences.mode !== next.mode
  const fitChanged = preferences.fit !== next.fit
  Object.assign(preferences, next)
  zoomed.value = false
  if (modeChanged) {
    offset = scrollMode.value && scrollOffsetPage === currentPage.value ? scrollOffset : 0
    await goToPage(currentPage.value)
  } else if (fitChanged) {
    viewport.value?.scrollTo(0, 0)
  }
}

function changePreference(key: keyof Preferences, value: string) {
  if (scopeSaving.value || preferences[key] === value) return
  void applyPreferences({ ...preferences, [key]: value })
  settingsDirty.value = true
  void savePreferences()
}

async function toggleBookSettings() {
  if (scopeSaving.value || settingsSaving.value || settingsDirty.value
    || localStorage.getItem('token') !== sessionToken) return
  scopeSaving.value = true
  notice.value = ''
  try {
    if (bookSettings.value) {
      const defaults = await api.get<Preferences>(preferencesEndpoint, quiet)
      if (disposed || localStorage.getItem('token') !== sessionToken) return
      await api.delete(bookPreferencesEndpoint, quiet)
      if (disposed) return
      bookSettings.value = false
      await applyPreferences(defaults.data)
    } else {
      await api.put(bookPreferencesEndpoint, { ...preferences }, quiet)
      if (!disposed) bookSettings.value = true
    }
  } catch {
    notice.value = '切换漫画设置范围失败，请重试。'
  } finally {
    scopeSaving.value = false
  }
}

async function savePreferences() {
  if (settingsSaving.value || !settingsDirty.value || localStorage.getItem('token') !== sessionToken) return
  settingsSaving.value = true
  settingsDirty.value = false
  preferenceError.value = false
  try {
    const target = bookSettings.value ? bookPreferencesEndpoint : preferencesEndpoint
    await api.put(target, { ...preferences }, quiet)
  } catch {
    settingsDirty.value = true
    preferenceError.value = true
  } finally {
    settingsSaving.value = false
    if (settingsDirty.value && !preferenceError.value) void savePreferences()
  }
}

async function restorePosition(position: Position) {
  if (!manifest.value) return
  const sameFile = position.revision === manifest.value.revision
  const page = Math.max(1, Math.min(position.page, manifest.value.pageCount))
  const fraction = sameFile && page === position.page ? position.offset : 0
  finished.value = sameFile && position.finished
  scrollOffsetPage = page
  scrollOffset = fraction
  setPage(page, scrollMode.value ? fraction : 0)
  await nextTick()
  if (scrollMode.value) alignScroll()
  requestPages()
}

function queueProgress() {
  if (!manifest.value || fileChanged.value) return
  const visible = doubleMode.value ? spreadPages.value : [currentPage.value]
  if (!visible.every(page => pageStates[page]?.shown)) return
  const finalSpread = visible.includes(manifest.value.pageCount)
  if (finalSpread) finished.value = true
  const page = finalSpread ? manifest.value.pageCount : currentPage.value
  const position = {
    page,
    offset: scrollOffsetPage === page ? Math.round(scrollOffset * 1000) / 1000 : 0,
    finished: finished.value,
    revision: manifest.value.revision,
  }
  pending = position
  clearTimeout(progressTimer)
  progressTimer = setTimeout(() => void flushProgress(), 600)
}

function samePosition(a: Omit<Position, 'token'>, b?: Omit<Position, 'token'>) {
  return b && a.page === b.page && a.offset === b.offset
    && a.finished === b.finished && a.revision === b.revision
}

async function flushProgress(keepalive = false) {
  if (!pending || saving || conflict.value || fileChanged.value
      || localStorage.getItem('token') !== sessionToken) return
  const snapshot = pending
  pending = undefined
  if (samePosition(snapshot, lastSaved)) return
  saving = true
  try {
    const response = await api.put<Position>(endpoint + '/progress', { ...snapshot, token },
      { ...quiet, adapter: keepalive ? 'fetch' : undefined, fetchOptions: { keepalive } })
    token = response.data.token
    lastSaved = snapshot
    syncError.value = false
  } catch (error) {
    const message = await errorText(error, '')
    fileChanged.value = statusOf(error) === 409 && message.includes('文件已更新')
    conflict.value = statusOf(error) === 409 && !fileChanged.value
    syncError.value = true
    pending ??= snapshot
  } finally {
    saving = false
    if (pending && !syncError.value && !fileChanged.value) {
      void flushProgress(disposed || document.visibilityState === 'hidden')
    }
  }
}

async function resolveConflict(keepLocal: boolean) {
  try {
    const { data } = await api.get<Position>(endpoint + '/progress', quiet)
    if (disposed || !manifest.value) return
    if (data.revision && data.revision !== manifest.value.revision) {
      // Ask the manifest endpoint to distinguish an old saved position from an updated archive.
      const fresh = await api.get<Manifest>(endpoint, quiet)
      if (fresh.data.revision !== manifest.value.revision) {
        fileChanged.value = true
        return
      }
    }
    token = data.token
    conflict.value = false
    syncError.value = false
    if (keepLocal) {
      finished.value ||= data.revision === manifest.value.revision && data.finished
      if (pending) pending.finished ||= finished.value
      void flushProgress()
    } else {
      pending = undefined
      const { token: _token, ...stored } = data
      lastSaved = stored
      await restorePosition(data)
    }
  } catch {
    syncError.value = true
  }
}

async function returnToBook() {
  await Promise.race([flushProgress(true), new Promise(resolve => setTimeout(resolve, 350))])
  const previous = router.options.history.state.back
  if (typeof previous === 'string' && previous.split(/[?#]/)[0] === `/books/${bookId}`) {
    router.back()
    return
  }
  void router.replace({
    name: 'BookDetail',
    params: { id: bookId },
    query: { ...libraryQuery, versionId: String(versionId) },
  })
}

function handleVisibility() {
  if (document.visibilityState === 'hidden') void flushProgress(true)
}

function flushOnExit() {
  void flushProgress(true)
}

function retryOnline() {
  void flushProgress()
  if (preferenceError.value) void savePreferences()
}

onMounted(() => {
  resizeObserver = new ResizeObserver(() => {
    if (!viewport.value) return
    viewportWidth.value = viewport.value.clientWidth
    viewportHeight.value = viewport.value.clientHeight
    if (scrollMode.value && manifest.value) {
      void nextTick(() => {
        alignScroll()
        requestPages()
      })
    }
  })
  if (viewport.value) resizeObserver.observe(viewport.value)
  document.addEventListener('visibilitychange', handleVisibility)
  window.addEventListener('pagehide', flushOnExit)
  window.addEventListener('online', retryOnline)
  void initialize()
})

onBeforeUnmount(() => {
  disposed = true
  clearTimeout(progressTimer)
  void flushProgress(true)
  cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
  resetImages()
  document.removeEventListener('visibilitychange', handleVisibility)
  window.removeEventListener('pagehide', flushOnExit)
  window.removeEventListener('online', retryOnline)
})
</script>

<style scoped>
.comic-reader {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  min-height: 0;
  background: #14161a;
  color: #f1f3f6;
}

.comic-header,
.comic-tools,
.comic-settings,
.comic-notice,
.comic-footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: #20242a;
}

.comic-header {
  flex-wrap: nowrap;
}

.comic-header.compact {
  padding-block: 4px;
}

.comic-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.comic-reader button,
.comic-reader input {
  min-height: 36px;
  padding: 4px 9px;
  border: 1px solid #626b78;
  border-radius: 7px;
  background: #272c35;
  color: inherit;
  font: inherit;
}

.comic-reader button {
  cursor: pointer;
}

.comic-reader button:disabled {
  opacity: 0.45;
  cursor: default;
}

.comic-reader :focus-visible {
  outline: 2px solid #8bc7ff;
  outline-offset: -2px;
}

.comic-jump {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.comic-jump input {
  width: 62px;
}

.comic-settings {
  --blue-soft: #272c35;
  --blue-muted: #f1f3f6;
  --blue-line: #626b78;
}

.comic-settings > button[aria-pressed="true"] {
  border-color: #8bc7ff;
  background: #274a6b;
}

.comic-settings > div {
  display: grid;
  gap: 4px;
  max-width: 100%;
}

.comic-notice,
.comic-tools small {
  color: #ffd99a;
}

.comic-footer {
  justify-content: center;
  min-height: 32px;
  padding-block: 4px;
}

.comic-viewport {
  flex: 1;
  min-height: 0;
  overflow: auto;
  overflow-anchor: none;
  overscroll-behavior: contain;
}

.comic-viewport.is-scroll {
  overflow-x: hidden;
}

.comic-spread {
  display: flex;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.comic-spread.rtl {
  flex-direction: row-reverse;
}

.comic-spread-page {
  display: grid;
  place-items: center;
  width: 50%;
  min-width: 0;
  min-height: 0;
}

.comic-spread-page:only-child {
  width: 100%;
}

.comic-spread-page img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  user-select: none;
}

.comic-spread-page:not(:only-child):first-child img,
.comic-spread.rtl .comic-spread-page:not(:only-child):last-child img {
  object-position: right center;
}

.comic-spread-page:not(:only-child):last-child img,
.comic-spread.rtl .comic-spread-page:not(:only-child):first-child img {
  object-position: left center;
}

.comic-spread.fit-width,
.comic-spread.zoomed {
  height: auto;
  min-height: 100%;
  align-items: flex-start;
}

.comic-spread.fit-width img,
.comic-spread.zoomed img {
  height: auto;
}

.comic-spread.zoomed {
  width: 200%;
  cursor: grab;
}

.comic-single {
  display: flex;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.comic-single img {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  user-select: none;
}

.comic-single.fit-width {
  height: auto;
  min-height: 100%;
}

.comic-single.fit-width img {
  width: 100%;
  max-width: 1200px;
  max-height: none;
  align-self: flex-start;
}

.comic-single.zoomed {
  width: 200%;
  height: auto;
  min-height: 100%;
  cursor: grab;
}

.comic-single.zoomed img {
  width: 100%;
  max-width: none;
  max-height: none;
  align-self: flex-start;
}

.comic-strip {
  position: relative;
  width: min(100%, 1000px);
  margin-inline: auto;
}

.comic-strip-page {
  position: absolute;
  display: grid;
  place-items: center;
  width: 100%;
}

.comic-strip-page img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.comic-status {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 64px;
  padding: 12px;
  text-align: center;
}
</style>
