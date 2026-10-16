import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/DiscoveryChapterListDialog.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const events = []
const active = new Set([2])
const props = {modelValue: true, initialBooks: [], initialSiteId: 7, isActive: book => active.has(book.id)}
const calls = []
let openDialog
const context = vm.createContext({
  defineProps: () => props,
  defineEmits: () => (...args) => events.push(args),
  ref: value => ({value}),
  computed: fn => ({get value() { return fn() }}),
  watch: (_source, fn) => { openDialog = fn },
  message: {success() {}, error() {}, warning() {}},
  crawlerApi: {
    discoveredBooks: async query => {
      calls.push(query)
      return {content: [{id: query.page === 0 ? 1 : 3}, {id: 2}], totalElements: 40}
    },
    batchChapterLists: async ids => { calls.push(ids); return ids.map(id => ({id: String(id)})) },
  },
})
vm.runInContext(ts.transpile(script.replace(/^import .*$/gm, ''), {target: ts.ScriptTarget.ES2022}), context)
openDialog(true)
await vm.runInContext('loadBooks()', context)
vm.runInContext('addCurrentPage()', context)
assert.equal(vm.runInContext('selected.value.length', context), 1)
vm.runInContext('page.value = 2', context)
await vm.runInContext('loadBooks()', context)
vm.runInContext('addCurrentPage(); add({id: 1})', context)
assert.equal(vm.runInContext('selected.value.length', context), 2)
assert.equal(calls[0].siteId, 7)
vm.runInContext('remove(1)', context)
await vm.runInContext('submit()', context)
assert.deepEqual(Array.from(calls.at(-1)), [3])
assert.equal(events[0][0], 'created')
assert.deepEqual(events[1], ['update:modelValue', false])
context.crawlerApi.batchChapterLists = async () => { throw new Error('failed') }
vm.runInContext('add({id: 4})', context)
const eventCount = events.length
await vm.runInContext('submit()', context)
assert.equal(events.length, eventCount)
assert.equal(vm.runInContext('submitting.value', context), false)
assert.equal(vm.runInContext('selected.value.length', context), 2)
context.crawlerApi.discoveredBooks = async () => { throw new Error('network') }
await vm.runInContext('loadBooks()', context)
assert.equal(vm.runInContext('loadFailed.value', context), true)
assert.equal(vm.runInContext('books.value.length', context), 0)
console.log('章节信息选书：跨页选择、去重、活动任务禁选、移除、批量提交、失败保留与加载错误验证通过')
