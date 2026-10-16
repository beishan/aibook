import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/DiscoveryChapterListDialog.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const events = []
const active = new Set([2])
const dataset = Array.from({length: 66}, (_, index) => ({id: index + 1}))
const props = {
  modelValue: true, initialBooks: dataset.filter(book => [1, 3, 4, 5, 6].includes(book.id)),
  initialSiteId: 7, isActive: book => active.has(book.id),
}
const calls = []
let openDialog
const candidates = async query => {
  calls.push(query)
  const filtered = dataset.filter(book => !query.excludedIds.includes(book.id))
  return {content: filtered.slice(query.page * query.size, (query.page + 1) * query.size), totalElements: filtered.length}
}
const context = vm.createContext({
  defineProps: () => props,
  defineEmits: () => (...args) => events.push(args),
  ref: value => ({value}),
  computed: fn => ({get value() { return fn() }}),
  watch: (_source, fn) => { openDialog = fn },
  message: {success() {}, error() {}, warning() {}},
  crawlerApi: {
    discoveryCandidates: candidates,
    batchChapterLists: async ids => { calls.push(ids); return ids.map(id => ({id: String(id)})) },
  },
})
vm.runInContext(ts.transpile(script.replace(/^import .*$/gm, ''), {target: ts.ScriptTarget.ES2022}), context)
const run = code => vm.runInContext(code, context)
openDialog(true)
await run('loadBooks()')
assert.equal(run('selected.value.length'), 5)
assert.equal(run('available.value.length'), 20)
assert.equal(run('total.value'), 61)
assert.equal(calls[0].siteId, 7)
assert.deepEqual(Array.from(calls[0].excludedIds), [1, 3, 4, 5, 6])
await run('addCurrentPage()')
assert.equal(run('selected.value.length'), 24)
assert.equal(run('available.value.length'), 20)
assert.equal(run('total.value'), 42)
assert.equal(run('selectedIds.value.has(2)'), false)
run('page.value = 2')
await run('loadBooks()')
await run('addCurrentPage()')
assert.equal(run('selected.value.length'), 44)
assert.equal(run('available.value.length'), 2)
await run('addCurrentPage()')
assert.equal(run('page.value'), 1)
assert.equal(run('available.value.length'), 20)
assert.equal(run('total.value'), 20)
const before = run('selected.value.length')
await run('add({id: 1})')
assert.equal(run('selected.value.length'), before)
await run('remove(1)')
assert.equal(run('total.value'), 21)
assert.equal(run('available.value[0].id'), 1)
await run('clearSelection()')
assert.equal(run('total.value'), 66)
assert.equal(run('available.value.length'), 20)
assert.equal(run('selected.value.length'), 0)
await run('add({id: 3})')
await run('submit()')
assert.deepEqual(Array.from(calls.at(-1)), [3])
assert.equal(events[0][0], 'created')
assert.deepEqual(events[1], ['update:modelValue', false])
context.crawlerApi.batchChapterLists = async () => { throw new Error('failed') }
const eventCount = events.length
await run('submit()')
assert.equal(events.length, eventCount)
assert.equal(run('submitting.value'), false)
assert.equal(run('selected.value.length'), 1)
let finishOld
context.crawlerApi.discoveryCandidates = () => new Promise(resolve => { finishOld = resolve })
const oldRequest = run('loadBooks()')
context.crawlerApi.discoveryCandidates = candidates
await run('clearSelection()')
finishOld({content: [{id: 999}], totalElements: 1})
await oldRequest
assert.equal(run('available.value[0].id'), 1)
context.crawlerApi.discoveryCandidates = async () => { throw new Error('network') }
await run('loadBooks()')
assert.equal(run('loadFailed.value'), true)
assert.equal(run('books.value.length'), 0)
console.log('章节选书：预选排除后分页、整页添加补位、末页回退、移除/清空补位、去重、任务禁选、提交及失败与过期响应验证通过')
