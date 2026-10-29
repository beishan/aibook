import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { reactive } = require('vue')
let token = 'account-a'
global.localStorage = { getItem: () => token }
let pending
let rejectSave = false
const api = {
  get: () => new Promise(resolve => { pending = resolve }),
  put: async (_url, value) => {
    if (rejectSave) throw new Error('save failed')
    return { data: structuredClone(value) }
  },
}
const source = fs.readFileSync(new URL('../src/utils/bookCardBadges.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: {
  module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022,
} }).outputText
const module = { exports: {} }
new Function('require', 'module', 'exports', code)(name =>
  name === 'vue' ? { reactive } : { default: api }, module, module.exports)
const settings = module.exports
assert.equal(settings.badgeType('structured').defaultValue, '线')
assert.equal(settings.badgeType('EPUB').key, 'epub')
assert.equal(settings.badgeType('htm').key, 'html')
assert.equal(settings.badgeType('unknown').key, 'other')
const first = settings.hydrateBookCardBadges()
settings.resetBookCardBadges()
token = 'account-b'
pending({ data: { abbreviations: { structured: '旧' }, transparency: 99 } })
await first
assert.equal(settings.bookCardBadges.abbreviations.structured, '线', 'stale account load ignored')
await settings.saveBookCardBadges({ abbreviations: { ...settings.defaultBadgeSettings().abbreviations, structured: '网' }, transparency: 55 })
assert.equal(settings.bookCardBadges.abbreviations.structured, '网')
rejectSave = true
await assert.rejects(() => settings.saveBookCardBadges(settings.defaultBadgeSettings()))
assert.equal(settings.bookCardBadges.transparency, 55, 'failed save preserves active settings')
settings.resetBookCardBadges()
assert.equal(settings.bookCardBadges.transparency, 30)
assert.equal(settings.bookCardBadges.abbreviations.structured, '线')
console.log('Card badges: format aliases, unknown fallback, account reset, stale response, persistence and failed save passed')
