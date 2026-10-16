import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = fs.readFileSync(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const extract = (name, next) => {
  const start = source.indexOf(`async function ${name}(`)
  const nextStart = source.indexOf(`function ${next}(`, start)
  const end = source.lastIndexOf('\n', nextStart)
  assert(start >= 0 && end > start)
  return ts.transpileModule(source.slice(start, end), {
    compilerOptions: { target: ts.ScriptTarget.ES2022 },
  }).outputText
}
const messages = []
const submitted = []
const submitting = []
let returnedTasks = []
let failure
let refreshes = 0
const context = vm.createContext({
  selectedDiscoveries: { value: [] },
  batchDiscoveryCrawling: { value: false },
  currentCrawlerTasks: { value: [{ id: 'old-task' }] },
  crawlerApi: {
    async batchCrawl(ids) {
      submitted.push(Array.from(ids))
      if (failure) throw failure
      return returnedTasks
    },
  },
  message: Object.fromEntries(['success', 'warning', 'error'].map(type =>
    [type, text => messages.push({ type, text })])),
  refreshAfterCrawlSubmission: () => refreshes++,
  isBookTaskActive: () => false,
  setBookTaskSubmitting: (id, busy) => submitting.push([id, busy]),
})
vm.runInContext([
  extract('batchCrawl', 'batchDiscovery'),
  extract('crawlDiscovered', 'refreshAfterCrawlSubmission'),
].join('\n'), context)

context.selectedDiscoveries.value = [{ id: 1 }, { id: 2 }, { id: 3 }]
returnedTasks = [{ id: 'new-2', bookId: 2 }, { id: 'new-3', bookId: 3 }]
await vm.runInContext('batchCrawl()', context)
assert.deepEqual(submitted.at(-1), [1, 2, 3], 'server decides which books have active tasks')
assert.deepEqual(messages.at(-1), { type: 'success', text: '成功创建 2 个采集任务，跳过 1 本书籍' })
assert.deepEqual(Array.from(context.currentCrawlerTasks.value, task => task.id),
  ['new-2', 'new-3', 'old-task'])
assert.equal(context.selectedDiscoveries.value.length, 0)
assert.equal(context.batchDiscoveryCrawling.value, false)

context.selectedDiscoveries.value = [{ id: 1 }, { id: 2 }]
returnedTasks = []
await vm.runInContext('batchCrawl()', context)
assert.deepEqual(messages.at(-1), { type: 'warning', text: '成功创建 0 个采集任务，跳过 2 本书籍' })
assert.equal(refreshes, 2, 'all-skipped submissions still refresh task states')

context.selectedDiscoveries.value = [{ id: 1 }, { id: 1 }, { id: 2 }]
returnedTasks = [{ id: 'new-2', bookId: 2 }]
await vm.runInContext('batchCrawl()', context)
assert.equal(messages.at(-1).text, '成功创建 1 个采集任务，跳过 1 本书籍',
  'duplicate selection IDs must not inflate skipped counts')

context.selectedDiscoveries.value = [{ id: 4 }]
returnedTasks = [{ id: 'new-4', bookId: 4 }]
await vm.runInContext('batchCrawl()', context)
assert.deepEqual(messages.at(-1), { type: 'success', text: '成功创建 1 个采集任务' })

context.selectedDiscoveries.value = [{ id: 5 }]
failure = { response: { data: { message: '采集书籍不存在' } } }
const refreshesBeforeError = refreshes
await vm.runInContext('batchCrawl()', context)
assert.deepEqual(messages.at(-1), { type: 'error', text: '采集书籍不存在' })
assert.equal(context.selectedDiscoveries.value.length, 1, 'errors preserve selection for retry')
assert.equal(context.batchDiscoveryCrawling.value, false)
assert.equal(refreshes, refreshesBeforeError)

failure = undefined
returnedTasks = []
await vm.runInContext('crawlDiscovered({ id: 6 })', context)
assert.equal(messages.at(-1).type, 'warning', 'single-book skip must not claim success')
assert.deepEqual(submitting.slice(-2), [[6, true], [6, false]])

console.log('Batch crawl: partial/all skips, actual success counts, unique selection, errors, refresh and single-book skips passed')
