import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/CrawlerSiteAutoImportDialog.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('dialog.ts', script, ts.ScriptTarget.Latest, true)
const functions = ast.statements.filter(node => ts.isFunctionDeclaration(node))
  .map(node => node.getText(ast)).join('\n')
const form = { enabled: false, formats: ['STRUCTURED'], rules: [] }
const calls = []
const notices = []
const emitted = []
const ref = value => ({ value })
const configuration = { enabled: false, formats: ['STRUCTURED'], rules: [] }
const api = {
  siteAutoImport: async () => structuredClone(configuration),
  setSiteAutoImport: async (id, data) => calls.push({ id, data: JSON.parse(JSON.stringify(data)) }),
}
const context = vm.createContext({
  form, props: { site: { id: 7 }, modelValue: true },
  loading: ref(false), saving: ref(false), loadFailed: ref(false),
  ruleKeys: ref([]), expressionKeys: ref([]), requestId: 0, nextKey: 0,
  crawlerApi: api,
  message: { warning: text => notices.push(text), success: text => notices.push(text) },
  emit: (...args) => emitted.push(args),
})
vm.runInContext(ts.transpile(functions, { target: ts.ScriptTarget.ES2022 }), context)
const run = code => vm.runInContext(code, context)

await run('load()')
assert.equal(form.enabled, false)
assert.equal(context.loading.value, false)
await run('save()')
assert.equal(calls.at(-1).id, 7)
assert.equal(calls.at(-1).data.enabled, false)

calls.length = 0
form.enabled = true
await run('save()')
assert.equal(calls.length, 0, 'enabled rule-free configuration cannot be saved')
run('addRule(); addExpression(0)')
form.rules[0].expressions[0].values = ['神话', '传说']
context.expression = form.rules[0].expressions[1]
context.expression.type = 'CHAPTER_COUNT'
run('changeType(expression)')
context.expression.chapterCount = 10
run('addRule(); addExpression(1)')
form.rules[1].expressions[0].type = 'TAG'
form.rules[1].expressions[0].values = ['aaa']
Object.assign(form.rules[1].expressions[1], { type: 'CHAPTER_COUNT', values: [], comparison: 'GT', chapterCount: 20 })
await run('save()')
assert.equal(calls.length, 1)
assert.equal(calls[0].data.rules.length, 2)
assert.deepEqual(calls[0].data.rules[0].expressions[0].values, ['神话', '传说'])
assert.equal(calls[0].data.rules[0].expressions[1].chapterCount, 10)
assert.equal(calls[0].data.rules[1].expressions[1].chapterCount, 20)
assert.ok(emitted.some(([event, value]) => event === 'update:modelValue' && value === false))

calls.length = 0
run('removeExpression(0, 1); removeExpression(0, 0)')
await run('save()')
assert.equal(calls.length, 0, 'empty AND groups cannot accidentally match every book')
run('removeRule(0)')
assert.equal(context.ruleKeys.value.length, 1)
assert.equal(context.expressionKeys.value[0].length, 2)
form.formats = []
await run('save()')
assert.equal(calls.length, 0)
form.formats = ['STRUCTURED']

let finishOld
api.siteAutoImport = () => new Promise(resolve => { finishOld = resolve })
const oldRequest = run('load()')
context.props.site.id = 8
api.siteAutoImport = async () => ({ enabled: false, formats: ['EPUB'], rules: [] })
await run('load()')
finishOld({ enabled: true, formats: ['TXT'], rules: [] })
await oldRequest
assert.equal(form.enabled, false, 'late results from another site cannot overwrite current configuration')
assert.deepEqual(Array.from(form.formats), ['EPUB'])

api.siteAutoImport = async () => { throw new Error('load failure') }
await run('load()')
assert.equal(context.loadFailed.value, true)
await run('save()')
assert.equal(calls.length, 0, 'failed loading must never overwrite server rules with defaults')
api.siteAutoImport = async () => structuredClone(configuration)
await run('load()')
assert.equal(context.loadFailed.value, false)
api.setSiteAutoImport = async () => { throw new Error('save failure') }
await assert.rejects(run('save()'), /save failure/)
assert.equal(context.saving.value, false)
console.log('网站自动入库编辑器：规则组合、账户网站保存、空条件拦截、跨网站响应隔离、加载失败保护及失败重试通过')
