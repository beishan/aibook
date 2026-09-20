import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/views/BookDetailView.vue', import.meta.url),
  'utf8',
)

assert.match(source, /class="detail-breadcrumb"/)
assert.match(source, /class="book-hero"/)
assert.match(source, /class="cover-column"/)
assert.match(source, /class="book-summary"/)
assert.match(source, /class="book-side-card"/)
assert.match(source, /class="side-book-info"/)
assert.match(source, /class="version-panel"/)
assert.match(source, /class="detail-segmented-tabs"/)
assert.match(source, /\.book-content \{[\s\S]*padding: 24px;[\s\S]*border-radius: 24px;/)
assert.match(source, /\.version-list \{[\s\S]*grid-template-columns: repeat\(auto-fit, minmax\(300px, 1fr\)\)/)
assert.match(source, /grid-template-columns: 230px minmax\(390px, 1fr\) minmax\(300px, 360px\)/)
assert.match(source, /@media \(max-width: 1240px\)[\s\S]*\.book-side-card \{[\s\S]*grid-column: 1 \/ -1/)
assert.match(source, /@media \(max-width: 768px\)[\s\S]*\.book-hero \{[\s\S]*grid-template-columns: 1fr/)
assert.match(source, /role="tablist"/)
assert.match(source, /@keydown="handleDetailTabKeydown\(\$event, index\)"/)
assert.doesNotMatch(source, /hero-excerpt|heroExcerpt/)

console.log('Book detail view matches the responsive reference layout, keeps accessible tabs, and omits the hero description excerpt')
