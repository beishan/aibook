<template>
  <section class="card glass operation-log-card">
    <header class="log-header">
      <div>
        <h2 class="panel-title">操作日志</h2>
        <p class="header-hint">查看关键操作，为每种日志配置保留时间；已保留日志仅可手动删除；已删除账户的归档日志仅管理员可见。</p>
      </div>
    </header>

    <div class="log-tabs-scroll">
      <div class="log-tabs" role="tablist" aria-label="操作日志栏目">
        <span class="log-tab-indicator" :style="{ transform: `translateX(${activeTab === 'list' ? 0 : 100}%)` }" aria-hidden="true" />
        <button
          v-for="tab in tabs"
          :id="`operation-log-tab-${tab.key}`"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :aria-controls="`operation-log-panel-${tab.key}`"
          :tabindex="activeTab === tab.key ? 0 : -1"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
          @keydown="handleTabKey"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <section
      v-show="activeTab === 'list'"
      id="operation-log-panel-list"
      ref="tablePanelRef"
      role="tabpanel"
      aria-labelledby="operation-log-tab-list"
    >
      <div class="log-filters">
        <el-select v-model="actionFilter" clearable placeholder="全部日志类型" aria-label="日志类型筛选" @change="applyFilters">
          <el-option v-for="rule in savedRules" :key="rule.action" :value="rule.action" :label="rule.label" />
        </el-select>
        <el-input
          v-model="keyword"
          clearable
          maxlength="200"
          placeholder="搜索操作内容、详情或书名"
          aria-label="日志内容搜索"
          @keyup.enter="applyFilters"
          @clear="applyFilters"
        />
        <el-button :loading="loading" @click="applyFilters">搜索</el-button>
        <el-button :disabled="loading" @click="resetFilters">重置</el-button>
        <el-button :loading="loading" @click="loadLogs()">刷新</el-button>
      </div>
      <div v-if="loading && !logs.length" class="log-state" role="status">加载中…</div>
      <div v-else-if="!logs.length" class="log-state">暂无符合条件的操作日志</div>
      <div
        v-else
        class="log-table-wrap"
        :style="{ maxHeight: `${tableMaxHeight}px` }"
        tabindex="0"
        role="region"
        aria-label="操作日志列表，可滚动查看"
      >
        <table class="log-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>操作类型</th>
              <th>操作内容</th>
              <th>详情</th>
              <th>保留状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td class="time-cell">{{ formatChinaDateTime(log.createdAt) }}</td>
              <td><span class="tag tag-primary">{{ actionLabel(log.action) }}</span></td>
              <td class="description-cell">
                <span>{{ log.description }}</span>
                <small v-if="log.archived" class="archived-owner">
                  已删除账户：{{ log.ownerUsername || '未知用户' }}（#{{ log.ownerUserId ?? '未知' }}）
                </small>
              </td>
              <td class="details-cell">{{ log.details || '—' }}</td>
              <td><span :class="{ 'retained-label': log.retained }">{{ log.retained ? '永久保留' : log.archived ? '归档保留' : '按类型策略' }}</span></td>
              <td>
                <div class="log-row-actions">
                  <el-button
                    size="small"
                    :disabled="log.retained || busyIds.has(log.id)"
                    :aria-label="`${log.retained ? '已保留' : '保留'}日志：${log.description}`"
                    @click="retainLog(log)"
                  >
                    {{ log.retained ? '已保留' : '保留' }}
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    plain
                    :loading="busyIds.has(log.id)"
                    :aria-label="`删除日志：${log.description}`"
                    @click="deleteLog(log)"
                  >
                    删除
                  </el-button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="total > 0" class="log-pagination">
        <span>共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="applyFilters"
        />
      </div>
    </section>

    <section
      v-show="activeTab === 'retention'"
      id="operation-log-panel-retention"
      class="log-retention"
      role="tabpanel"
      aria-labelledby="operation-log-tab-retention"
    >
      <p class="retention-description">
        规则及自动清理计划按当前账户保存。可按计划自动清理，也可一键清理全部符合已保存规则的日志，不受列表筛选影响。
        未配置、永久留存和单条已保留的日志不清理。期限按日志创建时间计算，“最近两天”表示保留最近 48 小时。
      </p>
      <p class="retention-description">单条日志点击保留后无法取消保留，任何批量或自动流程均跳过；如需移除，请在日志列表手动删除。已删除账户的归档日志不参与规则清理，仅管理员可手动管理。</p>
      <el-alert v-if="policyError" :title="policyError" type="error" :closable="false" />
      <section v-if="!policyLoading && !policyError" class="cleanup-schedule" aria-labelledby="cleanup-schedule-title">
        <div class="schedule-heading">
          <h3 id="cleanup-schedule-title">自动清理计划</h3>
          <el-switch
            v-model="draftSchedule.enabled"
            active-text="启用自动清理"
            aria-label="启用日志自动清理"
            :disabled="saving || cleaning"
          />
        </div>
        <p class="header-hint">按北京时间（Asia/Shanghai）执行。关闭自动清理不影响手动一键清理；修改后点击下方“保存策略”生效。</p>
        <div class="schedule-fields">
          <label>
            <span>执行频率</span>
            <el-select v-model="draftSchedule.frequency" aria-label="自动清理执行频率" :disabled="!draftSchedule.enabled || saving || cleaning">
              <el-option label="每小时" value="HOURLY" />
              <el-option label="每天" value="DAILY" />
              <el-option label="每周" value="WEEKLY" />
            </el-select>
          </label>
          <label v-if="draftSchedule.frequency === 'WEEKLY'">
            <span>执行星期</span>
            <el-select v-model="draftSchedule.dayOfWeek" aria-label="自动清理执行星期" :disabled="!draftSchedule.enabled || saving || cleaning">
              <el-option v-for="(day, index) in weekDays" :key="day" :label="day" :value="index + 1" />
            </el-select>
          </label>
          <label v-if="draftSchedule.frequency === 'HOURLY'">
            <span>每小时第几分钟执行（0–59）</span>
            <el-input-number
              v-model="draftSchedule.minute"
              :min="0"
              :max="59"
              :precision="0"
              controls-position="right"
              aria-label="自动清理执行分钟"
              :disabled="!draftSchedule.enabled || saving || cleaning"
            />
          </label>
          <label v-else>
            <span>执行时间</span>
            <el-time-picker
              v-model="scheduleTime"
              format="HH:mm"
              value-format="HH:mm"
              :clearable="false"
              aria-label="自动清理执行时间"
              :disabled="!draftSchedule.enabled || saving || cleaning"
            />
          </label>
        </div>
        <p class="schedule-status">
          已保存计划的下一次执行：{{ !savedSchedule.enabled ? '自动清理已关闭' : !hasExpiringRules ? '无有限期限规则，不执行' : savedSchedule.nextRunAt ? formatChinaDateTime(savedSchedule.nextRunAt) : '等待计划初始化' }}
        </p>
        <p v-if="savedSchedule.lastRunAt" class="header-hint">上次自动检查完成：{{ formatChinaDateTime(savedSchedule.lastRunAt) }}</p>
      </section>
      <div v-if="policyLoading" class="log-state" role="status">加载保留策略…</div>
      <div v-else-if="draftRules.length" class="retention-grid">
        <label v-for="rule in draftRules" :key="rule.action" class="retention-rule">
          <span>{{ rule.label }}</span>
          <el-select
            :model-value="rule.retentionDays === null ? 'unset' : rule.retentionDays"
            :disabled="saving || cleaning"
            :aria-label="`${rule.label}日志保留时间`"
            @change="rule.retentionDays = $event === 'unset' ? null : Number($event)"
          >
            <el-option
              v-for="option in retentionOptions"
              :key="option.value === null ? 'unset' : option.value"
              :value="option.value === null ? 'unset' : option.value"
              :label="option.label"
            />
            <el-option
              v-if="isCustomDays(rule.retentionDays)"
              :value="rule.retentionDays!"
              :label="`最近 ${rule.retentionDays} 天`"
            />
          </el-select>
        </label>
      </div>
      <p v-if="dirty" class="unsaved-hint" role="status">有未保存的修改，请先保存策略再清理。</p>
      <div class="retention-actions">
        <el-button type="primary" :loading="saving" :disabled="policyLoading || !!policyError || cleaning || !dirty" @click="savePolicies">
          保存策略
        </el-button>
        <el-button
          type="danger"
          plain
          :loading="cleaning"
          :disabled="policyLoading || !!policyError || saving || dirty || !hasExpiringRules"
          @click="cleanupLogs"
        >
          按规则一键清理
        </el-button>
        <el-button :disabled="saving || cleaning || policyLoading" @click="reloadPolicies">重新加载策略</el-button>
      </div>
      <p v-if="!policyLoading && !hasExpiringRules" class="header-hint">尚未保存有限期限规则，当前没有可清理的日志类型。</p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useViewportTableHeight } from '@/utils/useViewportTableHeight'
import { formatChinaDateTime } from '@/utils/dateTime'
import { confirm, message } from '@/utils/message'
import {
  operationLogsApi,
  retentionKey,
  cleanupScheduleKey,
  defaultLogCleanupSchedule,
  type LogCleanupSchedule,
  retentionOptions,
  type LogRetentionRule,
  type OperationLog,
} from '@/utils/operationLogs'

const { tablePanelRef, tableMaxHeight } = useViewportTableHeight({
  tableSelector: ':scope > .log-table-wrap',
  paginationSelector: ':scope > .log-pagination',
})

type LogTab = 'list' | 'retention'
const tabs: { key: LogTab; label: string }[] = [
  { key: 'list', label: '日志列表' },
  { key: 'retention', label: '保留策略' },
]
const activeTab = ref<LogTab>('list')
const logs = ref<OperationLog[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const actionFilter = ref('')
const keyword = ref('')
const busyIds = ref(new Set<number>())
const savedRules = ref<LogRetentionRule[]>([])
const draftRules = ref<LogRetentionRule[]>([])
const savedSchedule = ref<LogCleanupSchedule>(defaultLogCleanupSchedule())
const draftSchedule = ref<LogCleanupSchedule>(defaultLogCleanupSchedule())
const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const scheduleTime = computed({
  get: () => `${String(draftSchedule.value.hour).padStart(2, '0')}:${String(draftSchedule.value.minute).padStart(2, '0')}`,
  set: (value: string) => {
    if (!value) return
    const [hour, minute] = value.split(':').map(Number)
    draftSchedule.value.hour = hour
    draftSchedule.value.minute = minute
  },
})
const policyLoading = ref(false)
const policyError = ref('')
const saving = ref(false)
const cleaning = ref(false)
let listRequest = 0
const dirty = computed(() => retentionKey(savedRules.value) !== retentionKey(draftRules.value)
  || cleanupScheduleKey(savedSchedule.value) !== cleanupScheduleKey(draftSchedule.value))
const hasExpiringRules = computed(() => savedRules.value.some(rule => (rule.retentionDays ?? 0) > 0))
const actionLabel = (action: string) => savedRules.value.find(rule => rule.action === action)?.label || action
const isCustomDays = (days: number | null) => days !== null && !retentionOptions.some(option => option.value === days)

async function handleTabKey(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  activeTab.value = event.key === 'Home' ? 'list' : event.key === 'End' ? 'retention'
    : activeTab.value === 'list' ? 'retention' : 'list'
  await nextTick()
  document.getElementById(`operation-log-tab-${activeTab.value}`)?.focus()
}

async function loadLogs(page = currentPage.value - 1) {
  const request = ++listRequest
  loading.value = true
  try {
    const data = await operationLogsApi.list({
      page,
      size: pageSize.value,
      action: actionFilter.value || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    if (request !== listRequest) return
    const lastPage = Math.max(0, Math.ceil(data.totalElements / pageSize.value) - 1)
    if (page > lastPage) return await loadLogs(lastPage)
    logs.value = data.content
    total.value = data.totalElements
    currentPage.value = data.number + 1
  } catch (error: any) {
    if (request === listRequest) message.error(error.response?.data?.message || '操作日志加载失败')
  } finally {
    if (request === listRequest) loading.value = false
  }
}

function applyFilters() {
  currentPage.value = 1
  void loadLogs(0)
}

function resetFilters() {
  actionFilter.value = ''
  keyword.value = ''
  applyFilters()
}

const handlePageChange = (page: number) => void loadLogs(page - 1)

async function loadPolicies() {
  policyLoading.value = true
  policyError.value = ''
  try {
    const data = await operationLogsApi.settings()
    savedRules.value = data.rules.map(rule => ({ ...rule }))
    draftRules.value = data.rules.map(rule => ({ ...rule }))
    savedSchedule.value = { ...defaultLogCleanupSchedule(), ...data.schedule }
    draftSchedule.value = { ...savedSchedule.value }
  } catch (error: any) {
    policyError.value = error.response?.data?.message || '保留策略加载失败，请重新加载'
  } finally {
    policyLoading.value = false
  }
}

async function reloadPolicies() {
  if (dirty.value && !await confirm('重新加载将放弃尚未保存的策略修改，是否继续？')) return
  await loadPolicies()
}

async function savePolicies() {
  if (saving.value || cleaning.value) return
  saving.value = true
  try {
    const data = await operationLogsApi.saveSettings({ rules: draftRules.value, schedule: draftSchedule.value })
    savedRules.value = data.rules.map(rule => ({ ...rule }))
    draftRules.value = data.rules.map(rule => ({ ...rule }))
    savedSchedule.value = { ...defaultLogCleanupSchedule(), ...data.schedule }
    draftSchedule.value = { ...savedSchedule.value }
    message.success('日志保留策略及自动清理计划已保存')
  } catch (error: any) {
    message.error(error.response?.data?.message || '保留策略保存失败')
  } finally {
    saving.value = false
  }
}

async function cleanupLogs() {
  if (cleaning.value || saving.value || dirty.value || !hasExpiringRules.value) return
  if (!await confirm('按已保存规则清理当前账户全部过期日志？此操作不可恢复，未配置和已保留的日志不会删除。')) return
  cleaning.value = true
  try {
    const result = await operationLogsApi.cleanup()
    message.success(`清理完成，已删除 ${result.deletedCount} 条过期日志`)
    await loadLogs()
  } catch (error: any) {
    message.error(error.response?.data?.message || '日志清理失败')
  } finally {
    cleaning.value = false
  }
}

async function retainLog(log: OperationLog) {
  if (log.retained || busyIds.value.has(log.id)) return
  busyIds.value.add(log.id)
  try {
    await operationLogsApi.retain(log.id)
    log.retained = true
    message.success('已永久保留，之后只能手动删除')
    await loadLogs()
  } catch (error: any) {
    message.error(error.response?.data?.message || '保留日志失败')
  } finally {
    busyIds.value.delete(log.id)
  }
}

async function deleteLog(log: OperationLog) {
  if (busyIds.value.has(log.id)) return
  if (!await confirm(log.retained ? '这条日志已永久保留，确定仍要手动删除？删除后不可恢复。' : '确定删除这条日志？删除后不可恢复。')) return
  busyIds.value.add(log.id)
  try {
    await operationLogsApi.delete(log.id)
    message.success('日志已删除')
    await loadLogs()
  } catch (error: any) {
    message.error(error.response?.data?.message || '删除日志失败')
  } finally {
    busyIds.value.delete(log.id)
  }
}

onMounted(() => {
  void loadLogs()
  void loadPolicies()
})
</script>

<style scoped>
.operation-log-card {
  overflow: hidden;
}
.log-header {
  padding: 24px;
  border-bottom: 1px solid var(--border-color-light);
}
.panel-title {
  margin: 0;
  color: var(--text-primary);
  font-size: 20px;
}
.header-hint {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.8;
}
.log-tabs-scroll {
  overflow-x: auto;
  padding: 20px 24px 0;
}
.log-tabs {
  position: relative;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  width: min(100%, 360px);
  min-width: 240px;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-muted, var(--primary-alpha-10));
  isolation: isolate;
}
.log-tab-indicator {
  position: absolute;
  inset: 4px auto 4px 4px;
  z-index: -1;
  width: calc((100% - 8px) / 2);
  border: 1px solid var(--border-color-light);
  border-radius: 10px;
  background: var(--surface-card, var(--bg-primary));
  transition: transform .24s ease;
}
.log-tabs button {
  min-height: 38px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}
.log-tabs button.active {
  color: var(--primary);
  font-weight: 600;
}
.log-tabs button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
.log-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 20px 24px;
}
.log-filters .el-button + .el-button {
  margin-left: 0;
}
.log-filters > .el-select {
  flex: 1 1 180px;
  max-width: 240px;
}
.log-filters > .el-input {
  flex: 2 1 220px;
}
.log-state {
  display: grid;
  min-height: 220px;
  place-items: center;
  color: var(--text-secondary);
}
.log-table-wrap {
  overflow: auto;
}
.log-table-wrap:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}
.log-table {
  width: 100%;
  min-width: 1000px;
  border-collapse: collapse;
  color: var(--text-primary);
  font-size: 13px;
}
.log-table th,
.log-table td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color-light);
  text-align: left;
  vertical-align: middle;
}
.log-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--surface-muted, var(--primary-alpha-10));
  color: var(--text-secondary);
  white-space: nowrap;
}
.time-cell,
.log-table td:nth-child(5) {
  white-space: nowrap;
}
.description-cell,
.details-cell {
  max-width: 400px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.archived-owner {
  display: block;
  margin-top: 6px;
  color: var(--text-secondary);
  font-weight: 400;
}
.details-cell {
  color: var(--text-secondary);
}
.tag-primary {
  background: var(--primary-alpha-10);
  color: var(--primary);
}
.log-row-actions {
  display: flex;
  gap: 8px;
}
.log-row-actions .el-button + .el-button {
  margin-left: 0;
}
.retained-label {
  color: var(--primary);
  font-weight: 600;
}
.log-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  overflow-x: auto;
  color: var(--text-secondary);
  font-size: 13px;
}
.log-retention {
  padding: 24px;
}
.retention-description {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.9;
}
.cleanup-schedule {
  margin: 20px 0;
  padding: 20px;
  border: 1px solid var(--border-color-light);
  border-radius: 12px;
}
.schedule-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.schedule-heading h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
}
.schedule-fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-top: 16px;
}
.schedule-fields label {
  display: grid;
  gap: 8px;
  color: var(--text-primary);
  font-size: 13px;
}
.schedule-fields :deep(.el-input-number),
.schedule-fields :deep(.el-date-editor) {
  width: 100%;
}
.schedule-status {
  margin: 16px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.retention-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px 24px;
  margin: 24px 0;
}
.retention-rule {
  display: grid;
  gap: 8px;
  color: var(--text-primary);
  font-size: 13px;
}
.retention-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.retention-actions .el-button + .el-button {
  margin-left: 0;
}
.unsaved-hint {
  color: var(--primary);
  font-size: 13px;
}
@media (max-width: 640px) {
  .log-header,
  .log-retention {
    padding: 16px;
  }
  .log-tabs-scroll {
    padding: 16px 16px 0;
  }
  .log-filters,
  .log-pagination {
    padding: 16px;
  }
  .log-filters > .el-select {
    max-width: none;
  }
  .retention-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (prefers-reduced-motion: reduce) {
  .log-tab-indicator {
    transition: none;
  }
}
</style>
