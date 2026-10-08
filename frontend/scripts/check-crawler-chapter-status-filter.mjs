import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('CrawlerView.ts', script, ts.ScriptTarget.Latest, true)
const names = new Set(['syncOpenBookProgress', 'setChapterStatusFilter', 'chapterSortApiValue', 'handleChapterStatusFilterKey'])
const functions = ast.statements
  .filter(node => ts.isFunctionDeclaration(node) && names.has(node.name?.text))
  .map(node => node.getText(ast)).join('\n')
assert.equal(ast.statements.filter(node => ts.isFunctionDeclaration(node) && names.has(node.name?.text)).length, 4)
const compiled = ts.transpileModule(functions, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText

function harness() {
  const calls = []
  const context = {
    chapterRequestSequence: 0,
    selectedBook: { value: { id: 15 } },
    bookDrawer: { value: true },
    chapterStatusFilter: { value: '' },
    followCurrentChapter: { value: true },
    chapterPage: { value: 9 },
    chapterPageSize: { value: 20 },
    chapterSort: { value: 'indexDesc' },
    chapterLoading: { value: false },
    chapterTotal: { value: 0 },
    currentCrawlingChapter: { value: undefined },
    chapters: { value: [] },
    crawlerLogs: { value: [] },
    books: { value: [{ id: 15 }] },
    scrollToCurrentCrawlingChapter: async () => {},
    crawlerApi: {
      book: async () => ({ id: 15 }),
      logs: async () => [],
      currentChapter: async () => {
        calls.push({ focus: true })
        return { page: 1, chapter: { id: 19 } }
      },
      chapters: async (id, params) => {
        calls.push({ id, ...params })
        return { content: [{ id: 22, crawlStatus: params.crawlStatus }], totalElements: 61, totalPages: 4 }
      },
    },
  }
  context.chapterFollowActive = { get value() {
    return context.followCurrentChapter.value && !context.chapterStatusFilter.value
  } }
  vm.createContext(context)
  vm.runInContext(compiled, context)
  return { context, calls }
}

const statuses = ['NOT_CRAWLED', 'WAITING', 'CRAWLING', 'COMPLETED', 'PENDING_RELEASE', 'FAILED', 'CONTENT_SUSPECTED', 'IGNORED']
for (const status of statuses) {
  const { context, calls } = harness()
  await context.setChapterStatusFilter(status)
  assert.equal(context.chapterPage.value, 1)
  assert.equal(context.chapterTotal.value, 61)
  assert.equal(context.chapterLoading.value, false)
  assert.equal(context.chapters.value[0].crawlStatus, status)
  assert.equal(calls.length, 1, '筛选期间不得调用跟随采集定位接口')
  assert.equal(calls[0].crawlStatus, status)
  assert.equal(calls[0].page, 0)
  assert.equal(context.followCurrentChapter.value, true, '暂停跟随不得修改账户偏好')
  await context.setChapterStatusFilter('')
  assert.equal(context.chapterSort.value, 'indexAsc')
  assert.equal(context.chapterPage.value, 2)
  assert.equal(calls.at(-1).crawlStatus, undefined)
  assert.equal(calls.at(-1).sort, 'INDEX_ASC')
}

{
  const { context } = harness()
  const pending = []
  context.crawlerApi.chapters = (id, params) => new Promise(resolve => pending.push({ params, resolve }))
  const oldRequest = context.setChapterStatusFilter('FAILED')
  const newRequest = context.setChapterStatusFilter('COMPLETED')
  assert.equal(pending.length, 2)
  pending[1].resolve({ content: [{ id: 2 }], totalElements: 1, totalPages: 1 })
  await newRequest
  pending[0].resolve({ content: [{ id: 1 }], totalElements: 100, totalPages: 5 })
  await oldRequest
  assert.equal(context.chapters.value[0].id, 2, '迟到响应不得覆盖最新筛选结果')
  assert.equal(context.chapterTotal.value, 1)
}

{
  const { context, calls } = harness()
  context.chapterStatusFilter.value = 'FAILED'
  context.chapterPage.value = 8
  context.crawlerApi.chapters = async (id, params) => {
    calls.push(params)
    return { content: [], totalElements: 0, totalPages: 0 }
  }
  await context.syncOpenBookProgress()
  assert.equal(context.chapterPage.value, 1, '状态变化使分页失效时回到有效页面')
  assert.equal(context.chapterTotal.value, 0)
  assert.deepEqual(calls.map(call => call.page), [7, 0])
  assert.ok(calls.every(call => call.crawlStatus === 'FAILED'))
}

{
  const { context } = harness()
  context.chapterStatusFilterOptions = ['', ...statuses].map(value => ({ value }))
  context.chapterStatusFilterIndex = { get value() {
    return context.chapterStatusFilterOptions.findIndex(option => option.value === context.chapterStatusFilter.value)
  } }
  const focused = []
  const scrolled = []
  const buttons = context.chapterStatusFilterOptions.map((_, index) => ({
    focus: () => focused.push(index),
    scrollIntoView: () => scrolled.push(index),
  }))
  let prevented = 0
  for (const [key, expected] of [['End', 'IGNORED'], ['ArrowRight', ''], ['ArrowLeft', 'IGNORED'], ['Home', '']]) {
    context.handleChapterStatusFilterKey({
      key,
      preventDefault: () => { prevented++ },
      currentTarget: { querySelectorAll: () => buttons },
    })
    assert.equal(context.chapterStatusFilter.value, expected)
    await new Promise(resolve => setImmediate(resolve))
  }
  assert.equal(prevented, 4)
  assert.deepEqual(focused, [8, 0, 8, 0])
  assert.deepEqual(scrolled, focused)
}

console.log('章节状态筛选检查通过：8 种状态、分页、跟随恢复、迟到响应及空结果回退。')
