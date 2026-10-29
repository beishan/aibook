import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import * as vue from 'vue'

const source = fs.readFileSync(new URL('../src/utils/imagePrivacy.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText
const storage = new Map([['token', 'account-a']])
const writes = []
let scope = { initialized: true, allHidden: true, overrides: { '3': true }, bookOverrides: { '1': false, '2': true } }
let failSave = false
let pendingHydration
const api = {
  async get() {
    if (pendingHydration) return new Promise(resolve => { pendingHydration.resolve = resolve })
    return { data: scope }
  },
  async put(url, payload) {
    writes.push({ url, payload })
    if (failSave) throw new Error('save failed')
    return { data: { bookId: Number(url.split('/').pop()), hidden: payload.hidden } }
  },
}
const visibilityContext = { exports: {}, require: () => vue }
vm.runInNewContext(ts.transpileModule(
  fs.readFileSync(new URL('../src/utils/bookCoverVisibility.ts', import.meta.url), 'utf8'),
  { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } },
).outputText, visibilityContext)
const context = {
  exports: {}, console,
  localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) },
  require(name) {
    if (name === 'vue') return vue
    if (name === '@/utils/bookCoverVisibility') return visibilityContext.exports
    if (name === '@/utils/api') return { default: api }
    throw new Error(name)
  },
}
vm.runInNewContext(code, context)
const privacy = context.exports
await privacy.hydrateBookCoverPrivacy(1)
assert.equal(privacy.shouldLoadBookCover(1), true, 'explicit show wins over global hidden')
assert.equal(privacy.shouldLoadBookCover(2), false)
assert.equal(privacy.shouldLoadBookCover(3), false)
assert.equal(privacy.shouldLoadBookCover(4), false, 'unconfigured books inherit global hidden')
privacy.setAllBookCoversHidden(false)
assert.equal(privacy.shouldLoadBookCover(2), false, 'global show cannot erase explicit hidden')
privacy.setAllBookCoversHidden(true)
assert.equal(privacy.shouldLoadBookCover(1), true, 'global hide cannot erase explicit show')
for (let index = 0; index < 12; index++) await Promise.resolve()
await privacy.toggleBookCover(4)
assert.equal(writes.at(-1).url, '/api/cover-privacy/books/4')
assert.equal(writes.at(-1).payload.hidden, false)
assert.equal(privacy.shouldLoadBookCover(4), true)
assert(!storage.get('aibook-book-cover-visibility-overrides')?.includes('4'), 'book state must not be stored as an account override')
failSave = true
await assert.rejects(privacy.toggleBookCover(4))
assert.equal(privacy.shouldLoadBookCover(4), true, 'failed persistence must leave the current state intact')
failSave = false
scope = { ...scope, bookOverrides: { ...scope.bookOverrides, '4': false } }
privacy.resetBookCoverPrivacy()
await privacy.hydrateBookCoverPrivacy(1)
assert.equal(privacy.shouldLoadBookCover(4), true, 'fresh sessions restore persisted book settings')
privacy.resetBookCoverPrivacy()
pendingHydration = {}
const late = privacy.hydrateBookCoverPrivacy(1)
storage.set('token', 'account-b')
pendingHydration.resolve({ data: scope })
await late
assert.equal(privacy.shouldLoadBookCover(2), true, 'late responses from a previous account must not leak settings')
assert.equal(privacy.allBookCoversHidden.value, false)
visibilityContext.exports.captureBookCoverVisibility({ content: [
  { id: 10, title: '隐藏书籍', coverHidden: true },
  { id: 11, title: '显示书籍', coverHidden: false },
] })
assert.equal(privacy.shouldLoadBookCover(10), false, 'DTO state also works if account preference hydration is unavailable')
privacy.allBookCoversHidden.value = true
assert.equal(privacy.shouldLoadBookCover(11), true)
visibilityContext.exports.captureBookCoverVisibility({ groups: [{ books: [
  { id: 12, title: '推荐隐藏封面', coverHidden: true },
] }] })
assert.equal(visibilityContext.exports.explicitBookCoverOverrides.value['12'], true,
  'grouped home recommendations must capture book privacy before rendering')
visibilityContext.exports.captureBookCoverVisibility({ book: { id: 10, title: '恢复默认', coverHidden: null } })
privacy.allBookCoversHidden.value = false
assert.equal(privacy.shouldLoadBookCover(10), true)
const detail = fs.readFileSync(new URL('../src/views/BookDetailView.vue', import.meta.url), 'utf8')
assert.match(detail, /<BookCoverPrivacyButton[\s\S]*?inline/)
assert(!detail.includes('!allBookCoversHidden'), 'the detail control must remain available under global hiding')
const books = fs.readFileSync(new URL('../src/views/BooksView.vue', import.meta.url), 'utf8')
assert(!books.includes('if (allBookCoversHidden.value) return'), 'preloading must respect explicit show exceptions')
console.log('Book cover visibility: per-book highest priority, persistence, global changes, failed saves, session restore and account isolation passed')

// Exercise the shared API response hook with current and stale account responses.
let onResponse
const client = {
  interceptors: {
    request: { use() {} },
    response: { use(handler) { onResponse = handler } },
  },
}
const apiContext = {
  exports: {}, console, localStorage: context.localStorage,
  require(name) {
    if (name === 'axios') return { default: { create: () => client } }
    if (name === '@/utils/bookCoverVisibility') return visibilityContext.exports
    if (name === '@/router') return { default: {} }
    if (name === '@/utils/authSession') return { clearStoredAuthSession() {} }
    if (name === '@/utils/message') return { message: {} }
    throw new Error(name)
  },
}
vm.runInNewContext(ts.transpileModule(
  fs.readFileSync(new URL('../src/utils/api.ts', import.meta.url), 'utf8'),
  { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } },
).outputText, apiContext)
onResponse({ data: { id: 21, title: '当前账户书籍', coverHidden: true }, config: {
  url: '/api/books/21', headers: { get: () => 'Bearer account-b' },
} })
assert.equal(privacy.shouldLoadBookCover(21), false)
onResponse({ data: { id: 21, title: '旧账户响应', coverHidden: false }, config: {
  url: '/api/books/21', headers: { Authorization: 'Bearer account-a' },
} })
assert.equal(privacy.shouldLoadBookCover(21), false)
console.log('Book DTO privacy synchronization accepts current account responses and ignores stale accounts')

visibilityContext.exports.captureBookCoverVisibility({
  id: 901, bookId: 902, title: '有声版本', narrator: '演播者', coverHidden: true,
})
assert.equal(visibilityContext.exports.explicitBookCoverOverrides.value['902'], true)
assert.equal(visibilityContext.exports.explicitBookCoverOverrides.value['901'], undefined)
console.log('Audiobook cover visibility is keyed by work ID, not edition ID')
