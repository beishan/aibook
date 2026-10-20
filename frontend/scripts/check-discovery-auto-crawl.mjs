import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
function extract(name) {
  const start = source.search(new RegExp(`(?:async )?function ${name}\\(`))
  assert.ok(start >= 0)
  const open = source.indexOf('{', source.indexOf(')', start))
  let depth = 1
  let end = open + 1
  for (; depth; end++) {
    if (source[end] === '{') depth++
    if (source[end] === '}') depth--
  }
  return ts.transpile(source.slice(start, end), {target: ts.ScriptTarget.ES2022})
}
const ref = value => ({value})
const calls = []
const context = vm.createContext({
  discoveryPageSite: ref(), editingDiscoveryPage: ref(), discoveryPageDialog: ref(false),
  discoveryPageForm: {}, saving: ref(false),
  discoveryPagesBySite: ref({7: [{id: 9}]}),
  loadDiscoveryPages: async () => {},
  message: {success() {}, warning() {}},
  crawlerApi: {
    updateDiscoveryPage: async (id, payload) => {
      calls.push({id, payload: {...payload}})
      return {id, ...payload}
    },
    createDiscoveryPage: async (id, payload) => calls.push({id, payload: {...payload}}),
  },
})
vm.runInContext(['openDiscoveryPage', 'toggleDiscoveryPageAuto', 'saveDiscoveryPage'].map(extract).join('\n'), context)
const site = {id: 7, baseUrl: 'https://example.com'}
context.site = site
context.page = {id: 9, siteId: 7, pageName: '热门', pageUrl: 'https://example.com/hot', autoScanEnabled: false, autoCrawlEnabled: true, scanIntervalMinutes: 360, maxPages: 50}
vm.runInContext('openDiscoveryPage(site, page)', context)
assert.equal(context.discoveryPageForm.autoCrawlEnabled, true)
await vm.runInContext('saveDiscoveryPage()', context)
assert.equal(calls.at(-1).payload.autoCrawlEnabled, true)
await vm.runInContext('toggleDiscoveryPageAuto(page, true)', context)
assert.equal(calls.at(-1).payload.autoScanEnabled, true)
assert.equal(calls.at(-1).payload.autoCrawlEnabled, true)
vm.runInContext('openDiscoveryPage(site)', context)
assert.equal(context.discoveryPageForm.autoCrawlEnabled, false)
context.discoveryPageForm.pageName = '新发现页'
context.discoveryPageForm.autoCrawlEnabled = true
await vm.runInContext('saveDiscoveryPage()', context)
assert.equal(calls.at(-1).payload.autoCrawlEnabled, true)
context.page.autoCrawlEnabled = undefined
vm.runInContext('openDiscoveryPage(site, page)', context)
assert.equal(context.discoveryPageForm.autoCrawlEnabled, false)
assert.match(source, /<el-form-item label="自动采集">\s*<el-switch v-model="discoveryPageForm.autoCrawlEnabled" aria-label="自动采集"/)
console.log('发现页自动采集：默认关闭、历史数据兼容、新增/编辑保存、自动扫描切换保留设置验证通过')
