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
let failureResult = null
const api = {
  async get(url, options) {
    requests.push({ url, options })
    if (url === '/api/books') return new Promise(resolve => { pendingBook = resolve })
    if (url.startsWith('/api/scraper/trial/')) {
      if (failPoll) throw { response: { status: 404 } }
      if (failureResult) return { data: failureResult }
      return { data: { finished: finishPoll, status: finishPoll ? 'MATCHED' : 'RUNNING', elapsedMs: 1200, logs: [], metadata: null } }
    }
    return { data: [] }
  },
  async post(url, payload) {
    requests.push({ url, payload })
    if (url.endsWith('/confirm')) {
      failureResult = { ...failureResult, status: 'APPLIED', applied: true, updatedFields: ['description'] }
      return { data: { success: true } }
    }
    if (url.includes('/candidates/')) {
      return { data: { ...failureResult, finished: false, status: 'RUNNING' } }
    }
    return { data: { taskId: 'task-1' } }
  },
}
const preferenceState = vue.reactive({
  scraperTrialViewMode: 'table',
  setScraperTrialViewMode(value) { this.scraperTrialViewMode = value },
  hydrate: async () => {},
})
const scheduled = []
const privacyAllowsCover = vue.ref(true)
const context = {
  exports: {},
  require(name) {
    if (name === '@/composables/useBookNameDisplay') return { useBookNameDisplay: () => ({ displayBookName: title => title || '' }) }
    if (name === 'vue') return { ...vue, onBeforeUnmount: () => {} }
    if (name === '@/utils/api') return { default: api }
    if (name === '@/stores/preferences') return { usePreferencesStore: () => preferenceState }
    if (name === '@/utils/cover') return { getCoverThumbnailUrl: value => value }
    if (name === '@/utils/imagePrivacy') return { shouldLoadBookCover: () => privacyAllowsCover.value }
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
assert.equal(state.activeTab.value, 'books')
assert.equal(state.tabs.value[1].label, '刮削过程', 'candidate controls must not appear without candidates')
await state.switchTabByKeyboard({ key: 'End', preventDefault() {}, currentTarget: { querySelector: () => ({ focus() {} }) } })
assert.equal(state.activeTab.value, 'result')
await state.switchTabByKeyboard({ key: 'ArrowRight', preventDefault() {}, currentTarget: { querySelector: () => ({ focus() {} }) } })
assert.equal(state.activeTab.value, 'books', 'tab keyboard navigation wraps around')
await state.start()
assert.equal(state.activeTab.value, 'process', 'starting a trial opens progress')
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
assert.equal(state.activeTab.value, 'result', 'a completed lookup opens results')
const restriction = {
  code: 'ACCESS_DENIED', label: '访问被拒绝（疑似反爬或权限限制）',
  detail: '来源返回 HTTP 403；无法仅凭状态码确认是否为反爬。',
  suggestion: '检查来源访问要求或更换来源。', stage: 'ISBN 查询',
  httpStatus: 403, endpoint: 'https://example.com/search',
}
failureResult = {
  finished: true, status: 'NO_RESULT', elapsedMs: 2400, logs: [], metadata: null,
  failure: restriction, diagnostics: [restriction],
}
await state.poll(1)
assert.equal(state.snapshot.value.failure.code, 'ACCESS_DENIED')
assert.equal(state.snapshot.value.diagnostics[0].httpStatus, 403)
assert.equal(state.snapshot.value.failure.suggestion, restriction.suggestion)
assert.equal(state.statusLabel.value, '未取得元信息')
failPoll = true
await state.poll(1)
assert.equal(state.taskId.value, '')
assert.match(state.pollError.value, /过期/)
assert(emitted.some(([event, value]) => event === 'running' && value === true))
assert(emitted.some(([event, value]) => event === 'running' && value === false))
// Candidate preview must remain read-only until the user explicitly confirms.
failPoll = false
state.taskId.value = 'candidate-task'
failureResult = {
  finished: true, status: 'AWAITING_SELECTION', elapsedMs: 2000, logs: [], metadata: null,
  candidates: [{ id: '123', title: '活着（纪念版）' }],
}
await state.poll(1)
assert.equal(state.activeTab.value, 'process', 'a candidate search stays on the selection step')
assert.match(state.tabs.value[1].label, /备选选择/)
state.activeTab.value = 'books'
await state.poll(1)
assert.equal(state.activeTab.value, 'books', 'repeated polling must not override manual tab navigation')
const candidate = { id: '123', title: '活着（纪念版）', author: '余华', publishDate: '2024', sourceUrl: 'https://book.douban.com/subject/123/' }
failureResult = {
  finished: true, status: 'REVIEW_REQUIRED', elapsedMs: 3000, logs: [],
  metadata: { title: candidate.title, description: '候选简介', coverUrl: 'covers/candidate.jpg', tags: ['小说', '文学'] },
  candidates: [candidate], selectedCandidateId: '123', applied: false,
}
const beforeConfirmation = requests.filter(request => request.url.endsWith('/confirm')).length
await state.loadCandidate('123')
await Promise.resolve()
assert.equal(state.snapshot.value.status, 'REVIEW_REQUIRED')
assert.equal(state.activeTab.value, 'result', 'candidate detail opens the visual result preview')
assert.equal(state.resultCover.value, 'covers/candidate.jpg')
assert.deepEqual(Array.from(state.resultTags.value), ['小说', '文学'])
assert.equal(state.coverAllowed.value, true)
props.modelValue = true
await vue.nextTick()
for (let index = 0; index < 8; index++) await Promise.resolve()
const requestsBeforeCoverToggle = requests.length
state.toggleResultCover()
assert.equal(state.resultCoverVisible.value, false)
assert.equal(privacyAllowsCover.value, true, 'temporary hiding must not change the privacy preference')
state.toggleResultCover()
assert.equal(state.resultCoverVisible.value, true)
privacyAllowsCover.value = false
state.previewCoverOverride.value = null
assert.equal(state.resultCoverVisible.value, false)
state.toggleResultCover()
assert.equal(state.resultCoverVisible.value, true, 'explicit preview can temporarily show a hidden cover')
assert.equal(privacyAllowsCover.value, false, 'temporary showing must not persist the privacy preference')
props.modelValue = false
await vue.nextTick()
assert.equal(state.previewCoverOverride.value, null, 'closing the dialog clears the temporary choice')
assert.equal(state.resultCoverVisible.value, false)
assert.equal(requests.length, requestsBeforeCoverToggle, 'preview visibility must not write to any API')
privacyAllowsCover.value = true
state.toggleResultCover()
assert.equal(state.resultCoverVisible.value, false)

state.resultCoverFailed.value = true
failureResult = { ...failureResult, metadata: { ...failureResult.metadata, coverUrl: 'covers/new.jpg' } }
await state.poll(2)
await vue.nextTick()
assert.equal(state.resultCoverFailed.value, false, 'a new cover clears the prior image error')
assert.equal(state.previewCoverOverride.value, null, 'changing the result cover clears its temporary visibility override')
assert.equal(requests.filter(request => request.url.endsWith('/confirm')).length, beforeConfirmation)
assert.deepEqual(Array.from(state.overwriteFields.value), [], 'overwrite defaults to no selected fields')
state.setOverwriteField('description', true)
assert.deepEqual(Array.from(state.overwriteFields.value), ['description'])
state.setOverwriteField('isbn', true)
state.setOverwriteField('coverHidden', true)
assert.deepEqual(Array.from(state.overwriteFields.value), ['description'], 'missing or non-metadata fields cannot be selected')
state.selectAllOverwriteFields()
assert.deepEqual(Array.from(state.overwriteFields.value).sort(), ['title', 'description', 'coverUrl', 'tags'].sort())
state.clearOverwriteFields()
state.setOverwriteField('description', true)
await state.poll(2)
assert.deepEqual(Array.from(state.overwriteFields.value), ['description'], 'polling the same result retains overwrite choices')
await state.confirmCandidate()
assert.deepEqual(Array.from(requests.findLast(request => request.url.endsWith('/confirm')).payload.overwriteFields), ['description'])
assert.equal(state.snapshot.value.applied, true)
assert.equal(state.snapshot.value.status, 'APPLIED')
assert.equal(state.snapshot.value.updatedFields[0], 'description')
assert.equal(state.updatedFieldLabels.value, '简介')
await state.confirmCandidate()
assert.equal(requests.filter(request => request.url.endsWith('/confirm')).length, beforeConfirmation + 1)
state.selectBook({id: 99, title: '另一本书'})
assert.equal(state.snapshot.value, null, 'changing the target book must discard the previous candidate review')
assert.equal(state.taskId.value, '')
assert.equal(state.overwriteFields.value.length, 0, 'changing books resets overwrite choices')
console.log('Scraper trial: filters, stale searches, view switching, selection retention, selection lock, duplicate prevention, polling, failure diagnostics, candidate confirmation, visual result fields, tab routing, keyboard navigation and expiration passed')

// The library/detail manual flow fixes the target and uses account-scoped manual APIs.
const manualProps = vue.reactive({
  modelValue: false, manual: true,
  book: { id: 42, title: '指定目标', author: '原作者' },
  source: { configKey: 'jd', name: '京东读书' },
})
const manualEvents = []
const manualState = context.exports.default.setup(manualProps, {
  expose() {}, emit: (...args) => manualEvents.push(args),
})
const priorBookQueries = requests.filter(request => request.url === '/api/books').length
manualProps.modelValue = true
await vue.nextTick()
assert.equal(manualState.selected.value.id, 42)
assert.equal(manualState.tabs.value.length, 2)
assert.equal(manualState.activeTab.value, 'process')
assert.equal(requests.filter(request => request.url === '/api/books').length, priorBookQueries)
failureResult = { finished: true, status: 'MATCHED', elapsedMs: 12, logs: [], metadata: { title: '指定元信息' } }
// Reuse the mock response for either API namespace.
const originalGet = api.get
api.get = async (url, options) => url.startsWith('/api/scraper/manual/')
  ? { data: failureResult } : originalGet(url, options)
const priorWrites = requests.filter(request => request.url.endsWith('/confirm')).length
await manualState.start()
for (let index = 0; index < 8; index++) await Promise.resolve()
assert(requests.some(request => request.url === '/api/scraper/manual/jd/books/42'))
assert.equal(manualState.canConfirm.value, true, 'a single-source result can be explicitly saved')
assert.equal(manualState.activeTab.value, 'result')
assert.equal(requests.filter(request => request.url.endsWith('/confirm')).length, priorWrites)
manualState.setOverwriteField('title', true)
await manualState.confirmCandidate()
assert.deepEqual(Array.from(requests.findLast(request => request.url.endsWith('/confirm')).payload.overwriteFields), ['title'])
assert(requests.some(request => request.url === '/api/scraper/manual/task-1/confirm'))
assert.equal(manualEvents.filter(([event]) => event === 'saved').length, 1)
await manualState.poll(1)
assert.equal(manualEvents.filter(([event]) => event === 'saved').length, 1, 'refresh once per applied task')
manualProps.book = { id: 43, title: '新的固定目标' }
await vue.nextTick()
assert.equal(manualState.snapshot.value, null)
assert.equal(manualState.selected.value.id, 43)
console.log('Manual scraping: fixed target, scoped APIs, no writes until confirmation, single-result save and refresh passed')
