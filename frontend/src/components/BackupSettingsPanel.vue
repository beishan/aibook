<template>
  <section class="backup-settings">
    <div class="backup-hero card glass">
      <div class="backup-hero-copy">
        <p class="backup-eyebrow">RECOVERY POINTS</p>
        <h2>数据备份</h2>
        <p>为数据库与书库文件建立独立备份计划，备份不再占用发版时间。</p>
      </div>
      <div class="backup-hero-actions">
        <button class="btn" :disabled="loading" @click="refreshAll">刷新</button>
        <button class="btn btn-primary" @click="openDialog('run')">立即备份</button>
      </div>
      <div class="backup-path" :class="pathStatusClass">
        <span class="backup-path-dot" />
        <span>NAS 备份目录</span>
        <code>{{ pathInfo?.path || '读取中…' }}</code>
        <strong>{{ pathStatusText }}</strong>
      </div>
    </div>

    <div class="backup-tabs" role="tablist" aria-label="数据备份页面">
      <span
        class="backup-tab-indicator"
        :class="`backup-tab-indicator--${activeTab}`"
        aria-hidden="true"
      />
      <button
        id="backup-tasks-tab"
        ref="taskTabButton"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'tasks'"
        :aria-controls="'backup-tasks-panel'"
        :tabindex="activeTab === 'tasks' ? 0 : -1"
        @click="activateTab('tasks')"
        @keydown="handleTabKeydown($event, 'tasks')"
      >
        备份任务 <span>{{ tasks.length }}</span>
      </button>
      <button
        id="backup-retention-tab"
        ref="retentionTabButton"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'retention'"
        :aria-controls="'backup-retention-panel'"
        :tabindex="activeTab === 'retention' ? 0 : -1"
        @click="activateTab('retention')"
        @keydown="handleTabKeydown($event, 'retention')"
      >
        保留策略
      </button>
      <button
        id="backup-executions-tab"
        ref="executionTabButton"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'executions'"
        :aria-controls="'backup-executions-panel'"
        :tabindex="activeTab === 'executions' ? 0 : -1"
        @click="activateTab('executions')"
        @keydown="handleTabKeydown($event, 'executions')"
      >
        执行结果 <span>{{ executions.length }}</span>
      </button>
    </div>

    <section
      id="backup-tasks-panel"
      class="backup-tab-panel"
      role="tabpanel"
      aria-labelledby="backup-tasks-tab"
      tabindex="0"
      v-show="activeTab === 'tasks'"
    >
      <div class="backup-section-heading">
        <div><span class="backup-section-kicker">PLANS</span><h3>备份任务</h3></div>
        <button class="btn btn-primary" @click="openDialog('task')">＋ 新建任务</button>
      </div>

      <div v-if="loading && tasks.length === 0" class="backup-empty">正在读取备份计划…</div>
      <div v-else-if="tasks.length === 0" class="backup-empty card glass">
        <span class="backup-empty-mark">＋</span>
        <strong>还没有备份任务</strong>
        <p>创建一个计划，选择备份内容和独立的执行时间。</p>
        <button class="btn btn-primary" @click="openDialog('task')">创建第一个任务</button>
      </div>
      <div v-else class="backup-list-frame card glass">
        <div class="backup-list-scroll">
          <table class="backup-data-table backup-task-table">
            <thead>
              <tr>
                <th scope="col">任务</th>
                <th scope="col">备份内容</th>
                <th scope="col">执行计划</th>
                <th scope="col">状态</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in tasks" :key="task.id">
                <th scope="row" class="task-name-cell">{{ task.name }}</th>
                <td class="task-content-cell">{{ contentLabel(task) }}</td>
                <td class="task-schedule-cell">
                  <strong>{{ task.scheduleEnabled ? task.cronExpression : '仅手动执行' }}</strong>
                  <small v-if="task.scheduleEnabled">下次执行 {{ formatTime(task.nextRunAt) }}</small>
                  <small v-else>定时计划已关闭</small>
                </td>
                <td>
                  <span class="backup-task-state" :class="task.enabled ? 'is-on' : 'is-off'">
                    {{ task.enabled ? '已启用' : '已停用' }}
                  </span>
                </td>
                <td>
                  <div class="backup-task-actions">
                    <button class="btn btn-primary" :disabled="!pathInfo?.writable" @click="runTask(task)">立即备份</button>
                    <button class="btn" @click="openDialog('task', task)">编辑</button>
                    <button class="btn btn-danger" @click="removeTask(task)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <section
      id="backup-retention-panel"
      class="backup-tab-panel"
      role="tabpanel"
      aria-labelledby="backup-retention-tab"
      tabindex="0"
      v-show="activeTab === 'retention'"
    >
      <div class="backup-section-heading">
        <div><span class="backup-section-kicker">RETENTION</span><h3>备份保留策略</h3></div>
        <button
          class="btn btn-primary"
          :disabled="loading || retentionSaving"
          @click="saveRetention"
        >
          {{ retentionSaving ? '保存中…' : '保存保留设置' }}
        </button>
      </div>
      <section class="backup-retention card glass">
        <label class="backup-inline-check">
          <input v-model="retention.enabled" type="checkbox" />
          <span>启用自动清理</span>
        </label>
        <div class="backup-retention-fields">
          <label class="backup-retention-field">
            <span>最近备份全部保留</span>
            <div>
              <input v-model.number="retention.recentDays" type="number" min="1" max="3650" />
              <small>天</small>
            </div>
          </label>
          <label class="backup-retention-field">
            <span>之后每月保留最新备份</span>
            <div>
              <input v-model.number="retention.monthlyMonths" type="number" min="0" max="120" />
              <small>个月</small>
            </div>
          </label>
        </div>
        <p class="backup-retention-note">
          {{ retentionDescription }} 每天凌晨 04:15 执行清理。关闭自动清理时，备份文件不会自动删除；执行历史会保留。
        </p>
      </section>
    </section>

    <section
      id="backup-executions-panel"
      class="backup-tab-panel"
      role="tabpanel"
      aria-labelledby="backup-executions-tab"
      tabindex="0"
      v-show="activeTab === 'executions'"
    >
      <div class="backup-section-heading">
        <div><span class="backup-section-kicker">RUN HISTORY</span><h3>执行结果</h3></div>
        <span class="backup-history-note">最近 100 次执行</span>
      </div>

      <div v-if="executions.length === 0" class="backup-empty card glass">
        <strong>暂无执行记录</strong>
        <p>手动执行或定时任务开始后，结果会显示在这里。</p>
      </div>
      <div v-else class="backup-list-frame card glass">
        <div class="backup-list-scroll">
          <table class="backup-data-table backup-execution-table">
            <thead>
              <tr>
                <th scope="col">执行时间</th>
                <th scope="col">任务</th>
                <th scope="col">备份内容 / 结果</th>
                <th scope="col">状态</th>
                <th scope="col">文件数</th>
                <th scope="col">大小</th>
                <th scope="col">备份目录</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="execution in executions" :key="execution.id">
                <td class="execution-time-cell">
                  {{ formatTime(execution.finishedAt || execution.startedAt) }}
                </td>
                <th scope="row" class="execution-task-cell">{{ execution.taskName }}</th>
                <td class="execution-details-cell">
                  <strong>{{ execution.contents }}</strong>
                  <small>{{ execution.details || execution.errorMessage || '任务正在等待执行' }}</small>
                </td>
                <td>
                  <span class="history-status" :class="`status-${execution.status.toLowerCase()}`">
                    <span>{{ statusGlyph(execution.status) }}</span>
                    {{ statusLabel(execution.status) }}
                  </span>
                </td>
                <td>{{ execution.fileCount ?? '—' }}</td>
                <td>{{ formatBytes(execution.fileSizeBytes) }}</td>
                <td class="execution-path-cell">
                  <code v-if="execution.outputPath">{{ execution.outputPath }}</code>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="min(620px, calc(100vw - 32px))" destroy-on-close>
      <div class="backup-form">
        <label v-if="dialogMode === 'task'" class="backup-field">
          <span>任务名称</span>
          <el-input v-model="draft.name" maxlength="120" placeholder="例如：每晚数据库备份" />
        </label>
        <fieldset class="backup-content-options">
          <legend>备份内容</legend>
          <label v-for="option in contentOptions" :key="option.key" class="backup-check-option">
            <input v-model="draft[option.key]" type="checkbox" />
            <span><strong>{{ option.label }}</strong><small>{{ option.description }}</small></span>
          </label>
        </fieldset>
        <div v-if="dialogMode === 'task'" class="backup-schedule-options">
          <label class="backup-inline-check">
            <input v-model="draft.enabled" type="checkbox" />
            <span>启用此任务</span>
          </label>
          <label class="backup-inline-check">
            <input v-model="draft.scheduleEnabled" type="checkbox" />
            <span>按计划定时执行</span>
          </label>
          <label v-if="draft.scheduleEnabled" class="backup-field">
            <span>定时规则（Cron）</span>
            <el-input v-model="draft.cronExpression" placeholder="0 0 2 * * ?" />
            <small>示例：每天 02:00 使用 <code>0 0 2 * * ?</code>；每周日 03:30 使用 <code>0 30 3 ? * SUN</code>。</small>
          </label>
        </div>
        <p class="backup-form-path">输出目录：<code>{{ pathInfo?.path || '/app/backups' }}</code></p>
      </div>
      <template #footer>
        <button class="btn" @click="dialogVisible = false">取消</button>
        <button class="btn btn-primary" :disabled="saving || !pathInfo?.writable" @click="submitDialog">
          {{ saving ? '提交中…' : dialogMode === 'run' ? '开始备份' : '保存任务' }}
        </button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { message } from '@/utils/message'
import {
  backupApi,
  type BackupExecution,
  type BackupPath,
  type BackupRetentionSettings,
  type BackupTask,
  type BackupTaskInput,
} from '@/utils/backups'

type ContentKey = 'databaseEnabled' | 'booksEnabled' | 'uploadsEnabled' | 'crawlerDataEnabled'
type DialogMode = 'task' | 'run'
type BackupTab = 'tasks' | 'retention' | 'executions'

const contentOptions: { key: ContentKey; label: string; description: string }[] = [
  { key: 'databaseEnabled', label: 'PostgreSQL 数据库', description: '业务数据、账户与系统配置；使用 pg_dump 一致性归档。' },
  { key: 'booksEnabled', label: '书籍文件', description: '复制 NAS 上配置的主书库目录，文件量较大时耗时较长。' },
  { key: 'uploadsEnabled', label: '用户上传文件', description: '封面、导入文件等应用上传内容。' },
  { key: 'crawlerDataEnabled', label: '采集数据文件', description: '采集任务产生的中间文件和导出内容。' },
]

const tasks = ref<BackupTask[]>([])
const executions = ref<BackupExecution[]>([])
const pathInfo = ref<BackupPath | null>(null)
const loading = ref(false)
const saving = ref(false)
const retentionSaving = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('task')
const editingId = ref<number | null>(null)
const activeTab = ref<BackupTab>('tasks')
const taskTabButton = ref<HTMLButtonElement | null>(null)
const retentionTabButton = ref<HTMLButtonElement | null>(null)
const executionTabButton = ref<HTMLButtonElement | null>(null)

const emptyDraft = (): BackupTaskInput => ({
  name: '', databaseEnabled: true, booksEnabled: false, uploadsEnabled: false,
  crawlerDataEnabled: false, scheduleEnabled: true, enabled: true,
  cronExpression: '0 0 2 * * ?',
})
const draft = reactive<BackupTaskInput>(emptyDraft())
const retention = reactive<BackupRetentionSettings>({
  enabled: false,
  recentDays: 7,
  monthlyMonths: 12,
})

const dialogTitle = computed(() => dialogMode.value === 'run' ? '立即备份' : editingId.value ? '编辑备份任务' : '新建备份任务')
const pathStatusText = computed(() => !pathInfo.value ? '正在检查' : pathInfo.value.writable ? '可写入' : pathInfo.value.exists ? '无写入权限' : '目录未挂载')
const pathStatusClass = computed(() => pathInfo.value?.writable ? 'path-ready' : 'path-error')
const retentionDescription = computed(() => {
  if (retention.monthlyMonths === 0) {
    return `保留最近 ${retention.recentDays} 天的所有备份，超过后清理。`
  }
  return `保留最近 ${retention.recentDays} 天的所有备份；更早的备份按月保留最新 1 份，共保留最近 ${retention.monthlyMonths} 个月。`
})

const activateTab = (tab: BackupTab) => {
  activeTab.value = tab
}

const focusTab = (tab: BackupTab) => {
  activateTab(tab)
  void nextTick(() => {
    const button = {
      tasks: taskTabButton.value,
      retention: retentionTabButton.value,
      executions: executionTabButton.value,
    }[tab]
    button?.focus()
  })
}

const handleTabKeydown = (event: KeyboardEvent, currentTab: BackupTab) => {
  const tabs: BackupTab[] = ['tasks', 'retention', 'executions']
  const currentIndex = tabs.indexOf(currentTab)

  if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
    event.preventDefault()
    const step = event.key === 'ArrowRight' ? 1 : -1
    const nextIndex = (currentIndex + step + tabs.length) % tabs.length
    focusTab(tabs[nextIndex])
  } else if (event.key === 'Home') {
    event.preventDefault()
    focusTab('tasks')
  } else if (event.key === 'End') {
    event.preventDefault()
    focusTab('executions')
  }
}

const refreshAll = async (quiet = false) => {
  loading.value = true
  try {
    const [path, taskRows, executionRows, retentionSettings] = await Promise.all([
      backupApi.path(), backupApi.tasks(), backupApi.executions(), backupApi.retention(),
    ])
    pathInfo.value = path
    tasks.value = taskRows
    executions.value = executionRows
    Object.assign(retention, retentionSettings)
  } catch (error: any) {
    if (!quiet) message.error(error.response?.data?.message || '备份信息加载失败')
  } finally {
    loading.value = false
  }
}

const saveRetention = async () => {
  if (
    !Number.isInteger(retention.recentDays)
    || retention.recentDays < 1
    || retention.recentDays > 3650
  ) {
    message.warning('最近备份保留天数需设置为 1 到 3650 天')
    return
  }
  if (
    !Number.isInteger(retention.monthlyMonths)
    || retention.monthlyMonths < 0
    || retention.monthlyMonths > 120
  ) {
    message.warning('月度备份保留月数需设置为 0 到 120 个月')
    return
  }
  retentionSaving.value = true
  try {
    Object.assign(retention, await backupApi.updateRetention({ ...retention }))
    message.success('备份保留设置已保存')
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存备份保留设置失败')
  } finally {
    retentionSaving.value = false
  }
}

const openDialog = (mode: DialogMode, task?: BackupTask) => {
  dialogMode.value = mode
  editingId.value = task?.id ?? null
  Object.assign(draft, task ? {
    name: task.name,
    databaseEnabled: task.databaseEnabled,
    booksEnabled: task.booksEnabled,
    uploadsEnabled: task.uploadsEnabled,
    crawlerDataEnabled: task.crawlerDataEnabled,
    scheduleEnabled: task.scheduleEnabled,
    enabled: task.enabled,
    cronExpression: task.cronExpression,
  } : emptyDraft())
  if (mode === 'run') draft.name = '立即备份'
  dialogVisible.value = true
}

const submitDialog = async () => {
  if (!hasContent()) {
    message.warning('至少选择一种备份内容')
    return
  }
  if (dialogMode.value === 'task' && !draft.name.trim()) {
    message.warning('请填写任务名称')
    return
  }
  saving.value = true
  try {
    if (dialogMode.value === 'run') {
      await backupApi.runImmediately({ ...draft })
      message.success('备份已加入执行队列')
    } else if (editingId.value) {
      await backupApi.updateTask(editingId.value, { ...draft })
      message.success('备份任务已更新')
    } else {
      await backupApi.createTask({ ...draft })
      message.success('备份任务已创建')
    }
    dialogVisible.value = false
    await refreshAll(true)
  } catch (error: any) {
    message.error(error.response?.data?.message || '备份操作失败')
  } finally {
    saving.value = false
  }
}

const runTask = async (task: BackupTask) => {
  try {
    await backupApi.runTask(task.id)
    message.success(`「${task.name}」已加入执行队列`)
    await refreshAll(true)
  } catch (error: any) {
    message.error(error.response?.data?.message || '无法启动备份任务')
  }
}

const removeTask = async (task: BackupTask) => {
  if (!window.confirm(`删除备份任务「${task.name}」？已有执行记录会保留。`)) return
  try {
    await backupApi.deleteTask(task.id)
    message.success('备份任务已删除')
    await refreshAll(true)
  } catch (error: any) {
    message.error(error.response?.data?.message || '删除备份任务失败')
  }
}

const hasContent = () => contentOptions.some(option => draft[option.key])
const contentLabel = (task: BackupTask) => contentOptions.filter(option => task[option.key]).map(option => option.label).join(' · ')
const formatTime = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
const formatBytes = (value: number | null) => {
  if (value === null) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = Math.max(0, value)
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${size >= 10 || unit === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[unit]}`
}
const statusLabel = (status: BackupExecution['status']) => ({
  QUEUED: '排队中',
  RUNNING: '执行中',
  SUCCESS: '成功',
  FAILED: '失败',
})[status]
const statusGlyph = (status: BackupExecution['status']) => ({
  QUEUED: '…',
  RUNNING: '↻',
  SUCCESS: '✓',
  FAILED: '!',
})[status]

onMounted(() => {
  void refreshAll()
})
</script>

<style scoped>
.backup-settings {
  display: grid;
  gap: 22px;
  color: var(--text-primary);
}

.backup-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 22px;
  padding: 26px;
  border: 1px solid color-mix(in srgb, var(--primary-color) 18%, var(--border-color));
  background: linear-gradient(
    118deg,
    color-mix(in srgb, var(--primary-color) 9%, var(--surface-card)),
    var(--surface-card) 66%
  );
}

.backup-eyebrow,
.backup-section-kicker {
  margin: 0 0 7px;
  color: var(--primary-color);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .17em;
}

.backup-hero h2 {
  margin: 0;
  font-size: 24px;
  letter-spacing: -.035em;
}

.backup-hero-copy > p:last-child {
  margin: 7px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.backup-hero-actions {
  display: flex;
  align-items: center;
  gap: 9px;
}

.backup-path {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  padding-top: 15px;
  border-top: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 12px;
}

.backup-path-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--warning);
}

.backup-path.path-ready .backup-path-dot {
  background: var(--success);
}

.backup-path.path-error .backup-path-dot {
  background: var(--danger);
}

.backup-path code,
.backup-form-path code {
  color: var(--text-primary);
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  overflow-wrap: anywhere;
}

.backup-path strong {
  margin-left: auto;
  color: var(--text-primary);
  font-size: 11px;
}

.backup-section-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 14px;
}

.backup-section-kicker {
  display: block;
  margin-bottom: 3px;
}

.backup-section-heading h3 {
  margin: 0;
  font-size: 18px;
  letter-spacing: -.02em;
}

.backup-tabs {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 15px;
  background: color-mix(in srgb, var(--bg-secondary) 72%, var(--surface-card));
  isolation: isolate;
}

.backup-tab-indicator {
  position: absolute;
  top: 4px;
  bottom: 4px;
  left: 4px;
  z-index: 0;
  width: calc((100% - 8px) / 3);
  border: 1px solid color-mix(in srgb, var(--primary-color) 20%, var(--border-color));
  border-radius: 11px;
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
  transition: transform .24s cubic-bezier(.2, .8, .2, 1);
}

.backup-tab-indicator--retention {
  transform: translateX(100%);
}

.backup-tab-indicator--executions {
  transform: translateX(200%);
}

.backup-tabs button {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.backup-tabs button[aria-selected="true"] {
  color: var(--primary-color);
  font-weight: 700;
}

.backup-tabs button span {
  min-width: 20px;
  padding: 2px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--primary-color) 10%, transparent);
  color: inherit;
  font-size: 10px;
  line-height: 1.2;
  text-align: center;
}

.backup-tabs button:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: -2px;
}

.backup-tab-panel {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.backup-tab-panel:focus-visible {
  border-radius: 12px;
  outline: 2px solid color-mix(in srgb, var(--primary-color) 55%, transparent);
  outline-offset: 3px;
}

.backup-list-frame {
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.backup-list-scroll {
  overflow-x: auto;
}

.backup-data-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.backup-task-table {
  min-width: 760px;
}

.backup-execution-table {
  min-width: 1020px;
}

.backup-data-table th,
.backup-data-table td {
  padding: 13px 15px;
  border-bottom: 1px solid var(--border-color);
  vertical-align: middle;
}

.backup-data-table thead th {
  background: color-mix(in srgb, var(--bg-secondary) 48%, transparent);
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.backup-data-table tbody tr:last-child th,
.backup-data-table tbody tr:last-child td {
  border-bottom: 0;
}

.backup-data-table tbody tr:hover {
  background: color-mix(in srgb, var(--primary-color) 4%, transparent);
}

.backup-data-table tbody th {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
}

.backup-data-table tbody td {
  color: var(--text-secondary);
  font-size: 11px;
}

.task-name-cell {
  min-width: 150px;
}

.task-content-cell {
  min-width: 170px;
}

.task-schedule-cell {
  min-width: 190px;
}

.task-schedule-cell strong,
.task-schedule-cell small,
.execution-details-cell strong,
.execution-details-cell small {
  display: block;
}

.task-schedule-cell strong,
.execution-details-cell strong {
  color: var(--text-primary);
  font-size: 11px;
}

.task-schedule-cell small,
.execution-details-cell small {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 10px;
  line-height: 1.5;
  white-space: pre-line;
}

.execution-time-cell {
  min-width: 145px;
  white-space: nowrap;
}

.execution-task-cell {
  min-width: 135px;
}

.execution-details-cell {
  min-width: 230px;
  max-width: 360px;
}

.execution-details-cell code,
.execution-path-cell code {
  display: block;
  margin-top: 5px;
  color: var(--text-secondary);
  font-family: var(--font-mono, monospace);
  font-size: 9px;
  overflow-wrap: anywhere;
  white-space: normal;
}

.execution-path-cell {
  min-width: 180px;
}

.backup-task-state {
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 10px;
  font-weight: 700;
}

.backup-task-state.is-on {
  color: var(--success);
  background: color-mix(in srgb, var(--success) 12%, transparent);
}

.backup-task-state.is-off {
  color: var(--text-secondary);
  background: color-mix(in srgb, var(--text-secondary) 10%, transparent);
}

.backup-task-actions {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
}

.backup-task-actions .btn {
  padding: 7px 10px;
  font-size: 11px;
}

.backup-retention-heading {
  margin-top: 3px;
}

.backup-retention {
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--border-color);
}

.backup-retention-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 22px;
}

.backup-retention-field {
  display: grid;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 11px;
}

.backup-retention-field > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.backup-retention-field input {
  width: 100px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
}

.backup-retention-field input:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--primary-color) 55%, transparent);
  outline-offset: 1px;
}

.backup-retention-note {
  margin: 0;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.6;
}

.backup-history-note {
  color: var(--text-secondary);
  font-size: 11px;
}

.history-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 700;
}

.history-status span {
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border-radius: 50%;
  font-size: 12px;
}

.status-running {
  color: var(--primary-color);
}

.status-queued {
  color: var(--text-secondary);
}

.status-queued span {
  background: color-mix(in srgb, var(--text-secondary) 12%, transparent);
}

.status-running span {
  animation: spin 1.5s linear infinite;
}

.status-running span,
.status-success span {
  background: color-mix(in srgb, var(--success) 13%, transparent);
}

.status-success {
  color: var(--success);
}

.status-failed {
  color: var(--danger);
}

.status-failed span {
  background: color-mix(in srgb, var(--danger) 13%, transparent);
}

.backup-empty {
  display: grid;
  min-height: 118px;
  place-content: center;
  justify-items: center;
  gap: 7px;
  padding: 24px;
  color: var(--text-secondary);
  text-align: center;
}

.backup-empty strong {
  color: var(--text-primary);
  font-size: 13px;
}

.backup-empty p {
  margin: 0 0 5px;
  font-size: 11px;
}

.backup-empty-mark {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px dashed var(--border-color);
  border-radius: 9px;
  color: var(--primary-color);
  font-size: 18px;
}

.backup-form {
  display: grid;
  gap: 19px;
}

.backup-field {
  display: grid;
  gap: 7px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
}

.backup-field small {
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 400;
  line-height: 1.6;
}

.backup-content-options {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.backup-content-options legend {
  padding: 0 5px;
  font-size: 12px;
  font-weight: 700;
}

.backup-check-option {
  display: flex;
  align-items: start;
  gap: 10px;
  padding: 9px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--bg-secondary) 42%, transparent);
  cursor: pointer;
}

.backup-check-option input,
.backup-inline-check input {
  margin-top: 2px;
  accent-color: var(--primary-color);
}

.backup-check-option strong,
.backup-check-option small {
  display: block;
}

.backup-check-option strong {
  font-size: 11px;
}

.backup-check-option small {
  margin-top: 3px;
  color: var(--text-secondary);
  font-size: 10px;
}

.backup-schedule-options {
  display: grid;
  gap: 12px;
}

.backup-inline-check {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 11px;
}

.backup-form-path {
  margin: 0;
  color: var(--text-secondary);
  font-size: 10px;
  overflow-wrap: anywhere;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 760px) {
  .backup-hero {
    grid-template-columns: 1fr;
  }

  .backup-hero-actions {
    justify-content: flex-start;
  }

  .backup-path strong {
    margin-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .backup-tab-indicator {
    transition: none;
  }

  .status-running span {
    animation: none;
  }
}
</style>
