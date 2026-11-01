import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/BookQuickReader.vue', import.meta.url), 'utf8')
const preferences = await readFile(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
function declarations(code, names) {
  const ast = ts.createSourceFile('source.ts', code, ts.ScriptTarget.Latest, true)
  const found = new Map()
  function visit(node) {
    if (ts.isVariableStatement(node)) {
      for (const declaration of node.declarationList.declarations) {
        const name = declaration.name.getText(ast)
        if (names.includes(name)) found.set(name, node.getText(ast))
      }
    }
    ts.forEachChild(node, visit)
  }
  visit(ast)
  return names.map(name => {
    assert.ok(found.has(name), `missing ${name}`)
    return found.get(name)
  }).join('\n')
}
const stored = { value: null }
const saved = []
const context = vm.createContext({
  quickReaderWindow: stored,
  quickReaderSize: { value: { width: 800, height: 600, left: 20, top: 30 } },
  computed: fn => ({ get value() { return fn() } }),
  preferencesStore: { setQuickReaderWindow: (value, ...options) => {
    stored.value = value
    saved.push({ value, options })
  } },
})
const names = ['overlayTransparency', 'quickReaderMaskColor', 'saveQuickReaderWindow', 'setOverlayTransparency']
vm.runInContext(ts.transpile(declarations(script, names))
  + '\nglobalThis.mask = { overlayTransparency, quickReaderMaskColor, saveQuickReaderWindow, setOverlayTransparency }', context)
assert.equal(context.mask.overlayTransparency.value, 50)
assert.equal(context.mask.quickReaderMaskColor.value, 'rgba(0, 0, 0, 0.5)')
for (const [value, alpha] of [[0, 1], [35, 0.65], [100, 0]]) {
  context.mask.setOverlayTransparency({ target: { value: String(value) } })
  assert.equal(stored.value.overlayTransparency, value)
  assert.equal(context.mask.quickReaderMaskColor.value, `rgba(0, 0, 0, ${alpha})`)
  assert.equal(stored.value.width, 800)
}
context.mask.saveQuickReaderWindow()
assert.equal(stored.value.overlayTransparency, 100, 'moving/resizing must retain transparency')
assert.deepEqual(Array.from(saved.at(-1).options), [true, true], 'use account persistence with debounce')
const count = saved.length
for (const value of ['-1', '101', 'NaN', '0.5']) context.mask.setOverlayTransparency({ target: { value } })
assert.equal(saved.length, count)
stored.value = { width: 800, height: 600, left: 20, top: 30 }
assert.equal(context.mask.overlayTransparency.value, 50, 'older accounts retain the previous mask default')

const validation = vm.createContext({})
vm.runInContext(ts.transpile(declarations(preferences, ['isQuickReaderWindow']))
  + '\nglobalThis.validate = isQuickReaderWindow', validation)
const geometry = { width: 800, height: 600, left: 20, top: 30 }
assert.equal(validation.validate(geometry), true)
for (const value of [0, 50, 100]) assert.equal(validation.validate({ ...geometry, overlayTransparency: value }), true)
for (const value of [-1, 101, 0.5, '50', null, NaN]) assert.equal(validation.validate({ ...geometry, overlayTransparency: value }), false)

const watcher = script.match(/watch\(\[quickReaderFrame, quickReaderMaskColor\],[\s\S]*?\{ flush: 'post' \}\)/)[0]
const styles = []
const overlayContext = vm.createContext({
  quickReaderFrame: {}, quickReaderMaskColor: {},
  watch: (_refs, callback) => { overlayContext.updateMask = callback },
})
vm.runInContext(ts.transpile(watcher), overlayContext)
overlayContext.updateMask([null, 'rgba(0, 0, 0, 0)'])
overlayContext.updateMask([{ closest: selector => {
  assert.equal(selector, '.book-quick-reader-overlay')
  return { style: { setProperty: (...args) => styles.push(args) } }
} }, 'rgba(0, 0, 0, 0.35)'])
assert.deepEqual(styles, [['--el-overlay-color-lighter', 'rgba(0, 0, 0, 0.35)']])
const overlayCss = await readFile(new URL('../node_modules/element-plus/theme-chalk/el-overlay.css', import.meta.url), 'utf8')
assert.match(overlayCss, /background-color:\s*var\(--el-overlay-color-lighter\)/)
assert.match(source, /modal-class="book-quick-reader-overlay"/)
assert.match(source, /:close-on-click-modal="false"/)
assert.match(source, /@change="preferencesStore.flushQuickReaderWindow\(\)"/)
console.log('Quick reader mask: opacity bounds, live updates, per-overlay styling, geometry preservation and account persistence passed')
