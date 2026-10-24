import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { ref, computed, watch } from 'vue'

const source = await readFile(new URL('../src/views/ShelfView.vue', import.meta.url), 'utf8')
const paging = source.slice(source.indexOf('const shelfPageSize ='), source.indexOf('const selectedShelfTitle ='))
const savedSize = ref(24)
const writes = []
const preferencesStore = {
  get shelfPageSize() { return savedSize.value },
  setShelfPageSize(value) { savedSize.value = value; writes.push(value) },
}
const names = ['shelf', 'favorite', 'reading', 'finished', 'wanted', 'lists']
const state = {
  preferencesStore,
  activeTab: ref('shelf'),
  selectedShelfGroup: ref('ungrouped'),
  displayedShelfBooks: ref(Array.from({ length: 49 }, (_, id) => ({ id }))),
  favoriteBooks: ref(Array.from({ length: 30 }, (_, id) => ({ id }))),
  readingBooks: ref([]),
  finishedBooks: ref(Array.from({ length: 125 }, (_, id) => ({ id }))),
  wantedBooks: ref([]),
  bookLists: ref([]),
  shelfLoading: ref(false),
  favoriteLoading: ref(false),
  readingLoading: ref(false),
  finishedLoading: ref(false),
  wantedLoading: ref(false),
}
const compiled = ts.transpile(paging + '\nreturn {shelfPageSize, activePage, tabPages, pagedShelfBooks, pagedFavoriteBooks, pagedFinishedBooks, shelfPageOffset, activeTotal, activeLoading};', { target: ts.ScriptTarget.ES2022 })
const result = new Function('ref', 'computed', 'watch', ...Object.keys(state), compiled)(ref, computed, watch, ...Object.values(state))
assert.equal(result.pagedShelfBooks.value.length, 24)
result.activePage.value = 2
assert.equal(result.pagedShelfBooks.value[0].id, 24)
assert.equal(result.shelfPageOffset.value, 24)
result.activePage.value = 3
assert.deepEqual(result.pagedShelfBooks.value.map(book => book.id), [48])
state.displayedShelfBooks.value = state.displayedShelfBooks.value.slice(0, 48)
assert.equal(result.activePage.value, 2, 'removing the last item returns to the last available page')
state.activeTab.value = 'favorite'
assert.equal(result.activePage.value, 1)
result.activePage.value = 2
state.activeTab.value = 'shelf'
assert.equal(result.activePage.value, 2, 'tab page positions are independent')
state.selectedShelfGroup.value = 3
assert.equal(result.activePage.value, 1, 'changing folders resets shelf pagination')
state.activeTab.value = 'finished'
result.activePage.value = 6
assert.equal(result.pagedFinishedBooks.value.length, 5)
assert.equal(result.activeTotal.value, 125, 'finished books are not limited to the first 100 books')
state.finishedLoading.value = true
assert.equal(result.activeLoading.value, true)
state.finishedBooks.value = []
assert.equal(result.activePage.value, 1, 'empty collections keep a valid page')

// Size changes reset every tab and update both slicing and ordering offsets.
state.activeTab.value = 'shelf'
result.activePage.value = 2
result.shelfPageSize.value = 12
assert.equal(result.activePage.value, 1)
assert.equal(result.pagedShelfBooks.value.length, 12)
result.activePage.value = 3
assert.equal(result.shelfPageOffset.value, 24)
assert.equal(result.pagedShelfBooks.value[0].id, 24)
state.activeTab.value = 'favorite'
assert.equal(result.pagedFavoriteBooks.value.length, 12)
result.activePage.value = 2
result.shelfPageSize.value = 48
assert.equal(result.activePage.value, 1)
assert.equal(result.pagedFavoriteBooks.value.length, 30)
state.activeTab.value = 'shelf'
assert.equal(result.activePage.value, 1, 'size changes reset inactive tabs too')
assert.equal(result.pagedShelfBooks.value.length, 48)
// Account hydration also resets pages and applies the remotely restored size.
result.activePage.value = 2
savedSize.value = 96
assert.equal(result.activePage.value, 1)
assert.deepEqual(writes, [12, 48])
result.shelfPageSize.value = 24
assert.match(source, /v-model:page-size="shelfPageSize"/)
assert.match(source, /layout="sizes, prev, pager, next"/)

// Execute the actual preference setter and hydration path, including validation.
const preferenceSource = await readFile(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8')
const accountSize = ref(24)
const remoteWrites = []
const options = [12, 24, 48, 96]
const setterSource = preferenceSource.slice(preferenceSource.indexOf('  const setShelfPageSize ='),
  preferenceSource.indexOf('  const setCategoryPageSize ='))
const setSize = new Function('shelfPageSize', 'SHELF_PAGE_SIZE_OPTIONS', 'persistRemote',
  ts.transpile(setterSource + '\nreturn setShelfPageSize;', { target: ts.ScriptTarget.ES2022 }))(
  accountSize, options, patch => remoteWrites.push(patch),
)
setSize(48)
assert.equal(accountSize.value, 48)
assert.deepEqual(remoteWrites, [{ shelfPageSize: 48 }])
for (const invalid of [0, -1, 25, 100]) setSize(invalid)
assert.equal(accountSize.value, 48)
assert.equal(remoteWrites.length, 1)
const hydrationSource = preferenceSource.slice(preferenceSource.indexOf('      setShelfPageSize('),
  preferenceSource.indexOf('      setCategoryPageSize('))
const hydrateSize = new Function('data', 'SHELF_PAGE_SIZE_OPTIONS', 'setShelfPageSize', ts.transpile(hydrationSource))
hydrateSize({ shelfPageSize: 96 }, options, setSize)
assert.equal(accountSize.value, 96)
hydrateSize({ shelfPageSize: 1000 }, options, setSize)
assert.equal(accountSize.value, 24)
hydrateSize({}, options, setSize)
assert.equal(accountSize.value, 24)
assert.equal(remoteWrites.length, 1, 'hydration must not write preferences back')
assert.match(preferenceSource, /const resetHydration = \(\) => \{[\s\S]*?shelfPageSize.value = 24/)

// Execute the actual ordering handler with a page-two boundary index.
state.selectedShelfGroup.value = 'ungrouped'
const ordering = source.slice(source.indexOf('const moveShelfBookOrder ='), source.indexOf('const moveBookToShelfGroup ='))
let request
const move = new Function('selectedShelfGroup', 'displayedShelfBooks', 'replaceSelectedShelfBooks', 'shelfActionBookId', 'api', 'message', ts.transpile(ordering + '\nreturn moveShelfBookOrder;', { target: ts.ScriptTarget.ES2022 }))(
  state.selectedShelfGroup, state.displayedShelfBooks,
  books => { state.displayedShelfBooks.value = books }, ref(null),
  { put: async (path, body) => { request = { path, body } } },
  { error: text => { throw new Error(text) } },
)
await move(24, -1)
assert.equal(state.displayedShelfBooks.value[23].id, 24, 'page-two first book can move into page one')
assert.equal(request.body.bookIds.length, 48, 'sorting submits the full group, not only the visible page')
assert.deepEqual(request.body.bookIds.slice(23, 25), [24, 23])
const previousOrder = state.displayedShelfBooks.value.map(book => book.id)
let sortingError
const failingMove = new Function('selectedShelfGroup', 'displayedShelfBooks', 'replaceSelectedShelfBooks', 'shelfActionBookId', 'api', 'message', ts.transpile(ordering + '\nreturn moveShelfBookOrder;', { target: ts.ScriptTarget.ES2022 }))(
  state.selectedShelfGroup, state.displayedShelfBooks,
  books => { state.displayedShelfBooks.value = books }, ref(null),
  { put: async () => { throw new Error('offline') } },
  { error: text => { sortingError = text } },
)
await failingMove(24, 1)
assert.deepEqual(state.displayedShelfBooks.value.map(book => book.id), previousOrder)
assert.equal(sortingError, '书籍排序失败')

const finishedLoader = source.slice(source.indexOf('const loadFinishedBooks ='), source.indexOf('const loadMarkedBooks ='))
const finished = ref([])
const loading = ref(false)
const requestedPages = []
const loadFinished = new Function('finishedLoading', 'finishedBooks', 'api', 'message', ts.transpile(finishedLoader + '\nreturn loadFinishedBooks;', { target: ts.ScriptTarget.ES2022 }))(
  loading, finished,
  { get: async (path, { params }) => {
    assert.equal(params.status, 'FINISHED')
    requestedPages.push(params.page)
    return { data: { content: Array.from({ length: params.page === 0 ? 100 : 25 }, (_, id) => ({ id: params.page * 100 + id })), totalPages: 2 } }
  } },
  { error: text => { throw new Error(text) } },
)
await loadFinished()
assert.deepEqual(requestedPages, [0, 1])
assert.equal(finished.value.length, 125)
assert.equal(finished.value[124].id, 124)
assert.equal(loading.value, false)

for (const name of names) {
  const collection = name === 'shelf' ? 'pagedShelfBooks' : name === 'lists' ? 'pagedBookLists' : `paged${name[0].toUpperCase() + name.slice(1)}Books`
  assert.ok(source.includes(`in ${collection}"`), `${name} renders only its current page`)
}
assert.match(source, /moveShelfBookOrder\(shelfPageOffset \+ bookIndex, -1\)/)
assert.match(source, /moveShelfBookOrder\(shelfPageOffset \+ bookIndex, 1\)/)
assert.match(source, /status: 'FINISHED'/)
assert.match(source, /while \(page < totalPages\)/)
console.log('Shelf pagination: sizes, account persistence/hydration, page reset, boundaries, independent tabs, shrinking collections and cross-page ordering passed')

// Group search operates before pagination and does not mutate manual ordering.
const searchSource = source.slice(source.indexOf('const displayedShelfBooks ='), source.indexOf('watch(shelfKeyword,'))
const groupedShelfBooks = ref([
  { id: 1, title: '三体', author: '刘慈欣', description: '科幻' },
  { id: 2, title: 'DUNE', author: 'Frank Herbert' },
  { id: 3, title: '活着', author: '余华' },
])
const shelfKeyword = ref('')
const searched = new Function('computed', 'groupedShelfBooks', 'shelfKeyword',
  ts.transpile(searchSource + '\nreturn displayedShelfBooks;', { target: ts.ScriptTarget.ES2022 }),
)(computed, groupedShelfBooks, shelfKeyword)
for (const [keyword, expected] of [[' 刘慈欣 ', [1]], ['dune', [2]], ['科幻', [1]], ['不存在', []], ['', [1, 2, 3]]]) {
  shelfKeyword.value = keyword
  assert.deepEqual(searched.value.map(book => book.id), expected)
}
assert.deepEqual(groupedShelfBooks.value.map(book => book.id), [1, 2, 3])
console.log('Shelf search: title, author, description, case normalization, empty results and stable source ordering passed')
