import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
const notices = []
const requests = []
let fail = false
const api = {
  post: async (url, data, options) => {
    requests.push({ method: 'POST', url, data, options })
    if (fail) throw { response: { status: 409 } }
    return {}
  },
  delete: async (url, options) => { requests.push({ method: 'DELETE', url, options }); return {} },
}
const source = fs.readFileSync(new URL('../src/components/library/LibraryCoverEditor.vue', import.meta.url), 'utf8')
const compiled = compileScript(parse(source).descriptor, { id: 'library-cover-editor' }).content
const code = ts.transpileModule(compiled, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const module = { exports: {} }
new Function('require', 'module', 'exports', code)(name => {
  if (name === 'vue') return vue
  if (name === '@/utils/api') return { default: api }
  if (name === '@/utils/message') return { message: { success: text => notices.push(text), error: text => notices.push(text) } }
  return { default: {} }
}, module, module.exports)
const panel = module.exports.default
panel.render = () => null
const open = vue.ref(true)
let saved = 0
const library = { id: 7, revision: 3, config: { cover: 'arches', coverImage: 'old-image' } }
const renderer = vue.createRenderer({ insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}),
  createComment: () => ({}), setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {} })
const app = renderer.createApp({ render: () => vue.h(panel, { library, modelValue: open.value,
  'onUpdate:modelValue': value => { open.value = value }, onSaved: () => { saved++ } }) })
app.mount({})
const state = app._instance.subTree.component.setupState
const choose = file => state.choose({ target: { files: [file], value: 'chosen' } })
choose(new File(['x'], 'wrong.gif', { type: 'image/gif' }))
assert.equal(state.file, undefined)
choose(new File(['image'], 'cover.png', { type: 'image/png' }))
assert.ok(state.preview.startsWith('blob:'))
fail = true
await state.upload()
assert.equal(open.value, true)
assert.equal(state.file.name, 'cover.png', 'failed upload retains draft')
assert.ok(notices.at(-1).includes('刷新'))
fail = false
await state.upload()
await vue.nextTick()
assert.equal(open.value, false)
assert.equal(saved, 1)
assert.equal(requests.at(-1).url, '/api/libraries/7/cover')
assert.equal(requests.at(-1).options.params.revision, 3)
assert.equal(state.preview, '')
open.value = true
await vue.nextTick()
await state.restore()
assert.equal(requests.at(-1).method, 'DELETE')
assert.equal(saved, 2)
app.unmount()
console.log('Library cover editor: file validation, preview, revision, failed draft retention, upload, restore and URL cleanup passed')
