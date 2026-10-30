import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
import { reactive } from 'vue'
let token = 'account-a'
global.localStorage = { getItem: () => token }
let resolveLoad
let rejectSave = false
const api = {
  get: () => new Promise(resolve => { resolveLoad = resolve }),
  put: async (_url, data) => {
    if (rejectSave) throw new Error('save failed')
    return { data: structuredClone(data) }
  },
}
const code = ts.transpileModule(fs.readFileSync(new URL('../src/utils/bookCoverPlaceholder.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const module = { exports: {} }
new Function('require', 'module', 'exports', code)(name => name === 'vue' ? { reactive } : { default: api }, module, module.exports)
const value = module.exports
assert.equal(value.placeholderText('三国演义'), '三国')
assert.equal(value.placeholderText('𠮷野家的书'), '𠮷野')
assert.equal(value.placeholderText('书'), '书')
assert.equal(value.placeholderText(null), '书')
assert.equal(value.placeholderText('三**义', { mode: 'PREFIX', length: 3, text: '书' }), '三**')
assert.equal(value.placeholderText('三国演义', { mode: 'FULL', length: 2, text: '书' }), '三国演义')
assert.equal(value.placeholderText('三国演义', { mode: 'CUSTOM', length: 2, text: '阅读' }), '阅读')
const pending = value.hydrateBookCoverPlaceholder()
value.resetBookCoverPlaceholder()
token = 'account-b'
resolveLoad({ data: { mode: 'CUSTOM', length: 2, text: '旧账户' } })
await pending
assert.equal(value.bookCoverPlaceholder.mode, 'PREFIX')
await value.saveBookCoverPlaceholder({ mode: 'PREFIX', length: 4, text: '书' })
assert.equal(value.placeholderText('三国演义'), '三国演义')
rejectSave = true
await assert.rejects(() => value.saveBookCoverPlaceholder(value.defaultPlaceholderSettings()))
assert.equal(value.bookCoverPlaceholder.length, 4)
value.resetBookCoverPlaceholder()
assert.equal(value.bookCoverPlaceholder.length, 2)
console.log('Cover placeholders: modes, prefix length, Unicode, short/empty names, masked names, account isolation, stale load and failed save passed')
