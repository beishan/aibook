import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'
const view = await readFile(new URL('../src/views/BooksView.vue', import.meta.url), 'utf8')
const start = view.indexOf('const loadBooks = async () =>')
const end = view.indexOf('\nconst handleToggleFavorite', start)
const ref = value => ({value})
const calls = []
const context = vm.createContext({
  loadBooksSequence:0, coversPreparing:ref(false), searchKeyword:ref(' 三体 '),
  currentPage:ref(3), pageSize:ref(20), sortBy:ref('title'), filterSource:ref('site:示例网站'),
  filterFormat:ref('epub'), filterStatus:ref('READING'), filterCategoryId:ref('7'), filterTagId:ref('9'),
  bookStore:{fetchBooks:async (...args) => {calls.push(args);return {content:[]}}},
  prepareBookCovers:async () => {},
})
vm.runInContext(ts.transpile(view.slice(start,end), {target:ts.ScriptTarget.ES2022}),context)
await vm.runInContext('loadBooks()',context)
assert.equal(calls[0][0],2)
assert.equal(calls[0][4].sourceType,'CRAWLER')
assert.equal(calls[0][4].sourceSiteName,'示例网站')
assert.equal(calls[0][4].keyword,'三体')
assert.equal(calls[0][4].categoryId,7)
assert.equal(calls[0][4].tagId,9)
assert.equal(calls[0][4].format,'epub')
for (const source of ['UPLOAD','DIRECTORY_SCAN','CRAWLER','']) {
  context.filterSource.value=source
  await vm.runInContext("handleFilterChange('source')",context)
  const call=calls.at(-1)
  assert.equal(call[0],0)
  assert.equal(call[4].sourceType,source||undefined)
  assert.equal(call[4].sourceSiteName,undefined)
  assert.equal(call[4].tagId,9)
}
vm.runInContext('resetFilters()',context)
assert.equal(context.filterSource.value,'')
assert.equal(calls.at(-1)[4].sourceType,undefined)
assert.equal(calls.at(-1)[4].keyword,undefined)
console.log('书库来源类型、具体网站、组合筛选、分页归位及重置验证通过')
