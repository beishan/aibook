import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = fs.readFileSync(new URL('../src/views/LibrariesView.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('libraries.ts', script, ts.ScriptTarget.Latest, true)
const calls = []
let fails = false
let loads = 0
const state = vm.createContext({
  draggingLibraryId: { value: null }, dropTargetId: { value: null },
  canReorder: { value: true }, savingOrder: { value: false },
  libraries: { value: [{ id: 1 }, { id: 2 }] },
  personalLibraries: { async order(...args) { calls.push(args); if (fails) throw Error('offline') } },
  async load() { loads++ },
  message: { success() {}, error() {} },
})
for (const name of ['clearLibraryDrag', 'startLibraryDrag', 'dragOverLibrary', 'dropLibrary']) {
  const node = ast.statements.find(item => ts.isFunctionDeclaration(item) && item.name.text === name)
  vm.runInContext(ts.transpile(node.getText(ast), { target: ts.ScriptTarget.ES2022 }), state)
}
const event = () => ({ prevented: false, preventDefault() { this.prevented = true },
  dataTransfer: { effectAllowed: '', dropEffect: '', setData() {} } })
state.startLibraryDrag(event(), 1)
const over = event()
state.dragOverLibrary(over, 2)
assert.equal(over.prevented, true)
assert.equal(state.dropTargetId.value, 2)
await state.dropLibrary(event(), 2)
assert.deepEqual(calls, [[1, 'libraries', 1, 2]])
assert.equal(loads, 1)
assert.equal(state.savingOrder.value, false)
assert.equal(state.draggingLibraryId.value, null)
assert.equal(state.dropTargetId.value, null)
await state.dropLibrary(event(), 2)
assert.equal(calls.length, 1, 'external drops cannot reorder')
state.startLibraryDrag(event(), 1)
await state.dropLibrary(event(), 1)
assert.equal(calls.length, 1, 'same-card drops do nothing')
state.startLibraryDrag(event(), 1)
state.canReorder.value = false
await state.dropLibrary(event(), 2)
assert.equal(calls.length, 1, 'mode changes or pending requests block drops')
const blocked = event()
state.startLibraryDrag(blocked, 1)
assert.equal(blocked.prevented, true)
state.canReorder.value = true
state.startLibraryDrag(event(), 1)
await state.dropLibrary(event(), 99)
assert.equal(calls.length, 1, 'stale or unknown target cards are ignored')
fails = true
state.startLibraryDrag(event(), 1)
await state.dropLibrary(event(), 2)
assert.equal(state.savingOrder.value, false)
assert.equal(loads, 1, 'failed requests do not claim refreshed order')
assert.equal(state.draggingLibraryId.value, null)
console.log('Library order: internal drag, save/refresh, same-card/external/stale drops, mode guards and failure cleanup passed')
