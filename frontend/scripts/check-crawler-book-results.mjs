import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/views/CrawlerView.vue', import.meta.url),
  'utf8',
)

assert.match(source, /function isBookCompleted\(book:CrawlerBook\)\{return book\.crawlStatus==='COMPLETED'\}/)
assert.match(source, /label="进度 \/ 采集结果"/)
assert.match(source, /v-if="isBookCompleted\(row\)" class="book-crawl-result table-result"/)
const cardBody = source.match(/<div class="discovery-card-body crawler-book-card-body">([\s\S]*?)\s*<\/article>/)?.[1]
assert.ok(cardBody, 'crawler card body must exist')
assert.match(cardBody, /class="crawler-book-card-summary"/)
assert.match(cardBody, /\{\{ book\.crawledChapterCount \}\} \/ \{\{ book\.chapterCount \}\} 章/)
assert.doesNotMatch(cardBody, /book\.tags|book\.category|待开放|失败|采集结果详情|<dl>/,
  'card body must keep secondary metadata and result details in the detail view')
assert.match(cardBody, /v-if="!isBookCompleted\(book\)"[\s\S]*?:show-text="false"[\s\S]*?:stroke-width="3"/)
assert.match(source, /v-if="isBookCompleted\(selectedBook\)" class="book-crawl-result drawer-result"/)
assert.match(source, /正文 \{\{ row\.crawledChapterCount \}\} · 待开放 \{\{ row\.pendingReleaseChapterCount \}\} · 失败 \{\{ row\.failedChapterCount \}\}/)
assert.match(source, /@click\.stop="openBook\(row\)">采集结果详情<\/el-button>/)
assert.match(cardBody, /@click\.stop="openBook\(book\)"/,
  'book title must still open the complete result details')

const completedBranches = source.match(/v-if="isBookCompleted\(/g) || []
assert.equal(completedBranches.length, 2, 'table and drawer retain full completed-result summaries')

console.log('Crawler cards keep compact chapter summaries; table and drawer retain full results and detail entry points')
