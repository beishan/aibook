import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/CrawlerTaskLogManagementPanel.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('logs.ts', script, ts.ScriptTarget.Latest, true)
const functions = ast.statements.filter(ts.isFunctionDeclaration).map(node => node.getText(ast)).join('\n')
const ref = value => ({ value })
const calls = []
const notices = []
let confirmed = false
let fail = false
const context = vm.createContext({
  base: '/api/crawler/task-logs', type: ref(''), keyword: ref(''), rows: ref([]), selected: ref([]),
  rules: ref([]), page: ref(1), total: ref(0), loading: ref(false), rulesLoading: ref(false),
  busy: ref(false), requestId: 0, types: [],
  api: {
    get: async (url, options) => {
      calls.push(['get', url, options])
      if (fail) throw new Error('offline')
      return { data: url.endsWith('/retention') ? [{ taskType: 'BOOK_FULL_CRAWL', retentionDays: 7, automatic: false }]
        : { content: [{ id: 1 }, { id: 2 }], totalElements: 2 } }
    },
    post: async (url, body) => {
      calls.push(['post', url, body])
      if (fail) throw new Error('offline')
      return { data: { affectedCount: body?.ids?.length || 0, deletedCount: 3 } }
    },
    put: async (url, body) => { calls.push(['put', url, body]); return { data: body } },
  },
  confirm: async () => confirmed,
  message: { success: text => notices.push(text), error: text => notices.push(text) },
})
vm.runInContext(ts.transpile(functions, { target: ts.ScriptTarget.ES2022 }), context)
const run = code => vm.runInContext(code, context)
await run('load()')
assert.equal(context.rows.value.length, 2)
await run('loadRules()')
assert.equal(context.rules.value[0].retentionDays, 7)
context.selected.value = [{ id: 1 }, { id: 2 }]
await run('retain(true)')
assert.deepEqual([...calls.find(call => call[1].endsWith('/retain'))[2].ids], [1, 2])
assert.equal(context.selected.value.length, 0)
context.selected.value = [{ id: 1 }]
await run('remove()')
assert.equal(calls.filter(call => call[1].endsWith('/delete')).length, 0, 'cancel never deletes')
assert.equal(context.busy.value, false)
confirmed = true
await run('remove()')
assert.equal(calls.filter(call => call[1].endsWith('/delete')).length, 1)
await run('saveRules()')
assert.equal(calls.filter(call => call[0] === 'put').length, 1)
confirmed = false
await run('cleanup()')
assert.equal(calls.filter(call => call[1].endsWith('/cleanup')).length, 0)
confirmed = true
await run('cleanup()')
assert.equal(calls.filter(call => call[1].endsWith('/cleanup')).length, 1)
fail = true
await run('load()')
assert.equal(context.rows.value.length, 0, 'failed refresh does not display stale rows')
assert.ok(notices.some(text => text.includes('加载失败')))
console.log('爬虫任务日志：批量保留、删除确认、策略保存、清理确认及加载失败处理通过')
