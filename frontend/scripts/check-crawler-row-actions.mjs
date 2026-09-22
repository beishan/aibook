import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const discoveryTable = source.match(/<el-table v-if="discoveryViewMode==='table'"([\s\S]*?)<\/el-table>/)?.[1] ?? ''
const crawlerBookTable = source.match(/<el-table v-if="bookViewMode==='table'"([\s\S]*?)<\/el-table>/)?.[1] ?? ''
const taskTable = source.match(/const TaskTable = defineComponent\(([\s\S]*?)\r?\n\r?\ntype TabKey=/)?.[1] ?? ''

assert.ok(discoveryTable, 'discovery-book table must exist')
assert.match(discoveryTable, /type="selection"[\s\S]*?label="收藏" width="58" fixed="left"[\s\S]*?label="书籍"/, 'discovery favorite must be directly after selection')
assert.match(discoveryTable, /class="favorite-action row-hover-action"/, 'discovery favorite must reveal with its row')
assert.match(discoveryTable, /class="crawler-book-action-cluster discovery-book-action-cluster row-hover-action"/, 'discovery actions must reveal with their row')

assert.ok(crawlerBookTable, 'crawler-book table must exist')
assert.match(crawlerBookTable, /type="selection"[\s\S]*?label="收藏" width="58" fixed="left"[\s\S]*?label="书籍"/, 'crawler-book favorite must be directly after selection')
assert.match(crawlerBookTable, /class="favorite-action row-hover-action"/, 'crawler-book favorite must reveal with its row')
assert.match(crawlerBookTable, /class="crawler-book-action-cluster row-hover-action"/, 'crawler-book actions must reveal with their row')

assert.ok(taskTable, 'shared task table must exist')
assert.match(taskTable, /type:'selection'[\s\S]*?label:'收藏',width:58,fixed:'left'[\s\S]*?label:'任务'/, 'task favorite must be placed before the task column and after selection when present')
assert.match(taskTable, /class:\['favorite-action','row-hover-action'/, 'task favorite must reveal with its row')
assert.match(taskTable, /class:'task-action-cluster row-hover-action'/, 'task actions must reveal with their row')
assert.match(source, /class="queued-task-actions row-hover-action"/, 'queue actions must reveal with their row')

assert.match(source, /:deep\(\.el-table__row \.row-hover-action\)\{opacity:0;pointer-events:none/, 'row actions must be hidden and non-interactive by default')
assert.match(source, /:deep\(\.el-table__row:hover \.row-hover-action\)[\s\S]*?:deep\(\.el-table__row:focus-within \.row-hover-action\)/, 'row actions must be revealed by pointer hover and keyboard focus')
assert.match(source, /@media\(hover:none\)[\s\S]*?:deep\(\.el-table__row \.row-hover-action\)\{opacity:1;pointer-events:auto/, 'touch devices must retain accessible row actions')

console.log('Crawler list favorites and operation clusters are positioned and revealed per row')
