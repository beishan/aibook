import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'
import { chapterTitleModuleUrl } from './reader-chapter-title-module.mjs'

const { CHAPTER_TITLE_STYLES, chapterTitleFontSize, applyQuickChapterTitle } = await import(chapterTitleModuleUrl)
for (const [style, size] of [['compact', 18], ['body', 24], ['divider', 16]]) {
  assert.equal(chapterTitleFontSize({ style, fontSize: null, bodyFontSize: 24 }), size)
  assert.equal(chapterTitleFontSize({ style, fontSize: 14, bodyFontSize: 24 }), 14)
}

const preferenceSource = await readFile(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8')
const ast = ts.createSourceFile('preferences.ts', preferenceSource, ts.ScriptTarget.Latest, true)
const names = ['DEFAULT_READER_SETTINGS', 'DEFAULT_BLUE_GRAY_TYPOGRAPHY', 'DEFAULT_WARM_PAPER_TYPOGRAPHY',
  'READER_APPEARANCES', 'READER_EPUB_ENGINES', 'READER_CONTENT_WIDTHS', 'READER_SCREEN_MODES',
  'READER_BACKGROUND_COLORS', 'READER_BUILT_IN_FONTS', 'isOneOf', 'normalizedNumber', 'normalizeReaderSettings']
const declarations = ast.statements.filter(node => ts.isVariableStatement(node)
  && node.declarationList.declarations.some(item => names.includes(item.name.getText(ast))))
  .map(node => node.getText(ast).replace(/^export /, '')).join('\n')
const context = vm.createContext({ CHAPTER_TITLE_STYLES })
vm.runInContext(ts.transpile(declarations) + '\nglobalThis.normalize = normalizeReaderSettings', context)
assert.equal(context.normalize({}).quickChapterTitleStyle, 'original')
assert.equal(context.normalize({}).quickChapterTitleFontSize, null)
for (const style of CHAPTER_TITLE_STYLES) {
  for (const size of [null, 12, 40]) {
    const value = context.normalize({ quickChapterTitleStyle: style, quickChapterTitleFontSize: size, fontSize: 22 })
    assert.equal(value.quickChapterTitleStyle, style)
    assert.equal(value.quickChapterTitleFontSize, size)
    assert.equal(value.fontSize, 22)
  }
}
for (const size of [11, 41, 17.5, '18', NaN]) assert.equal(context.normalize({ quickChapterTitleFontSize: size }).quickChapterTitleFontSize, null)
assert.equal(context.normalize({ quickChapterTitleStyle: '<script>' }).quickChapterTitleStyle, 'original')

function makeDocument(connected = true) {
  const attrs = new Map(), properties = new Map(), elements = new Map()
  return {
    attrs, properties, elements,
    defaultView: { frameElement: { isConnected: connected } },
    documentElement: {
      setAttribute: (name, value) => attrs.set(name, value),
      removeAttribute: name => attrs.delete(name),
      toggleAttribute: (name, enabled) => enabled ? attrs.set(name, '') : attrs.delete(name),
      style: { setProperty: (name, value) => properties.set(name, value), removeProperty: name => properties.delete(name) },
    },
    getElementById: id => elements.get(id),
    createElement: () => ({ remove() { elements.delete(this.id) } }),
    head: { appendChild: el => elements.set(el.id, el) },
  }
}
const doc = makeDocument()
applyQuickChapterTitle(doc, { style: 'divider', fontSize: 18, bodyFontSize: 24 })
assert.equal(doc.attrs.get('data-quick-chapter-title'), 'divider')
assert.equal(doc.properties.get('--quick-chapter-title-size'), '18px')
applyQuickChapterTitle(doc, { style: 'body', fontSize: null, bodyFontSize: 24 })
assert.equal(doc.elements.size, 1, 'reuse one style across repeated updates')
assert.equal(doc.properties.get('--quick-chapter-title-size'), '24px')
assert.equal(doc.attrs.has('data-quick-chapter-title-custom-size'), false)
applyQuickChapterTitle(doc, null)
assert.equal(doc.elements.size, 0)
assert.equal(doc.attrs.size, 0)
assert.equal(doc.properties.size, 0)

const engineSource = await readFile(new URL('../src/reader/ReadiumReaderEngine.ts', import.meta.url), 'utf8')
const navigatorUrl = `data:text/javascript;base64,${Buffer.from('export class EpubPreferences { constructor(value) { Object.assign(this, value) } }').toString('base64')}`
const engineJs = ts.transpileModule(engineSource, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText.replace('./chapterTitle', chapterTitleModuleUrl).replaceAll("import('@readium/navigator')", `import(${JSON.stringify(navigatorUrl)})`)
const { ReadiumReaderEngine } = await import(`data:text/javascript;base64,${Buffer.from(engineJs).toString('base64')}`)
const engine = new ReadiumReaderEngine({ onPositionChanged() {}, onError() {} })
engine.navigator = { submitPreferences: async () => {}, destroy: async () => {} }
const inactiveDoc = makeDocument(false)
engine.chapterTitleDocuments.add(doc)
engine.chapterTitleDocuments.add(inactiveDoc)
await engine.applySettings({ chapterTitle: { style: 'compact', fontSize: 19, bodyFontSize: 24 } })
assert.equal(doc.properties.get('--quick-chapter-title-size'), '19px')
assert.equal(engine.chapterTitleDocuments.has(inactiveDoc), false)
const listenerCode = engineSource.match(/frameLoaded: (\(wnd: Window\) => \{[\s\S]*?\n      \}),/)[1]
const listenerContext = vm.createContext({ engine, applyQuickChapterTitle })
vm.runInContext(ts.transpile(`globalThis.onFrame = function() { return ${listenerCode} }.call(engine)`, { target: ts.ScriptTarget.ES2022 }), listenerContext)
const nextDoc = makeDocument()
engine.chapterTitleDocuments.add(inactiveDoc)
listenerContext.onFrame({ document: nextDoc })
assert.equal(nextDoc.properties.get('--quick-chapter-title-size'), '19px', 'new Readium chapters inherit current preferences')
assert.equal(engine.chapterTitleDocuments.has(inactiveDoc), false)
await engine.applySettings({})
assert.equal(nextDoc.elements.size, 0)
await engine.destroy()
assert.equal(engine.chapterTitleDocuments.size, 0)
console.log('Quick chapter preferences: defaults, validation, independent sizes, style reuse/reset and Readium current/new chapter lifecycle passed')
