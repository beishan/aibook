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

// Batch membership captures a stable cross-page selection and keeps retryable failures selected.
const batchCalls = []
const snapshots = new Map([[3, { id: 3, isFavorite: false }], [7, { id: 7 }]])
const batchContext = vm.createContext({
  selectedBooks: { value: new Set([3, 7]) },
  selectedBookSnapshots: { value: snapshots },
  membershipBusy: { value: false },
  batchListBookIds: { value: [] },
  bookToAddToList: { value: { id: 99 } },
  showAddToListDialog: { value: false },
  message: { success() {}, warning() {}, error() {} },
  bookStore: { async addMembershipBatch(ids, action) {
    batchCalls.push([Array.from(ids), action])
    return ids.map(id => ({ id, isFavorite: true }))
  } },
})
for (const name of ['captureMembershipSelection', 'openBatchBookLists', 'addSelectedMembership']) {
  const node = ast.statements.find(item => ts.isFunctionDeclaration(item) && item.name.text === name)
  vm.runInContext(ts.transpile(node.getText(ast)), batchContext)
}
batchContext.openBatchBookLists()
assert.deepEqual(Array.from(batchContext.batchListBookIds.value), [3, 7])
batchContext.selectedBooks.value.add(11)
assert.deepEqual(Array.from(batchContext.batchListBookIds.value), [3, 7], 'dialog targets are a snapshot')
assert.equal(batchContext.bookToAddToList.value, null, 'batch clears previous single target')
await batchContext.addSelectedMembership('FAVORITE')
assert.deepEqual(batchCalls[0], [[3, 7, 11], 'FAVORITE'])
assert.equal(snapshots.get(3).isFavorite, true)
assert.equal(batchContext.selectedBooks.value.size, 3, 'selection remains available for the next action')
batchContext.membershipBusy.value = true
await batchContext.addSelectedMembership('SHELF')
assert.equal(batchCalls.length, 1, 'duplicate submissions are blocked')
batchContext.membershipBusy.value = false
batchContext.bookStore.addMembershipBatch = async () => { throw new Error('offline') }
await batchContext.addSelectedMembership('SHELF')
assert.equal(batchContext.membershipBusy.value, false)
assert.equal(batchContext.selectedBooks.value.size, 3, 'failed request preserves retry targets')
batchContext.selectedBooks.value = new Set(Array.from({ length: 201 }, (_, index) => index + 1))
assert.equal(batchContext.captureMembershipSelection(), null)
batchContext.selectedBooks.value = new Set()
assert.equal(batchContext.captureMembershipSelection(), null)
console.log('Batch membership: cross-page capture, isolated dialog, explicit favorite/add actions, pending guard, snapshots, retries and limits passed')

const dialogSource = await readFile(new URL('../src/components/AddToBookListDialog.vue', import.meta.url), 'utf8')
const dialogAst = ts.createSourceFile('dialog.ts', dialogSource.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1], ts.ScriptTarget.Latest, true)
const dialogCalls = []
const dialogProps = { book: null, bookIds: [3, 7] }
let dialogFails = false
const dialogContext = vm.createContext({
  props: dialogProps,
  computed: getter => ({ get value() { return getter() } }),
  loading: { value: false }, loadFailed: { value: false }, submitting: { value: false },
  bookLists: { value: [] }, selectedListIds: { value: [] }, existingListIds: { value: new Set() },
  api: {
    async get() { return { data: [
      { id: 1, books: [{ id: 3 }, { id: 7 }] },
      { id: 2, books: [{ id: 3 }] },
      { id: 3, books: [] },
    ] } },
    async post(path) { dialogCalls.push(path) },
  },
  bookStore: { async addMembershipBatch(ids, action, lists) {
    if (dialogFails) throw new Error('offline')
    dialogCalls.push([Array.from(ids), action, Array.from(lists)])
  } },
  message: { success() {}, error() {}, warning() {} },
  emit() {},
})
for (const name of ['targetBookIds', 'newListIds', 'loadBookLists', 'submit', 'closeDialog']) {
  const node = dialogAst.statements.find(item => ts.isVariableStatement(item)
    && item.declarationList.declarations.some(declaration => declaration.name.getText(dialogAst) === name))
  vm.runInContext(ts.transpile(node.getText(dialogAst), { target: ts.ScriptTarget.ES2022 }), dialogContext)
}
const dialogRun = code => vm.runInContext(code, dialogContext)
await dialogRun('loadBookLists()')
assert.deepEqual(Array.from(dialogContext.existingListIds.value), [1], 'only fully populated lists are disabled')
dialogContext.selectedListIds.value = [1, 2, 3]
await dialogRun('submit()')
assert.deepEqual(dialogCalls[0], [[3, 7], 'BOOK_LIST', [2, 3]])
assert.equal(dialogContext.submitting.value, false)
assert.deepEqual(Array.from(dialogContext.selectedListIds.value), [], 'successful close resets selection')
await dialogRun('loadBookLists()')
dialogContext.selectedListIds.value = [2]
dialogFails = true
await dialogRun('submit()')
assert.deepEqual(Array.from(dialogContext.selectedListIds.value), [2], 'failed submit keeps target lists for retry')
assert.equal(dialogContext.submitting.value, false)
dialogProps.book = { id: 42 }
dialogProps.bookIds = [3, 7]
await dialogRun('loadBookLists()')
dialogContext.selectedListIds.value = [3]
await dialogRun('submit()')
assert.equal(dialogCalls.at(-1), '/api/booklists/3/books/42', 'single-book callers retain their endpoint and target')
console.log('Book list dialog: mixed membership, one batch request, retry selection and single-book compatibility passed')
