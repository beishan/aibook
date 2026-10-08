import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('CrawlerView.ts', script, ts.ScriptTarget.Latest, true)
const names = new Set(['openChapterEditor', 'beginChapterEdit', 'saveChapterEdit', 'handleChapterReaderKeydown'])
const functions = ast.statements.filter(node => ts.isFunctionDeclaration(node) && names.has(node.name?.text))
assert.equal(functions.length, names.size)
const eligibility = ast.statements.find(node => ts.isVariableStatement(node)
  && node.declarationList.declarations.some(item => item.name.getText(ast) === 'canEditChapter'))
assert(eligibility)
const compiled = ts.transpileModule([eligibility.getText(ast), ...functions.map(node => node.getText(ast))].join('\n'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022 },
}).outputText
const calls = []
let active = true
const context = {
  computed: getter => ({ get value() { return getter() } }),
  selectedBook: { value: { id: 1 } },
  chapterReaderActive: { value: { id: 2, crawlStatus: 'COMPLETED' } },
  chapterReaderLoaded: { value: true }, chapterReaderLoading: { value: false },
  chapterDetail: { value: { content: '原正文' } },
  chapterEditing: { value: false }, chapterEditContent: { value: '' },
  chapterReaderSettingsOpen: { value: true }, chapterDialog: { value: true },
  savingChapter: { value: false },
  chapters: { value: [{ id: 2, wordCount: 3 }] },
  chapterReaderChapters: { value: [{ id: 2, wordCount: 3 }] },
  isBookTaskActive: () => active,
  openChapter: async chapter => { context.chapterReaderActive.value = chapter },
  crawlerApi: { saveChapterContent: async (...args) => calls.push(args) },
  message: { success: () => {}, error: text => { throw new Error(text) } },
  moveChapterReader: () => { throw new Error('Typing must not navigate chapters') },
  HTMLInputElement: class {}, HTMLTextAreaElement: class {},
  HTMLButtonElement: class {}, HTMLAnchorElement: class {},
}
vm.createContext(context)
vm.runInContext(compiled, context)
await context.openChapterEditor({ id: 2, crawlStatus: 'COMPLETED' })
assert(context.chapterEditing.value, 'Completed chapter opens directly in editing mode during other collection')
assert.equal(context.chapterEditContent.value, '原正文')
context.handleChapterReaderKeydown({ key: 'ArrowRight', target: new context.HTMLTextAreaElement() })
context.chapterEditContent.value = '修订\n正文'
await context.saveChapterEdit(false)
assert.deepEqual(calls[0], [1, 2, '修订\n正文', false])
assert.equal(context.chapterDetail.value.content, '修订\n正文')
assert.equal(context.chapters.value[0].wordCount, 4)
assert.equal(context.chapterReaderChapters.value[0].wordCount, 4)
assert.equal(context.chapterEditing.value, false)
context.chapterReaderActive.value.crawlStatus = 'CRAWLING'
context.beginChapterEdit()
assert.equal(context.chapterEditing.value, false)
context.chapterReaderActive.value.crawlStatus = 'WAITING'
context.beginChapterEdit()
assert.equal(context.chapterEditing.value, false)
active = false
context.chapterReaderLoaded.value = false
context.beginChapterEdit()
assert.equal(context.chapterEditing.value, false, 'Loading failures must not open an empty editor')
console.log('Chapter edit checks passed: completed during collection, direct entry, save, word counts, typing, state guards')
