import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const dialogStart = source.indexOf('<el-dialog v-model="chapterDialog"')
const dialogEnd = source.indexOf('</el-dialog>', dialogStart)

assert.ok(dialogStart >= 0 && dialogEnd > dialogStart, 'chapter content must remain in an el-dialog')
const dialog = source.slice(dialogStart, dialogEnd)

assert.match(dialog, /class="chapter-reader-dialog"/, 'chapter dialog must use the temporary-reading shell')
assert.match(dialog, /:show-close="false"/, 'chapter dialog must hide the outer Element Plus close control')
assert.match(dialog, /class="chapter-reader-surface"/, 'chapter dialog must render a reading surface')
assert.match(dialog, /class="chapter-reader-heading"[\s\S]*?<strong>\{\{ chapterDetail\?\.title/, 'chapter title must live at the left of the reading toolbar')
assert.match(dialog, /class="chapter-reader-close"[\s\S]*?@click="chapterDialog=false"/, 'reading toolbar must own the close control')
assert.doesNotMatch(dialog.slice(0, dialog.indexOf('>')), /:title=/, 'chapter dialog must not render the outer title bar')
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
assert.match(source, /:global\(\.chapter-reader-dialog\)\{--el-dialog-padding-primary:0;/, 'reading surface must fill the dialog without outer padding')
assert.match(source, /:global\(\.chapter-reader-dialog\)\{[^}]*height:80vh;height:80dvh;/, 'chapter dialog must follow about 80 percent of the viewport height')
assert.match(source, /:global\(\.chapter-reader-dialog\)[^}]*border:0!important;[^}]*background:transparent!important;[^}]*box-shadow:none!important/, 'outer dialog frame must be fully transparent and borderless')
assert.match(source, /\.chapter-reader\{[^}]*height:100%;[^}]*border:0;[^}]*border-radius:18px/, 'reading surface must fill the dialog and own all four rounded corners')
assert.match(source, /\.chapter-reader-stage\{[^}]*height:auto;min-height:0;/, 'reading content must expand into the available dialog height')

console.log('Crawler chapter popup provides temporary reading mode without a table of contents')
