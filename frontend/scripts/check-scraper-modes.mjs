import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import ts from 'typescript'
import * as vue from 'vue'

const filename = new URL('../src/components/ScraperDialog.vue', import.meta.url)
const { descriptor } = parse(fs.readFileSync(filename, 'utf8'))
assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: filename.pathname, id: 'scrape-modes' }).errors, [])
const compiled = compileScript(descriptor, { id: 'scrape-modes' }).content
const code = ts.transpileModule(compiled, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText
const writes = []
const events = []
let finishAuto
const context = {
  exports: {},
  require(name) {
    if (name === 'vue') return vue
    if (name === '@/composables/useBookNameDisplay') return { useBookNameDisplay: () => ({ displayBookName: title => title || '' }) }
    if (name === '@element-plus/icons-vue') return { Loading: {} }
    if (name === '@/components/ScraperTrialDialog.vue') return { default: {} }
    if (name === '@/utils/scraper') return {
      scrapeBook: id => { writes.push(id); return new Promise(resolve => { finishAuto = resolve }) },
    }
    if (name === '@/utils/config') return { getScraperStatus: async () => [
      { configKey: 'jd', name: '京东', priority: 2, enabled: true },
      { configKey: 'douban', name: '豆瓣', priority: 1, enabled: true },
    ] }
    throw new Error(name)
  },
}
vm.runInNewContext(code, context)
const props = vue.reactive({ visible: false, book: { id: 7, title: '目标书籍' } })
const state = context.exports.default.setup(props, { expose() {}, emit: (...args) => events.push(args) })
props.visible = true
await vue.nextTick()
assert.equal(writes.length, 0, 'opening the mode chooser must not start scraping')
state.changeMode('manual')
for (let index = 0; index < 8; index++) await Promise.resolve()
assert.equal(state.selectedSource.value.configKey, 'douban')
assert.equal(writes.length, 0, 'choosing a manual source never invokes automatic save')
state.manualVisible.value = true
assert.equal(state.dialogVisible.value, false, 'entering the workbench hides the mode chooser')
state.dialogVisible.value = false
assert.equal(events.filter(([event]) => event === 'close').length, 0, 'hiding the chooser must not close the flow')
state.manualVisible.value = false
assert.equal(state.dialogVisible.value, true, 'returning from the workbench restores the mode chooser')

await state.switchModeByKeyboard({ key: 'Home', preventDefault() {}, currentTarget: { querySelector: () => ({ focus() {} }) } })
assert.equal(state.mode.value, 'auto')
const pending = state.handleScrape()
assert.equal(state.loading.value, true)
state.changeMode('manual')
assert.equal(state.mode.value, 'auto', 'cannot change modes during automatic save')
await state.handleScrape()
assert.deepEqual(writes, [7], 'prevent duplicate automatic requests')
finishAuto({ success: true, message: '已补全', updatedFields: ['author'] })
await pending
await vue.nextTick()
assert.equal(events.filter(([event]) => event === 'refresh').length, 1)
state.manualRunning.value = true
await vue.nextTick()
state.handleClose()
assert.equal(events.filter(([event]) => event === 'close').length, 0)
state.manualRunning.value = false
await vue.nextTick()
state.handleClose()
assert.equal(events.filter(([event]) => event === 'close').length, 1)
for (const path of ['../src/views/BooksView.vue', '../src/views/BookDetailView.vue', '../src/views/ShelfView.vue']) {
  const view = fs.readFileSync(new URL(path, import.meta.url), 'utf8')
  assert(!view.includes('startScrape('), 'page entry must only open the mode chooser')
  assert.match(view, /<ScraperDialog[\s\S]*?:book=/)
  assert.match(view, /@running=/)
}
console.log('Scraping modes: no writes on open, source selection, explicit automatic execution, busy guards, refresh and library/detail/shelf entries passed')
