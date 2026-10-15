<template>
  <el-dialog
    v-model="dialogVisible"
    :title="`刮削书籍 · ${book?.title || ''}`"
    class="scrape-mode-dialog"
    width="700px"
    top="5vh"
    style="max-width: calc(100vw - 24px)"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
  >
    <template #header="{ titleId }">
      <div class="scrape-dialog-heading">
        <span class="scrape-brand-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path d="M4 5.5c3-1 5.5-.5 8 1.2 2.5-1.7 5-2.2 8-1.2v14c-3-1-5.5-.5-8 1.2-2.5-1.7-5-2.2-8-1.2v-14Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
            <path d="M12 7v13M7 10h2M15 10h2M7 13h2M15 13h2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </span>
        <div>
          <span class="scrape-eyebrow">图书元信息</span>
          <h2 :id="titleId">让书籍信息更完整</h2>
        </div>
      </div>
    </template>
    <div class="scrape-target-card">
      <span class="scrape-target-icon" aria-hidden="true">书</span>
      <div class="scrape-target-copy">
        <small>当前书籍</small>
        <strong>{{ book?.title || '尚未选择书籍' }}</strong>
        <span>{{ book?.author || '作者信息待补全' }}</span>
      </div>
      <span class="scrape-safe-badge">{{ mode === 'auto' ? '保留已有字段' : '按字段确认保存' }}</span>
    </div>
    <div class="scrape-mode-scroll">
      <div class="scrape-mode-switch" role="radiogroup" aria-label="刮削模式" @keydown="switchModeByKeyboard">
        <span class="scrape-mode-indicator" :class="{ 'is-manual': mode === 'manual' }" aria-hidden="true"></span>
        <button
          v-for="(option, index) in modes"
          :key="option.value"
          type="button"
          role="radio"
          :aria-checked="mode === option.value"
          :tabindex="mode === option.value ? 0 : -1"
          :disabled="busy"
          @click="changeMode(option.value)"
        >
          <span class="scrape-mode-number" aria-hidden="true">0{{ index + 1 }}</span>
          <span>
            <strong>{{ option.label }}</strong>
            <small>{{ option.value === 'auto' ? '按优先级查询，自动补全' : '指定来源，核对后保存' }}</small>
          </span>
        </button>
      </div>
    </div>
    <div v-if="mode === 'auto'" class="scraper-content">
      <div class="scrape-section-heading">
        <h3>交给已配置的刮削器</h3>
        <p>依次查询已启用来源，按优先级合并信息，完成后自动保存。</p>
      </div>
      <ol v-if="!loading && !result" class="scrape-workflow" aria-label="自动刮削流程">
        <li>
          <span>01</span><strong>按优先级查询</strong>
          <small>使用系统来源配置</small>
        </li>
        <li>
          <span>02</span><strong>合并元信息</strong>
          <small>高优先级来源优先</small>
        </li>
        <li>
          <span>03</span><strong>自动补全</strong>
          <small>只填充缺失字段</small>
        </li>
      </ol>
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
      <div class="scrape-section-heading">
        <h3>由你决定采用哪份信息</h3>
        <p>选择来源，核对备选、封面和元信息，可按字段选择覆盖后保存。</p>
      </div>
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
      <div class="scrape-manual-note">
        <span aria-hidden="true">✓</span>
        <div>
          <strong>查询和预览不会修改书籍</strong>
          <p>豆瓣可选择多个备选；其他来源返回单条时直接预览。</p>
        </div>
      </div>
      <el-alert v-if="sourceError" :title="sourceError" type="error" :closable="false" />
      <el-button v-if="sourceError" @click="loadSources">重新加载刮削器</el-button>
      <p v-if="manualRunning" role="status">手动查询正在执行，可重新打开过程查看进度。</p>
    </div>
    <template #footer>
      <div class="scrape-mode-footer">
        <span class="scrape-footer-note">{{ mode === 'auto' ? '完成后自动保存' : '确认后才会保存' }}</span>
        <div class="scrape-footer-buttons">
          <el-button :disabled="busy" @click="handleClose">关闭</el-button>
          <el-button v-if="mode === 'auto'" type="primary" :loading="loading" :disabled="busy || !book" @click="handleScrape">
            {{ result ? '重新自动刮削' : '开始自动刮削' }}
          </el-button>
          <el-button v-else type="primary" :disabled="!selectedSource || loadingSources || loading || !book" @click="manualVisible = true">
            {{ manualRunning ? '查看查询进度' : '进入手动刮削' }}
          </el-button>
        </div>
      </div>
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
  get: () => props.visible && !manualVisible.value,
  set: value => { if (!value) handleClose() },
})
watch(busy, value => emit('running', value))
function handleClose() {
  if (busy.value || manualVisible.value) return
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
:global(.scrape-mode-dialog) {
  --el-color-primary: var(--primary);
  --el-color-primary-light-3: color-mix(in srgb, var(--primary) 70%, white);
  --el-color-primary-light-5: color-mix(in srgb, var(--primary) 50%, white);
  --el-color-primary-light-7: color-mix(in srgb, var(--primary) 30%, white);
  --el-color-primary-light-8: color-mix(in srgb, var(--primary) 20%, white);
  --el-color-primary-light-9: color-mix(in srgb, var(--primary) 10%, white);
  --el-color-primary-dark-2: var(--primary-dark);
  display: flex;
  flex-direction: column;
  max-height: 90dvh;
  overflow: hidden;
  padding: 28px;
  border: 1px solid var(--border-color);
  border-radius: 24px;
  background: var(--surface-elevated);
}
:global(.scrape-mode-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
:global(.scrape-mode-dialog .el-dialog__header),
:global(.scrape-mode-dialog .el-dialog__footer) {
  flex-shrink: 0;
}
.scrape-dialog-heading {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-right: 24px;
}
.scrape-brand-mark {
  display: grid;
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
  border: 1px solid color-mix(in srgb, var(--primary) 18%, transparent);
  border-radius: 15px;
  background: color-mix(in srgb, var(--primary) 9%, transparent);
  color: var(--primary);
  place-items: center;
}
.scrape-brand-mark svg {
  width: 26px;
  height: 26px;
}
.scrape-eyebrow {
  color: var(--text-secondary);
  font-size: 11px;
  letter-spacing: 2px;
}
.scrape-dialog-heading h2 {
  margin: 5px 0 0;
  color: var(--text-primary);
  font-size: 23px;
  font-weight: 650;
  line-height: 1.35;
}
.scrape-target-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  margin: 10px 0 24px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: linear-gradient(110deg, color-mix(in srgb, var(--primary) 6%, var(--surface-card)), var(--surface-card));
}
.scrape-target-icon {
  display: grid;
  flex: 0 0 44px;
  height: 58px;
  border-left: 3px solid color-mix(in srgb, var(--primary) 55%, transparent);
  border-radius: 3px 8px 8px 3px;
  background: color-mix(in srgb, var(--primary) 14%, var(--surface-card));
  color: var(--primary);
  font-family: serif;
  font-size: 21px;
  place-items: center;
}
.scrape-target-copy {
  display: grid;
  flex: 1;
  gap: 4px;
  min-width: 0;
}
.scrape-target-copy strong {
  font-size: 17px;
  overflow-wrap: anywhere;
}
.scrape-target-copy small,
.scrape-target-copy > span {
  color: var(--text-secondary);
  font-size: 12px;
}
.scrape-safe-badge {
  padding: 5px 9px;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  color: var(--primary);
  font-size: 11px;
  white-space: nowrap;
}
.scrape-mode-scroll {
  overflow-x: auto;
}
.scrape-mode-switch {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-width: 360px;
  padding: 5px;
  border: 1px solid var(--border-color);
  border-radius: 17px;
  background: color-mix(in srgb, var(--primary) 5%, var(--surface-hover));
}
.scrape-mode-indicator {
  position: absolute;
  z-index: -1;
  top: 5px;
  bottom: 5px;
  left: 5px;
  width: calc((100% - 10px) / 2);
  border: 1px solid color-mix(in srgb, var(--primary) 24%, var(--border-color));
  border-radius: 12px;
  box-sizing: border-box;
  background: var(--surface-elevated);
  transition: transform 220ms ease;
}
.scrape-mode-indicator.is-manual {
  transform: translateX(100%);
}
.scrape-mode-switch button {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 76px;
  padding: 12px 16px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.scrape-mode-switch button[aria-checked="true"] {
  color: var(--primary);
}
.scrape-mode-number {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  opacity: .65;
}
.scrape-mode-switch strong,
.scrape-mode-switch small {
  display: block;
}
.scrape-mode-switch strong {
  margin-bottom: 5px;
  font-size: 15px;
}
.scrape-mode-switch small {
  font-size: 11px;
}
.scrape-mode-switch button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.scraper-content {
  min-height: 200px;
  padding-top: 24px;
}
.scrape-section-heading h3 {
  margin: 0 0 8px;
  color: var(--text-primary);
  font-size: 16px;
}
.scrape-section-heading p {
  margin: 0 0 20px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.scrape-workflow {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.scrape-workflow li {
  display: grid;
  gap: 10px;
  padding: 16px 12px;
  border: 1px solid var(--border-color);
  border-radius: 13px;
}
.scrape-workflow li > span {
  color: var(--text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.scrape-workflow strong {
  color: var(--text-primary);
  font-size: 13px;
}
.scrape-workflow small {
  color: var(--text-secondary);
  font-size: 11px;
}
.scrape-manual-note {
  display: flex;
  gap: 10px;
  margin: 16px 0;
  padding: 14px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--primary) 6%, transparent);
  color: var(--primary);
  font-size: 12px;
}
.scrape-manual-note p {
  margin: 5px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}
.scraper-content :deep(.el-select) {
  width: 100%;
}
.scraping-status {
  padding: 28px 0;
  color: var(--text-secondary);
  text-align: center;
}
.scraping-status .el-icon {
  color: var(--primary);
}
.scrape-mode-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 20px;
  margin-top: 8px;
  border-top: 1px solid var(--border-color);
}
.scrape-footer-note {
  color: var(--text-secondary);
  font-size: 12px;
}
.scrape-footer-buttons {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.scrape-footer-buttons .el-button + .el-button {
  margin-left: 0;
}
@media (max-width: 640px) {
  :global(.scrape-mode-dialog) {
    padding: 20px;
    border-radius: 20px;
  }
  .scrape-dialog-heading h2 {
    font-size: 20px;
  }
  .scrape-safe-badge {
    display: none;
  }
  .scrape-mode-switch button {
    padding: 12px;
  }
  .scrape-mode-number {
    display: none;
  }
  .scrape-workflow {
    gap: 6px;
  }
  .scrape-workflow li {
    padding: 12px 8px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .scrape-mode-indicator {
    transition: none;
  }
}
</style>
