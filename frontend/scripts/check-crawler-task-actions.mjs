import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const taskTable = source.match(/const TaskTable = defineComponent\(([\s\S]*?)\n\ntype TabKey=/)?.[1]

assert.ok(taskTable, 'CrawlerView must define the shared task table')
assert.match(taskTable, /type:'selection',width:48,reserveSelection:true,fixed:'left'/, 'the selection column must stay fixed beside the task name')
assert.match(taskTable, /label:'任务',minWidth:240,fixed:'left'/, 'the task-name column must be fixed on the left')
assert.match(taskTable, /label:'操作',width:250,fixed:'right'/, 'the action column must be fixed on the right')
assert.match(taskTable, /h\(ElButtonGroup,\{class:'task-action-button-group'\}/, 'task actions must use an Element Plus button group')
assert.match(taskTable, /'RUNNING','WAITING'[\s\S]*?'暂停'/, 'running and waiting tasks must expose pause in the primary action group')
assert.match(taskTable, /'RUNNING','WAITING','PAUSED'[\s\S]*?'取消'/, 'active tasks must expose cancel in the primary action group')
assert.match(taskTable, /h\(ElDropdown,[\s\S]*?'aria-label':'更多操作'/, 'secondary actions must use an Element Plus dropdown')
assert.match(taskTable, /command:'scan-results',label:'扫描结果'/)
assert.match(taskTable, /command:'resume',label:'继续'/)
assert.match(taskTable, /command:'edit',label:'修改'/)
assert.match(taskTable, /command:'delete',label:'删除'/)

const mainActionBlock = taskTable.match(/return h\(ElButtonGroup[\s\S]*?\n\s*\]\)/)?.[0] ?? ''
assert.doesNotMatch(mainActionBlock, /onClick:[^\n]*emit\('command',row,'resume'\)/, 'resume must not remain a primary action button')
assert.doesNotMatch(mainActionBlock, /onClick:[^\n]*emit\('delete',row\)/, 'delete must not remain a primary action button')

console.log('Crawler task actions use a compact Element Plus button group and overflow menu')
