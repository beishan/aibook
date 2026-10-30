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
const { maskBookName, normalizeBookNamePrefixLength, isBookNameMaskCharacter, normalizeBookNameMaskCharacter } = await import(`data:text/javascript;base64,${Buffer.from(javascript).toString('base64')}`)
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
const bookNameMaskCharacter = ref('*')
const writes = []
const setterSource = preferences.slice(preferences.indexOf('  const setHideBookNames ='), preferences.indexOf('  const setShelfPageSize ='))
const setters = new Function('hideBookNames', 'bookNameMaskMode', 'bookNamePrefixLength', 'normalizeBookNamePrefixLength', 'bookNameMaskCharacter', 'isBookNameMaskCharacter', 'persistRemote',
  ts.transpile(setterSource + '\nreturn { setHideBookNames, setBookNameMaskMode, setBookNamePrefixLength, setBookNameMaskCharacter };'))(
  hideBookNames, bookNameMaskMode, bookNamePrefixLength, normalizeBookNamePrefixLength, bookNameMaskCharacter, isBookNameMaskCharacter, value => writes.push(value),
)
setters.setHideBookNames(true)
setters.setBookNameMaskMode('PREFIX')
setters.setBookNamePrefixLength(3)
assert.deepEqual(writes, [{ hideBookNames: true }, { bookNameMaskMode: 'PREFIX' }, { bookNamePrefixLength: 3 }])
for (const value of [0, -1, 101, 2.5]) setters.setBookNamePrefixLength(value)
setters.setBookNameMaskMode('INVALID')
assert.equal(writes.length, 3)

const hydration = preferences.slice(preferences.indexOf('      setHideBookNames('), preferences.indexOf('      setShelfPageSize('))
let restoredDisplayTab
const restoreDisplayTab = (value, syncRemote) => {
  restoredDisplayTab = value
  assert.equal(syncRemote, false)
}
const restore = new Function('data', 'setHideBookNames', 'setBookNameMaskMode', 'setBookNamePrefixLength', 'setBookNameMaskCharacter', 'normalizeBookNamePrefixLength', 'normalizeBookNameMaskCharacter', 'setBookDisplaySettingsTab', ts.transpile(hydration))
restore({ hideBookNames: true, bookNameMaskMode: 'PREFIX', bookNamePrefixLength: 5, bookDisplaySettingsTab: 'cover' }, ...Object.values(setters), normalizeBookNamePrefixLength, normalizeBookNameMaskCharacter, restoreDisplayTab)
assert.equal(restoredDisplayTab, 'cover')
assert.equal(bookNamePrefixLength.value, 5)
restore({}, ...Object.values(setters), normalizeBookNamePrefixLength, normalizeBookNameMaskCharacter, restoreDisplayTab)
assert.equal(restoredDisplayTab, 'name')
assert.equal(hideBookNames.value, false)
assert.equal(bookNameMaskMode.value, 'HEAD_TAIL')
assert.equal(bookNamePrefixLength.value, 2)
assert.equal(writes.length, 3, 'restoring settings must not save them again')

const state = { hydrated: false, hideBookNames: false, bookNameMaskMode: 'HEAD_TAIL', bookNamePrefixLength: 2, bookNameMaskCharacter: '*' }
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
const bookDisplaySettingsTab = ref('cover')
new Function('hideBookNames', 'bookNameMaskMode', 'bookNamePrefixLength', 'bookNameMaskCharacter', 'bookDisplaySettingsTab', 'let hydrationSequence = 0; const navigationMenuOrder = { value: [] }; const defaultNavigationMenuOrder = () => [];\n' + resetSource + '}\nresetHydration();')(
  hideBookNames, bookNameMaskMode, bookNamePrefixLength, bookNameMaskCharacter, bookDisplaySettingsTab,
)
assert.equal(bookDisplaySettingsTab.value, 'name')
assert.equal(hideBookNames.value, false)
assert.equal(bookNameMaskMode.value, 'HEAD_TAIL')
assert.equal(bookNamePrefixLength.value, 2)
// A replacement is one visible grapheme, including multi-code-point emoji.
for (const character of ['*', '#', '●', '隐', '🙂', '👨‍👩‍👧', '🇨🇳', 'e\u0301', '<']) {
  assert.equal(isBookNameMaskCharacter(character), true, character)
  assert.equal(maskBookName('三国演义', true, 'HEAD_TAIL', 2, character), `三${character.repeat(2)}义`)
  assert.equal(maskBookName('三国演义', true, 'PREFIX', 2, character), `三国${character.repeat(2)}`)
}
for (const invalid of ['', ' ', '\n', '\t', '\u0000', '\u200b', '\u0301', '\ud800', '**', 'abc', '🙂🙂', '*'.repeat(65)]) {
  assert.equal(isBookNameMaskCharacter(invalid), false, JSON.stringify(invalid))
  assert.equal(normalizeBookNameMaskCharacter(invalid), '*')
}
setters.setBookNameMaskCharacter('●')
assert.equal(bookNameMaskCharacter.value, '●')
assert.deepEqual(writes.at(-1), { bookNameMaskCharacter: '●' })
setters.setBookNameMaskCharacter('invalid')
assert.equal(bookNameMaskCharacter.value, '●')
restore({ bookNameMaskCharacter: '👨‍👩‍👧' }, ...Object.values(setters), normalizeBookNamePrefixLength, normalizeBookNameMaskCharacter, restoreDisplayTab)
assert.equal(bookNameMaskCharacter.value, '👨‍👩‍👧')
restore({}, ...Object.values(setters), normalizeBookNamePrefixLength, normalizeBookNameMaskCharacter, restoreDisplayTab)
assert.equal(restoredDisplayTab, 'name')
assert.equal(bookNameMaskCharacter.value, '*')
state.bookNameMaskCharacter = '●'
assert.equal(displayBookName('三国演义'), '三国●●')
for (const view of ['CrawlerView', 'CrawlerTrialReaderView']) {
  const template = fs.readFileSync(new URL(`../src/views/${view}.vue`, import.meta.url), 'utf8')
  assert.doesNotMatch(template, /<CrawlerBookTitle[^>]*:title="displayBookName\(/,
    'crawler title components must receive original text to avoid masking multi-code-point replacements twice')
}
state.bookNameMaskCharacter = '*'
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

// Crawler titles ignore all global name display settings, in every switch combination.
const crawlerDisplaySource = fs.readFileSync(new URL('../src/composables/useCrawlerBookNameDisplay.ts', import.meta.url), 'utf8')
const crawlerDisplayContext = vm.createContext({ exports: {}, require: name => {
  if (name === '@/stores/preferences') return { usePreferencesStore: () => state }
  return { maskCrawlerBookTitle: (title, hidden) => maskBookName(title, hidden) }
} })
vm.runInContext(ts.transpileModule(crawlerDisplaySource, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, crawlerDisplayContext)
const { useCrawlerBookNameDisplay } = crawlerDisplayContext.exports
const { displayCrawlerBookName } = useCrawlerBookNameDisplay()
const crawlerSource = fs.readFileSync(new URL('../src/components/CrawlerBookTitle.vue', import.meta.url), 'utf8')
const crawlerScript = compileScript(parse(crawlerSource).descriptor, { id: 'name-display-test', inlineTemplate: true })
const crawlerContext = vm.createContext({ exports: {}, require: name => {
  if (name === 'vue') return vue
  if (name === '@/composables/useCrawlerBookNameDisplay') return { useCrawlerBookNameDisplay }
  throw new Error(`Unexpected crawler display dependency: ${name}`)
} })
vm.runInContext(ts.transpileModule(crawlerScript.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, crawlerContext)
for (const globalHidden of [false, true]) {
  for (const crawlerHidden of [false, true]) {
    state.hydrated = true
    state.hideBookNames = globalHidden
    state.crawlerHideBookNames = crawlerHidden
    state.bookNameMaskMode = 'PREFIX'
    state.bookNamePrefixLength = 1
    state.bookNameMaskCharacter = '👨‍👩‍👧'
    const expectedText = crawlerHidden ? '三**义' : '三国演义'
    assert.equal(displayCrawlerBookName('三国演义'), expectedText)
    const html = await renderToString(vue.createSSRApp({ render: () => vue.h(crawlerContext.exports.default, { title: '三国演义' }) }))
    assert(html.includes('title="三国演义"'))
    assert(html.includes(`>${expectedText}</span>`))
    // The reverse dependency must also be absent: crawler changes do not affect library names.
    assert.equal(displayBookName('三国演义'), globalHidden ? '三👨‍👩‍👧👨‍👩‍👧👨‍👩‍👧' : '三国演义')
  }
}
for (const name of ['views/CrawlerView.vue', 'views/CrawlerTrialReaderView.vue',
  'components/CrawlerBookTitle.vue', 'components/DiscoveryChapterListDialog.vue',
  'components/CrawlerExecutorsSettingsPanel.vue']) {
  const content = fs.readFileSync(new URL(`../src/${name}`, import.meta.url), 'utf8')
  assert.doesNotMatch(content, /useBookNameDisplay|displayBookName|preferences\.hideBookNames/,
    `${name} must exclusively use the crawler setting`)
}
console.log('Book name display: modes, Unicode, persistence, account isolation and all independent crawler/global switch combinations passed')
