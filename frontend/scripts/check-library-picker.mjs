import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'

const source = fs.readFileSync(new URL('../src/components/library/AddToLibraryDialog.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
compileScript(descriptor, { id: 'library-picker-check', inlineTemplate: true })
const ast = ts.createSourceFile('picker.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true)
const ref = value => ({ value })
const requests = []
const warnings = []
const context = vm.createContext({
  generation: 0,
  loading: ref(false), saving: ref(false), loadError: ref(false),
  chosen: ref([99]), candidates: ref([]), page: ref(1), total: ref(0), pageSize: ref(48),
  keyword: ref('  测试  '),
  filters: { format: 'epub', status: 'READING', categoryId: 3, includeChildren: true, tagId: 5,
    source: 'site:example.org', sortBy: 'title', sortDir: 'asc' },
  addedRange: ref(['2026-11-01', '2026-11-13']),
  props: { libraryId: 8, listId: 9 },
  message: { warning: message => warnings.push(message), error() {} },
  api: { get: (url, options) => new Promise((resolve, reject) => requests.push({ url, options, resolve, reject })) },
})
for (const name of ['candidateTagNames', 'loadBooks', 'search', 'selectCurrentPage', 'toggle']) {
  const node = ast.statements.find(item => ts.isFunctionDeclaration(item) && item.name.text === name)
  vm.runInContext(ts.transpile(node.getText(ast), { target: ts.ScriptTarget.ES2022 }), context)
}
context.candidates.value = [{ id: 1, existing: true }, { id: 2 }, { id: 3 }]
context.selectCurrentPage()
assert.deepEqual(Array.from(context.chosen.value), [99, 2, 3], 'select only eligible page rows and retain other pages')
context.selectCurrentPage()
assert.equal(context.chosen.value.length, 3, 'no duplicates')
context.toggle(1)
assert.equal(context.chosen.value.length, 3, 'existing memberships are disabled')
context.loading.value = true
context.selectCurrentPage()
context.toggle(2)
assert.equal(context.chosen.value.length, 3, 'no changes while querying')
context.loading.value = false
context.chosen.value = Array.from({ length: 199 }, (_, index) => index + 1000)
context.selectCurrentPage()
assert.equal(context.chosen.value.length, 200)
assert.equal(warnings.length, 1, 'partial page selection reports the limit')
assert.deepEqual(Array.from(context.candidateTagNames({ tags: [{ name: '科幻' }], tagNames: ['科幻', '经典'] })), ['科幻', '经典'])

const first = context.loadBooks()
assert.deepEqual(JSON.parse(JSON.stringify(requests[0].options.params)), {
  keyword: '测试', format: 'epub', status: 'READING', categoryId: 3, includeChildren: true,
  tagId: 5, sourceType: 'CRAWLER', sourceSiteName: 'example.org', addedFrom: '2026-11-01',
  addedTo: '2026-11-13', sortBy: 'title', sortDir: 'asc', page: 0, size: 48,
})
requests[0].resolve({ data: { content: [{ id: 1 }, { id: 2 }], totalElements: 90 } })
await new Promise(resolve => setTimeout(resolve, 0))
assert.equal(requests[1].url, '/api/libraries/8/membership')
assert.equal(requests[1].options.params.listId, 9)
requests[1].resolve({ data: [1] })
await first
assert.equal(context.candidates.value[0].existing, true)
assert.equal(context.candidates.value[1].existing, false)
assert.equal(context.total.value, 90)

context.page.value = 2
const old = context.loadBooks()
context.page.value = 1
context.pageSize.value = 96
const latest = context.loadBooks()
requests[3].resolve({ data: { content: [], totalElements: 0 } })
await latest
requests[2].resolve({ data: { content: [{ id: 55 }], totalElements: 90 } })
await old
assert.equal(context.candidates.value.length, 0, 'late queries cannot replace a newer result')
assert.equal(context.total.value, 0)
assert.equal(context.chosen.value.length, 200, 'query changes retain cross-page selection')

context.filters.source = undefined
const failed = context.loadBooks()
assert.equal(requests[4].options.params.sourceType, undefined, 'clearing source restores all sources')
requests[4].reject(new Error('offline'))
await failed
assert.equal(context.loadError.value, true)
assert.equal(context.candidates.value.length, 0)
assert.equal(context.loading.value, false)
context.page.value = 9
const shrink = context.loadBooks()
requests[5].resolve({ data: { content: [], totalElements: 1 } })
await new Promise(resolve => setTimeout(resolve, 0))
assert.equal(context.page.value, 1, 'shrinking results return to a valid page')
requests[6].resolve({ data: { content: [], totalElements: 0 } })
await shrink

for (const label of ['类型', '标签', '章节数', '全选']) assert.ok(source.includes(label))
assert.ok(source.includes("row.chapterCount ?? '—'"), 'zero chapters remain visible')
assert.ok(source.includes('v-model:page-size="pageSize"'))
console.log('Library picker: filters, date/source/sort mapping, membership, current-page selection, cross-page retention, limits, races, errors and page fallback passed')
