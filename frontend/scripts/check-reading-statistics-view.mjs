import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/views/ReadingStatisticsView.vue', import.meta.url),
  'utf8',
)

assert.match(source, /stats\.value = normalizeStatistics\(data\)/)
assert.match(source, /loading\.value = false[\s\S]*await nextTick\(\)[\s\S]*if \(stats\.value\) renderCharts\(\)/)
assert.doesNotMatch(source, /watch\(stats,/)

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

console.log('Reading statistics waits for mounted chart containers and degrades safely')
