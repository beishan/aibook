import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
const source = fs.readFileSync(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const functions = source.slice(source.indexOf('async function openQueueExecutors()'), source.indexOf('\nfunction openCreateExecutor()'))
const notices = []
const queueSettingsTarget = { value: { id: 9 } }
const selectedSharedExecutorId = { value: undefined }
const sharedExecutorOptions = { value: [] }
const queueExecutors = { value: [] }
const queueExecutorsDialog = { value: false }
const queueExecutorsLoading = { value: false }
const sharingExecutor = { value: false }
const taskQueues = { value: [{ id: 9 }] }
const calls = []
let fail = false
const crawlerApi = {
  queueExecutors: async id => [{ id: 3, queueId: id, shared: true }],
  sharedExecutorOptions: async () => [{ id: 7, name: '另一网站执行器', sourceName: '网站一' }],
  shareQueueExecutor: async (queue, executor) => {
    calls.push([queue, executor])
    if (fail) throw new Error('failed')
  },
}
const args = { crawlerApi, queueSettingsTarget, selectedSharedExecutorId, sharedExecutorOptions,
  queueExecutors, queueExecutorsDialog, queueExecutorsLoading, sharingExecutor, taskQueues,
  loadTaskQueues: async () => {}, message: { success: value => notices.push(value), error: value => notices.push(value) } }
const { openQueueExecutors, shareExistingExecutor } = new Function(...Object.keys(args),
  ts.transpile('let queueExecutorRequest = 0;\n' + functions + '\nreturn { openQueueExecutors, shareExistingExecutor };', { target: ts.ScriptTarget.ES2022 }))(...Object.values(args))
await openQueueExecutors()
assert.equal(sharedExecutorOptions.value[0].id, 7)
assert.equal(queueExecutors.value[0].queueId, 9)
selectedSharedExecutorId.value = 7
sharedExecutorOptions.value[0].busy = true
await shareExistingExecutor()
assert.deepEqual(calls, [], 'busy executor cannot be newly shared')
sharedExecutorOptions.value[0].busy = false
await shareExistingExecutor()
assert.deepEqual(calls, [[9, 7]])
assert.equal(sharingExecutor.value, false)
assert.ok(notices[0].includes('共享'))
fail = true
selectedSharedExecutorId.value = 7
await shareExistingExecutor()
assert.equal(selectedSharedExecutorId.value, 7, 'failed sharing retains selection')
assert.equal(sharingExecutor.value, false)
assert.ok(notices.at(-1).includes('失败'))
let resolveOld
crawlerApi.queueExecutors = () => new Promise(resolve => { resolveOld = resolve })
const old = openQueueExecutors()
queueSettingsTarget.value = { id: 12 }
resolveOld([{ id: 99, queueId: 9 }])
await old
assert.equal(queueExecutors.value[0].id, 3, 'old queue response must not replace current data')
const capacityStart = source.indexOf('const totalQueueConcurrency =')
const capacitySource = source.slice(capacityStart, source.indexOf('\nconst ', capacityStart + 1))
taskQueues.value = [
  { id: 9, maxConcurrentTasks: 1, enabledExecutorIds: [3] },
  { id: 12, maxConcurrentTasks: 1, enabledExecutorIds: [3] },
]
const capacity = new Function('computed', 'taskQueues', capacitySource + '\nreturn totalQueueConcurrency;')(
  fn => ({ get value() { return fn() } }), taskQueues,
)
assert.equal(capacity.value, 1, 'shared executor capacity is counted once across queues')
taskQueues.value = [{ id: 9, maxConcurrentTasks: 2 }]
assert.equal(capacity.value, 2, 'older server responses retain capacity fallback')
console.log('Executor sharing dialog: cross-queue options, target identity, success refresh, failure selection and stale response passed')
