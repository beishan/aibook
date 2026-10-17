import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const functions = source.slice(source.indexOf('async function loadTasks('),
  source.indexOf('function handleTaskSelectionChange('))
const calls = []
const ref = value => ({ value })
const context = {
  taskKeyword: ref(' 三体 '),
  taskStatusFilter: ref('FAILED'),
  taskTypeFilter: ref('BOOK_CONTENT'),
  taskFavoriteOnly: ref(true),
  taskPage: ref(4),
  taskPageSize: ref(20),
  taskLoading: ref(false),
  selectedTasks: ref([{ id: 'old' }]),
  tasks: ref([]),
  taskTotal: ref(0),
  crawlerApi: {
    async tasks(params) {
      calls.push(params)
      return { content: [{ id: 'matched' }], totalElements: 41 }
    },
  },
}
vm.createContext(context)
vm.runInContext(functions.replace('options:LoadOptions={}', 'options={}'), context)

await context.applyTaskFilters()
assert.equal(context.taskPage.value, 1)
assert.equal(calls[0].keyword, '三体')
assert.equal(calls[0].status, 'FAILED')
assert.equal(calls[0].type, 'BOOK_CONTENT')
assert.equal(calls[0].favoriteOnly, true)
assert.equal(context.taskTotal.value, 41)
assert.equal(context.selectedTasks.value.length, 0)
assert.equal(context.taskLoading.value, false)

context.taskPage.value = 2
await context.loadTasks({ silent: true })
assert.equal(calls.at(-1).page, 1)
assert.equal(calls.at(-1).keyword, '三体')

context.taskKeyword.value = ''
await context.applyTaskFilters()
assert.equal(calls.at(-1).keyword, undefined)
assert.equal(calls.at(-1).status, 'FAILED')

context.taskKeyword.value = '作者'
await context.clearTaskFilters()
assert.equal(context.taskKeyword.value, '')
for (const key of ['keyword', 'status', 'type', 'favoriteOnly']) {
  assert.equal(calls.at(-1)[key], undefined)
}
assert.equal(context.taskPage.value, 1)
assert.match(source, /v-model="taskKeyword"[\s\S]*?@keyup.enter="applyTaskFilters"[\s\S]*?@clear="applyTaskFilters"/)
assert.match(source, /@click="applyTaskFilters">查询/)
console.log('Crawler task search, combined filters, pagination, polling and clearing passed')
