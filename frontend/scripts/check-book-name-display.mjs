import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { ref } from 'vue'
import * as vue from 'vue'
import { renderToString } from '@vue/server-renderer'
import { parse, compileScript } from '@vue/compiler-sfc'

const source = fs.readFileSync(new URL('../src/utils/bookName.ts', import.meta.url), 'utf8')
const javascript = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText
const { maskBookName, normalizeBookNamePrefixLength } = await import(`data:text/javascript;base64,${Buffer.from(javascript).toString('base64')}`)
for (const [title, hidden, mode, length, expected] of [
  ['三国演义', false, 'HEAD_TAIL', 2, '三国演义'],
  ['三国演义', true, 'HEAD_TAIL', 2, '三**义'],
  ['', true, 'HEAD_TAIL', 2, ''],
  [null, true, 'HEAD_TAIL', 2, ''],
  ['书', true, 'HEAD_TAIL', 2, '书'],
  ['活着', true, 'HEAD_TAIL', 2, '活着'],
  ['三国演义', true, 'PREFIX', 1, '三***'],
  ['三国演义', true, 'PREFIX', 2, '三国**'],
  ['三国演义', true, 'PREFIX', 3, '三国演*'],
  ['三国演义', true, 'PREFIX', 4, '三国演义'],
  ['活着', true, 'PREFIX', 100, '活着'],
  ['𠮷野家的书', true, 'HEAD_TAIL', 2, '𠮷***书'],
  ['𠮷野家的书', true, 'PREFIX', 2, '𠮷野***'],
]) assert.equal(maskBookName(title, hidden, mode, length), expected)
for (const value of [undefined, null, 0, -1, 1.5, 101, '3', NaN]) {
  assert.equal(normalizeBookNamePrefixLength(value), 2)
}
assert.equal(normalizeBookNamePrefixLength(100), 100)

const preferences = fs.readFileSync(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8')
const hideBookNames = ref(false), bookNameMaskMode = ref('HEAD_TAIL'), bookNamePrefixLength = ref(2)
const writes = []
const setterSource = preferences.slice(preferences.indexOf('  const setHideBookNames ='), preferences.indexOf('  const setShelfPageSize ='))
const setters = new Function('hideBookNames', 'bookNameMaskMode', 'bookNamePrefixLength', 'normalizeBookNamePrefixLength', 'persistRemote',
  ts.transpile(setterSource + '\nreturn { setHideBookNames, setBookNameMaskMode, setBookNamePrefixLength };'))(
  hideBookNames, bookNameMaskMode, bookNamePrefixLength, normalizeBookNamePrefixLength, value => writes.push(value),
)
setters.setHideBookNames(true)
setters.setBookNameMaskMode('PREFIX')
setters.setBookNamePrefixLength(3)
assert.deepEqual(writes, [{ hideBookNames: true }, { bookNameMaskMode: 'PREFIX' }, { bookNamePrefixLength: 3 }])
for (const value of [0, -1, 101, 2.5]) setters.setBookNamePrefixLength(value)
setters.setBookNameMaskMode('INVALID')
assert.equal(writes.length, 3)

const hydration = preferences.slice(preferences.indexOf('      setHideBookNames('), preferences.indexOf('      setShelfPageSize('))
const restore = new Function('data', 'setHideBookNames', 'setBookNameMaskMode', 'setBookNamePrefixLength', 'normalizeBookNamePrefixLength', ts.transpile(hydration))
restore({ hideBookNames: true, bookNameMaskMode: 'PREFIX', bookNamePrefixLength: 5 }, ...Object.values(setters), normalizeBookNamePrefixLength)
assert.equal(bookNamePrefixLength.value, 5)
restore({}, ...Object.values(setters), normalizeBookNamePrefixLength)
assert.equal(hideBookNames.value, false)
assert.equal(bookNameMaskMode.value, 'HEAD_TAIL')
assert.equal(bookNamePrefixLength.value, 2)
assert.equal(writes.length, 3, 'restoring settings must not save them again')

const state = { hydrated: false, hideBookNames: false, bookNameMaskMode: 'HEAD_TAIL', bookNamePrefixLength: 2 }
const composable = fs.readFileSync(new URL('../src/composables/useBookNameDisplay.ts', import.meta.url), 'utf8')
const context = vm.createContext({
  exports: {}, localStorage: { getItem: () => 'account-token' },
  require: name => name === '@/stores/preferences'
    ? { usePreferencesStore: () => state } : { maskBookName },
})
vm.runInContext(ts.transpileModule(composable, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, context)
const { displayBookName } = context.exports.useBookNameDisplay()
assert.equal(displayBookName('三国演义'), '****', 'a pending account load must not flash its titles')
state.hydrated = true
assert.equal(displayBookName('三国演义'), '三国演义')
state.hideBookNames = true
assert.equal(displayBookName('三国演义'), '三**义')
state.bookNameMaskMode = 'PREFIX'
assert.equal(displayBookName('三国演义'), '三国**')
const resetSource = preferences.slice(preferences.indexOf('  const resetHydration ='), preferences.indexOf('    crawlerHideBookNames.value = false', preferences.indexOf('  const resetHydration =')))
new Function('hideBookNames', 'bookNameMaskMode', 'bookNamePrefixLength', 'let hydrationSequence = 0;\n' + resetSource + '}\nresetHydration();')(
  hideBookNames, bookNameMaskMode, bookNamePrefixLength,
)
assert.equal(hideBookNames.value, false)
assert.equal(bookNameMaskMode.value, 'HEAD_TAIL')
assert.equal(bookNamePrefixLength.value, 2)
// Execute the account/sequence guard from hydrate with deliberately late responses.
const hydrationStart = preferences.indexOf('  const hydrate = async')
const guardSource = preferences.slice(hydrationStart, preferences.indexOf('      const missingPreferences', hydrationStart))
const pending = []
let activeToken = 'account-a'
const guardContext = vm.createContext({
  hydrated: ref(false), hydrationSequence: 0, applied: 0,
  localStorage: { getItem: () => activeToken },
  api: { get: () => new Promise(resolve => pending.push(resolve)) },
})
vm.runInContext(ts.transpile(guardSource + 'applied++; } catch {} }; globalThis.restoreAccount = hydrate;'), guardContext)
const oldAccount = guardContext.restoreAccount()
activeToken = 'account-b'
pending.shift()({ data: {} })
await oldAccount
assert.equal(guardContext.applied, 0)
const olderRequest = guardContext.restoreAccount()
const newerRequest = guardContext.restoreAccount()
pending[1]({ data: {} })
await newerRequest
pending[0]({ data: {} })
await olderRequest
assert.equal(guardContext.applied, 1)

// Render the real crawler title component: global privacy wins, while the old
// crawler-only tooltip behavior remains available when global privacy is off.
const crawlerSource = fs.readFileSync(new URL('../src/components/CrawlerBookTitle.vue', import.meta.url), 'utf8')
const crawlerScript = compileScript(parse(crawlerSource).descriptor, { id: 'name-display-test', inlineTemplate: true })
const crawlerContext = vm.createContext({ exports: {}, require: name => {
  if (name === 'vue') return vue
  if (name === '@/stores/preferences') return { usePreferencesStore: () => state }
  if (name === '@/composables/useBookNameDisplay') return { useBookNameDisplay: () => ({ displayBookName }) }
  return { maskCrawlerBookTitle: (title, hidden) => maskBookName(title, hidden) }
} })
vm.runInContext(ts.transpileModule(crawlerScript.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, crawlerContext)
for (const [globalHidden, crawlerHidden, expectedText, expectedHint] of [
  [true, true, '三国**', '三国**'],
  [false, true, '三**义', '三国演义'],
  [false, false, '三国演义', '三国演义'],
]) {
  state.hydrated = true
  state.hideBookNames = globalHidden
  state.crawlerHideBookNames = crawlerHidden
  const html = await renderToString(vue.createSSRApp({ render: () => vue.h(crawlerContext.exports.default, { title: '三国演义' }) }))
  assert(html.includes(`title="${expectedHint}"`))
  assert(html.includes(`>${expectedText}</span>`))
}
console.log('Book name display: both modes, Unicode, bounds, save/restore, account reset, stale responses, pre-hydration masking and crawler compatibility passed')
