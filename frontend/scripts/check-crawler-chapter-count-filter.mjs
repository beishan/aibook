import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const functions = source.slice(source.indexOf('async function loadBooks('),
  source.indexOf('async function loadTasks('))
const ref = value => ({ value })
const calls = []
let total = 120
const context = vm.createContext({
  bookLoading: ref(false), selectedBooks: ref([]), bookTableRef: ref(undefined),
  bookPage: ref(3), bookPageSize: ref(20), bookKeyword: ref(' 三体 '),
  bookSiteId: ref(7), bookCrawlStatus: ref('COMPLETED'), bookImportStatus: ref('READY'),
  bookTag: ref('科幻'), bookFavoriteOnly: ref(true), bookSort: ref('CREATED_DESC'),
  bookChapterCountGreaterThan: ref(100), books: ref([]), bookTotal: ref(0),
  crawlerApi: { books: async params => {
    calls.push(params)
    return { content: [{ id: 5, chapterCount: 101 }], totalElements: total }
  } },
})
vm.runInContext(ts.transpile(functions), context)
await context.applyBookFilters()
assert.equal(calls.at(-1).page, 0)
assert.equal(calls.at(-1).chapterCountGreaterThan, 100)
assert.equal(calls.at(-1).keyword, '三体')
assert.equal(calls.at(-1).siteId, 7)
assert.equal(calls.at(-1).tag, '科幻')
assert.equal(calls.at(-1).favoriteOnly, true)
assert.equal(calls.at(-1).crawlStatus, 'COMPLETED')
assert.equal(calls.at(-1).importStatus, 'READY')
context.bookPage.value = 3
await context.loadBooks({ silent: true, preserveSelection: true })
assert.equal(calls.at(-1).page, 2)
assert.equal(calls.at(-1).chapterCountGreaterThan, 100)
total = 21
await context.loadBooks()
assert.equal(calls.at(-1).page, 1)
assert.equal(calls.at(-1).chapterCountGreaterThan, 100)
for (const threshold of [0, 2147483647, null, undefined]) {
  context.bookChapterCountGreaterThan.value = threshold
  await context.applyBookFilters()
  assert.equal(calls.at(-1).chapterCountGreaterThan, threshold ?? undefined)
}
context.bookChapterCountGreaterThan.value = 100
await context.handleBookSizeChange()
assert.equal(calls.at(-1).chapterCountGreaterThan, 100)
await context.resetBookFilters()
assert.equal(context.bookChapterCountGreaterThan.value, undefined)
assert.equal(calls.at(-1).chapterCountGreaterThan, undefined)
assert.equal(calls.at(-1).keyword, undefined)
assert.equal(context.bookPage.value, 1)
assert.equal(context.bookLoading.value, false)
assert.ok(source.includes('aria-label="章节数大于（章）"'))
console.log('采集书籍章节数筛选、组合查询、翻页、静默刷新、清空与重置验证通过')
