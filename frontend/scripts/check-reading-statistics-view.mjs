import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/views/ReadingStatisticsView.vue', import.meta.url),
  'utf8',
)

assert.match(source, /stats\.value = normalizeStatistics\(data\)/)
assert.match(source, /loading\.value = false[\s\S]*await nextTick\(\)[\s\S]*if \(stats\.value\) renderCharts\(\)/)
assert.doesNotMatch(source, /watch\(stats,/)
assert.match(source, /const localDateKey = \(date: Date\): string =>/)
assert.match(source, /const key = localDateKey\(d\)/)
assert.doesNotMatch(source, /toISOString\(\)\.slice\(0, 10\)/)
assert.match(source, /range: \[data\[0\]\[0\], data\[data\.length - 1\]\[0\]\]/)
assert.match(source, /name: '活跃书籍'/)

for (const field of [
  'ratingDistribution',
  'categoryPreference',
  'authorPreference',
  'formatPreference',
  'topReadingTimeBooks',
]) {
  assert.match(
    source,
    new RegExp(`${field}: Array\\.isArray\\(value\\?\\.${field}\\)`),
    `${field} must tolerate missing data from an older backend`,
  )
}

assert.match(source, /v-else-if="loadError"/)
assert.match(source, /@click="loadStatistics"/)
assert.match(source, /import \{ getCoverUrl \} from '@\/utils\/cover'/)
assert.match(source, /:src="getCoverUrl\(book\.coverUrl\)"/)
assert.match(
  source,
  /import \{ shouldLoadBookCover \} from '@\/utils\/imagePrivacy'/,
  'reading-time ranking must reuse the shared cover privacy guard',
)
assert.match(
  source,
  /<img\s+v-if="book\.coverUrl && shouldLoadBookCover\(book\.bookId\)"\s+:src="getCoverUrl\(book\.coverUrl\)"\s+alt=""\s*\/>\s*<span v-else>/,
  'hidden ranking covers must not create an img/src and must retain the text placeholder',
)

console.log('Reading statistics renders safely and honors book cover privacy')
