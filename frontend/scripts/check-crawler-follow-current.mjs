import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const sync = source.match(/async function syncOpenBookProgress\(options:LoadOptions=\{\}\)\{([\s\S]*?)\n\}\nasync function syncOpenTask/)?.[1]?.replace(/\s+/g, '')

assert.match(source, /chapterFollowActive = computed\(\(\) => followCurrentChapter\.value && !chapterStatusFilter\.value\)/, 'status filtering must suspend following without changing the saved preference')
assert.ok(sync, 'CrawlerView must keep a dedicated open-book progress sync')
assert.match(sync, /latestFocus=chapterFollowActive\.value\?awaitcrawlerApi\.currentChapter/, 'follow mode must locate the current chapter before loading a chapter page')
assert.match(sync, /targetPage=chapterFollowActive\.value&&latestFocus\?latestFocus\.page\+1:chapterPage\.value/, 'follow mode must derive the requested page from the current chapter')
assert.match(sync, /loadTargetPage=\(\)=>crawlerApi\.chapters\(bookId,\{page:targetPage-1/, 'the chapter request must load the derived target page directly')
assert.doesNotMatch(sync, /returnawaitsyncOpenBookProgress/, 'cross-page following must not recurse through stale page requests')
assert.match(sync, /currentCrawlingChapter\.value=chapterFollowActive\.value\?latestFocus\?\.chapter:undefined[\s\S]*?scrollToCurrentCrawlingChapter/, 'the focused chapter must be applied before scrolling')

assert.match(source, /\.chapters-table \.current-crawling-row/, 'scrolling must target the highlighted row in the chapter table')
assert.match(source, /closest<HTMLElement>\('\.el-scrollbar__wrap'\)/, 'following must scroll the table viewport instead of the whole drawer')
assert.match(source, /viewport\.scrollTo\(\{top:viewport\.scrollTop\+rowRect\.top-viewportRect\.top-\(viewport\.clientHeight-rowRect\.height\)\/2/, 'the current chapter must be centered inside the table viewport')

console.log('crawler current-chapter following loads and scrolls the resolved target page')
