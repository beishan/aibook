import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import ts from 'typescript'
import * as vue from 'vue'

const filename = new URL('../src/components/ScraperTrialDialog.vue', import.meta.url)
const { descriptor } = parse(fs.readFileSync(filename, 'utf8'))
const template = compileTemplate({ source: descriptor.template.content, filename: filename.pathname, id: 'trial' })
assert.deepEqual(template.errors, [])
const compiled = compileScript(descriptor, { id: 'trial' }).content
const code = ts.transpileModule(compiled, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText
const requests = []
let pendingBook
let failPoll = false
let finishPoll = false
const api = {
  async get(url, options) {
    requests.push({ url, options })
    if (url === '/api/books') return new Promise(resolve => { pendingBook = resolve })
    if (url.startsWith('/api/scraper/trial/')) {
      if (failPoll) throw { response: { status: 404 } }
      return { data: { finished: finishPoll, status: finishPoll ? 'MATCHED' : 'RUNNING', elapsedMs: 1200, logs: [], metadata: null } }
    }
    return { data: [] }
  },
  async post(url) {
    requests.push({ url })
    return { data: { taskId: 'task-1' } }
  },
}
const preferenceState = vue.reactive({
  scraperTrialViewMode: 'table',
  setScraperTrialViewMode(value) { this.scraperTrialViewMode = value },
  hydrate: async () => {},
})
const scheduled = []
const context = {
  exports: {},
  require(name) {
    if (name === 'vue') return { ...vue, onBeforeUnmount: () => {} }
    if (name === '@/utils/api') return { default: api }
    if (name === '@/stores/preferences') return { usePreferencesStore: () => preferenceState }
    if (name === '@/utils/cover') return { getCoverThumbnailUrl: value => value }
    if (name === '@/utils/imagePrivacy') return { shouldLoadBookCover: () => true }
    throw new Error(name)
  },
  setTimeout: callback => { scheduled.push(callback); return scheduled.length },
  clearTimeout: () => {},
}
vm.runInNewContext(code, context)
const props = vue.reactive({ modelValue: false, source: { configKey: 'douban', name: '豆瓣' } })
const emitted = []
const state = context.exports.default.setup(props, { expose() {}, emit: (...args) => emitted.push(args) })
state.filters.keyword = '三体'
state.filters.sourceType = 'UPLOAD'
state.filters.categoryId = 7
state.filters.tagId = 8
state.dates.value = ['2026-10-01', '2026-10-15']
const first = state.loadBooks()
const resolveFirst = pendingBook
state.filters.keyword = '活着'
const second = state.loadBooks()
pendingBook({ data: { content: [{ id: 2, title: '活着' }], totalElements: 25 } })
await second
resolveFirst({ data: { content: [{ id: 1, title: '三体' }], totalElements: 1 } })
await first
assert.equal(state.books.value[0].id, 2, 'stale search must not replace newer results')
const params = requests[1].options.params
assert.equal(params.addedFrom, '2026-10-01')
assert.equal(params.addedTo, '2026-10-15')
assert.equal(params.categoryId, 7)
assert.equal(params.tagId, 8)
assert.equal(params.sourceType, 'UPLOAD')
assert.equal(params.size, 20)
state.selectBook({ id: 2, title: '活着' })
state.setViewMode('card')
assert.equal(state.viewMode.value, 'card')
assert.equal(state.selected.value.id, 2, 'view switching must retain selection')
state.selectBook(null)
assert.equal(state.selected.value.id, 2, 'table teardown must not clear selection')
await state.switchViewByKeyboard({key: 'Home', preventDefault() {}, currentTarget: {querySelector: () => ({focus() {}})}})
assert.equal(state.viewMode.value, 'table')
assert.equal(state.selected.value.id, 2)
await state.start()
await vue.nextTick()
await Promise.resolve()
assert.equal(state.running.value, true)
assert(requests.some(request => request.url === '/api/scraper/trial/douban/books/2'))
state.selectBook({ id: 3, title: '其他书籍' })
assert.equal(state.selected.value.id, 2, 'running trial must retain its selected book')
const count = requests.length
await state.start()
assert.equal(requests.length, count, 'double click must not create another task')
finishPoll = true
await scheduled.shift()()
await Promise.resolve()
await vue.nextTick()
assert.equal(state.running.value, false)
assert.equal(state.snapshot.value.status, 'MATCHED')
failPoll = true
await state.poll(1)
assert.equal(state.taskId.value, '')
assert.match(state.pollError.value, /过期/)
assert(emitted.some(([event, value]) => event === 'running' && value === true))
assert(emitted.some(([event, value]) => event === 'running' && value === false))
console.log('Scraper trial: filters, stale searches, view switching, selection retention, selection lock, duplicate prevention, polling and expiration passed')
