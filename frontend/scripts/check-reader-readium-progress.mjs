import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { Locator } from '@readium/shared'

// Exercise the engine's public navigation API using real Readium locators and a fake navigator.
const source = await readFile(new URL('../src/reader/ReadiumReaderEngine.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText.replaceAll("import('@readium/shared')", `import(${JSON.stringify(import.meta.resolve('@readium/shared'))})`)
const { ReadiumReaderEngine } = await import(`data:text/javascript;base64,${Buffer.from(js).toString('base64')}`)
const locations = [0, 0.33, 0.66].map((progression, i) => Locator.deserialize({
  href: `chapter-${i + 1}.xhtml`, type: 'application/xhtml+xml',
  locations: { position: i + 1, progression: 0, totalProgression: progression },
}))
assert.ok(locations.every(Boolean))
const calls = []
const engine = new ReadiumReaderEngine({ onPositionChanged() {}, onError() {} })
engine.navigator = { go(target, _animated, complete) { calls.push(target); complete(true) } }
engine.publication = {
  positionsFromManifest: async () => locations,
  linkWithHref: href => ({ locator: locations.find(item => item.href === href) }),
  readingOrder: { items: locations.map(locator => ({ href: locator.href, locator })) },
}
for (const [progression, chapter] of [[-1, 1], [0, 1], [0.5, 2], [1, 3], [2, 3]]) {
  assert.equal(await engine.goTo({ totalProgression: progression }), true)
  assert.equal(calls.at(-1).href, `chapter-${chapter}.xhtml`)
}
assert.equal(await engine.goTo({ href: 'chapter-2.xhtml', progression: 0.4 }), true)
assert.equal(calls.at(-1).locations.progression, 0.4)
engine.publication.positionsFromManifest = async () => []
assert.equal(await engine.goTo({ totalProgression: 1 }), true)
assert.equal(calls.at(-1).href, 'chapter-3.xhtml')
assert.equal(calls.at(-1).locations.progression, 1)
engine.publication.readingOrder.items = []
const previousCalls = calls.length
assert.equal(await engine.goTo({ totalProgression: 0.5 }), false)
assert.equal(calls.length, previousCalls)
console.log('Readium progress navigation: position list, chapter fallback, bounds and legacy href navigation passed')
