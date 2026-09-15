import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const readerSource = await readFile(
  new URL('../src/views/ReaderView.vue', import.meta.url),
  'utf8',
)

assert.match(
  readerSource,
  /import\s*{\s*shouldLoadBookCover\s*}\s*from\s*['"]@\/utils\/imagePrivacy['"]/,
  'ReaderView must use the shared book-cover privacy guard',
)

const readerCover = readerSource.match(
  /<div class="reader-cover" aria-hidden="true">([\s\S]*?)<\/div>/,
)?.[1]

assert.ok(readerCover, 'ReaderView header cover container must exist')
assert.match(
  readerCover,
  /<img\s+v-if="book\.coverUrl && shouldLoadBookCover\(book\.id\)"\s+:src="getCoverUrl\(book\.coverUrl\)"\s+alt=""\s*\/?>/,
  'cover img/src must only be created when image privacy permits loading',
)
assert.match(
  readerCover,
  /<span v-else>{{ book\.title\?\.slice\(0, 1\) \|\| '书' }}<\/span>/,
  'the no-cover placeholder must remain visible when the cover is hidden',
)

console.log('ReaderView header cover obeys image privacy without losing its placeholder')
