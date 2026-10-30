import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/CrawlerImportLibrariesDialog.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('dialog.ts', script, ts.ScriptTarget.Latest, true)
const functions = ast.statements.filter(ts.isFunctionDeclaration).map(node => node.getText(ast)).join('\n')
const ref = value => ({ value })
const emitted = []
const saved = []
let rejectSave = false
let fetchIds = async () => [7, 30]
let fetchPage = async () => ({ content: [{ id: 7, name: '历史' }, { id: 8, name: '文学' }] })
const context = vm.createContext({
  props: { modelValue: true, bookId: 3 },
  targets: ref([]), libraries: ref([]), loading: ref(false), searching: ref(false),
  saving: ref(false), loadFailed: ref(false), generation: 0, searchGeneration: 0,
  crawlerApi: {
    importLibraries: (...args) => fetchIds(...args),
    setImportLibraries: async (id, ids) => {
      if (rejectSave) throw new Error('保存失败')
      saved.push({ id, ids: [...ids] })
    },
  },
  personalLibraries: {
    list: (...args) => fetchPage(...args),
    get: async id => ({ id, name: `图书馆 ${id}` }),
  },
  emit: (...args) => emitted.push(args),
  message: { success() {} },
})
vm.runInContext(ts.transpile(functions, { target: ts.ScriptTarget.ES2022 }), context)
const run = code => vm.runInContext(code, context)

await run('load()')
assert.deepEqual([...context.targets.value], [7, 30], 'restore server selection including targets outside first page')
assert.ok(context.libraries.value.some(item => item.id === 30))
await run('save()')
assert.deepEqual(saved[0], { id: 3, ids: [7, 30] })
assert.ok(emitted.some(([event, value]) => event === 'saved' && value === 3))

emitted.length = 0
rejectSave = true
await assert.rejects(run('save()'), /保存失败/)
assert.equal(context.saving.value, false)
assert.equal(emitted.length, 0, 'save failure keeps selection and dialog open for retry')
assert.deepEqual([...context.targets.value], [7, 30])
rejectSave = false
context.targets.value = []
await run('save()')
assert.deepEqual(saved.at(-1), { id: 3, ids: [] }, 'empty selection can cancel future additions')

fetchIds = async () => { throw new Error('读取失败') }
await run('load()')
assert.equal(context.loadFailed.value, true)
const previousSaves = saved.length
await run('save()')
assert.equal(saved.length, previousSaves, 'failed loading cannot overwrite persisted targets')

let release
fetchIds = () => new Promise(resolve => { release = resolve })
const oldLoad = run('load()')
context.props.bookId = 4
fetchIds = async () => [8]
await run('load()')
release([7])
await oldLoad
assert.deepEqual([...context.targets.value], [8], 'stale response from previous book cannot replace current selection')

const searches = []
fetchPage = () => new Promise(resolve => searches.push(resolve))
const firstSearch = run("searchLibraries('历史')")
const secondSearch = run("searchLibraries('文学')")
searches[1]({ content: [{ id: 9, name: '文学新馆' }] })
await secondSearch
searches[0]({ content: [{ id: 10, name: '过期结果' }] })
await firstSearch
assert.ok(context.libraries.value.some(item => item.id === 9))
assert.ok(!context.libraries.value.some(item => item.id === 10), 'out-of-order search response is ignored')
assert.ok(context.libraries.value.some(item => item.id === 8), 'search retains selected library labels')
console.log('采集目标图书馆：服务端恢复、多馆保存、清空、失败重试、加载失败保护及过期响应隔离通过')
