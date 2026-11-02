import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const source = await readFile(new URL('../src/views/BooksView.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('books.ts', script, ts.ScriptTarget.Latest, true)
const open = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name.text === 'openLibraryDialog')
const context = vm.createContext({ libraryBookIds: { value: [] }, showLibraryDialog: { value: false } })
vm.runInContext(ts.transpile(open.getText(ast)), context)

const selected = [3, 7]
context.openLibraryDialog(selected)
assert.deepEqual(Array.from(context.libraryBookIds.value), [3, 7])
selected.push(99)
assert.deepEqual(Array.from(context.libraryBookIds.value), [3, 7], 'dialog must capture the intended books')
context.openLibraryDialog([42])
assert.deepEqual(Array.from(context.libraryBookIds.value), [42], 'single book must replace previous batch targets')
assert.equal(context.showLibraryDialog.value, true)
context.openLibraryDialog([11, 12])
assert.deepEqual(Array.from(context.libraryBookIds.value), [11, 12], 'batch must replace previous single target')
const shelfDeclaration = ast.statements.find(node => ts.isVariableStatement(node)
  && node.declarationList.declarations.some(item => item.name.getText(ast) === 'handleToggleShelf'))
const calls = []
const notices = []
const shelfContext = vm.createContext({
  bookStore: {
    addToShelf: async id => calls.push(['add', id]),
    removeFromShelf: async id => calls.push(['remove', id]),
  },
  message: { success: text => notices.push(text), error: text => notices.push(text) },
})
vm.runInContext(ts.transpile(shelfDeclaration.getText(ast)) + '\nglobalThis.toggleShelf = handleToggleShelf', shelfContext)
await shelfContext.toggleShelf({ id: 42, onShelf: false })
await shelfContext.toggleShelf({ id: 42, onShelf: true })
assert.deepEqual(calls, [['add', 42], ['remove', 42]])
shelfContext.bookStore.addToShelf = async () => { throw new Error('offline') }
await shelfContext.toggleShelf({ id: 42, onShelf: false })
assert.equal(notices.at(-1), '加入书架失败')

const menuDeclaration = ast.statements.find(node => ts.isVariableStatement(node)
  && node.declarationList.declarations.some(item => item.name.getText(ast) === 'handleMoreCommand'))
const menuContext = vm.createContext({
  handleToggleShelf: book => shelfContext.toggleShelf(book),
  openLibraryDialog: ids => context.openLibraryDialog(ids),
})
vm.runInContext(ts.transpile(menuDeclaration.getText(ast)) + '\nglobalThis.moreCommand = handleMoreCommand', menuContext)
shelfContext.bookStore.addToShelf = async id => calls.push(['add', id])
await menuContext.moreCommand('toggle-shelf', { id: 51, onShelf: false })
await menuContext.moreCommand('toggle-shelf', { id: 51, onShelf: true })
assert.deepEqual(calls.slice(-2), [['add', 51], ['remove', 51]], 'card menu retains shelf add/remove behavior')
await menuContext.moreCommand('add-to-library', { id: 52 })
assert.deepEqual(Array.from(context.libraryBookIds.value), [52], 'card menu uses only its own book, not prior batch selection')
const cardTemplate = source.slice(source.indexOf('class="book-cover-actions"'), source.indexOf('<!-- 列表视图 -->'))
assert.match(cardTemplate, /command="toggle-shelf"/)
assert.match(cardTemplate, /command="add-to-library"/)
assert.match(cardTemplate, /book.onShelf \? '移出书架' : '加入书架'/)
assert.ok(!source.includes('book-membership-actions'), 'card footer membership actions are removed')
assert.ok(!source.includes('book-membership-button'), 'unused card membership styles are removed')
assert.match(source, /@click.stop="openLibraryDialog\(\[row.id\]\)"/)
assert.match(source, /@click="openLibraryDialog\(Array.from\(selectedBooks\)\)"/)
assert.match(source, /<AddToLibraryDialog[^>]*:book-ids="libraryBookIds"/)
assert.match(source, /@click.stop="handleToggleShelf\(row\)"/)
console.log('Book membership actions: card more-menu routing, shelf add/remove and isolated single/batch dialog targets passed')
