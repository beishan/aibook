import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const crawlerViewSource = await readFile(
  new URL('../src/views/CrawlerView.vue', import.meta.url),
  'utf8',
)
const taskTableSource = crawlerViewSource.match(
  /const TaskTable = defineComponent\(([\s\S]*?)\n\ntype TabKey=/,
)?.[1]
const queuedTasksSource = crawlerViewSource.match(
  /<el-dialog v-model="queuedTasksDialog"([\s\S]*?)<\/el-dialog>/,
)?.[1]

assert.ok(taskTableSource, 'the shared crawler task table must exist')
assert.match(
  taskTableSource,
  /label:'任务类型',width:120[^\n]*taskTypeLabel\(row\.type\)/,
  'the crawler task list must display a dedicated task-type column',
)
assert.ok(queuedTasksSource, 'the current crawler task queue must exist')
assert.match(
  queuedTasksSource,
  /<el-table-column label="任务类型" width="120">[\s\S]*?taskTypeLabel\(row\.type\)/,
  'the current task queue must display the same task-type column',
)

console.log('Crawler task lists display a dedicated task type')
