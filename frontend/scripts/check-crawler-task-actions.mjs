import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const taskTable = source.match(/const TaskTable = defineComponent\(([\s\S]*?)\r?\n\r?\ntype TabKey=/)?.[1]

assert.ok(taskTable, 'CrawlerView must define the shared task table')
assert.match(taskTable, /type:'selection',width:48,reserveSelection:true,fixed:'left'/, 'the selection column must stay fixed beside the task name')
assert.match(taskTable, /label:'任务',minWidth:240,fixed:'left'/, 'the task-name column must be fixed on the left')
assert.match(taskTable, /label:'操作',width:250,fixed:'right'/, 'the action column must be fixed on the right')
assert.match(taskTable, /h\('div',\{class:'task-action-cluster'\}/, 'task actions must use a visually unified action cluster')
assert.match(taskTable, /task-action-details/, 'the details action must have a distinct primary treatment')
assert.match(taskTable, /task-action-pause/, 'the pause action must have a neutral treatment')
assert.match(taskTable, /task-action-cancel/, 'the cancel action must have a danger treatment')
assert.match(taskTable, /circle:true[\s\S]*?task-action-more/, 'the overflow action must use a compact circular button')
assert.match(taskTable, /'RUNNING','WAITING'[\s\S]*?'暂停'/, 'running and waiting tasks must expose pause in the primary action group')
assert.match(taskTable, /'RUNNING','WAITING','PAUSED'[\s\S]*?'取消'/, 'active tasks must expose cancel in the primary action group')
assert.match(taskTable, /h\(ElDropdown,[\s\S]*?'aria-label':'更多操作'/, 'secondary actions must use an Element Plus dropdown')
assert.match(taskTable, /command:'scan-results',label:'扫描结果'/)
assert.match(taskTable, /command:'resume',label:'继续'/)
assert.match(taskTable, /command:'edit',label:'修改'/)
assert.match(taskTable, /command:'delete',label:'删除'/)
assert.match(taskTable, /row\.status==='SUCCESS'&&row\.type==='SITE_SCAN'/, 'completed discovery scans must use the detailed scan result')
assert.match(taskTable, /h\('i',`新增 \$\{row\.newBookCount\}`\)/, 'completed discovery scans must show the new-book count')
assert.match(taskTable, /h\('em',`重复 \$\{row\.duplicateCount\}`\)/, 'completed discovery scans must show the duplicate-book count')

const mainActionBlock = taskTable.match(/return h\('div',\{class:'task-action-cluster'\}[\s\S]*?\n\s*\]\)/)?.[0] ?? ''
assert.doesNotMatch(mainActionBlock, /onClick:[^\n]*emit\('command',row,'resume'\)/, 'resume must not remain a primary action button')
assert.doesNotMatch(mainActionBlock, /onClick:[^\n]*emit\('delete',row\)/, 'delete must not remain a primary action button')

const queueDialog = source.match(/<el-dialog v-model="queuedTasksDialog"([\s\S]*?)<\/el-dialog>/)?.[1]
assert.ok(queueDialog, 'CrawlerView must define the current-task queue dialog')
assert.match(queueDialog, /<el-button-group class="queued-task-button-group">/, 'queue row actions must use one Element Plus button group')
assert.match(queueDialog, />暂停<\/el-button>/)
assert.match(queueDialog, />优先<\/el-button>/)
assert.match(queueDialog, />详情<\/el-button>/)

console.log('Crawler task actions use a polished action cluster with distinct states and overflow menu')
