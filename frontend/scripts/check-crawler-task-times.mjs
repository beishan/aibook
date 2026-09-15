import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const crawlerViewSource = await readFile(
  new URL('../src/views/CrawlerView.vue', import.meta.url),
  'utf8',
)
const taskTableSource = crawlerViewSource.match(
  /const TaskTable = defineComponent\(([\s\S]*?)\n\ntype TabKey=/,
)?.[1]

assert.ok(taskTableSource, 'the shared crawler task table must exist')
assert.match(
  taskTableSource,
  /label:'创建时间',[^\n]*formatTime\(row\.createdAt\)/,
  'the crawler task list must display its creation time',
)
assert.match(
  taskTableSource,
  /label:'完成时间',[^\n]*formatTime\(row\.finishedAt\)/,
  'the crawler task list must display its completion time',
)
assert.match(
  crawlerViewSource,
  /function formatTime\(value\?:string\)\{return value\?[^\n]*:'—'}/,
  'unfinished crawler tasks must retain a visible time placeholder',
)

console.log('Crawler task lists display creation and completion times')
