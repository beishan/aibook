import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const dialogStart = source.indexOf('<el-dialog v-model="chapterDialog"')
const dialogEnd = source.indexOf('</el-dialog>', dialogStart)

assert.ok(dialogStart >= 0 && dialogEnd > dialogStart, 'chapter content must remain in an el-dialog')
const dialog = source.slice(dialogStart, dialogEnd)

assert.match(dialog, /class="chapter-reader-dialog"/, 'chapter dialog must use the temporary-reading shell')
assert.match(dialog, /class="chapter-reader-surface"/, 'chapter dialog must render a reading surface')
assert.match(dialog, />上一章</)
assert.match(dialog, />下一章</)
assert.match(dialog, /阅读设置/)
assert.match(dialog, /readerSettings\.fontSize/)
assert.match(dialog, /readerSettings\.lineHeight/)
assert.doesNotMatch(dialog, /章节目录|toc-panel/, 'chapter popup must not show a table of contents')

assert.match(source, /size:100,sort:'INDEX_ASC'/, 'chapter navigation must load the ordered full chapter index')
assert.match(source, /first\.totalPages-1/, 'chapter navigation must work beyond the visible drawer page')
assert.match(source, /preferencesStore\.setReaderSettings/, 'reading adjustments must use account preferences')
assert.match(source, /handleChapterReaderKeydown/, 'chapter navigation must provide keyboard support')

console.log('Crawler chapter popup provides temporary reading mode without a table of contents')
