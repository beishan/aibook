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
    <div v-else class="backup-task-grid">
      <article v-for="task in tasks" :key="task.id" class="backup-task-card card glass">
        <div class="backup-task-topline">
          <span class="backup-task-mark">{{ task.name.slice(0, 1) }}</span>
          <span class="backup-task-state" :class="task.enabled ? 'is-on' : 'is-off'">
            {{ task.enabled ? '已启用' : '已停用' }}
          </span>
        </div>
        <h4>{{ task.name }}</h4>
        <p class="backup-task-content">{{ contentLabel(task) }}</p>
        <div class="backup-task-schedule">
          <span class="schedule-glyph">◷</span>
          <div>
            <strong>{{ task.scheduleEnabled ? task.cronExpression : '仅手动执行' }}</strong>
            <small v-if="task.scheduleEnabled">下次执行 {{ formatTime(task.nextRunAt) }}</small>
            <small v-else>定时计划已关闭</small>
          </div>
        </div>
        <div class="backup-task-actions">
          <button class="btn btn-primary" :disabled="!pathInfo?.writable" @click="runTask(task)">立即备份</button>
          <button class="btn" @click="openDialog('task', task)">编辑</button>
          <button class="btn btn-danger" @click="removeTask(task)">删除</button>
        </div>
      </article>
    </div>

    <div class="backup-section-heading backup-history-heading">
      <div><span class="backup-section-kicker">RUN HISTORY</span><h3>执行结果</h3></div>
      <span class="backup-history-note">最近 100 次执行</span>
    </div>

    <div v-if="executions.length === 0" class="backup-empty card glass">
      <strong>暂无执行记录</strong>
      <p>手动执行或定时任务开始后，结果会显示在这里。</p>
    </div>
    <div v-else class="backup-history-list">
      <article v-for="execution in executions" :key="execution.id" class="backup-history-item card glass">
        <div class="history-status" :class="`status-${execution.status.toLowerCase()}`">
          <span>{{ statusGlyph(execution.status) }}</span>
          {{ statusLabel(execution.status) }}
        </div>
        <div class="history-main">
          <div class="history-title-row">
            <strong>{{ execution.taskName }}</strong>
            <span>{{ execution.contents }}</span>
          </div>
          <p>{{ execution.details || execution.errorMessage || '任务正在等待执行' }}</p>
          <code v-if="execution.outputPath">{{ execution.outputPath }}</code>
        </div>
        <div class="history-metrics">
          <strong>{{ execution.fileCount ?? '—' }}<small>文件</small></strong>
          <strong>{{ formatBytes(execution.fileSizeBytes) }}</strong>
          <time>{{ formatTime(execution.finishedAt || execution.startedAt) }}</time>
        </div>
      </article>
    </div>

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
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from '@/utils/message'
import { backupApi, type BackupExecution, type BackupPath, type BackupTask, type BackupTaskInput } from '@/utils/backups'

type ContentKey = 'databaseEnabled' | 'booksEnabled' | 'uploadsEnabled' | 'crawlerDataEnabled'
type DialogMode = 'task' | 'run'

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
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('task')
const editingId = ref<number | null>(null)

const emptyDraft = (): BackupTaskInput => ({
  name: '', databaseEnabled: true, booksEnabled: false, uploadsEnabled: false,
  crawlerDataEnabled: false, scheduleEnabled: true, enabled: true,
  cronExpression: '0 0 2 * * ?',
})
const draft = reactive<BackupTaskInput>(emptyDraft())

const dialogTitle = computed(() => dialogMode.value === 'run' ? '立即备份' : editingId.value ? '编辑备份任务' : '新建备份任务')
const pathStatusText = computed(() => !pathInfo.value ? '正在检查' : pathInfo.value.writable ? '可写入' : pathInfo.value.exists ? '无写入权限' : '目录未挂载')
const pathStatusClass = computed(() => pathInfo.value?.writable ? 'path-ready' : 'path-error')

const refreshAll = async (quiet = false) => {
  loading.value = true
  try {
    const [path, taskRows, executionRows] = await Promise.all([
      backupApi.path(), backupApi.tasks(), backupApi.executions(),
    ])
    pathInfo.value = path
    tasks.value = taskRows
    executions.value = executionRows
  } catch (error: any) {
    if (!quiet) message.error(error.response?.data?.message || '备份信息加载失败')
  } finally {
    loading.value = false
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
.history-main code,
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

.backup-task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}

.backup-task-card {
  padding: 17px;
  border: 1px solid var(--border-color);
  transition: border-color .18s ease, transform .18s ease;
}

.backup-task-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--primary-color) 38%, var(--border-color));
}

.backup-task-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.backup-task-mark {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 11px;
  background: color-mix(in srgb, var(--primary-color) 12%, var(--surface-card));
  color: var(--primary-color);
  font-size: 16px;
  font-weight: 800;
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

.backup-task-card h4 {
  margin: 14px 0 5px;
  font-size: 15px;
}

.backup-task-content {
  min-height: 34px;
  margin: 0;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.55;
}

.backup-task-schedule {
  display: flex;
  gap: 10px;
  align-items: start;
  margin: 15px 0;
  padding: 11px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--bg-secondary) 55%, transparent);
}

.schedule-glyph {
  color: var(--primary-color);
  font-size: 18px;
  line-height: 1;
}

.backup-task-schedule strong,
.backup-task-schedule small {
  display: block;
}

.backup-task-schedule strong {
  font-size: 11px;
}

.backup-task-schedule small {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 10px;
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

.backup-history-heading {
  margin-top: 3px;
}

.backup-history-note {
  color: var(--text-secondary);
  font-size: 11px;
}

.backup-history-list {
  display: grid;
  gap: 9px;
}

.backup-history-item {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr) auto;
  align-items: center;
  gap: 15px;
  padding: 14px 16px;
  border: 1px solid var(--border-color);
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

.history-main {
  min-width: 0;
}

.history-title-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 9px;
}

.history-title-row strong {
  font-size: 12px;
}

.history-title-row span {
  color: var(--text-secondary);
  font-size: 10px;
}

.history-main p {
  margin: 5px 0;
  color: var(--text-secondary);
  font-size: 10px;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.history-main code {
  display: block;
}

.history-metrics {
  display: flex;
  align-items: end;
  gap: 14px;
  color: var(--text-secondary);
  text-align: right;
}

.history-metrics strong {
  font-size: 11px;
  white-space: nowrap;
}

.history-metrics small {
  margin-left: 3px;
  font-size: 9px;
  font-weight: 400;
}

.history-metrics time {
  min-width: 125px;
  font-size: 10px;
  white-space: nowrap;
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

  .backup-history-item {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .history-metrics {
    justify-content: space-between;
    text-align: left;
  }

  .history-metrics time {
    min-width: 0;
  }

  .backup-path strong {
    margin-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .backup-task-card {
    transition: none;
  }

  .backup-task-card:hover {
    transform: none;
  }

  .status-running span {
    animation: none;
  }
}
</style>
