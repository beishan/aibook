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
    <p>使用服务器已保存的配置，仅测试所选来源（包括未启用来源）。绕过缓存，查询与选择阶段不修改书籍；豆瓣候选需加载详情并人工确认后，才会补全书籍，保留已有字段。</p>
    <div class="trial-tab-scroll">
      <div class="trial-tabs" role="tablist" aria-label="试刮削步骤" @keydown="switchTabByKeyboard">
        <span class="trial-tab-indicator" :style="tabIndicatorStyle" aria-hidden="true"></span>
        <button
          v-for="tab in tabs"
          :id="`trial-tab-${tab.value}`"
          :key="tab.value"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.value"
          :aria-controls="`trial-panel-${tab.value}`"
          :tabindex="activeTab === tab.value ? 0 : -1"
          @click="activeTab = tab.value"
        >{{ tab.label }}</button>
      </div>
    </div>
    <section
      v-show="activeTab === 'books'"
      id="trial-panel-books"
      class="trial-panel"
      role="tabpanel"
      aria-labelledby="trial-tab-books"
      tabindex="0"
    >
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
                :disabled="running || confirming"
                :aria-label="`选择${row.title}`"
                @change="selectBook(row)"
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
        <el-button type="primary" :disabled="!selected || loading || running || confirming" :loading="running" @click="start">
          {{ running ? '试刮削中' : '开始试刮削' }}
        </el-button>
        <el-button v-if="pollError && taskId" @click="resumePolling">重新连接进度</el-button>
      </div>
    </section>
    <section
      v-show="activeTab === 'process'"
      id="trial-panel-process"
      class="trial-panel"
      role="tabpanel"
      aria-labelledby="trial-tab-process"
      tabindex="0"
    >
      <el-empty v-if="!snapshot && !running && !pollError" description="选择书籍并开始试刮削后，在这里查看过程" />
      <template v-else>
        <div class="trial-stage-heading" aria-live="polite">
          <div>
            <h3>{{ statusLabel }}</h3>
            <p>{{ selected?.title || '试刮削任务' }} · {{ elapsed }} 秒</p>
          </div>
          <el-button v-if="pollError && taskId" @click="resumePolling">重新连接进度</el-button>
          <el-button v-if="snapshot?.metadata" @click="activeTab = 'result'">查看刮削结果</el-button>
        </div>
        <el-progress :percentage="snapshot?.finished ? 100 : 50" :indeterminate="running" :duration="3" :show-text="false" />
        <ol class="trial-logs">
          <li v-for="(entry, index) in snapshot?.logs || []" :key="index">
            <time>{{ new Date(entry.time).toLocaleTimeString() }}</time>
            <span>{{ entry.message }}</span>
          </li>
        </ol>
        <p v-if="pollError" role="alert">{{ pollError }}</p>
        <section v-if="snapshot?.candidates?.length" class="trial-candidates">
          <h4>备选元信息 · 请核对作者、出版年份和版次后选择</h4>
          <p>目标书籍：{{ selected?.title }} · {{ selected?.author || '未知作者' }}</p>
          <p>书名可能包含副标题或不同版本，候选排序不代表已确认匹配。</p>
          <div class="trial-candidate-list">
            <article
              v-for="candidate in snapshot?.candidates"
              :key="candidate.id"
              class="trial-candidate"
              :class="{ 'is-selected': snapshot?.selectedCandidateId === candidate.id }"
            >
              <div>
                <strong>{{ candidate.title }}</strong>
                <p>{{ candidate.author || '作者待加载详情' }} · {{ candidate.publishDate || '出版年份待核对' }}</p>
                <a :href="candidate.sourceUrl" target="_blank" rel="noopener noreferrer">查看豆瓣原书页</a>
              </div>
              <el-button
                :disabled="running || confirming || snapshot?.applied"
                @click="loadCandidate(candidate.id)"
              >
                {{ snapshot?.selectedCandidateId === candidate.id ? '重新加载详情' : '选择并加载详情' }}
              </el-button>
            </article>
          </div>
        </section>
        <section v-if="snapshot?.diagnostics?.length" class="trial-diagnostics">
          <h4>{{ snapshot?.metadata ? '查询中遇到的问题（后续查询已取得元信息）' : '各查询步骤的诊断' }}</h4>
          <ol>
            <li v-for="(item, index) in snapshot?.diagnostics" :key="index">
              <strong>{{ item.stage }} · {{ item.label }}</strong>
              <span v-if="item.httpStatus"> · HTTP {{ item.httpStatus }}</span>
              <p>{{ item.detail }}</p>
              <p v-if="item.endpoint" class="trial-diagnostic-endpoint">请求端点：{{ item.endpoint }}</p>
              <p>建议：{{ item.suggestion }}</p>
            </li>
          </ol>
        </section>
        <p v-if="candidateError" role="alert">{{ candidateError }}</p>
      </template>
    </section>
    <section
      v-if="activeTab === 'result'"
      id="trial-panel-result"
      class="trial-panel"
      role="tabpanel"
      aria-labelledby="trial-tab-result"
      tabindex="0"
    >
      <div v-if="snapshot?.metadata" class="trial-result">
        <div class="trial-result-heading">
          <div>
            <span class="trial-result-eyebrow">{{ source?.name }} · 元信息预览</span>
            <h3>{{ formatValue(snapshot.metadata.title) }}</h3>
            <p>{{ snapshot.applied ? '已确认补全，已有字段保持不变' : '仅预览，尚未写入书籍' }}</p>
          </div>
          <el-tag :type="snapshot.applied ? 'success' : 'info'">{{ statusLabel }}</el-tag>
        </div>
        <div class="trial-result-layout">
          <aside class="trial-result-cover">
            <div class="trial-result-image">
              <img
                v-if="resultCover && coverAllowed && !resultCoverFailed"
                :src="getCoverThumbnailUrl(resultCover)"
                :alt="`${formatValue(snapshot.metadata.title)}的封面`"
                @error="resultCoverFailed = true"
              />
              <div v-else class="trial-cover-placeholder">
                <strong>{{ formatValue(snapshot.metadata.title) }}</strong>
                <p>{{ !coverAllowed ? '封面已按隐私设置隐藏' : resultCoverFailed ? '封面加载失败' : '来源未提供封面' }}</p>
                <el-button v-if="resultCoverFailed && coverAllowed" size="small" @click="resultCoverFailed = false">重试加载</el-button>
              </div>
            </div>
            <span>封面预览</span>
          </aside>
          <el-form label-position="top" class="trial-metadata-form">
            <el-form-item v-for="(label, field) in summaryFields" :key="field" :label="label">
              <el-input :model-value="formatValue(snapshot.metadata[field])" readonly />
            </el-form-item>
            <el-form-item label="标签" class="trial-field-wide">
              <div class="trial-result-tags">
                <el-tag v-for="tag in resultTags" :key="tag" effect="plain">{{ tag }}</el-tag>
                <span v-if="!resultTags.length" class="trial-muted">来源未提供标签</span>
              </div>
            </el-form-item>
            <el-form-item label="内容简介" class="trial-field-wide">
              <el-input
                :model-value="formatValue(snapshot.metadata.description)"
                type="textarea"
                :autosize="{ minRows: 5, maxRows: 14 }"
                resize="none"
                readonly
              />
            </el-form-item>
          </el-form>
        </div>
        <div v-if="snapshot.status === 'REVIEW_REQUIRED'" class="trial-result-confirm">
          <p>核对封面和元信息后确认。只补全空字段，保留已有书名、作者、ISBN、封面等信息。</p>
          <el-button type="primary" :loading="confirming" :disabled="running" @click="confirmCandidate">
            确认此候选并补全书籍（保留已有字段）
          </el-button>
          <el-button v-if="snapshot.candidates?.length" :disabled="confirming" @click="activeTab = 'process'">返回备选选择</el-button>
        </div>
        <el-alert
          v-if="snapshot.applied"
          type="success"
          :closable="false"
          :title="`已补全书籍 · 更新字段：${updatedFieldLabels}`"
          show-icon
        />
      </div>
      <div v-else-if="snapshot?.finished && snapshot.failure" class="trial-failure" role="status">
        <strong>{{ snapshot.failure.label }}</strong>
        <p>{{ snapshot.failure.detail }}</p>
        <p>建议：{{ snapshot.failure.suggestion }}</p>
        <el-button @click="activeTab = 'process'">查看详细过程</el-button>
      </div>
      <el-empty
        v-else
        :description="running ? '正在查询元信息，请在过程页查看进度' : snapshot?.candidates?.length ? '请先选择备选书籍并加载详情' : '暂无刮削结果'"
      >
        <el-button v-if="snapshot || running || pollError" @click="activeTab = 'process'">{{ snapshot?.candidates?.length ? '前往备选选择' : '查看刮削过程' }}</el-button>
      </el-empty>
      <p v-if="candidateError" role="alert">{{ candidateError }}</p>
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
interface ScrapeFailure {
  code: string
  label: string
  detail: string
  suggestion: string
  stage: string
  httpStatus: number | null
  endpoint: string
}
interface MetadataCandidate {
  id: string
  title: string
  author: string
  publishDate: string
  coverUrl: string
  sourceUrl: string
}
interface TrialSnapshot {
  status: string
  finished: boolean
  elapsedMs: number
  logs: { time: string; message: string }[]
  metadata: Record<string, unknown> | null
  failure?: ScrapeFailure | null
  diagnostics?: ScrapeFailure[]
  candidates?: MetadataCandidate[]
  selectedCandidateId?: string | null
  applied?: boolean
  updatedFields?: string[]
}
const preferences = usePreferencesStore()
const viewMode = computed(() => preferences.scraperTrialViewMode)
const viewOptions: { value: ScraperTrialViewMode; label: string }[] = [
  { value: 'table', label: '列表视图' },
  { value: 'card', label: '卡片视图' },
]
type TrialTab = 'books' | 'process' | 'result'
const activeTab = ref<TrialTab>('books')
const tabs = computed(() => [
  { value: 'books' as const, label: '书籍选择' },
  { value: 'process' as const, label: snapshot.value?.candidates?.length ? '刮削过程 & 备选选择' : '刮削过程' },
  { value: 'result' as const, label: '刮削结果' },
])
const tabIndicatorStyle = computed(() => ({
  transform: `translateX(${tabs.value.findIndex(tab => tab.value === activeTab.value) * 100}%)`,
}))
async function switchTabByKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const group = event.currentTarget as HTMLElement
  const index = tabs.value.findIndex(tab => tab.value === activeTab.value)
  const target = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.value.length - 1
    : (index + (event.key === 'ArrowRight' ? 1 : tabs.value.length - 1)) % tabs.value.length
  activeTab.value = tabs.value[target].value
  await nextTick()
  group.querySelector<HTMLButtonElement>('[aria-selected="true"]')?.focus()
}
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
const summaryFields = {
  title: '书名', author: '作者', isbn: 'ISBN', publisher: '出版社',
  publishDate: '出版日期', language: '语言', rating: '来源评分',
}
const resultCover = computed(() => typeof snapshot.value?.metadata?.coverUrl === 'string'
  ? snapshot.value.metadata.coverUrl : '')
const coverAllowed = computed(() => selected.value ? shouldLoadBookCover(selected.value.id) : false)
const resultCoverFailed = ref(false)
const resultTags = computed<string[]>(() => {
  const value = snapshot.value?.metadata?.tags
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string' && Boolean(item.trim()))
    : typeof value === 'string' && value.trim() ? [value] : []
})
const updatedFieldLabels = computed(() => snapshot.value?.updatedFields?.map(field =>
  fields[field as keyof typeof fields] || field).join('、') || '无（现有信息完整）')
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
const confirming = ref(false)
watch(() => running.value || confirming.value, value => emit('running', value))
const snapshot = ref<TrialSnapshot | null>(null)
const pollError = ref('')
const taskId = ref('')
const candidateError = ref('')
watch(resultCover, () => { resultCoverFailed.value = false })
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
  AWAITING_SELECTION: '待选择候选',
  REVIEW_REQUIRED: '待人工确认',
  APPLIED: '已确认补全',
}
const statusLabel = computed(() => pollError.value
  ? '连接异常'
  : statusLabels[snapshot.value?.status || 'RUNNING'])
const formatValue = (value: unknown) => Array.isArray(value) ? value.join('、') : String(value ?? '—')

function resetCandidateReview() {
  if (!snapshot.value) return
  clearTimeout(timer)
  generation++
  snapshot.value = null
  taskId.value = ''
  candidateError.value = ''
}
function selectBook(book: BookChoice | null) {
  if (!book || running.value || loading.value || confirming.value) return
  if (selected.value?.id !== book.id) resetCandidateReview()
  selected.value = book
}
async function loadBooks() {
  if (running.value || confirming.value) return
  resetCandidateReview()
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
    const previous = snapshot.value
    snapshot.value = data
    if (data.finished && (!previous?.finished || previous.status !== data.status)) {
      activeTab.value = data.candidates?.length && !data.metadata ? 'process' : 'result'
    }
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
async function loadCandidate(candidateId: string) {
  if (running.value || confirming.value || !taskId.value) return
  running.value = true
  activeTab.value = 'process'
  candidateError.value = ''
  try {
    const { data } = await api.post<TrialSnapshot>(`/api/scraper/trial/${taskId.value}/candidates/${candidateId}`)
    if (disposed) return
    snapshot.value = data
    if (data.finished && data.metadata) activeTab.value = 'result'
    running.value = !data.finished
    resumePolling()
  } catch (error: any) {
    running.value = false
    candidateError.value = error.response?.data?.message || '候选详情加载失败，请重试。'
  }
}
async function confirmCandidate() {
  if (confirming.value || running.value || snapshot.value?.status !== 'REVIEW_REQUIRED'
      || !snapshot.value.selectedCandidateId) return
  confirming.value = true
  candidateError.value = ''
  const request = generation
  try {
    await api.post(`/api/scraper/trial/${taskId.value}/candidates/${snapshot.value.selectedCandidateId}/confirm`)
    if (disposed || request !== generation) return
    await poll(request)
  } catch (error: any) {
    if (!disposed && request === generation) {
      candidateError.value = error.response?.data?.message || '确认补全请求未完成，正在核对服务器状态。'
      await poll(request)
    }
  } finally {
    confirming.value = false
  }
}
async function start() {
  if (!selected.value || !props.source || running.value || confirming.value) return
  running.value = true
  activeTab.value = 'process'
  snapshot.value = null
  pollError.value = ''
  candidateError.value = ''
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
  if (!running.value && !confirming.value && !snapshot.value) search()
  const results = await Promise.allSettled([api.get('/api/categories'), api.get('/api/tags')])
  if (disposed) return
  if (results[0].status === 'fulfilled') categories.value = results[0].value.data
  if (results[1].status === 'fulfilled') tags.value = results[1].value.data
  if (results.some(result => result.status === 'rejected')) loadError.value = '分类或标签加载失败，请关闭弹窗后重试。'
})
watch(() => props.source?.configKey, () => {
  if (running.value || confirming.value) return
  snapshot.value = null
  taskId.value = ''
  pollError.value = ''
  activeTab.value = 'books'
})
onBeforeUnmount(() => {
  disposed = true
  generation++
  searchGeneration++
  clearTimeout(timer)
})
</script>

<style scoped>

.trial-tab-scroll {
  overflow-x: auto;
  margin: 18px 0;
}
.trial-tabs {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  min-width: 540px;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: color-mix(in srgb, var(--primary) 12%, var(--surface-card));
}
.trial-tab-indicator {
  position: absolute;
  z-index: -1;
  top: 4px;
  bottom: 4px;
  left: 4px;
  width: calc((100% - 8px) / 3);
  border-radius: 10px;
  background: var(--surface-card);
  border: 1px solid var(--border-color);
  box-sizing: border-box;
  transition: transform 220ms ease;
}
.trial-tabs button {
  min-height: 40px;
  padding: 8px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.trial-tabs button[aria-selected="true"] {
  color: var(--primary);
  font-weight: 600;
}
.trial-tabs button:focus-visible,
.trial-panel:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.trial-panel {
  max-height: max(360px, calc(100dvh - 280px));
  min-height: 360px;
  overflow: auto;
  padding: 2px 6px 8px;
  overscroll-behavior: contain;
}
.trial-stage-heading,
.trial-result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin: 10px 0 20px;
}
.trial-stage-heading h3,
.trial-result-heading h3 {
  margin: 6px 0;
  font-size: 22px;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}
.trial-stage-heading p,
.trial-result-heading p,
.trial-muted {
  color: var(--text-secondary);
}
.trial-result-eyebrow {
  color: var(--primary);
  font-size: 12px;
}
.trial-result-layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  align-items: start;
  gap: 28px;
}
.trial-result-cover {
  display: grid;
  gap: 12px;
  text-align: center;
  color: var(--text-secondary);
}
.trial-result-image {
  display: grid;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface-hover);
  place-items: center;
}
.trial-result-image img {
  width: 100%;
  height: 100%;
  min-height: 0;
  object-fit: contain;
}
.trial-metadata-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 18px;
  min-width: 0;
}
.trial-field-wide {
  grid-column: 1 / -1;
}
.trial-result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.trial-result-confirm {
  margin: 20px 0;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface-hover);
}
.trial-result-confirm p {
  margin: 0 0 14px;
  line-height: 1.7;
}
.trial-result-confirm .el-button {
  max-width: 100%;
  height: auto;
  min-height: 32px;
  margin: 4px 8px 4px 0;
  white-space: normal;
}

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
  max-height: 340px;
  overflow: auto;
  padding-left: 24px;
  line-height: 1.9;
}
.trial-logs li {
  padding: 5px 0;
  overflow-wrap: anywhere;
}
.trial-logs time {
  margin-right: 10px;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
.trial-candidate-list {
  display: grid;
  gap: 10px;
  margin: 14px 0;
}
.trial-candidate {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
.trial-candidate.is-selected {
  border-color: var(--primary);
  background: var(--surface-hover);
}
.trial-candidate p {
  margin: 8px 0;
  color: var(--text-secondary);
}
.trial-candidate a {
  color: var(--primary);
}
.trial-candidate-note {
  margin: 16px 0;
  font-weight: 600;
}
.trial-failure {
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--surface-hover);
}
.trial-failure p,
.trial-diagnostics p {
  margin: 8px 0 0;
  overflow-wrap: anywhere;
}
.trial-diagnostics ol {
  display: grid;
  gap: 14px;
  padding-left: 24px;
}
.trial-diagnostic-endpoint {
  color: var(--text-secondary);
  font-size: 12px;
}
.trial-value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
@media (max-width: 640px) {
  .trial-result-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }
  .trial-result-cover {
    width: 160px;
    justify-self: center;
  }
  .trial-metadata-form {
    grid-template-columns: minmax(0, 1fr);
  }
  .trial-panel {
    min-height: 280px;
    max-height: max(280px, calc(100dvh - 260px));
  }
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
  .trial-tab-indicator,
  .trial-view-indicator {
    transition: none;
  }
  :deep(.el-progress-bar__inner) {
    animation: none !important;
    transition: none !important;
  }
}
</style>
