import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'
const [view, panel] = await Promise.all([
  readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/CrawlerExecutorsSettingsPanel.vue', import.meta.url), 'utf8'),
])
const start = view.indexOf('async function assignQueueExecutor(')
const end = view.indexOf('async function removeQueueExecutor(', start)
const calls = []
const context = vm.createContext({
  crawlerApi:{assignQueueExecutor:async (...args) => calls.push(args)},
  message:{success:() => {}, error:() => assert.fail('manual assignment failed')},
  openQueueExecutors:async () => calls.push('reload-executors'),
  loadTaskQueues:async () => calls.push('reload-queues'),
  queueSettingsTarget:{value:null}, taskQueues:{value:[{id:2,maxConcurrentTasks:2}]},
})
vm.runInContext(ts.transpile(view.slice(start,end), {target:ts.ScriptTarget.ES2022}),context)
await vm.runInContext('assignQueueExecutor({queueId:2,id:3})',context)
assert.equal(calls[0][0],2)
assert.equal(calls[0][1],3)
assert.ok(calls.includes('reload-executors'))
assert.ok(calls.includes('reload-queues'))
assert.equal(context.queueSettingsTarget.value.maxConcurrentTasks,2)
assert.ok(view.includes('!item.assigned && !item.deletionPending'))
assert.ok(view.includes(':disabled="item.deletionPending"'))
assert.ok(view.includes('待删除 · 当前任务完成后清理'))
assert.ok(panel.includes(':disabled="row.deletionPending"'))
assert.ok(panel.includes('v-if="!row.deletionPending"'))
assert.ok(panel.includes('新执行器需在任务队列中手动添加'))
console.log('执行器手动加入队列、状态刷新及待删除交互验证通过')
