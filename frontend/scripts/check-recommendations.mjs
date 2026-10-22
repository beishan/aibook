import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'

const require = createRequire(import.meta.url)
const { createRenderer, nextTick } = require('vue')
function evaluate(source, overrides) {
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const module = { exports: {} }
  new Function('require', 'module', 'exports', code)(
    name => Object.hasOwn(overrides, name) ? overrides[name] : require(name), module, module.exports,
  )
  return module.exports
}
const listeners = new Map()
const timers = new Map()
global.document = {
  visibilityState: 'visible',
  addEventListener: (event, fn) => listeners.set(event, fn),
  removeEventListener: event => listeners.delete(event),
}
global.window = {
  setInterval: fn => { timers.set(1, fn); return 1 },
  clearInterval: id => timers.delete(id),
}
const utility = evaluate(fs.readFileSync(new URL('../src/utils/recommendations.ts', import.meta.url), 'utf8'), {
  '@/utils/api': { default: {} },
})
const original = { ...utility.emptyRecommendationRule(), id: 1, name: '每日随缘' }
let currentRules = [original]
let todayDate = utility.recommendationDate()
let calls = [], accepted = false, fail = false, delayedPreview
const fakeApi = {
  async today() {
    calls.push('today')
    if (fail) throw new Error('network')
    return { date: todayDate, groups: currentRules.filter(r => r.enabled).map(rule => ({
      rule, date: todayDate, generatedAt: `${todayDate}T09:00:00`,
      candidateCount: 8, repeatedCount: 0, invalidCount: 0,
      books: [{ id: 7, title: '测试推荐', author: '作者', coverUrl: null, readingStatus: 'UNREADING', format: 'epub' }],
    })) }
  },
  async rules() { return currentRules },
  async options() { return { categories: [], tags: [], authors: [], shelf_groups: [] } },
  async save(rule) { calls.push('save'); currentRules = [{ ...rule, id: 1 }] },
  async delete() { calls.push('delete'); currentRules = [] },
  async reroll() { calls.push('reroll') },
  async preview() { return delayedPreview || 8 },
}
const notices = []
const source = fs.readFileSync(new URL('../src/views/RecommendationsView.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const compiled = compileScript(descriptor, { id: 'recommendations-check' })
const panel = evaluate(compiled.content, {
  '@/utils/recommendations': { ...utility, recommendationApi: fakeApi },
  '@/utils/message': { confirm: async () => accepted, message: {
    error: value => notices.push(value), success: value => notices.push(value),
  } },
  '@/utils/cover': { getCoverThumbnailUrl: value => value },
  '@/utils/imagePrivacy': { shouldLoadBookCover: () => false },
}).default
panel.render = () => null
const renderer = createRenderer({
  insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {},
})
const app = renderer.createApp(panel)
app.mount({})
const state = app._instance.setupState
async function flush() { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
await flush()
assert.equal(state.result.groups.length, 1)
assert.equal(timers.size, 1)
await state.load()
assert(!calls.includes('reroll'), 'refresh must never reroll')
state.edit(state.rules[0], true)
assert.equal(state.draft.id, undefined)
assert(state.draft.name.includes('副本'))
state.draft.filter.count = 2
assert.equal(original.filter.count, 6, 'editing a copy cannot mutate the source rule')
let resolvePreview
delayedPreview = new Promise(resolve => { resolvePreview = resolve })
const preview = state.preview()
state.draft.name = '新条件'
resolvePreview(8)
await preview
assert.equal(state.previewCount, null, 'stale preview results cannot attach to changed conditions')
delayedPreview = null
await state.save()
assert(calls.includes('save'))
assert.equal(state.editorOpen, false)
await state.reroll(state.result.groups[0])
assert.equal(calls.filter(c => c === 'reroll').length, 1)
await state.remove(state.rules[0])
assert(!calls.includes('delete'), 'canceling deletion preserves the rule')
accepted = true
await state.remove(state.rules[0])
assert(calls.includes('delete'))
assert.equal(state.result.groups.length, 0)
fail = true
await state.load()
assert(state.loadError, 'failed loads must be visible and retryable')
fail = false
await state.load()
assert.equal(state.loadError, '')
document.visibilityState = 'hidden'
listeners.get('visibilitychange')()
assert.equal(timers.size, 0)
state.result.date = '2000-01-01'
const before = calls.length
document.visibilityState = 'visible'
listeners.get('visibilitychange')()
await flush()
assert(calls.length > before, 'returning after a date change reloads today')
assert.equal(state.result.date, todayDate)
app.unmount()
assert.equal(timers.size, 0)
assert.equal(listeners.size, 0)
console.log('Recommendations: stable refresh, copy isolation, stale preview, save, reroll, cancel/delete, retry, date and visibility lifecycle passed')
