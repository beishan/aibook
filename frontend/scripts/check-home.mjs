import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'

const require = createRequire(import.meta.url)
const { createRenderer, createSSRApp, h, nextTick } = require('vue')
const { renderToString } = require('@vue/server-renderer')
function evaluate(source, overrides = {}) {
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const module = { exports: {} }
  new Function('require', 'module', 'exports', code)(
    name => Object.hasOwn(overrides, name) ? overrides[name] : require(name), module, module.exports,
  )
  return module.exports
}
const utility = evaluate(fs.readFileSync(new URL('../src/utils/home.ts', import.meta.url), 'utf8'), {
  '@/utils/api': { default: {} },
})
assert.equal(utility.readingPercent(500), 100)
assert.equal(utility.readingPercent(-7), 0)
assert.equal(utility.readingPercent(null), 0)
assert.equal(utility.readingPercent(NaN), 0)
assert.equal(utility.homeFileSize(1024 ** 3), '1.0 GB')
assert.equal(utility.homeFileSize(0), '0 B')
const listeners = new Map(), timers = new Map()
global.document = {
  visibilityState: 'visible',
  addEventListener: (key, fn) => listeners.set(key, fn),
  removeEventListener: key => listeners.delete(key),
}
global.window = { setInterval: fn => { timers.set(1, fn); return 1 }, clearInterval: id => timers.delete(id) }
let homeFails = false, recommendationFails = false, homeLoads = 0, recommendationLoads = 0, rerolls = []
const book = { id: 7, title: '测试书籍', author: '作者', format: 'epub', coverUrl: null, readingStatus: 'READING', lastReadAt: '2026-10-22T09:00:00', totalProgress: 45 }
const dashboard = {
  overview: { totalBooks: 10, readingBooks: 3, finishedBooks: 2, favoriteBooks: 1, wantedBooks: 1, fileSizeBytes: 1024 },
  categories: [{ id: 3, name: '文学', count: 8 }], continuing: [book], recentlyRead: [book],
  recentlyAdded: [{ ...book, lastReadAt: undefined }, { ...book, id: 8, title: '另一本书' }],
  recentlyUpdated: [], wanted: [],
}
const today = () => '2026-10-22'
const groups = [1, 2].map(id => ({ rule: { id, name: `组${id}` }, date: today(), books: [book] }))
const fakeApi = {
  async today() {
    recommendationLoads++
    if (recommendationFails) throw new Error('unavailable')
    return { date: today(), groups: groups.map(g => ({ ...g })) }
  },
  async reroll(id) { rerolls.push(id); return { ...groups[0], books: [{ ...book, id: 9 }] } },
}
const dateUtils = evaluate(fs.readFileSync(new URL('../src/utils/dateTime.ts', import.meta.url), 'utf8'))
const source = fs.readFileSync(new URL('../src/views/HomeView.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const compiled = compileScript(descriptor, { id: 'home-check' })
const panel = evaluate(compiled.content, {
  '@/components/HomeBookCover.vue': { default: {} },
  '@/utils/home': { ...utility, homeApi: { async dashboard() {
    homeLoads++
    if (homeFails) throw new Error('unavailable')
    return dashboard
  } } },
  '@/utils/recommendations': { recommendationApi: fakeApi, recommendationDate: today },
  '@/stores/user': { useUserStore: () => ({ userInfo: { nickname: '读者' } }) },
  '@/utils/dateTime': dateUtils,
}).default
panel.render = () => null
const renderer = createRenderer({
  insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {},
})
const app = renderer.createApp(panel)
app.mount({})
const state = app._instance.setupState
async function flush() { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
await flush()
assert.equal(state.displayName, '读者')
assert.equal(state.completionRate, 20)
assert.equal(state.heroBooks.length, 2)
assert.equal(state.heroBook.lastReadAt, book.lastReadAt, 'duplicate added books must not replace reading metadata')
state.moveHero(1)
assert.equal(state.heroBook.id, 8)
state.moveHero(1)
assert.equal(state.heroBook.id, 7)
await state.rerollRecommendation()
assert.deepEqual(rerolls, [1])
assert.equal(state.recommendations.groups[0].books[0].id, 9)
assert.equal(state.recommendations.groups[1].books[0].id, 7, 'reroll affects only the displayed group')
recommendationFails = true
await state.refreshHome()
assert(state.dashboard && state.recommendationError, 'recommendation failure must not blank the dashboard')
recommendationFails = false
homeFails = true
await state.refreshHome()
assert(state.homeError && state.recommendations, 'dashboard failure must not blank recommendations')
homeFails = false
await state.refreshHome()
assert.equal(state.homeError, '')
assert.equal(state.recommendationError, '')
assert.equal(rerolls.length, 1, 'refresh never rerolls')
document.visibilityState = 'hidden'
listeners.get('visibilitychange')()
assert.equal(timers.size, 0)
state.recommendations.date = '2026-10-21'
const before = recommendationLoads
document.visibilityState = 'visible'
listeners.get('visibilitychange')()
await flush()
assert(recommendationLoads > before)
app.unmount()
assert.equal(timers.size, 0)
assert.equal(listeners.size, 0)

// Render the actual reusable cover: explicit book privacy wins over account defaults.
const coverSource = fs.readFileSync(new URL('../src/components/HomeBookCover.vue', import.meta.url), 'utf8')
const coverDescriptor = parse(coverSource).descriptor
const cover = evaluate(compileScript(coverDescriptor, { id: 'cover-check', inlineTemplate: true }).content, {
  '@/utils/imagePrivacy': { shouldLoadBookCover: () => false },
  '@/utils/cover': { getCoverThumbnailUrl: value => value },
}).default
for (const [hidden, expectImage] of [[true, false], [null, false], [false, true]]) {
  const html = await renderToString(createSSRApp({ render: () => h(cover, { book: { ...book, coverUrl: '/cover.jpg', coverHidden: hidden } }) }))
  assert.equal(html.includes('<img'), expectImage)
}

// Exercise deep links using the actual BooksView route hydration function.
const booksView = fs.readFileSync(new URL('../src/views/BooksView.vue', import.meta.url), 'utf8')
const start = booksView.indexOf('function applyHomeRouteFilters()')
const end = booksView.indexOf('\nwatch(', start)
const context = vm.createContext({ route: { query: { categoryId: '3', includeChildren: 'false', sortBy: 'updatedAt' } },
  searchKeyword: { value: '' }, filterCategoryId: { value: '' }, filterIncludeChildren: { value: true }, sortBy: { value: 'createdAt' } })
vm.runInContext(ts.transpile(booksView.slice(start, end)), context)
context.applyHomeRouteFilters()
assert.equal(context.filterCategoryId.value, '3')
assert.equal(context.filterIncludeChildren.value, false)
assert.equal(context.sortBy.value, 'updatedAt')
context.route.query = { categoryId: 'not-an-id', search: '三体' }
context.applyHomeRouteFilters()
assert.equal(context.filterCategoryId.value, '')
assert.equal(context.filterIncludeChildren.value, true)
assert.equal(context.searchKeyword.value, '三体')
console.log('Home: real data, hero metadata, scoped reroll, partial failures/retry, date lifecycle, cover privacy and library deep links passed')
