import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [detailSource, booksSource, shelfSource, readerSource, downloadSource] = await Promise.all([
  readFile(new URL('../src/views/BookDetailView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/views/BooksView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/views/ShelfView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/views/ReaderView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/utils/bookDownload.ts', import.meta.url), 'utf8'),
])

assert.match(detailSource, />\{\{ downloadingBook \? '下载中\.\.\.' : '下载到本地' \}\}</)
assert.match(detailSource, /versionId: selectedVersionId\.value/)
assert.match(detailSource, /format: selectedVersionFormat\.value/)

assert.match(booksSource, /aria-label="下载到本地"/)
assert.match(booksSource, /downloadingBookId === row\.id \? '下载中\.\.\.' : '下载到本地'/)
assert.match(booksSource, /@click\.stop="handleDownloadBook\(book\)"/)
assert.match(booksSource, /@click\.stop="handleDownloadBook\(row\)"/)
assert.match(booksSource, /if \(downloadingBookId\.value !== null\) return/)

assert.equal(
  [...shelfSource.matchAll(/aria-label="下载到本地"/g)].length,
  5,
  'every shelf book grid section must expose a download button',
)
assert.equal(
  [...shelfSource.matchAll(/'下载中\.\.\.' : '下载到本地'/g)].length,
  5,
  'every shelf book list section must expose download progress',
)
assert.match(shelfSource, /downloadBookToLocal\(\{[\s\S]*bookId: book\.id,[\s\S]*format: book\.format/)

assert.match(readerSource, /title="下载到本地" @click="handleDownload"/)
assert.match(readerSource, /versionId: selectedVersionId\.value/)
assert.match(readerSource, /format: selectedVersion\.value\?\.format \|\| book\.value\.format/)
assert.match(readerSource, /downloadingBook \? '下载中' : '下载'/)

assert.match(downloadSource, /api\.get\(`\/api\/books\/\$\{bookId\}\/content`/)
assert.match(downloadSource, /params: versionId \? \{ versionId \} : undefined/)
assert.match(downloadSource, /responseType: 'blob'/)
assert.match(downloadSource, /anchor\.download = safeDownloadName\(title, format\)/)
assert.match(downloadSource, /URL\.revokeObjectURL\(downloadUrl\)/)

console.log('Book detail, library, shelf, and reader views expose authenticated local downloads')
