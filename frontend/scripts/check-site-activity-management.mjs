import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('crawler.ts', script, ts.ScriptTarget.Latest, true)
const names = new Set([
  'loadSiteActivities', 'loadSiteActivityPolicy', 'saveSiteActivityPolicy',
  'retainSiteActivities', 'deleteSiteActivities', 'clearSiteActivities',
])
const functions = ast.statements.filter(node => ts.isFunctionDeclaration(node) && names.has(node.name?.text))
  .map(node => node.getText(ast)).join('\n')
const ref = value => ({ value })
const calls = []
let confirmed = false
let fail = false
const context = vm.createContext({
  siteActivitySite: ref({ id: 1, siteName: '甲站' }),
  siteActivityPage: ref(1), siteActivityPageSize: ref(20), siteActivityEventType: ref(undefined),
  siteActivities: ref([]), siteActivityTotal: ref(0), selectedSiteActivities: ref([]),
  siteActivityLoading: ref(false), siteActivityBusy: ref(false), siteActivityRequestSequence: 0,
  siteActivityPolicy: ref({ retentionDays: 0, automatic: false }),
  siteActivityPolicyLoading: ref(false), siteActivityPolicyLoaded: ref(false), siteActivityPolicyRequestSequence: 0,
  crawlerApi: {
    siteActivities: async id => {
      calls.push(['list', id])
      if (fail) throw new Error('offline')
      return { content: [{ id: 2 }, { id: 3 }], totalElements: 2 }
    },
    siteActivityRetention: async id => { calls.push(['policy', id]); return { retentionDays: 7, automatic: true } },
    saveSiteActivityRetention: async (id, policy) => { calls.push(['save', id, policy]); return policy },
    retainSiteActivities: async (id, ids, retained) => { calls.push(['retain', id, ids, retained]); return { affectedCount: ids.length } },
    deleteSiteActivities: async (id, ids) => { calls.push(['delete', id, ids]); return { affectedCount: ids.length } },
    clearSiteActivities: async id => { calls.push(['clear', id]); return { deletedCount: 5 } },
  },
  confirm: async () => confirmed,
  message: { success() {}, error() {} },
})
vm.runInContext(ts.transpile(functions, { target: ts.ScriptTarget.ES2022 }), context)
const run = code => vm.runInContext(code, context)
await run('loadSiteActivities()')
await run('loadSiteActivityPolicy()')
assert.equal(context.siteActivityPolicy.value.retentionDays, 7)
context.selectedSiteActivities.value = [{ id: 2 }, { id: 3 }]
await run('retainSiteActivities(true)')
assert.deepEqual([...calls.find(call => call[0] === 'retain')[2]], [2, 3])
assert.equal(context.selectedSiteActivities.value.length, 0)
context.selectedSiteActivities.value = [{ id: 2 }]
await run('deleteSiteActivities()')
await run('clearSiteActivities()')
assert.equal(calls.filter(call => call[0] === 'delete' || call[0] === 'clear').length, 0)
confirmed = true
await run('deleteSiteActivities()')
await run('clearSiteActivities()')
await run('saveSiteActivityPolicy()')
assert.equal(calls.filter(call => call[0] === 'delete').length, 1)
assert.equal(calls.filter(call => call[0] === 'clear').length, 1)
assert.equal(calls.find(call => call[0] === 'save')[1], 1)
fail = true
await run('loadSiteActivities()')
assert.equal(context.siteActivities.value.length, 0)
console.log('网站行为记录：多选保留、删除及清理确认、策略保存与加载失败处理通过')
