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

assert.match(source, /v-if="!selectionMode" class="book-membership-actions" @click.stop/)
assert.match(source, /@click.stop="openLibraryDialog\(\[book.id\]\)"/)
assert.match(source, /@click.stop="openLibraryDialog\(\[row.id\]\)"/)
assert.match(source, /@click="openLibraryDialog\(Array.from\(selectedBooks\)\)"/)
assert.match(source, /<AddToLibraryDialog[^>]*:book-ids="libraryBookIds"/)
assert.match(source, /@click.stop="handleToggleShelf\(book\)"/)
assert.match(source, /@click.stop="handleToggleShelf\(row\)"/)
assert.match(source, /\.book-membership-actions\s*\{[^}]*flex-wrap: wrap;/)
assert.match(source, /\.book-membership-button:focus-visible/)
console.log('Book membership actions: card/list entry points and isolated single/batch dialog targets passed')
