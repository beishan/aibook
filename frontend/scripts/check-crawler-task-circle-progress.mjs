import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const crawlerViewSource = await readFile(
  new URL('../src/views/CrawlerView.vue', import.meta.url),
  'utf8',
)
const taskHeroSource = crawlerViewSource.match(
  /<header class="task-detail-hero">([\s\S]*?)<\/header>/,
)?.[1]

assert.ok(taskHeroSource, 'the crawler task detail header must exist')
assert.match(taskHeroSource, /<el-progress[\s\S]*?type="circle"/)
assert.match(taskHeroSource, /:percentage="taskProgress\(selectedTask\)"/)
assert.match(taskHeroSource, /:status="taskCircleStatus\(selectedTask\.status\)"/)
assert.doesNotMatch(
  taskHeroSource,
  /<div class="task-detail-mark">/,
  'the custom static progress badge must not return',
)
assert.match(
  crawlerViewSource,
  /function taskCircleStatus\(status:string\):'success'\|'exception'\|'warning'\|undefined/,
  'the circular progress must use Element Plus status styles',
)

console.log('Crawler task detail uses the Element Plus circular progress component')
