import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'

const source = fs.readFileSync(new URL('../src/utils/crawlerBookTitle.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
})
const { maskCrawlerBookTitle } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
for (const [title, hidden, expected] of [
  ['三国演义', true, '三**义'],
  ['三国演义', false, '三国演义'],
  ['', true, ''],
  ['书', true, '书'],
  ['活着', true, '活着'],
  ['𠮷野家的书', true, '𠮷***书'],
]) {
  assert.equal(maskCrawlerBookTitle(title, hidden), expected)
}
console.log('Crawler book title masking checks passed')
