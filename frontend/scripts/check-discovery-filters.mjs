import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const view = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
function extract(name) {
  const start = view.indexOf(`async function ${name}(`)
  const open = view.indexOf('{', view.indexOf(')', start))
  let depth = 1
  let end = open + 1
  for (; depth; end++) {
    if (view[end] === '{') depth++
    if (view[end] === '}') depth--
  }
  return ts.transpile(view.slice(start, end), {target:ts.ScriptTarget.ES2022})
}
const calls = []
const ref = value => ({value})
const context = vm.createContext({
  discoveryBooksRequest:0,
  discoveryPage:ref(3), discoveryPageSize:ref(20), discoveryKeyword:ref(' 三体 '),
  discoverySiteId:ref(7), discoveryCategory:ref('科幻'), discoveryTag:ref('太空'),
  discoveryFavoriteOnly:ref(true), discoverySort:ref('BOOK_NAME_ASC'),
  discoveryLoading:ref(false), selectedDiscoveries:ref([{id:1}]),
  discoveredBooks:ref([]), discoveredTotal:ref(0),
  crawlerApi:{discoveredBooks:async params => {
    calls.push(params)
    return {content:[{id:9}], totalElements:65}
  }},
})
vm.runInContext([
  extract('loadDiscoveredBooks'), extract('applyDiscoveryFilters'), extract('resetDiscoveryFilters'),
].join('\n'), context)
await vm.runInContext('applyDiscoveryFilters()', context)
assert.equal(calls[0].page, 0)
assert.equal(calls[0].category, '科幻')
assert.equal(calls[0].tag, '太空')
assert.equal(calls[0].siteId, 7)
assert.equal(calls[0].keyword, '三体')
assert.equal(calls[0].favoriteOnly, true)
assert.equal(context.selectedDiscoveries.value.length, 0)
await vm.runInContext('resetDiscoveryFilters()', context)
assert.equal(calls[1].category, undefined)
assert.equal(calls[1].tag, undefined)
assert.equal(calls[1].siteId, undefined)
assert.equal(calls[1].favoriteOnly, undefined)
let finishOld
context.crawlerApi.discoveredBooks = () => new Promise(resolve => { finishOld = resolve })
const oldRequest = vm.runInContext('loadDiscoveredBooks()', context)
context.crawlerApi.discoveredBooks = async () => ({content:[{id:10}], totalElements:1})
await vm.runInContext('loadDiscoveredBooks()', context)
finishOld({content:[{id:11}], totalElements:1})
await oldRequest
assert.equal(context.discoveredBooks.value[0].id, 10)
assert.equal(context.discoveryLoading.value, false)
console.log('发现书籍分类、标签组合筛选、重置、分页与过期请求验证通过')

const filterContext = vm.createContext({
  discoveryFilterRequest: 0,
  discoveryFilterSiteId: undefined,
  discoverySiteId: ref(undefined),
  discoveryFilterOptions: ref({categories: [], tags: ['旧标签']}),
  discoveryFiltersLoading: ref(false),
  discoveryFiltersError: ref(false),
  ElMessage: {error: () => {}},
  crawlerApi: {},
})
vm.runInContext(extract('loadDiscoveryFilterOptions'), filterContext)
let finishFilters
filterContext.crawlerApi.discoveryFilters = () => new Promise(resolve => { finishFilters = resolve })
const loadingFilters = vm.runInContext('loadDiscoveryFilterOptions(true)', filterContext)
assert.equal(filterContext.discoveryFiltersLoading.value, true)
assert.equal(filterContext.discoveryFilterOptions.value.tags[0], '旧标签')
finishFilters({categories: ['科幻'], tags: ['太空']})
await loadingFilters
assert.equal(filterContext.discoveryFiltersLoading.value, false)
assert.equal(filterContext.discoveryFilterOptions.value.tags[0], '太空')
const staleFilters = vm.runInContext('loadDiscoveryFilterOptions(true)', filterContext)
filterContext.discoverySiteId.value = 7
filterContext.crawlerApi.discoveryFilters = async siteId => {
  assert.equal(siteId, 7)
  return {categories: [], tags: ['新网站标签']}
}
await vm.runInContext('loadDiscoveryFilterOptions(true)', filterContext)
finishFilters({categories: [], tags: ['过期标签']})
await staleFilters
assert.equal(filterContext.discoveryFilterOptions.value.tags[0], '新网站标签')
filterContext.crawlerApi.discoveryFilters = async () => { throw new Error('network') }
await vm.runInContext('loadDiscoveryFilterOptions(true)', filterContext)
assert.equal(filterContext.discoveryFiltersError.value, true)
assert.equal(filterContext.discoveryFiltersLoading.value, false)
filterContext.crawlerApi.discoveryFilters = async () => ({categories: [], tags: []})
await vm.runInContext('loadDiscoveryFilterOptions(true)', filterContext)
assert.equal(filterContext.discoveryFiltersError.value, false)
assert.equal(filterContext.discoveryFilterOptions.value.tags.length, 0)
console.log('筛选选项加载状态、保留已有标签、网站切换、过期响应与失败重试验证通过')
