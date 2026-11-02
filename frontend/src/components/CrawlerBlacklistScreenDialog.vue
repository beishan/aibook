<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${bookScopeLabel} · 一键筛选黑名单`"
    width="min(960px, 96vw)"
    append-to-body
    :close-on-click-modal="!applying"
    :close-on-press-escape="!applying"
    :show-close="!applying"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="screen-description">
      按各网站已启用的自动黑名单规则，筛选本地{{ bookScopeLabel }}的书名、作者、简介与标签，不访问源网站。
      筛选不会自动拉黑，确认结果后再加入黑名单。
    </p>
    <div class="screen-controls">
      <el-select v-model="siteId" filterable aria-label="书籍来源网站" :disabled="busy || restoring">
        <el-option :value="0" label="全部网站" />
        <el-option v-for="site in sites" :key="site.id" :value="site.id" :label="site.siteName" />
      </el-select>
      <el-button type="primary" :loading="starting" :disabled="busy || restoring" @click="start">
        开始筛选
      </el-button>
      <el-button v-if="error" :disabled="busy && !running" @click="restore">重新查询进度与结果</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="screen-error" />
    <section v-if="job" class="screen-progress" aria-live="polite" aria-label="黑名单筛选进度">
      <div>
        <strong>{{ statusLabel }}</strong>
        <span>范围：{{ scopeLabel }} · 已检查 {{ job.processed }} / {{ job.total }} 本 · 命中 {{ job.matched }} 本</span>
      </div>
      <el-progress :percentage="percentage" :status="job.status === 'FAILED' ? 'exception' : job.status === 'COMPLETED' ? 'success' : undefined" />
      <p v-if="job.status === 'RUNNING'">可关闭弹窗，后台继续筛选；再次打开可恢复进度。</p>
      <p v-else-if="job.status === 'FAILED'">{{ job.errorMessage }}；下方可能是部分结果，请重新筛选后再加入黑名单。</p>
      <p v-else>本次结果共 {{ total }} 本，分页切换会清空勾选；“全部加入”包含本次筛选所有页，自动跳过已忽略项；忽略仅限本次结果。</p>
    </section>
    <template v-if="job && !running">
      <div class="screen-actions">
        <el-button :disabled="!canApply || !selectableRows.length" @click="selectPage">一键选择本页</el-button>
        <el-button :disabled="!canApply || !selectedIds.length" :loading="applying === 'selected'" @click="apply(false)">
          加入黑名单（{{ selectedIds.length }}）
        </el-button>
        <el-button type="danger" plain :disabled="!canApply || !total" :loading="applying === 'all'" @click="apply(true)">
          一键全部加入黑名单（跳过已忽略）
        </el-button>
      </div>
      <el-table
        ref="table" v-loading="loadingResults" :data="rows" row-key="bookId" max-height="42vh"
        @selection-change="selectionChanged"
      >
        <el-table-column type="expand" width="40">
          <template #default="{ row }">
            <section class="match-details" aria-label="黑名单命中详情">
              <h4>命中详情 · 筛选时记录</h4>
              <p>以下为本次筛选时保存的规则与原文片段，之后修改规则或书籍信息不会改变此记录。</p>
              <template v-if="row.matchDetails?.length">
                <article v-for="(detail, index) in row.matchDetails" :key="index" class="match-detail">
                  <div>
                    <el-tag size="small" :type="detail.type === 'TAG' ? 'warning' : 'danger'">
                      {{ detail.type === 'TAG' ? '标签规则' : '关键字规则' }}
                    </el-tag>
                    <strong>{{ detail.field }}{{ detail.type === 'TAG' ? '完整匹配标签' : '包含关键字' }}「{{ detail.value }}」</strong>
                  </div>
                  <blockquote>
                    <CrawlerBookTitle v-if="detail.field === '书名'" :title="detail.evidence" />
                    <template v-else>{{ detail.evidence }}</template>
                  </blockquote>
                </article>
              </template>
              <el-alert
                v-else type="info" :closable="false"
                :title="row.reason || '旧筛选结果未记录命中原因'"
                description="此历史结果未保存详细匹配记录。重新开始筛选后，可查看匹配字段和原文片段。"
              />
            </section>
          </template>
        </el-table-column>
        <el-table-column type="selection" width="48" :selectable="rowSelectable" />
        <el-table-column label="书名" min-width="180">
          <template #default="{ row }"><CrawlerBookTitle :title="row.bookName" /></template>
        </el-table-column>
        <el-table-column prop="author" label="作者" min-width="100" show-overflow-tooltip />
        <el-table-column prop="siteName" label="来源网站" min-width="120" show-overflow-tooltip />
        <el-table-column label="命中原因" min-width="220">
          <template #default="{ row }">
            <div class="match-reason">
              <span>{{ row.reason || '未记录命中原因' }}</span>
              <el-button link type="primary" @click="toggleMatchDetails(row)">
                查看详情<template v-if="row.matchDetails?.length">（{{ row.matchDetails.length }}）</template>
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.blacklisted ? 'danger' : 'info'">
              {{ !row.available ? '书籍已删除' : row.blacklisted ? '已加入' : row.ignored ? '已忽略' : '待确认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              link type="primary" :loading="ignoringId === row.bookId"
              :disabled="!canApply || !row.available || row.blacklisted"
              @click="toggleIgnored(row)"
            >
              {{ row.ignored ? '取消忽略' : '忽略' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>{{ loadingResults ? '加载中' : '本次筛选没有匹配书籍' }}</template>
      </el-table>
      <div class="screen-pagination">
        <el-pagination
          v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[20, 50, 100]"
          :disabled="busy || loadingResults" layout="total, sizes, prev, pager, next" small
          @current-change="loadPage" @size-change="pageSizeChanged"
        />
      </div>
    </template>
    <template #footer>
      <el-button :disabled="!!applying" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, type TableInstance } from 'element-plus'
import CrawlerBookTitle from '@/components/CrawlerBookTitle.vue'
import { crawlerApi, type CrawlerSite } from '@/utils/crawler'
import type { BlacklistScreenJob, BlacklistScreenItem, BlacklistScreenScope } from '@/utils/crawlerBlacklistScreen'

const props = withDefaults(defineProps<{
  modelValue: boolean
  sites: CrawlerSite[]
  scope?: BlacklistScreenScope
}>(), { scope: 'MANAGED' })
const bookScopeLabel = computed(() => props.scope === 'DISCOVERED' ? '发现书籍' : '采集书籍')
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const siteId = ref(0)
const job = ref<BlacklistScreenJob>()
const rows = ref<BlacklistScreenItem[]>([])
const selectedIds = ref<number[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const table = ref<TableInstance>()
const restoring = ref(false)
const starting = ref(false)
const loadingResults = ref(false)
const applying = ref<false | 'all' | 'selected'>(false)
const ignoringId = ref<number | null>(null)
const error = ref('')
let generation = 0
let pageRequest = 0
let timer: ReturnType<typeof setTimeout> | undefined
const running = computed(() => job.value?.status === 'RUNNING')
const busy = computed(() => starting.value || running.value || !!applying.value || ignoringId.value !== null)
const canApply = computed(() => job.value?.status === 'COMPLETED'
  && !busy.value && !restoring.value && !loadingResults.value && !error.value)
const selectableRows = computed(() => rows.value.filter(row => row.available && !row.blacklisted && !row.ignored))
const percentage = computed(() => job.value?.status === 'COMPLETED' ? 100
  : job.value?.total ? Math.min(99, Math.floor(job.value.processed / job.value.total * 100)) : 0)
const statusLabel = computed(() => job.value?.status === 'COMPLETED' ? '筛选完成'
  : job.value?.status === 'FAILED' ? '筛选失败' : '正在筛选')
const scopeLabel = computed(() => job.value?.siteId
  ? props.sites.find(site => site.id === job.value?.siteId)?.siteName || '原来源网站' : '全部网站')

watch(() => props.modelValue, open => {
  if (open) void restore()
  else stopPolling()
}, { immediate: true })
onBeforeUnmount(stopPolling)

function stopPolling() {
  generation++
  pageRequest++
  starting.value = false
  loadingResults.value = false
  restoring.value = false
  ignoringId.value = null
  if (timer) clearTimeout(timer)
}

function reason(failure: any, fallback: string) {
  return failure?.response?.data?.message || fallback
}

async function restore() {
  stopPolling()
  const current = generation
  restoring.value = true
  error.value = ''
  try {
    const latest = await crawlerApi.latestBlacklistScreen(props.scope)
    if (current !== generation) return
    if (job.value?.id !== latest?.id) page.value = 1
    job.value = latest || undefined
    if (!latest) {
      rows.value = []
      total.value = 0
      return
    }
    if (latest.status === 'RUNNING') schedulePoll(current)
    else await loadPage()
  } catch (failure) {
    if (current === generation) error.value = reason(failure, '无法恢复筛选，请重新查询')
  } finally {
    if (current === generation) restoring.value = false
  }
}

async function start() {
  if (busy.value || restoring.value) return
  stopPolling()
  const current = generation
  starting.value = true
  error.value = ''
  selectedIds.value = []
  try {
    const created = await crawlerApi.startBlacklistScreen(siteId.value || undefined, props.scope)
    if (current !== generation) return
    job.value = created
    rows.value = []
    page.value = 1
    total.value = 0
    if (created.status === 'RUNNING') schedulePoll(current)
    else await loadPage()
  } catch (failure) {
    if (current === generation) error.value = reason(failure, '启动结果尚未确认，请重新查询进度，避免重复启动')
  } finally {
    if (current === generation) starting.value = false
  }
}

function schedulePoll(current: number) {
  timer = setTimeout(() => void poll(current), 700)
}

async function poll(current: number) {
  if (current !== generation || !job.value) return
  try {
    const progress = await crawlerApi.blacklistScreenProgress(job.value.id)
    if (current !== generation) return
    job.value = progress
    if (progress.status === 'RUNNING') schedulePoll(current)
    else await loadPage()
  } catch (failure) {
    if (current === generation) error.value = reason(failure, '进度查询中断，后台仍在执行，请重新查询')
  }
}

async function loadPage() {
  if (!job.value || running.value) return
  const current = ++pageRequest
  const currentGeneration = generation
  loadingResults.value = true
  error.value = ''
  selectedIds.value = []
  table.value?.clearSelection()
  try {
    const result = await crawlerApi.blacklistScreenResults(job.value.id, page.value - 1, pageSize.value)
    if (current !== pageRequest || currentGeneration !== generation) return
    rows.value = result.content
    total.value = result.totalElements
  } catch (failure) {
    if (current === pageRequest && currentGeneration === generation) {
      rows.value = []
      error.value = reason(failure, '筛选结果加载失败，请重新查询')
    }
  } finally {
    if (current === pageRequest && currentGeneration === generation) loadingResults.value = false
  }
}

function pageSizeChanged() {
  page.value = 1
  void loadPage()
}

function toggleMatchDetails(row: BlacklistScreenItem) {
  table.value?.toggleRowExpansion(row)
}

function rowSelectable(row: BlacklistScreenItem) {
  return canApply.value && row.available && !row.blacklisted && !row.ignored
}

function selectionChanged(selected: BlacklistScreenItem[]) {
  selectedIds.value = selected.filter(row => row.available && !row.blacklisted && !row.ignored).map(row => row.bookId)
}

function selectPage() {
  if (!canApply.value) return
  table.value?.clearSelection()
  for (const row of selectableRows.value) table.value?.toggleRowSelection(row, true)
}

async function toggleIgnored(row: BlacklistScreenItem) {
  if (!canApply.value || !job.value || !row.available || row.blacklisted) return
  const currentGeneration = generation
  const ignored = !row.ignored
  ignoringId.value = row.bookId
  try {
    await crawlerApi.ignoreBlacklistScreenResult(job.value.id, row.bookId, ignored)
    if (currentGeneration !== generation) return
    row.ignored = ignored
    if (ignored) {
      selectedIds.value = selectedIds.value.filter(id => id !== row.bookId)
      table.value?.toggleRowSelection(row, false)
    }
    ElMessage.success(ignored ? '已忽略，仅对本次筛选结果生效' : '已取消本次忽略')
  } catch (failure) {
    if (currentGeneration === generation) {
      ElMessage.error(reason(failure, '忽略状态未确认，请重新查询结果'))
    }
  } finally {
    if (currentGeneration === generation) ignoringId.value = null
  }
}

async function apply(all: boolean) {
  if (!canApply.value || !job.value || (!all && !selectedIds.value.length)) return
  applying.value = all ? 'all' : 'selected'
  try {
    const result = await crawlerApi.applyBlacklistScreen(job.value.id, all, all ? [] : selectedIds.value)
    ElMessage.success(`已处理 ${result.affectedCount} 本书籍，加入黑名单`)
    selectedIds.value = []
    await loadPage()
    emit('changed')
  } catch (failure) {
    ElMessage.error(reason(failure, '加入黑名单结果未确认，请重新查询结果后重试'))
  } finally {
    applying.value = false
  }
}
</script>

<style scoped>
.match-reason {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  overflow-wrap: anywhere;
}
.match-details {
  padding: 16px 24px;
  max-height: 360px;
  overflow: auto;
}
.match-details h4 {
  margin: 0 0 8px;
}
.match-details > p {
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.match-detail {
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.match-detail > div {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.match-detail strong {
  min-width: 0;
  overflow-wrap: anywhere;
}
.match-detail blockquote {
  margin: 10px 0 0;
  padding: 10px 14px;
  border-left: 3px solid var(--el-color-warning);
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
}

.screen-description,
.screen-progress p {
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}
.screen-controls,
.screen-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 16px 0;
}
.screen-controls .el-select {
  width: min(280px, 100%);
}
.screen-actions .el-button + .el-button {
  margin-left: 0;
}
.screen-error {
  margin-bottom: 16px;
}
.screen-progress {
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
}
.screen-progress > div:first-child {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.screen-pagination {
  margin-top: 16px;
  overflow-x: auto;
}
</style>
