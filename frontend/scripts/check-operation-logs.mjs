import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'

const require = createRequire(import.meta.url)
const { createRenderer, nextTick } = require('vue')
function evaluate(source, overrides) {
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const module = { exports: {} }
  new Function('require', 'module', 'exports', code)(
    name => Object.hasOwn(overrides, name) ? overrides[name] : require(name), module, module.exports,
  )
  return module.exports
}
const clone = value => JSON.parse(JSON.stringify(value))
const requests = []
let rules = [
  { action: 'OPEN_BOOK', label: '打开书籍', retentionDays: null },
  { action: 'CRAWLER_TASK', label: '爬虫任务', retentionDays: 0 },
]
let schedule = { enabled: true, frequency: 'HOURLY', hour: 3, minute: 35, dayOfWeek: 1,
  nextRunAt: '2026-10-22T03:35:00', lastRunAt: null }
let rows = [
  { id: 1, action: 'OPEN_BOOK', description: '打开数学', details: '详情', createdAt: '2026-10-21T12:00:00', retained: false },
  { id: 2, action: 'CRAWLER_TASK', description: '采集完成', createdAt: '2026-10-01T12:00:00', retained: true },
]
let rejectPolicyLoad = false
const fakeApi = {
  async get(url, options) {
    requests.push({ method: 'GET', url, params: options?.params })
    if (url.endsWith('/retention')) {
      if (rejectPolicyLoad) throw new Error('load failure')
      return { data: { rules: clone(rules), schedule: clone(schedule) } }
    }
    const params = options.params
    const filtered = rows.filter(row => (!params.action || row.action === params.action)
      && (!params.keyword || row.description.includes(params.keyword)))
    return { data: { content: clone(filtered.slice(params.page * params.size, (params.page + 1) * params.size)),
      number: params.page, totalElements: filtered.length } }
  },
  async put(url, data) {
    requests.push({ method: 'PUT', url, data: clone(data) })
    rules = clone(data.rules)
    schedule = clone(data.schedule)
    return { data: { rules: clone(rules), schedule: clone(schedule) } }
  },
  async post(url) {
    requests.push({ method: 'POST', url })
    if (url.endsWith('/cleanup')) return { data: { deletedCount: 0 } }
    const id = Number(url.split('/').at(-2))
    rows.find(row => row.id === id).retained = true
    return {}
  },
  async delete(url) {
    requests.push({ method: 'DELETE', url })
    rows = rows.filter(row => row.id !== Number(url.split('/').at(-1)))
  },
}
const utility = evaluate(fs.readFileSync(new URL('../src/utils/operationLogs.ts', import.meta.url), 'utf8'), {
  '@/utils/api': { default: fakeApi },
})
const notices = []
let accepted = true
const { descriptor } = parse(fs.readFileSync(new URL('../src/components/OperationLogPanel.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, { id: 'operation-log-check' })
const panel = evaluate(compiled.content, {
  '@/utils/operationLogs': utility,
  '@/utils/dateTime': { formatChinaDateTime: value => value },
  '@/utils/message': { confirm: async () => accepted, message: {
    error: value => notices.push(value), success: value => notices.push(value),
  } },
}).default
panel.render = () => null
const renderer = createRenderer({
  insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {},
})
const app = renderer.createApp(panel)
app.mount({})
const state = app._instance.setupState
async function flush() { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
await flush()
assert.equal(state.logs.length, 2)
assert.equal(state.dirty, false)
assert.equal(state.hasExpiringRules, false)

state.actionFilter = 'OPEN_BOOK'
state.keyword = ' 数学 '
state.applyFilters()
await flush()
assert.equal(state.logs.length, 1)
assert.deepEqual(requests.at(-1).params, { page: 0, size: 20, action: 'OPEN_BOOK', keyword: '数学' })
state.resetFilters()
await flush()
assert.equal(state.logs.length, 2)
await state.retainLog(state.logs[0])
assert.equal(state.logs[0].retained, true)
const retainedCalls = requests.filter(item => item.url.endsWith('/1/retain')).length
await state.retainLog(state.logs[0])
assert.equal(requests.filter(item => item.url.endsWith('/1/retain')).length, retainedCalls)

accepted = false
await state.deleteLog(state.logs[0])
assert.equal(rows.length, 2)
accepted = true
await state.deleteLog(state.logs[0])
assert.equal(rows.length, 1)
assert.ok(requests.some(item => item.method === 'DELETE' && item.url === '/api/operation-logs/1'))

state.draftRules[0].retentionDays = 2
assert.equal(state.dirty, true)
await state.cleanupLogs()
assert.ok(!requests.some(item => item.url.endsWith('/cleanup')))
await state.savePolicies()
assert.equal(state.dirty, false)
assert.equal(state.hasExpiringRules, true)
assert.equal(rules[0].retentionDays, 2)
await state.cleanupLogs()
assert.ok(requests.some(item => item.method === 'POST' && item.url.endsWith('/cleanup')))
assert.equal(state.cleaning, false)

rejectPolicyLoad = true
await state.loadPolicies()
assert.ok(state.policyError.includes('加载失败'))
rejectPolicyLoad = false
await state.loadPolicies()
assert.equal(state.policyError, '')

// Automatic cleanup controls persist independently of manual cleanup eligibility.
state.draftSchedule.enabled = false
assert.equal(state.dirty, true)
await state.savePolicies()
assert.equal(schedule.enabled, false)
assert.equal(state.savedSchedule.enabled, false)
const manualBefore = requests.filter(item => item.url.endsWith('/cleanup')).length
await state.cleanupLogs()
assert.equal(requests.filter(item => item.url.endsWith('/cleanup')).length, manualBefore + 1)
state.draftSchedule.enabled = true
state.draftSchedule.frequency = 'WEEKLY'
state.draftSchedule.dayOfWeek = 7
state.scheduleTime = '04:15'
assert.equal(state.draftSchedule.hour, 4)
assert.equal(state.draftSchedule.minute, 15)
assert.equal(state.dirty, true)
await state.savePolicies()
assert.equal(schedule.frequency, 'WEEKLY')
assert.equal(schedule.dayOfWeek, 7)
assert.equal(state.savedSchedule.hour, 4)
await state.loadPolicies()
assert.equal(state.draftSchedule.frequency, 'WEEKLY')
assert.equal(state.scheduleTime, '04:15')
assert.equal(state.dirty, false)
state.savedSchedule.nextRunAt = '2026-10-23T04:15:00'
assert.equal(state.dirty, false, 'Execution metadata must not make configuration dirty')

// Older network responses must never overwrite the latest filter result.
let resolveOld, resolveNew
utility.operationLogsApi.list = () => new Promise(resolve => {
  if (!resolveOld) resolveOld = resolve
  else resolveNew = resolve
})
const oldRequest = state.loadLogs(0)
const newRequest = state.loadLogs(0)
resolveNew({ content: [{ ...rows[0], id: 20 }], totalElements: 1, number: 0 })
await newRequest
resolveOld({ content: [{ ...rows[0], id: 10 }], totalElements: 1, number: 0 })
await oldRequest
assert.equal(state.logs[0].id, 20)
assert.equal(state.loading, false)

const pages = []
utility.operationLogsApi.list = async params => {
  pages.push(params.page)
  return { content: params.page ? [] : clone(rows), totalElements: 1, number: params.page }
}
await state.loadLogs(3)
assert.deepEqual(pages, [3, 0])
assert.equal(state.currentPage, 1)
app.unmount()
console.log('Operation log panel: filtering, retention, manual deletion, policy save/cleanup, failure, stale response and page fallback passed')
