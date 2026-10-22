import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/useViewportTableHeight.ts', import.meta.url), 'utf8')
const view = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const hooks = {}
const listeners = new Map()
const viewportListeners = new Map()
let pendingFrame
let observed = []
let top = 250
let paginationHeight = 52
let tableVisible = true
const ancestor = { parentElement: null, scrollTop: 0, paddingBottom: '90px' }
const table = { getBoundingClientRect: () => ({ top }) }
const pagination = { getBoundingClientRect: () => ({ height: paginationHeight }) }
const controls = { classList: { contains: () => false } }
const panel = {
  parentElement: ancestor,
  paddingBottom: '24px',
  children: [controls],
  querySelector: selector => selector.includes('.data-table')
    ? (tableVisible ? table : null) : pagination,
}
const viewport = {
  height: 900,
  addEventListener: (event, callback) => viewportListeners.set(event, callback),
  removeEventListener: event => viewportListeners.delete(event),
}
const windowMock = {
  innerHeight: 900,
  scrollY: 0,
  visualViewport: viewport,
  requestAnimationFrame: callback => { pendingFrame = callback; return 1 },
  cancelAnimationFrame: () => { pendingFrame = undefined },
  addEventListener: (event, callback) => listeners.set(event, callback),
  removeEventListener: event => listeners.delete(event),
}
const context = vm.createContext({
  window: windowMock,
  document: { body: {}, documentElement: {} },
  getComputedStyle: element => element,
  ref: value => ({ value }),
  watch: (_ref, callback) => { hooks.watch = callback },
  onMounted: callback => { hooks.mount = callback },
  onUpdated: callback => { hooks.update = callback },
  onBeforeUnmount: callback => { hooks.unmount = callback },
  ResizeObserver: class {
    constructor(callback) { hooks.resize = callback }
    observe(element) { observed.push(element) }
    disconnect() { observed = [] }
  },
})
const code = ts.transpile(source.replace(/^import .*from 'vue'\r?\n/, '').replace('export function', 'function'))
vm.runInContext(`${code}\nvar result = useViewportTableHeight()`, context)
const { tablePanelRef, tableMaxHeight } = context.result
const flush = () => { const callback = pendingFrame; pendingFrame = undefined; callback?.() }
tablePanelRef.value = panel
hooks.mount()
flush()
assert.equal(tableMaxHeight.value, 484, 'reserves pagination, panel padding and dock space')
assert.ok(observed.includes(controls), 'observes wrapping filters and batch controls')

viewport.height = 700
viewportListeners.get('resize')()
flush()
assert.equal(tableMaxHeight.value, 284, 'adapts to visible viewport changes')
top = 310
paginationHeight = 80
hooks.resize()
flush()
assert.equal(tableMaxHeight.value, 196, 'adapts to expanded controls and wrapped pagination')

windowMock.scrollY = 100
ancestor.scrollTop = 50
top = 160
hooks.update()
flush()
assert.equal(tableMaxHeight.value, 196, 'page and container scrolling do not grow the table')

viewport.height = 300
hooks.resize()
flush()
assert.equal(tableMaxHeight.value, 120, 'short screens retain a usable scrollable table')
windowMock.visualViewport = undefined
windowMock.innerHeight = 800
listeners.get('resize')()
flush()
assert.equal(tableMaxHeight.value, 296, 'falls back to browser height without Visual Viewport API')
windowMock.visualViewport = viewport
tableVisible = false
hooks.update()
flush()
assert.equal(tableMaxHeight.value, 296, 'card views do not participate in table sizing')
hooks.unmount()
assert.equal(observed.length, 0)
assert.equal(listeners.size, 0)
assert.equal(viewportListeners.size, 0)
assert.equal(pendingFrame, undefined)

// Exercise the log panel's native table and pagination selectors with the same sizing lifecycle.
const logView = await readFile(new URL('../src/components/OperationLogPanel.vue', import.meta.url), 'utf8')
const selectors = []
panel.querySelector = selector => {
  selectors.push(selector)
  return selector.includes('.log-table-wrap') ? table : pagination
}
top = 250
windowMock.scrollY = 0
ancestor.scrollTop = 0
viewport.height = 900
paginationHeight = 72
vm.runInContext(`var logResult = useViewportTableHeight({
  tableSelector: ':scope > .log-table-wrap',
  paginationSelector: ':scope > .log-pagination'
})`, context)
context.logResult.tablePanelRef.value = panel
hooks.mount()
flush()
assert.equal(context.logResult.tableMaxHeight.value, 464, 'log table reserves its pagination')
viewport.height = 700
viewportListeners.get('resize')()
flush()
assert.equal(context.logResult.tableMaxHeight.value, 264, 'log table shrinks with the browser')
assert(selectors.includes(':scope > .log-table-wrap'))
assert(selectors.includes(':scope > .log-pagination'))
assert(logView.includes('ref="tablePanelRef"'))
assert(logView.includes('maxHeight: `${tableMaxHeight}px`'))
hooks.unmount()

for (const tab of ['discovered', 'books']) {
  const section = view.split(`activeTab === '${tab}'" ref="tablePanelRef"`)[1]?.split('</section>')[0]
  assert.ok(section?.includes(':max-height="tableMaxHeight"'), `${tab} table uses the viewport limit`)
}
console.log('Viewport table height checks passed')
