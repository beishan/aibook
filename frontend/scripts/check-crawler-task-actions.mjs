import assert from 'node:assert/strict'
import { runInNewContext } from 'node:vm'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const taskTable = source.match(/const TaskTable = defineComponent\(([\s\S]*?)\r?\n\r?\ntype TabKey=/)?.[1]

assert.ok(taskTable, 'CrawlerView must define the shared task table')
assert.match(taskTable, /type:\s*'selection',\s*width:\s*60,\s*reserveSelection:\s*true,\s*fixed:\s*isMobileCrawlerLayout\.value\s*\?\s*false\s*:\s*'left'/, 'the selection column must stay fixed beside the task name')
assert.match(taskTable, /label:\s*'任务',\s*minWidth:\s*240,\s*fixed:\s*isMobileCrawlerLayout\.value\s*\?\s*false\s*:\s*'left'/, 'the task-name column must be fixed on the left')
assert.doesNotMatch(taskTable, /label:\s*'操作'/, 'task actions must not occupy a dedicated column')
assert.match(taskTable, /className: 'task-ending-cell'[\s\S]*?renderTaskActions\(row\)/, 'task actions must be rendered in the final data cell')
assert.match(taskTable, /label:'当前章节'[\s\S]*?label:'创建时间'/, 'the current-chapter column must appear before the created-time column')
assert.match(taskTable, /h\('div',\{class:'task-action-cluster row-hover-action'\}/, 'task actions must use a row-hover action cluster')
assert.match(taskTable, /task-action-details/, 'the details action must have a distinct primary treatment')
assert.match(taskTable, /task-action-pause/, 'the pause action must have a neutral treatment')
assert.match(taskTable, /task-action-cancel/, 'the cancel action must have a danger treatment')
assert.match(taskTable, /circle:true[\s\S]*?task-action-more/, 'the overflow action must use a compact circular button')
assert.match(taskTable, /'RUNNING','WAITING'[\s\S]*?icon:VideoPause[\s\S]*?'aria-label':'暂停任务'/, 'running and waiting tasks must expose an accessible pause icon')
assert.match(taskTable, /row\.status==='PAUSED'[\s\S]*?icon:VideoPlay[\s\S]*?'aria-label':'继续任务'/, 'paused tasks must expose an accessible play icon')
assert.match(taskTable, /'RUNNING','WAITING','PAUSED'[\s\S]*?'取消'/, 'active tasks must expose cancel in the primary action group')
assert.match(taskTable, /h\(ElDropdown,[\s\S]*?'aria-label':'更多操作'/, 'secondary actions must use an Element Plus dropdown')
assert.match(taskTable, /command:'scan-results',label:'扫描结果'/)
assert.match(taskTable, /command:'resume',label:'继续'/)
assert.match(taskTable, /command:'edit',label:'修改'/)
assert.match(taskTable, /command:'delete',label:'删除'/)
assert.match(taskTable, /row\.status==='SUCCESS'&&row\.type==='SITE_SCAN'/, 'completed discovery scans must use the detailed scan result')
assert.match(taskTable, /h\('i',`新增 \$\{row\.newBookCount\}`\)/, 'completed discovery scans must show the new-book count')
assert.match(taskTable, /h\('em',`重复 \$\{row\.duplicateCount\}`\)/, 'completed discovery scans must show the duplicate-book count')
assert.match(taskTable, /showFailureReason:\s*\{\s*type:\s*Boolean,\s*default:\s*false\s*\}/, 'the shared task table must support a dedicated failure-reason column')
assert.match(taskTable, /label:'失败原因',minWidth:280/, 'failed tasks must expose a clearly labeled failure-reason column')
assert.match(taskTable, /row\.errorMessage\?\.trim\(\)\|\|'任务执行失败，未记录详细原因'/, 'failure rows must show the backend error or an explicit fallback')
assert.match(taskTable, /h\(ElTooltip,\{content:reason/, 'long failure reasons must remain available in a tooltip')
assert.match(source, /:show-failure-reason="activeTab === 'failed'"/, 'only the failed-task view should enable the failure-reason column')

const mainActionBlock = taskTable.match(/return h\('div',\{class:'task-action-cluster row-hover-action'\}[\s\S]*?\n\s*\]\)/)?.[0] ?? ''
assert.match(taskTable, /row\.status==='FAILED'\?\{command:'resume',label:'继续'\}/, 'failed tasks must keep resume in the overflow menu')
assert.doesNotMatch(mainActionBlock, /onClick:[^\n]*emit\('delete',row\)/, 'delete must not remain a primary action button')

const queueDialog = source.match(/<el-dialog\s+v-model="queuedTasksDialog"([\s\S]*?)<\/el-dialog>/)?.[1]
assert.ok(queueDialog, 'CrawlerView must define the current-task queue dialog')
assert.match(queueDialog, /label="操作"\s+width="250"\s+:fixed="isMobileCrawlerLayout \? false : 'right'"\s+align="right"/, 'queue actions must remain fixed and right-aligned')
assert.match(queueDialog, /<div class="queued-task-actions row-hover-action">/, 'queue row actions must use the hover-revealed polished action cluster')
assert.match(queueDialog, /:data="pagedQueuedTasks"/, 'queue table must render the current page')
assert.match(queueDialog, /v-model:current-page="queuedTaskPage"/, 'queue dialog must expose pagination')
assert.match(queueDialog, /v-model:page-size="queuedTaskPageSize"/, 'queue dialog must allow changing page size')
assert.match(queueDialog, /:index="index=>index\+\(queuedTaskPage-1\)\*queuedTaskPageSize\+1"/, 'queue row numbering must continue across pages')
assert.match(queueDialog, /运行中任务固定在最前/, 'queue ordering guidance must explain the running-task rule')
assert.match(queueDialog, /queued-task-action-details/)
assert.match(queueDialog, /queued-task-action-pause/)
assert.match(queueDialog, /queued-task-action-resume/)
assert.match(queueDialog, /queued-task-action-prioritize/)
assert.match(queueDialog, /queued-task-action-cancel/)
assert.match(queueDialog, /:icon="VideoPause"[\s\S]*?aria-label="暂停任务"/)
assert.match(queueDialog, /:icon="VideoPlay"[\s\S]*?aria-label="继续任务"/)
assert.match(queueDialog, />优先<\/el-button>/)
assert.match(queueDialog, />详情<\/el-button>/)
assert.match(queueDialog, /commandCurrentTask\(row,'cancel'\)">取消<\/el-button>/, 'active queue tasks must expose cancel')
assert.match(source, /queuedTaskCommand\s*=\s*ref<'pause'\s*\|\s*'resume'\s*\|\s*'cancel'>\(\)/, 'queue command state must track cancellation')
assert.match(source, /commandCurrentTask\(task:CrawlerTask,command:'pause'\|'resume'\|'cancel'\)/, 'queue command handler must support cancellation')
assert.match(source, /currentTaskStatusRank\(task:CrawlerTask\).*task\.status==='RUNNING'\?0:task\.status==='WAITING'\?1:task\.status==='PAUSED'\?2:3/, 'running tasks must always sort before waiting and paused tasks')
assert.match(source, /orderCurrentTasks\(await crawlerApi\.currentTasks\(activeQueue\.value\?\.id\)\)/, 'queue refresh must normalize task status order before pagination')
assert.match(source, /pagedQueuedTasks=computed\(\(\)=>queuedTasks\.value\.slice/, 'queue pagination must slice the normalized full queue')

const crawlerBookTable = source.match(/<el-table\s+v-if="bookViewMode==='table'"([\s\S]*?)<\/el-table>/)?.[1]
assert.ok(crawlerBookTable, 'CrawlerView must define the crawler-book table')
assert.doesNotMatch(crawlerBookTable, /label="操作"/, 'crawler-book actions must not occupy a dedicated column')
assert.match(crawlerBookTable, /label="失败"[\s\S]*?class-name="task-ending-cell"[\s\S]*?crawler-book-action-cluster/, 'crawler-book actions must share the final cell')
assert.match(crawlerBookTable, /class="crawler-book-action-cluster row-hover-action"/, 'crawler-book actions must use a hover-revealed unified rounded cluster')
assert.match(crawlerBookTable, /crawler-book-action-details/, 'crawler-book details must remain a primary action')
assert.match(crawlerBookTable, /crawler-book-action-continue/, 'crawler-book continue must remain a primary action')
assert.match(crawlerBookTable, /crawler-book-action-import/, 'crawler-book import must remain a primary action')
assert.match(crawlerBookTable, /circle\s+:icon="MoreFilled"\s+class="crawler-book-action-more"/, 'crawler-book secondary actions must use a compact overflow button')
assert.match(crawlerBookTable, /command="updates"[\s\S]*?command="metadata"[\s\S]*?command="trial"[\s\S]*?command="generate"/, 'crawler-book overflow must preserve all secondary actions')
assert.match(source, /function handleCrawlerBookTableMore\([\s\S]*?command==='trial'[\s\S]*?startTrial\(book\)/, 'crawler-book overflow must route the trial action')

const discoveryBookTable = source.match(/<el-table\s+v-if="discoveryViewMode==='table'"([\s\S]*?)<\/el-table>/)?.[1]
assert.ok(discoveryBookTable, 'CrawlerView must define the discovery-book table')
assert.match(discoveryBookTable, /label="操作"\s+width="180"\s+:fixed="isMobileCrawlerLayout \? false : 'right'"\s+align="right"/, 'discovery-book actions must match the crawler-book fixed column alignment')
assert.match(discoveryBookTable, /class="crawler-book-action-cluster discovery-book-action-cluster row-hover-action"/, 'discovery-book actions must reuse the hover-revealed unified rounded cluster')
assert.match(discoveryBookTable, /crawler-book-action-details[\s\S]*?crawler-book-action-import/, 'discovery-book details and crawl must remain primary actions')
assert.match(discoveryBookTable, /circle\s+:icon="MoreFilled"\s+class="crawler-book-action-more"/, 'discovery-book secondary actions must use a compact overflow button')
assert.match(discoveryBookTable, /command="book-lists"[\s\S]*?command="metadata"[\s\S]*?command="website"[\s\S]*?command="ignore"[\s\S]*?command="blacklist"/, 'discovery-book overflow must preserve all secondary actions')
assert.match(source, /function handleDiscoveryCardMore\([\s\S]*?command==='ignore'[\s\S]*?batchDiscovery\('IGNORED',\[book\.id\]\)/, 'discovery-book overflow must route the ignore action')
assert.match(source, /Star, StarFilled[\s\S]*?VideoPause, VideoPlay/, 'crawler controls must reuse the Element Plus star, pause, and play icons')

console.log('Crawler task, discovery-book, and crawler-book actions use polished action clusters with compact overflow menus')

// Exercise the production geometry with a table wider than its visible viewport.
const positioningBody = source.match(/const updateActionPosition = \(\) => \{([\s\S]*?)\n    \}/)?.[1]
assert.ok(positioningBody, 'task actions must track the visible table edge')
for (const [scrollWidth, clientWidth, scrollLeft] of [
  [1800, 900, 0],
  [1800, 900, 450],
  [1800, 900, 900],
  [1800, 600, 300],
  [900, 900, 0],
]) {
  const styles = new Map()
  runInNewContext('(() => {' + positioningBody + '})()', {
    scrollWrapper: { scrollWidth, clientWidth, scrollLeft },
    table: { $el: { style: { setProperty: (key, value) => styles.set(key, value) } } },
  })
  const offset = Number.parseFloat(styles.get('--task-actions-hidden-right'))
  assert.equal(scrollWidth - scrollLeft - offset, clientWidth, 'action right edge must follow the visible viewport throughout scrolling and resizing')
  assert.equal(styles.get('--task-actions-viewport-width'), clientWidth + 'px')
}
assert.match(source, /right: calc\(12px \+ var\(--task-actions-hidden-right, 0px\)\)/)
console.log('Task action positioning follows the visible right edge at scroll endpoints, midway, and after resize')

const overlayRule = source.match(/:deep\(\.el-table__row \.task-ending-cell :is\(\.task-action-cluster, \.crawler-book-action-cluster\)\) \{([\s\S]*?)\}/)?.[1]
assert.ok(overlayRule, 'both list action groups must share their positioning rule')
assert.match(overlayRule, /top: 50%;/)
assert.match(overlayRule, /translate: 0 -50%;/, 'vertical centering must use independent translate so hover transforms cannot override it')
assert.doesNotMatch(overlayRule, /transform:/, 'centering must not compete with the common hover transform')
assert.match(source, /@media \(hover: none\) \{[\s\S]*?task-ending-cell[\s\S]*?position: static;[\s\S]*?translate: none;/, 'touch inline actions must reset centering')
console.log('Task and crawler-book overlays center independently of hover and focus transforms')
