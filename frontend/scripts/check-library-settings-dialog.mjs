import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'

const source = fs.readFileSync(new URL('../src/views/PersonalLibraryView.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
compileScript(descriptor, { id: 'settings-dialog-check', inlineTemplate: true })
const ast = ts.createSourceFile('library.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true)
const ref = value => ({ value })
let agrees = false
let initialized = 0
let saved = 0
const routes = []
const state = vm.createContext({
  settingsOpen: ref(false), settingsTab: ref('order'), library: ref({ id: 8 }),
  busy: ref(false), saving: ref(false), dirty: ref(false), id: ref(8),
  draft: ref({ name: '测试馆', config: {} }), conflict: ref(false),
  route: { meta: {} }, router: { async replace(path) { routes.push(path) } },
  async confirm() { return agrees },
  initializeDraft() { initialized++ },
  message: { success() {}, error() {} },
  personalLibraries: { async save() { saved++; throw { response: { status: 409 } } } },
})
for (const name of ['openSettings', 'closeSettings', 'allowLeave', 'beforeUnload', 'saveSettings']) {
  const node = ast.statements.find(item => ts.isFunctionDeclaration(item) && item.name.text === name)
  vm.runInContext(ts.transpile(node.getText(ast), { target: ts.ScriptTarget.ES2022 }), state)
}
state.openSettings()
assert.equal(state.settingsOpen.value, true)
assert.equal(state.settingsTab.value, 'basic')
assert.equal(initialized, 1)
state.settingsTab.value = 'appearance'
state.openSettings()
assert.equal(initialized, 1, 'opening an already open dialog must not reset edits')
state.dirty.value = true
await state.closeSettings()
assert.equal(state.settingsOpen.value, true, 'cancel preserves the dialog and draft')
assert.equal(await state.allowLeave(), false)
const event = { preventDefault() { this.prevented = true } }
state.beforeUnload(event)
assert.equal(event.prevented, true)
agrees = true
assert.equal(await state.allowLeave(), true)
state.saving.value = true
await state.closeSettings()
assert.equal(state.settingsOpen.value, true, 'cannot close while saving')
assert.equal(await state.allowLeave(), false)
state.saving.value = false
await state.saveSettings()
assert.equal(saved, 1)
assert.equal(state.conflict.value, true, 'conflicts preserve the edit session')
assert.equal(state.draft.value.name, '测试馆')
assert.equal(state.saving.value, false)
let done = 0
state.route.meta.libraryTab = 'settings'
await state.closeSettings(() => done++)
assert.equal(done, 1)
assert.equal(state.settingsOpen.value, false)
assert.deepEqual(routes, ['/libraries/8'], 'closing a legacy settings URL returns to the library')
for (const tab of ['basic', 'appearance', 'home', 'order', 'manage', 'preview']) {
  assert.ok(source.includes(`settingsTab === '${tab}'`), tab)
}
assert.ok(!source.includes("v-else-if=\"activeTab === 'settings'\""))
assert.ok(source.includes(':before-close="closeSettings"'))
console.log('Library settings dialog: six sections, draft retention, close/navigation/unload guards, save conflict and legacy URL handling passed')
