import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [crawlerViewSource, crawlerApiSource, controllerSource, serviceSource, repositorySource] =
  await Promise.all([
    readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/utils/crawler.ts', import.meta.url), 'utf8'),
    readFile(new URL('../../backend/src/main/java/com/aibook/controller/CrawlerController.java', import.meta.url), 'utf8'),
    readFile(new URL('../../backend/src/main/java/com/aibook/service/crawler/CrawlerManagementService.java', import.meta.url), 'utf8'),
    readFile(new URL('../../backend/src/main/java/com/aibook/repository/CrawlerTaskRepository.java', import.meta.url), 'utf8'),
  ])
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
assert.match(
  crawlerViewSource,
  /v-model="taskTypeFilter" clearable placeholder="全部任务类型"[^>]*@change="handleTaskTypeFilter"/,
  'the crawler task toolbar must provide a task-type filter',
)
assert.match(
  crawlerViewSource,
  /crawlerApi\.tasks\(\{[^\n]*type:taskTypeFilter\.value\|\|undefined/,
  'the selected type must be sent with every paginated task query',
)
assert.match(crawlerApiSource, /CrawlerTaskQuery \{[^}]*type\?:string/)
assert.match(controllerSource, /@RequestParam\(required = false\) String type/)
assert.match(serviceSource, /CrawlerTask\.TaskType taskType = parseTaskType\(type\)/)
assert.match(repositorySource, /findByUserAndTypeAndStatusInOrderByCreatedAtDesc/)

console.log('Crawler task lists display and server-filter by task type')
