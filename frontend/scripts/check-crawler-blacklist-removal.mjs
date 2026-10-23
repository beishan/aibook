import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const view = fs.readFileSync(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const source = view.slice(view.indexOf('async function loadBlacklistBooks()'),
  view.indexOf('async function loadDiscoveredBooks('))
const ref = value => ({ value })
let statusError, refreshError, releaseStatus
let pages = [], changes = [], messages = [], refreshes = 0
const context = vm.createContext({
  blacklistRequest: 0,
  blacklistLoading: ref(false), blacklistError: ref(''), blacklistRemovingId: ref(null),
  blacklistBooks: ref([]), blacklistKeyword: ref(' 测试 '), blacklistSiteId: ref(7),
  blacklistPage: ref(2), blacklistPageSize: ref(20), blacklistTotal: ref(21),
  crawlerApi: {
    async setDiscoveryStatus(ids, status) {
      changes.push({ ids: Array.from(ids), status })
      if (releaseStatus) await new Promise(resolve => { releaseStatus = resolve })
      if (statusError) throw statusError
    },
    async blacklistBooks(params) {
      pages.push({ ...params })
      return { content: params.page === 0 ? [{ id: 1 }] : [], totalElements: 20 }
    },
  },
  async refresh() { refreshes++; if (refreshError) throw refreshError },
  message: Object.fromEntries(['success', 'error', 'warning'].map(kind =>
    [kind, text => messages.push({ kind, text })])),
})
vm.runInContext(ts.transpile(source), context)

// Restores ACTIVE only, preserves filters and backs up from an empty last page.
await context.removeFromBlacklist({ id: 21 })
assert.deepEqual(changes, [{ ids: [21], status: 'ACTIVE' }])
assert.deepEqual(pages.map(p => p.page), [1, 0])
assert(pages.every(p => p.keyword === '测试' && p.siteId === 7))
assert.equal(context.blacklistPage.value, 1)
assert.equal(context.blacklistBooks.value[0].id, 1)
assert.equal(context.blacklistRemovingId.value, null)
assert.equal(refreshes, 1)

// A rejected mutation leaves the row and pagination intact and skips refresh.
statusError = { response: { data: { message: '无权操作' } } }
pages = []; messages = []
await context.removeFromBlacklist({ id: 1 })
assert.equal(pages.length, 0)
assert.equal(refreshes, 1)
assert.equal(context.blacklistBooks.value[0].id, 1)
assert.equal(messages[0].text, '无权操作')
assert.equal(context.blacklistRemovingId.value, null)

// Duplicate clicks are ignored while the request is pending.
statusError = null; releaseStatus = true
const pending = context.removeFromBlacklist({ id: 1 })
const count = changes.length
await context.removeFromBlacklist({ id: 1 })
assert.equal(changes.length, count)
const release = releaseStatus; releaseStatus = null; release()
await pending

// A refresh error after success must not report that the removal failed.
refreshError = new Error('offline'); messages = []
await context.removeFromBlacklist({ id: 1 })
assert.deepEqual(messages.map(m => m.kind), ['success', 'warning'])
assert.equal(context.blacklistRemovingId.value, null)
assert.match(view, /@click="removeFromBlacklist\(row\)"/)
console.log('Blacklist removal: restore, pagination, filters, rejection, duplicate clicks and refresh failure passed')
