import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/CrawlerSiteAutoBlacklistDialog.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('dialog.ts', script, ts.ScriptTarget.Latest, true)
const functions = ast.statements.filter(ts.isFunctionDeclaration).map(node => node.getText(ast)).join('\n')
const ref = value => ({ value })
let server = { enabled: true, keywords: ['历史', 'myth'], tags: ['传说', 'aaa'] }
let get = async () => structuredClone(server)
let failSave = false
const writes = []
const emitted = []
const context = vm.createContext({
  props: { modelValue: true, site: { id: 7 } },
  form: { enabled: false, keywords: [], tags: [] },
  loading: ref(false), saving: ref(false), loadFailed: ref(false), requestId: 0,
  crawlerApi: {
    siteAutoBlacklist: id => get(id),
    setSiteAutoBlacklist: async (id, data) => {
      if (failSave) throw new Error('保存失败')
      server = JSON.parse(JSON.stringify(data))
      writes.push({ id, data: server })
    },
  },
  emit: (...args) => emitted.push(args),
  ElMessage: { success() {}, error() {}, warning() {} },
})
vm.runInContext(ts.transpile(functions, { target: ts.ScriptTarget.ES2022 }), context)
const run = code => vm.runInContext(code, context)
await run('load()')
assert.deepEqual(context.form, server, 'restore enabled, multiple keywords and tags from server')
await run('save()')
assert.equal(writes[0].id, 7)
assert.deepEqual(writes[0].data.tags, ['传说', 'aaa'])
assert.ok(emitted.some(([name, value]) => name === 'update:modelValue' && !value))

emitted.length = 0
failSave = true
await run('save()')
assert.equal(emitted.length, 0, 'failed save keeps dialog open for retry')
assert.equal(context.saving.value, false)
failSave = false
get = async () => { throw new Error('加载失败') }
await run('load()')
const count = writes.length
await run('save()')
assert.equal(context.loadFailed.value, true)
assert.equal(writes.length, count, 'failed load cannot overwrite server configuration')

let release
get = () => new Promise(resolve => { release = resolve })
const stale = run('load()')
context.props.site = { id: 8 }
get = async () => ({ enabled: false, keywords: ['新网站'], tags: [] })
await run('load()')
release({ enabled: true, keywords: ['旧网站'], tags: ['旧标签'] })
await stale
assert.deepEqual([...context.form.keywords], ['新网站'], 'late response cannot overwrite another site')
await run('save()')
assert.equal(writes.at(-1).id, 8)
assert.equal(writes.at(-1).data.enabled, false, 'disable setting is persisted')

context.form.keywords = ['x'.repeat(201)]
const validCount = writes.length
await run('save()')
assert.equal(writes.length, validCount, 'oversized value is rejected before save')
console.log('自动黑名单配置：多值恢复/保存、停用、失败保护、网站切换响应隔离及长度限制通过')
