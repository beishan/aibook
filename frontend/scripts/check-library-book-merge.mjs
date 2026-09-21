import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const booksView = await readFile(new URL('../src/views/BooksView.vue', import.meta.url), 'utf8')
const mergeDialog = await readFile(new URL('../src/components/BookMergeDialog.vue', import.meta.url), 'utf8')

assert.match(booksView, /@click="openBookMergeEntry"[\s\S]*?<span>合并书籍<\/span>/, 'library header must expose the merge entry')
assert.match(booksView, /:disabled="selectedBooks\.size < 2"[\s\S]*?@click="openSelectedBookMerge"/, 'batch merge must require at least two selected books')
assert.match(booksView, /<BookMergeDialog[\s\S]*?:books="selectedBookItems"[\s\S]*?@complete="handleBookMergeComplete"/, 'library must pass selected books to the merge dialog and refresh after completion')
assert.match(mergeDialog, /role="radiogroup"[\s\S]*?targetBookId === book\.id/, 'merge dialog must let the user select the retained target book')
assert.match(mergeDialog, /api\.get<BookVersion\[]>\(`\/api\/books\/\$\{book\.id\}\/versions`\)/, 'merge dialog must preview every selected book version')
assert.match(mergeDialog, /api\.post\(`\/api\/books\/\$\{target\.id\}\/versions\/import`/, 'merge dialog must reuse the existing version import endpoint')
assert.match(mergeDialog, /sourceHandling:\s*'MERGE'/, 'library merge must migrate source data and remove source books')
assert.match(mergeDialog, /versionIds:\s*versionsFor\(book\.id\)\.map\(version => version\.id\)/, 'merge must include every source version')
assert.match(mergeDialog, /append-to-body/, 'merge overlay must mount outside filtered page containers')

console.log('Library book merge entry, target selection, full-version migration, and refresh flow are wired')
