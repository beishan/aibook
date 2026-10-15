<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${source?.name || ''} · 试刮削`"
    width="1120px"
    top="5vh"
    style="max-width: calc(100vw - 24px)"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p>使用服务器已保存的配置，仅测试所选来源（包括未启用来源）。绕过缓存，结果仅供预览，不修改书籍。</p>
    <fieldset :disabled="running" class="trial-filters">
      <el-input v-model="filters.keyword" placeholder="搜索书名、作者、ISBN" clearable @keyup.enter="search" />
      <el-select :disabled="running" v-model="filters.sourceType" placeholder="全部来源" clearable>
        <el-option label="上传" value="UPLOAD" />
        <el-option label="目录扫描" value="DIRECTORY_SCAN" />
        <el-option label="网站采集" value="CRAWLER" />
      </el-select>
      <el-select :disabled="running" v-model="filters.format" placeholder="全部文件类型" clearable>
        <el-option v-for="format in formats" :key="format" :label="format.toUpperCase()" :value="format" />
      </el-select>
      <el-select :disabled="running" v-model="filters.categoryId" placeholder="全部分类" clearable filterable>
        <el-option v-for="category in categories" :key="category.id" :label="category.path || category.name" :value="category.id" />
      </el-select>
      <el-select :disabled="running" v-model="filters.tagId" placeholder="全部标签" clearable filterable>
        <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
      </el-select>
      <el-date-picker
        :disabled="running"
        v-model="dates"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="入库开始日期"
        end-placeholder="入库结束日期"
      />
      <el-button :loading="loading" :disabled="running" @click="search">搜索 / 筛选</el-button>
    </fieldset>
    <p v-if="loadError" role="alert">{{ loadError }}</p>
    <div class="trial-browser-toolbar">
      <strong>选择书籍 · 共 {{ total }} 本</strong>
      <div
        class="trial-view-switch"
        :class="{ 'is-card': viewMode === 'card' }"
        role="radiogroup"
        aria-label="选书视图"
        @keydown="switchViewByKeyboard"
      >
        <span class="trial-view-indicator" aria-hidden="true"></span>
        <button
          v-for="option in viewOptions"
          :key="option.value"
          type="button"
          role="radio"
          :aria-checked="viewMode === option.value"
          :tabindex="viewMode === option.value ? 0 : -1"
          @click="setViewMode(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </div>
    <div v-loading="loading" class="trial-book-browser" :aria-busy="loading">
      <el-table
        v-if="viewMode === 'table'"
        :data="books"
        height="100%"
        row-key="id"
        :current-row-key="selected?.id"
        empty-text="没有符合条件的书籍"
        highlight-current-row
        @current-change="selectBook"
      >
        <el-table-column label="选择" width="65">
          <template #default="{ row }">
            <input
              type="radio"
              :checked="selected?.id === row.id"
              :disabled="running"
              :aria-label="`选择${row.title}`"
              @change="selected = row"
            />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="书名" min-width="180" />
        <el-table-column prop="author" label="作者" min-width="100" />
        <el-table-column prop="format" label="类型" width="80" />
        <el-table-column prop="categoryName" label="分类" min-width="100" />
        <el-table-column prop="createdAt" label="入库时间" min-width="165" />
      </el-table>
      <div v-else class="trial-card-scroll">
        <el-empty v-if="books.length === 0" description="没有符合条件的书籍" />
        <div v-else class="trial-card-grid">
          <button
            v-for="book in books"
            :key="book.id"
            type="button"
            class="trial-book-card"
            :class="{ 'is-selected': selected?.id === book.id }"
            :aria-pressed="selected?.id === book.id"
            :aria-label="`选择《${book.title}》${book.author ? `，${book.author}` : ''}`"
            :disabled="running || loading"
            @click="selectBook(book)"
          >
            <span class="trial-card-cover">
              <img
                v-if="book.coverUrl && shouldLoadBookCover(book.id) && !failedCovers.has(book.id)"
                :src="getCoverThumbnailUrl(book.coverUrl)"
                alt=""
                loading="lazy"
                @error="failedCovers.add(book.id)"
              />
              <span v-else class="trial-cover-placeholder">{{ book.title }}</span>
              <span v-if="selected?.id === book.id" class="trial-card-check" aria-hidden="true">✓</span>
            </span>
            <span class="trial-card-title" :title="book.title">{{ book.title }}</span>
            <span class="trial-card-author">{{ book.author || '未知作者' }}</span>
            <span class="trial-card-meta">{{ book.format?.toUpperCase() || '未知类型' }} · {{ book.categoryName || '未分类' }}</span>
          </button>
        </div>
      </div>
    </div>
    <el-pagination
      :current-page="page + 1" :page-size="20" :total="total" layout="prev, pager, next, total"
      :disabled="running || loading" small @current-change="changePage"
    />
    <div class="trial-actions">
      <span>{{ selected ? `已选择：${selected.title}` : '请选择一本书籍' }}</span>
      <el-button type="primary" :disabled="!selected || loading || running" :loading="running" @click="start">
        {{ running ? '试刮削中' : '开始试刮削' }}
      </el-button>
      <el-button v-if="pollError && taskId" @click="resumePolling">重新连接进度</el-button>
    </div>
    <section v-if="snapshot || running || pollError" aria-live="polite">
      <h3>刮削过程 · {{ statusLabel }} · {{ elapsed }} 秒</h3>
      <el-progress :percentage="snapshot?.finished ? 100 : 50" :indeterminate="running" :duration="3" :show-text="false" />
      <ol class="trial-logs">
        <li v-for="(entry, index) in snapshot?.logs || []" :key="index">
          <time>{{ new Date(entry.time).toLocaleTimeString() }}</time> {{ entry.message }}
        </li>
      </ol>
      <p v-if="pollError" role="alert">{{ pollError }}</p>
      <template v-if="snapshot?.finished">
        <h3>刮削结果</h3>
        <p v-if="!snapshot.metadata">{{ snapshot.logs[snapshot.logs.length - 1]?.message }}</p>
        <el-descriptions v-else :column="1" border>
          <el-descriptions-item v-for="(label, field) in fields" :key="field" :label="label">
            <span class="trial-value">{{ formatValue(snapshot.metadata[field]) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </section>
    <template #footer>
      <span v-if="running">关闭弹窗后服务器仍会完成本次试验；重新打开可查看。</span>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import api from '@/utils/api'
import type { ScraperStatus } from '@/utils/config'
import { usePreferencesStore, type ScraperTrialViewMode } from '@/stores/preferences'
import { getCoverThumbnailUrl } from '@/utils/cover'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'

const props = defineProps<{ modelValue: boolean; source: ScraperStatus | null }>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'running', value: boolean): void
}>()
interface BookChoice {
  id: number
  title: string
  author?: string
  format?: string
  categoryName?: string
  coverUrl?: string
}
interface Option {
  id: number
  name: string
  path?: string
}
interface TrialSnapshot {
  status: string
  finished: boolean
  elapsedMs: number
  logs: { time: string; message: string }[]
  metadata: Record<string, unknown> | null
}
const preferences = usePreferencesStore()
const viewMode = computed(() => preferences.scraperTrialViewMode)
const viewOptions: { value: ScraperTrialViewMode; label: string }[] = [
  { value: 'table', label: '列表视图' },
  { value: 'card', label: '卡片视图' },
]
const failedCovers = reactive(new Set<number>())
function setViewMode(value: ScraperTrialViewMode) {
  preferences.setScraperTrialViewMode(value)
}
async function switchViewByKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const group = event.currentTarget as HTMLElement
  const value = event.key === 'Home' ? 'table'
    : event.key === 'End' ? 'card'
      : viewMode.value === 'table' ? 'card' : 'table'
  setViewMode(value)
  await nextTick()
  group.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.focus()
}
const formats = ['epub', 'pdf', 'txt', 'mobi', 'azw3', 'fb2', 'cbz', 'cbr']
const fields = {
  title: '书名', author: '作者', isbn: 'ISBN', publisher: '出版社', publishDate: '出版日期',
  description: '简介', coverUrl: '封面地址', language: '语言', rating: '评分', tags: '标签',
}
const filters = reactive({
  keyword: '',
  sourceType: '',
  format: '',
  categoryId: undefined as number | undefined,
  tagId: undefined as number | undefined,
})
const dates = ref<string[] | null>(null)
const categories = ref<Option[]>([])
const tags = ref<Option[]>([])
const books = ref<BookChoice[]>([])
const selected = ref<BookChoice | null>(null)
const loading = ref(false)
const loadError = ref('')
const page = ref(0)
const total = ref(0)
const running = ref(false)
watch(running, value => emit('running', value))
const snapshot = ref<TrialSnapshot | null>(null)
const pollError = ref('')
const taskId = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
let generation = 0
let searchGeneration = 0
let disposed = false
const elapsed = computed(() => ((snapshot.value?.elapsedMs || 0) / 1000).toFixed(1))
const statusLabels: Record<string, string> = {
  RUNNING: '执行中',
  MATCHED: '取得元信息',
  NO_RESULT: '未取得元信息',
  UNSUPPORTED: '不支持 / 配置缺失',
  FAILED: '失败',
}
const statusLabel = computed(() => pollError.value
  ? '连接异常'
  : statusLabels[snapshot.value?.status || 'RUNNING'])
const formatValue = (value: unknown) => Array.isArray(value) ? value.join('、') : String(value ?? '—')

function selectBook(book: BookChoice | null) {
  if (book && !running.value && !loading.value) selected.value = book
}
async function loadBooks() {
  const request = ++searchGeneration
  loading.value = true
  loadError.value = ''
  selected.value = null
  try {
    const { data } = await api.get('/api/books', { params: {
      ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '' && value != null)),
      page: page.value, size: 20, includeChildren: true,
      addedFrom: dates.value?.[0], addedTo: dates.value?.[1],
    } })
    if (request !== searchGeneration || disposed) return
    failedCovers.clear()
    books.value = data.content
    total.value = data.totalElements
  } catch {
    if (request !== searchGeneration || disposed) return
    books.value = []
    total.value = 0
    loadError.value = '书籍加载失败，请重新搜索。'
  } finally {
    if (request === searchGeneration) loading.value = false
  }
}
function search() {
  page.value = 0
  void loadBooks()
}
function changePage(value: number) {
  page.value = value - 1
  void loadBooks()
}
async function poll(request: number) {
  try {
    const { data } = await api.get<TrialSnapshot>(`/api/scraper/trial/${taskId.value}`)
    if (request !== generation || disposed) return
    snapshot.value = data
    pollError.value = ''
    running.value = !data.finished
    if (!data.finished) timer = setTimeout(() => void poll(request), 1000)
  } catch (error: any) {
    if (request !== generation || disposed) return
    if (error.response?.status === 404) {
      running.value = false
      taskId.value = ''
      pollError.value = '试刮削不存在或已过期，请重新开始。'
      return
    }
    pollError.value = '进度连接中断，任务可能仍在运行。点击重新连接进度；重启服务器或超过 30 分钟后任务会过期。'
  }
}
function resumePolling() {
  clearTimeout(timer)
  void poll(++generation)
}
async function start() {
  if (!selected.value || !props.source || running.value) return
  running.value = true
  snapshot.value = null
  pollError.value = ''
  try {
    const { data } = await api.post(`/api/scraper/trial/${props.source.configKey}/books/${selected.value.id}`)
    taskId.value = data.taskId
    if (!disposed) resumePolling()
  } catch {
    running.value = false
    pollError.value = '试刮削启动失败，请检查权限或稍后重试。'
  }
}
watch(() => props.modelValue, async (open) => {
  if (!open) return
  void preferences.hydrate()
  if (taskId.value && running.value) resumePolling()
  if (!running.value) search()
  const results = await Promise.allSettled([api.get('/api/categories'), api.get('/api/tags')])
  if (disposed) return
  if (results[0].status === 'fulfilled') categories.value = results[0].value.data
  if (results[1].status === 'fulfilled') tags.value = results[1].value.data
  if (results.some(result => result.status === 'rejected')) loadError.value = '分类或标签加载失败，请关闭弹窗后重试。'
})
watch(() => props.source?.configKey, () => {
  if (running.value) return
  snapshot.value = null
  taskId.value = ''
  pollError.value = ''
})
onBeforeUnmount(() => {
  disposed = true
  generation++
  searchGeneration++
  clearTimeout(timer)
})
</script>

<style scoped>

.trial-browser-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin: 16px 0 12px;
}
.trial-view-switch {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(2, 112px);
  max-width: 100%;
  overflow-x: auto;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-hover);
}
.trial-view-indicator {
  position: absolute;
  z-index: -1;
  top: 4px;
  bottom: 4px;
  left: 4px;
  width: calc((100% - 8px) / 2);
  border-radius: 10px;
  background: var(--surface-card);
  transition: transform 220ms ease;
}
.trial-view-switch.is-card .trial-view-indicator {
  transform: translateX(100%);
}
.trial-view-switch button {
  min-height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.trial-view-switch button[aria-checked="true"] {
  color: var(--primary);
  font-weight: 600;
}
.trial-view-switch button:focus-visible,
.trial-book-card:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.trial-book-browser {
  height: clamp(360px, 55vh, 640px);
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
.trial-card-scroll {
  height: 100%;
  overflow: auto;
  overscroll-behavior: contain;
}
.trial-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  align-items: start;
  gap: 14px;
  padding: 14px;
}
.trial-book-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 6px;
  padding: 10px;
  border: 2px solid transparent;
  border-radius: 12px;
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.trial-book-card:hover {
  background: var(--surface-hover);
}
.trial-book-card.is-selected {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 8%, var(--surface-card));
}
.trial-book-card:disabled {
  cursor: default;
}
.trial-card-cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  border-radius: 6px;
  background: var(--surface-hover);
}
.trial-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.trial-cover-placeholder {
  padding: 12px;
  overflow-wrap: anywhere;
  text-align: center;
  color: var(--text-secondary);
}
.trial-card-check {
  position: absolute;
  top: 6px;
  right: 6px;
  display: grid;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--primary);
  color: white;
  place-items: center;
}
.trial-card-title {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.trial-card-author,
.trial-card-meta {
  width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--text-secondary);
  font-size: 12px;
}

.trial-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 0;
  margin: 18px 0;
  border: 0;
}
.trial-filters :deep(.el-date-editor) {
  width: 100%;
}
.trial-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin: 18px 0;
}
.trial-logs {
  max-height: 220px;
  overflow: auto;
  padding-left: 24px;
  line-height: 1.9;
}
.trial-logs time {
  font-variant-numeric: tabular-nums;
}
.trial-value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
@media (max-width: 640px) {
  .trial-filters {
    grid-template-columns: minmax(0, 1fr);
  }
  .trial-book-browser {
    height: clamp(300px, 52vh, 480px);
  }
  .trial-card-grid {
    grid-template-columns: repeat(auto-fill, minmax(115px, 1fr));
    gap: 8px;
    padding: 8px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .trial-view-indicator {
    transition: none;
  }
  :deep(.el-progress-bar__inner) {
    animation: none !important;
    transition: none !important;
  }
}
</style>
