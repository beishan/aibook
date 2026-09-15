import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const crawlerViewSource = await readFile(
  new URL('../src/views/CrawlerView.vue', import.meta.url),
  'utf8',
)
const pollingSource = crawlerViewSource.match(
  /async function pollCrawlerProgress\(\)\{([\s\S]*?)\n}\nasync function syncOpenBookProgress/,
)?.[1]

assert.ok(pollingSource, 'crawler progress polling must exist')
assert.doesNotMatch(
  pollingSource,
  /loadDiscoveredBooks/,
  'automatic progress polling must not refresh the discovered-books page',
)
assert.match(
  crawlerViewSource,
  /async function refresh\(\)\{[^\n]*loadDiscoveredBooks\(\)/,
  'explicit full refresh must continue to reload discovered books',
)
assert.match(
  crawlerViewSource,
  /@current-change="loadDiscoveredBooks\(\)"/,
  'discovered-book pagination must continue to load the selected page',
)

console.log('Discovered books only refresh through explicit page actions')
