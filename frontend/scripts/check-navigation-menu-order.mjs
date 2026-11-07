import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
import { ref } from 'vue'
const source = fs.readFileSync(new URL('../src/utils/navigationMenu.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const { defaultNavigationMenuOrder, normalizeNavigationMenuOrder, sortNavigationMenu } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
const defaults = defaultNavigationMenuOrder()
assert.deepEqual(normalizeNavigationMenuOrder(['/settings', '/bad', '/settings']), ['/settings', ...defaults.filter(path => path !== '/settings')])
const reversed = [...defaults].reverse()
const input = [{ path: '/books' }, { path: '/rewrite' }, { path: '/text-repair' }, { path: '/format-conversion' }, { path: '/settings' }]
assert.deepEqual(sortNavigationMenu(input, reversed).map(item => item.path), ['/settings', '/rewrite', '/text-repair', '/format-conversion', '/books'])
assert.equal(input[0].path, '/books', 'sorting must not mutate the source')
const preferences = fs.readFileSync(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8')
const saveSource = preferences.slice(preferences.indexOf('  const saveNavigationMenuOrder ='), preferences.indexOf('  const setBookDisplaySettingsTab ='))
let token = 'account-a'
let fail = false
let release
const requests = []
const navigationMenuOrder = ref(defaults)
const save = new Function('api', 'navigationMenuOrder', 'normalizeNavigationMenuOrder', 'localStorage', ts.transpile(saveSource + '\nreturn saveNavigationMenuOrder;'))({
  put: async (path, data) => {
    requests.push({ path, data })
    if (fail) throw new Error('save failed')
    if (release === true) await new Promise(resolve => { release = resolve })
  },
}, navigationMenuOrder, normalizeNavigationMenuOrder, { getItem: () => token })
await save(reversed)
assert.deepEqual(navigationMenuOrder.value, reversed)
assert.equal(requests[0].path, '/api/user/preferences')
fail = true
await assert.rejects(() => save(defaults))
assert.deepEqual(navigationMenuOrder.value, reversed)
fail = false
release = true
const pending = save(defaults)
token = 'account-b'
release()
await pending
assert.deepEqual(navigationMenuOrder.value, reversed, 'old account save cannot change current navigation')
console.log('Navigation order: normalization, grouped tools, stable source, persistence, failed save and account switch passed')

const panel = fs.readFileSync(new URL('../src/components/NavigationMenuSettingsPanel.vue', import.meta.url), 'utf8')
const script = panel.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('panel.ts', script, ts.ScriptTarget.Latest, true)
const selected = new Set(['startDrag', 'dragMove', 'endDrag', 'cancelDrag'])
const functions = ast.statements.filter(node => ts.isFunctionDeclaration(node) && selected.has(node.name.text))
  .map(node => node.getText(ast)).join('\n')
const draft = ref(['/a', '/b', '/c'])
const dragPath = ref(null)
let targetPath = '/c'
const documentStub = { elementFromPoint: () => ({ closest: () => ({ dataset: { path: targetPath } }) }) }
const drag = new Function('draft', 'dragPath', 'saving', 'document', `
  let dragPointer = null, originalOrder = [];
  ${ts.transpile(functions, { target: ts.ScriptTarget.ES2022 })}
  return { startDrag, dragMove, endDrag, cancelDrag };
`)(draft, dragPath, ref(false), documentStub)
const event = { button: 0, pointerId: 1, clientX: 5, clientY: 5, preventDefault() {}, currentTarget: { closest: () => ({ setPointerCapture() {} }) } }
drag.startDrag(event, '/a')
drag.dragMove(event)
assert.deepEqual(draft.value, ['/b', '/c', '/a'])
drag.cancelDrag(event)
assert.deepEqual(draft.value, ['/a', '/b', '/c'], 'cancel restores the original order')
drag.startDrag(event, '/a')
targetPath = '/b'
drag.dragMove(event)
drag.endDrag(event)
drag.cancelDrag(event)
assert.deepEqual(draft.value, ['/b', '/a', '/c'], 'pointer release keeps the order despite lost capture')
assert.equal(dragPath.value, null)
console.log('Navigation drag: pointer reorder, cancellation and release passed')
