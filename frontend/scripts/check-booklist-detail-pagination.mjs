import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/views/BookListView.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('booklist.ts', script, ts.ScriptTarget.Latest, true)
const declaration = ast.statements.find(node => ts.isVariableStatement(node)
  && node.declarationList.declarations.some(item => item.name.getText(ast) === 'loadBookList'))
const ref = value => ({ value })
const pending = []
const context = vm.createContext({
  route: { params: { id: '1' } }, bookList: ref(null), books: ref([]), loading: ref(false),
  loadError: ref(false), page: ref(1), pageSize: 24, total: ref(0), requestSequence: 0,
  api: { get: (url, options) => new Promise((resolve, reject) => pending.push({ url, options, resolve, reject })) },
})
vm.runInContext(ts.transpile(declaration.getText(ast).replace('const loadBookList', 'var loadBookList'),
  { target: ts.ScriptTarget.ES2022 }), context)
const run = code => vm.runInContext(code, context)
let first = run('loadBookList()')
assert.equal(pending[0].url, '/api/booklists/1/books')
assert.equal(pending[0].options.params.size, 24)
pending[0].resolve({ data: { id: 1, name: '旧书单', books: { content: [{ id: 1 }], totalElements: 30 } } })
await first
assert.equal(context.total.value, 30)
context.page.value = 2
const old = run('loadBookList()')
assert.equal(pending[1].options.params.page, 1)
context.route.params.id = '2'
context.page.value = 1
const newer = run('loadBookList()')
pending[2].resolve({ data: { id: 2, name: '新书单', books: { content: [{ id: 2 }], totalElements: 1 } } })
await newer
pending[1].resolve({ data: { id: 1, name: '旧书单', books: { content: [{ id: 9 }], totalElements: 30 } } })
await old
assert.equal(context.bookList.value.id, 2)
assert.equal(context.books.value[0].id, 2)
context.page.value = 3
const shrink = run('loadBookList()')
pending[3].resolve({ data: { id: 2, books: { content: [], totalElements: 1 } } })
await new Promise(resolve => setTimeout(resolve, 0))
assert.equal(context.page.value, 1)
pending[4].resolve({ data: { id: 2, books: { content: [{ id: 2 }], totalElements: 1 } } })
await shrink
const failed = run('loadBookList()')
pending[5].reject(new Error('offline'))
await failed
assert.equal(context.loadError.value, true)
assert.equal(context.books.value.length, 0)
console.log('书单详情：服务端分页、跨书单请求隔离、页码回退与加载失败处理通过')
