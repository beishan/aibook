<template>
  <section class="card glass crawler-logs">
    <h2>爬虫任务日志管理</h2>
    <p>管理当前账户的爬虫任务和执行器详情日志；书籍详情与执行器详情共用这些记录。保留的日志不会被策略自动清理，切换页码会清空勾选。</p>
    <div class="filters">
      <el-select v-model="type" clearable placeholder="全部任务类型" aria-label="爬虫日志任务类型">
        <el-option v-for="item in types" :key="item.value" :value="item.value" :label="item.label" />
      </el-select>
      <el-input v-model="keyword" clearable maxlength="200" placeholder="搜索书籍或日志内容" @keyup.enter="search" />
      <el-button :loading="loading" @click="search">查询</el-button>
    </div>
    <div class="actions">
      <span>已选 {{ selected.length }} 条</span>
      <el-button :disabled="!selected.length || busy" @click="retain(true)">保留所选</el-button>
      <el-button :disabled="!selected.length || busy" @click="retain(false)">取消保留</el-button>
      <el-button type="danger" :disabled="!selected.length || busy" @click="remove">删除所选</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" row-key="id" max-height="55vh" @selection-change="selected = $event">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="createdAt" label="时间" min-width="170" />
      <el-table-column label="任务类型" min-width="125">
        <template #default="{ row }">{{ typeLabel(row.taskType) }}</template>
      </el-table-column>
      <el-table-column prop="executorName" label="执行器" min-width="100" />
      <el-table-column prop="bookName" label="书籍" min-width="120" show-overflow-tooltip />
      <el-table-column prop="description" label="事件" min-width="180" show-overflow-tooltip />
      <el-table-column label="保留" width="85">
        <template #default="{ row }">{{ row.retained ? '已保留' : '普通' }}</template>
      </el-table-column>
      <el-table-column type="expand">
        <template #default="{ row }"><pre class="details">{{ row.details || '暂无详情' }}</pre></template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page" :page-size="20" :total="total"
      layout="total, prev, pager, next" :disabled="busy" @current-change="load"
    />
    <h3>日志保留策略</h3>
    <p>按任务类型设置保留天数，0 表示永久保留；保存策略不会立即删除日志。开启自动清理后每天北京时间 02:00 永久删除过期的未保留日志；无法识别类型的旧日志不会自动清理。删除只影响爬虫日志，不影响系统操作日志或采集内容。</p>
    <el-table :data="rules" v-loading="rulesLoading">
      <el-table-column label="任务类型" min-width="140">
        <template #default="{ row }">{{ typeLabel(row.taskType) }}</template>
      </el-table-column>
      <el-table-column label="保留天数（0–3650）" min-width="180">
        <template #default="{ row }">
          <el-input-number v-model="row.retentionDays" :min="0" :max="3650" :step="1" :precision="0" :disabled="busy" />
        </template>
      </el-table-column>
      <el-table-column label="自动清理" width="120">
        <template #default="{ row }"><el-switch v-model="row.automatic" :disabled="busy" /></template>
      </el-table-column>
    </el-table>
    <div class="actions">
      <el-button type="primary" :disabled="rulesLoading || busy || !rules.length" @click="saveRules">保存策略</el-button>
      <el-button type="danger" plain :disabled="rulesLoading || busy || !rules.length" @click="cleanup">按已保存策略立即清理</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/utils/api'
import { confirm, message } from '@/utils/message'

interface LogRow {
  id: number
  taskType: string
  retained: boolean
}
interface Rule {
  taskType: string
  retentionDays: number
  automatic: boolean
}
const types = [
  { value: 'SITE_SCAN', label: '网站扫描' },
  { value: 'BOOK_METADATA', label: '书籍元数据' },
  { value: 'BOOK_CHAPTER_LIST', label: '章节目录' },
  { value: 'BOOK_CONTENT', label: '章节正文' },
  { value: 'BOOK_UPDATE_CHECK', label: '检查更新' },
  { value: 'BOOK_FULL_CRAWL', label: '完整采集（BOOK_FULL_CRAWL）' },
  { value: 'BOOK_EXPORT', label: '书籍导出' },
  { value: 'BOOK_IMPORT', label: '书籍入库' },
]
const base = '/api/crawler/task-logs'
const type = ref('')
const keyword = ref('')
const rows = ref<LogRow[]>([])
const selected = ref<LogRow[]>([])
const rules = ref<Rule[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const rulesLoading = ref(false)
const busy = ref(false)
let requestId = 0
function typeLabel(value: string) {
  return value === 'UNKNOWN' ? '未知类型' : types.find(item => item.value === value)?.label || value || '未知类型'
}
async function load() {
  const request = ++requestId
  loading.value = true
  selected.value = []
  try {
    const result = await api.get(base, {
      params: { page: page.value - 1, size: 20, keyword: keyword.value, type: type.value },
    })
    if (request !== requestId) return
    rows.value = result.data.content
    total.value = result.data.totalElements
    const last = Math.max(1, Math.ceil(total.value / 20))
    if (page.value > last) {
      page.value = last
      await load()
    }
  } catch (error: any) {
    if (request === requestId) {
      rows.value = []
      total.value = 0
      message.error(error.response?.data?.message || '爬虫日志加载失败')
    }
  } finally {
    if (request === requestId) loading.value = false
  }
}
function search() {
  page.value = 1
  void load()
}
async function loadRules() {
  rulesLoading.value = true
  try { rules.value = (await api.get(`${base}/retention`)).data }
  catch (error: any) { message.error(error.response?.data?.message || '保留策略加载失败') }
  finally { rulesLoading.value = false }
}
async function retain(value: boolean) {
  if (!selected.value.length || busy.value) return
  busy.value = true
  try {
    const result = await api.post(`${base}/retain`, { ids: selected.value.map(row => row.id), retained: value })
    message.success(`已处理 ${result.data.affectedCount} 条日志`)
    await load()
  } catch (error: any) { message.error(error.response?.data?.message || '操作失败') }
  finally { busy.value = false }
}
async function remove() {
  if (!selected.value.length || busy.value) return
  const ids = selected.value.map(row => row.id)
  busy.value = true
  try {
    if (!await confirm(`确定永久删除选中的 ${ids.length} 条爬虫日志吗？已保留的记录也会删除，无法恢复。`, '删除爬虫任务日志')) return
    const result = await api.post(`${base}/delete`, { ids })
    message.success(`已删除 ${result.data.affectedCount} 条日志`)
    await load()
  } catch (error: any) { message.error(error.response?.data?.message || '删除失败') }
  finally { busy.value = false }
}
async function saveRules() {
  if (busy.value) return
  busy.value = true
  try {
    rules.value = (await api.put(`${base}/retention`, rules.value)).data
    message.success('爬虫日志保留策略已保存')
  } catch (error: any) { message.error(error.response?.data?.message || '保存失败') }
  finally { busy.value = false }
}
async function cleanup() {
  if (busy.value) return
  busy.value = true
  try {
    if (!await confirm('将按已保存的策略永久删除过期的未保留日志，无法恢复。是否继续？', '立即清理爬虫日志')) return
    const result = await api.post(`${base}/cleanup`)
    message.success(`已清理 ${result.data.deletedCount} 条日志`)
    await load()
  } catch (error: any) { message.error(error.response?.data?.message || '清理失败') }
  finally { busy.value = false }
}
onMounted(() => { void load(); void loadRules() })
</script>

<style scoped>
.crawler-logs {
  padding: 24px;
  margin-top: 20px;
}
p {
  color: var(--text-secondary);
  line-height: 1.7;
}
.filters, .actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 16px 0;
}
.filters .el-input, .filters .el-select {
  width: min(260px, 100%);
}
.actions .el-button + .el-button {
  margin-left: 0;
}
.details {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  padding: 12px;
}
</style>
