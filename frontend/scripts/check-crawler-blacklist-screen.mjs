import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/CrawlerBlacklistScreenDialog.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('screen.ts', script, ts.ScriptTarget.Latest, true)
const functions = ast.statements.filter(ts.isFunctionDeclaration).map(node => node.getText(ast)).join('\n')
const ref = value => ({ value })
const emitted = []
const requests = []
const completed = { id: 'job-a', status: 'COMPLETED', siteId: null, total: 45, processed: 45, matched: 23 }
const data = [
  { bookId: 1, bookName: '甲', blacklisted: false, available: true },
  { bookId: 2, bookName: '乙', blacklisted: true, available: true },
  { bookId: 3, bookName: '丙', blacklisted: false, available: false },
  { bookId: 4, bookName: '丁', blacklisted: false, available: true },
]
let pageData = async () => ({ content: structuredClone(data), totalElements: 23 })
let fetchLatest = async () => structuredClone(completed)
let fetchProgress = async () => structuredClone(completed)
let failApply = false
let scheduled = []
const context = vm.createContext({
  props: { modelValue: true, sites: [] },
  siteId: ref(0), job: ref(undefined), rows: ref([]), selectedIds: ref([]),
  page: ref(1), pageSize: ref(20), total: ref(0), restoring: ref(false), starting: ref(false),
  loadingResults: ref(false), applying: ref(false), error: ref(''),
  generation: 0, pageRequest: 0, timer: undefined,
  setTimeout: callback => { scheduled.push(callback); return scheduled.length },
  clearTimeout() {},
  crawlerApi: {
    startBlacklistScreen: async siteId => {
      requests.push({ type: 'start', siteId })
      return { ...completed, status: 'RUNNING', processed: 0 }
    },
    latestBlacklistScreen: () => fetchLatest(),
    blacklistScreenProgress: id => { requests.push({ type: 'progress', id }); return fetchProgress() },
    blacklistScreenResults: (id, page, size) => {
      requests.push({ type: 'page', id, page, size }); return pageData()
    },
    applyBlacklistScreen: async (id, all, ids) => {
      if (failApply) throw new Error('网络错误')
      requests.push({ type: 'apply', id, all, ids: [...ids] })
      return { affectedCount: all ? 23 : ids.length }
    },
  },
  emit: (...args) => emitted.push(args),
  ElMessage: { success() {}, error() {} },
})
context.running = { get value() { return context.job.value?.status === 'RUNNING' } }
context.busy = { get value() { return context.starting.value || context.running.value || !!context.applying.value } }
context.canApply = { get value() {
  return context.job.value?.status === 'COMPLETED' && !context.busy.value
    && !context.restoring.value && !context.loadingResults.value && !context.error.value
} }
context.selectableRows = { get value() { return context.rows.value.filter(row => row.available && !row.blacklisted) } }
const selected = []
context.table = ref({
  clearSelection() { selected.length = 0; context.selectedIds.value = [] },
  toggleRowSelection(row, value) {
    if (value) selected.push(row)
    run('selectionChanged(selectedRows)', { selectedRows: selected })
  },
})
vm.runInContext(ts.transpile(functions, { target: ts.ScriptTarget.ES2022 }), context)
function run(code, vars = {}) { Object.assign(context, vars); return vm.runInContext(code, context) }

await run('start()')
assert.equal(requests[0].siteId, undefined, 'default scope is all sites')
assert.equal(context.job.value.status, 'RUNNING')
assert.equal(context.canApply.value, false, 'cannot apply partial running results')
assert.equal(scheduled.length, 1, 'running jobs schedule progress polling')
await run('poll(generation)')
assert.equal(context.job.value.status, 'COMPLETED')
assert.equal(context.rows.value.length, 4)
assert.equal(context.total.value, 23, 'results are paginated on server')
run('selectPage()')
assert.deepEqual([...context.selectedIds.value], [1, 4], 'select page excludes blacklisted and missing books')
await run('apply(false)')
assert.deepEqual(requests.find(req => req.type === 'apply'), { type: 'apply', id: 'job-a', all: false, ids: [1, 4] })
assert.ok(emitted.some(([name]) => name === 'changed'))
assert.equal(context.selectedIds.value.length, 0)

run('selectPage()')
context.page.value = 2
await run('loadPage()')
assert.equal(context.selectedIds.value.length, 0, 'page switch clears selection')
assert.deepEqual(requests.filter(req => req.type === 'page').at(-1), { type: 'page', id: 'job-a', page: 1, size: 20 })
await run('apply(true)')
assert.deepEqual(requests.filter(req => req.type === 'apply').at(-1), { type: 'apply', id: 'job-a', all: true, ids: [] },
  'all apply references job snapshot rather than visible page')

failApply = true
const changeCount = emitted.length
run('selectPage()')
await run('apply(false)')
assert.equal(emitted.length, changeCount, 'failure does not announce successful blacklist changes')
assert.equal(context.applying.value, false)
assert.deepEqual([...context.selectedIds.value], [1, 4], 'failure retains selection for retry')
failApply = false

let release
pageData = () => new Promise(resolve => { release = resolve })
const oldPage = run('loadPage()')
run('stopPolling()')
pageData = async () => ({ content: [{ ...data[0], bookId: 9 }], totalElements: 1 })
await run('restore()')
release({ content: data, totalElements: 23 })
await oldPage
assert.equal(context.rows.value[0].bookId, 9, 'late closed-dialog response cannot replace restored results')
assert.equal(context.loadingResults.value, false)

fetchLatest = async () => ({ ...completed, status: 'RUNNING' })
await run('restore()')
assert.equal(context.running.value, true, 'reopen resumes running server job')
fetchProgress = async () => { throw new Error('离线') }
await run('poll(generation)')
assert.ok(context.error.value.includes('查询中断'))
assert.equal(context.canApply.value, false)
context.starting.value = true
run('stopPolling()')
assert.equal(context.starting.value, false, 'closing a pending start cannot leave dialog stuck')
console.log('黑名单筛选：默认范围、进度恢复、分页、当页选择、选中/跨页全部加入、失败重试与过期响应隔离通过')
