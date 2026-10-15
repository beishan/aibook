<template>
  <el-dialog
    v-model="dialogVisible"
    :title="`刮削书籍 · ${book?.title || ''}`"
    width="640px"
    style="max-width: calc(100vw - 24px)"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
  >
    <div class="scrape-mode-scroll">
      <div class="scrape-mode-switch" role="radiogroup" aria-label="刮削模式" @keydown="switchModeByKeyboard">
        <span class="scrape-mode-indicator" :class="{ 'is-manual': mode === 'manual' }" aria-hidden="true"></span>
        <button
          v-for="option in modes"
          :key="option.value"
          type="button"
          role="radio"
          :aria-checked="mode === option.value"
          :tabindex="mode === option.value ? 0 : -1"
          :disabled="busy"
          @click="changeMode(option.value)"
        >{{ option.label }}</button>
      </div>
    </div>
    <div v-if="mode === 'auto'" class="scraper-content">
      <p>按系统中已启用刮削器的优先级依次查询，完成后自动补全元信息，保留已有字段。</p>
      <div v-if="loading" class="scraping-status" role="status">
        <el-icon class="is-loading" :size="36"><Loading /></el-icon>
        <p>正在查询并保存《{{ book?.title }}》的元信息…</p>
      </div>
      <el-result
        v-else-if="result"
        :icon="result.success ? 'success' : 'warning'"
        :title="result.success ? '自动刮削完成' : '未完成刮削'"
        :sub-title="result.message"
      >
        <template #extra>
          <p v-if="result.sources?.length">来源：{{ result.sources.join('、') }}</p>
          <p v-if="result.updatedFields?.length">更新字段：{{ result.updatedFields.map(field => fieldLabels[field] || field).join('、') }}</p>
          <p v-else-if="result.success">现有字段已保留，没有需要更新的字段。</p>
        </template>
      </el-result>
    </div>
    <div v-else class="scraper-content">
      <p>指定一个刮削器，查询后选择备选并核对封面和元信息，最后手动保存。查询和预览不会修改书籍。</p>
      <el-form label-position="top">
        <el-form-item label="指定刮削器">
          <el-select v-model="sourceKey" :loading="loadingSources" :disabled="busy" placeholder="选择刮削来源">
            <el-option
              v-for="source in sources"
              :key="source.configKey"
              :value="source.configKey"
              :label="`${source.name}${source.needsApiKey && !source.hasApiKey ? '（缺少 API Key）' : ''}`"
              :disabled="source.needsApiKey && !source.hasApiKey"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert v-if="sourceError" :title="sourceError" type="error" :closable="false" />
      <el-button v-if="sourceError" @click="loadSources">重新加载刮削器</el-button>
      <p v-if="manualRunning" role="status">手动查询正在执行，可重新打开过程查看进度。</p>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="handleClose">关闭</el-button>
      <el-button v-if="mode === 'auto'" type="primary" :loading="loading" :disabled="busy || !book" @click="handleScrape">
        {{ result ? '重新自动刮削' : '开始自动刮削' }}
      </el-button>
      <el-button v-else type="primary" :disabled="!selectedSource || loadingSources || loading || !book" @click="manualVisible = true">
        {{ manualRunning ? '查看查询进度' : '进入手动刮削' }}
      </el-button>
    </template>
  </el-dialog>
  <ScraperTrialDialog
    v-model="manualVisible"
    :book="book"
    :source="selectedSource"
    manual
    @running="manualRunning = $event"
    @saved="emit('refresh')"
  />
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import ScraperTrialDialog from '@/components/ScraperTrialDialog.vue'
import { scrapeBook, type ScrapeResponse } from '@/utils/scraper'
import { getScraperStatus, type ScraperStatus } from '@/utils/config'

const props = defineProps<{ visible: boolean; book: { id: number; title: string; author?: string } | null }>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'refresh'): void
  (e: 'running', value: boolean): void
}>()
type Mode = 'auto' | 'manual'
const mode = ref<Mode>('auto')
const modes: { value: Mode; label: string }[] = [
  { value: 'auto', label: '自动刮削' },
  { value: 'manual', label: '手动指定刮削' },
]
const loading = ref(false)
const manualRunning = ref(false)
const manualVisible = ref(false)
const busy = computed(() => loading.value || manualRunning.value)
const result = ref<ScrapeResponse | null>(null)
const sources = ref<ScraperStatus[]>([])
const sourceKey = ref('')
const loadingSources = ref(false)
const sourceError = ref('')
const selectedSource = computed(() => sources.value.find(source => source.configKey === sourceKey.value) || null)
const fieldLabels: Record<string, string> = {
  title: '书名', author: '作者', isbn: 'ISBN', publisher: '出版社', publishDate: '出版日期',
  description: '简介', coverUrl: '封面', language: '语言', rating: '评分', tags: '标签',
}
const dialogVisible = computed({
  get: () => props.visible,
  set: value => { if (!value) handleClose() },
})
watch(busy, value => emit('running', value))
function handleClose() {
  if (busy.value) return
  manualVisible.value = false
  emit('close')
}
function changeMode(value: Mode) {
  if (busy.value) return
  mode.value = value
  if (value === 'manual' && !sources.value.length) void loadSources()
}
async function switchModeByKeyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) || busy.value) return
  event.preventDefault()
  const group = event.currentTarget as HTMLElement
  changeMode(event.key === 'Home' ? 'auto' : event.key === 'End' ? 'manual' : mode.value === 'auto' ? 'manual' : 'auto')
  await nextTick()
  group.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.focus()
}
async function loadSources() {
  if (loadingSources.value) return
  loadingSources.value = true
  sourceError.value = ''
  try {
    sources.value = (await getScraperStatus()).sort((left, right) => left.priority - right.priority)
    if (!selectedSource.value) {
      sourceKey.value = sources.value.find(source => !source.needsApiKey || source.hasApiKey)?.configKey || ''
    }
  } catch {
    sourceError.value = '刮削器加载失败，请重试。'
  } finally {
    loadingSources.value = false
  }
}
async function handleScrape() {
  if (!props.book || busy.value) return
  const bookId = props.book.id
  loading.value = true
  result.value = null
  try {
    result.value = await scrapeBook(bookId)
    if (result.value.success) emit('refresh')
  } catch (error: any) {
    result.value = { success: false, message: error.response?.data?.message || error.message || '刮削失败，请稍后重试' }
  } finally {
    loading.value = false
  }
}
watch(() => props.book?.id, () => {
  result.value = null
  mode.value = 'auto'
  manualVisible.value = false
})
watch(() => props.visible, open => {
  if (open && !busy.value) result.value = null
})
</script>

<style scoped>
.scrape-mode-scroll {
  overflow-x: auto;
}
.scrape-mode-switch {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-width: 320px;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: color-mix(in srgb, var(--primary) 12%, var(--surface-card));
}
.scrape-mode-indicator {
  position: absolute;
  z-index: -1;
  top: 4px;
  bottom: 4px;
  left: 4px;
  width: calc((100% - 8px) / 2);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-sizing: border-box;
  background: var(--surface-card);
  transition: transform 220ms ease;
}
.scrape-mode-indicator.is-manual {
  transform: translateX(100%);
}
.scrape-mode-switch button {
  min-height: 40px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.scrape-mode-switch button[aria-checked="true"] {
  color: var(--primary);
  font-weight: 600;
}
.scrape-mode-switch button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.scraper-content {
  min-height: 180px;
  padding-top: 12px;
}
.scraper-content p {
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.scraper-content :deep(.el-select) {
  width: 100%;
}
.scraping-status {
  padding: 30px 0;
  text-align: center;
}
@media (prefers-reduced-motion: reduce) {
  .scrape-mode-indicator {
    transition: none;
  }
}
</style>
