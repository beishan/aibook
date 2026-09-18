import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/views/CrawlerView.vue', import.meta.url),
  'utf8',
)

assert.match(source, /function isBookCompleted\(book:CrawlerBook\)\{return book\.crawlStatus==='COMPLETED'\}/)
assert.match(source, /label="进度 \/ 采集结果"/)
assert.match(source, /v-if="isBookCompleted\(row\)" class="book-crawl-result table-result"/)
assert.match(source, /v-if="isBookCompleted\(book\)" class="book-crawl-result card-result"/)
assert.match(source, /v-if="isBookCompleted\(selectedBook\)" class="book-crawl-result drawer-result"/)
assert.match(source, /正文 \{\{ row\.crawledChapterCount \}\} · 待开放 \{\{ row\.pendingReleaseChapterCount \}\} · 失败 \{\{ row\.failedChapterCount \}\}/)
assert.match(source, /@click\.stop="openBook\(row\)">采集结果详情<\/el-button>/)
assert.match(source, /@click\.stop="openBook\(book\)">采集结果详情<\/el-button>/)

const completedBranches = source.match(/v-if="isBookCompleted\(/g) || []
assert.equal(completedBranches.length, 3, 'table, card, and drawer must all replace completed progress')

console.log('Completed crawler books show result summaries and detail entry points')
