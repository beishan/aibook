import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/CrawlerImportLibrariesDialog.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('dialog.ts', script, ts.ScriptTarget.Latest, true)
const functions = ast.statements.filter(ts.isFunctionDeclaration).map(node => node.getText(ast)).join('\n')
const visible = ast.statements.find(node => ts.isVariableStatement(node)
  && node.declarationList.declarations.some(declaration => declaration.name.getText(ast) === 'visibleLibraries'))
const ref = value => ({ value })
const emitted = []
const saved = []
let rejectSave = false
let fetchIds = async () => [7, 97, 150]
const all = Array.from({ length: 98 }, (_, index) => ({ id: index + 1, name: `图书馆 ${index + 1}` }))
let fetchPage = async ({ page, size }) => ({
  content: all.slice(page * size, (page + 1) * size), totalElements: all.length, number: page, size,
})
let fetchLibrary = async id => {
  if (id === 150) throw new Error('已删除')
  return { id, name: `图书馆 ${id}` }
}
const calls = []
const context = vm.createContext({
  props: { modelValue: true, bookId: 3 },
  targets: ref([]), libraries: ref([]), keyword: ref(''), loading: ref(false),
  saving: ref(false), loadFailed: ref(false), generation: 0,
  computed: getter => ({ get value() { return getter() } }),
  crawlerApi: {
    importLibraries: (...args) => fetchIds(...args),
    setImportLibraries: async (id, ids) => {
      if (rejectSave) throw new Error('保存失败')
      saved.push({ id, ids: [...ids] })
    },
  },
  personalLibraries: {
    list: params => { calls.push(params); return fetchPage(params) },
    get: id => fetchLibrary(id),
  },
  emit: (...args) => emitted.push(args),
  message: { success() {} },
})
vm.runInContext(ts.transpile(functions + '\n' + visible.getText(ast)
  + '\nglobalThis.filtered = visibleLibraries', { target: ts.ScriptTarget.ES2022 }), context)
const run = code => vm.runInContext(code, context)

await run('load()')
assert.deepEqual(calls.map(call => [call.page, call.size]), [[0, 96], [1, 96]], 'load all supported pages')
assert.deepEqual([...context.targets.value], [7, 97, 150])
assert.equal(context.libraries.value.length, 99, 'all libraries and unavailable selected item are displayed once')
assert.equal(context.libraries.value.find(item => item.id === 150).unavailable, true)
assert.ok(context.libraries.value.some(item => item.id === 98), 'libraries beyond first page are visible')
context.keyword.value = '图书馆 98'
assert.deepEqual([...context.filtered.value].map(item => item.id).sort((a, b) => a - b), [7, 97, 98, 150],
  'search retains selected items so they can be unchecked')
context.keyword.value = ''
await run('save()')
assert.deepEqual(saved[0], { id: 3, ids: [7, 97, 150] })
assert.ok(emitted.some(([event, value]) => event === 'saved' && value === 3))

emitted.length = 0
rejectSave = true
await assert.rejects(run('save()'), /保存失败/)
assert.equal(context.saving.value, false)
assert.equal(emitted.length, 0, 'save failure keeps selection and dialog open')
assert.deepEqual([...context.targets.value], [7, 97, 150])
rejectSave = false
context.targets.value = []
await run('save()')
assert.deepEqual(saved.at(-1), { id: 3, ids: [] }, 'empty selection cancels future additions')

fetchIds = async () => { throw new Error('读取失败') }
await run('load()')
assert.equal(context.loadFailed.value, true)
const previousSaves = saved.length
await run('save()')
assert.equal(saved.length, previousSaves, 'failed loading cannot overwrite persisted targets')
fetchIds = async () => [7]
fetchPage = async ({ page, size }) => {
  if (page === 1) throw new Error('第二页失败')
  return { content: all.slice(0, size), totalElements: 98, number: page, size }
}
await run('load()')
assert.equal(context.loadFailed.value, true, 'failure on later pages prevents saving incomplete results')

let release
fetchIds = () => new Promise(resolve => { release = resolve })
const oldLoad = run('load()')
context.props.bookId = 4
fetchIds = async () => [8]
fetchPage = async ({ page, size }) => ({ content: [all[7]], totalElements: 1, number: page, size })
await run('load()')
release([7])
await oldLoad
assert.deepEqual([...context.targets.value], [8], 'stale response from previous book cannot replace current selection')
assert.match(source, /<el-checkbox-group[\s\S]*?:max="20"/)
assert.match(source, /grid-template-columns: repeat\(auto-fill/)
console.log('采集目标图书馆：平铺全部分页、搜索保留已选、多选限制、服务端恢复及失败保护通过')
