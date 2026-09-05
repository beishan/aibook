<template>
  <main class="crawler-page">
    <header class="hero">
      <div>
        <p class="eyebrow">COLLECTION PIPELINE</p>
        <h1>书籍爬虫</h1>
        <p class="subtitle">从授权站点采集、清洗与校验，再安全发布到你的私人书库。</p>
      </div>
      <el-button type="primary" :icon="Link" @click="crawlDialog = true">URL 手动采集</el-button>
    </header>

    <div class="segmented-wrap" role="tablist" aria-label="采集中心栏目" @keydown="handleTabKey">
      <span class="segment-indicator" :style="{ transform: `translateX(${activeIndex * 100}%)`, width: `${100 / tabs.length}%` }" />
      <button v-for="tab in tabs" :key="tab.key" class="segment" :class="{ active: activeTab === tab.key }"
        role="tab" :aria-selected="activeTab === tab.key" :tabindex="activeTab === tab.key ? 0 : -1" @click="activeTab = tab.key">
        <el-icon><component :is="tab.icon" /></el-icon><span>{{ tab.label }}</span>
        <b v-if="tab.count">{{ tab.count }}</b>
      </button>
    </div>

    <section v-if="activeTab === 'overview'" class="panel" role="tabpanel">
      <div class="metric-grid">
        <article v-for="metric in metrics" :key="metric.label" class="metric-card">
          <span class="metric-icon"><el-icon><component :is="metric.icon" /></el-icon></span>
          <div><strong>{{ metric.value }}</strong><p>{{ metric.label }}</p></div>
          <small>{{ metric.note }}</small>
        </article>
      </div>
      <div class="section-heading"><div><p class="eyebrow">LIVE QUEUE</p><h2>最近任务</h2></div><el-button text :icon="Refresh" @click="refresh">刷新</el-button></div>
      <TaskTable :tasks="dashboard?.recentTasks || []" @command="runTaskCommand" />
    </section>

    <section v-else-if="activeTab === 'sites'" class="panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">SOURCES</p><h2>采集网站</h2></div><el-button type="primary" :icon="Plus" @click="openSite()">新增网站</el-button></div>
      <div v-if="sites.length" class="site-grid">
        <article v-for="site in sites" :key="site.id" class="site-card">
          <div class="site-top"><span class="site-mark">{{ site.siteName.slice(0, 1) }}</span><div><h3>{{ site.siteName }}</h3><a :href="site.homeUrl || site.baseUrl" target="_blank">{{ site.baseUrl }}</a></div><el-tag :type="site.enabled ? 'success' : 'info'">{{ site.enabled ? '启用' : '停用' }}</el-tag></div>
          <div class="site-stats"><span><b>{{ site.bookCount }}</b> 本书</span><span><b>{{ site.requestIntervalMillis }}</b> ms 间隔</span><span><b>{{ site.maxConcurrency }}</b> 并发</span></div>
          <div class="automation"><span :class="{on:site.autoUpdate}">更新</span><span :class="{on:site.autoCrawl}">自动采集</span><span :class="{on:site.autoImportLibrary}">自动入库</span></div>
          <footer><el-button text @click="openSite(site)">编辑规则</el-button><el-button text @click="openCrawl(site.id)">采集 URL</el-button><el-button text type="danger" @click="removeSite(site)">删除</el-button></footer>
        </article>
      </div>
      <el-empty v-else description="尚未配置采集网站" />
    </section>

    <section v-else-if="activeTab === 'books'" class="panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">STRUCTURED BOOKS</p><h2>采集书籍</h2></div><el-input v-model="bookKeyword" clearable placeholder="搜索书名、作者或网站" :prefix-icon="Search" class="search" /></div>
      <el-table :data="filteredBooks" @row-click="openBook" class="data-table">
        <el-table-column label="书籍" min-width="260"><template #default="{row}"><div class="book-cell"><div class="mini-cover">{{ row.bookName.slice(0,1) }}</div><div><strong>{{ row.bookName }}</strong><p>{{ row.author || '未知作者' }} · {{ row.siteName }}</p></div></div></template></el-table-column>
        <el-table-column label="进度" min-width="190"><template #default="{row}"><el-progress :percentage="progress(row)" :stroke-width="7" /><small>{{ row.crawledChapterCount }} / {{ row.chapterCount }} 章</small></template></el-table-column>
        <el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="statusType(row.crawlStatus)">{{ statusLabel(row.crawlStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="失败" width="80" prop="failedChapterCount" />
        <el-table-column label="操作" width="210"><template #default="{row}"><el-button text @click.stop="continueCrawl(row)">继续</el-button><el-button text :disabled="row.crawlStatus!=='COMPLETED'" @click.stop="generate(row)">生成</el-button><el-button text type="primary" :disabled="row.crawlStatus!=='COMPLETED' || row.importStatus==='IMPORTED'" @click.stop="importBook(row)">入库</el-button></template></el-table-column>
      </el-table>
    </section>

    <section v-else class="panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">{{ activeTab === 'failed' ? 'NEEDS ATTENTION' : 'PERSISTENT QUEUE' }}</p><h2>{{ activeTab === 'failed' ? '失败任务' : '采集任务' }}</h2></div><el-button text :icon="Refresh" @click="refresh">刷新</el-button></div>
      <TaskTable :tasks="activeTab === 'failed' ? failedTasks : tasks" @command="runTaskCommand" />
    </section>

    <el-dialog v-model="siteDialog" :title="editingSite ? '编辑采集网站' : '新增采集网站'" width="min(760px, 94vw)" destroy-on-close>
      <el-form label-position="top" class="site-form">
        <div class="form-grid"><el-form-item label="网站名称"><el-input v-model="siteForm.siteName" /></el-form-item><el-form-item label="唯一编码"><el-input v-model="siteForm.siteCode" placeholder="example_novel" /></el-form-item></div>
        <el-form-item label="根地址"><el-input v-model="siteForm.baseUrl" placeholder="https://example.com" /></el-form-item>
        <el-form-item label="首页地址"><el-input v-model="siteForm.homeUrl" placeholder="留空时使用根地址" /></el-form-item>
        <div class="form-grid"><el-form-item label="字符编码"><el-select v-model="siteForm.encoding"><el-option label="UTF-8" value="UTF-8"/><el-option label="GBK" value="GBK"/><el-option label="GB18030" value="GB18030"/></el-select></el-form-item><el-form-item label="请求间隔（ms）"><el-input-number v-model="siteForm.requestIntervalMillis" :min="100" :step="100" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="随机延迟（ms）"><el-input-number v-model="siteForm.randomDelayMillis" :min="0" :step="100" /></el-form-item><el-form-item label="最大并发"><el-input-number v-model="siteForm.maxConcurrency" :min="1" :max="8" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="超时（ms）"><el-input-number v-model="siteForm.timeoutMillis" :min="1000" :step="1000" /></el-form-item><el-form-item label="失败重试"><el-input-number v-model="siteForm.retryCount" :min="0" :max="8" /></el-form-item></div>
        <el-form-item label="User-Agent"><el-input v-model="siteForm.userAgent" placeholder="留空时使用合规的 AiBookCrawler 标识" /></el-form-item>
        <div class="form-grid"><el-form-item label="代理"><el-input v-model="siteForm.proxy" placeholder="http://127.0.0.1:7890" /></el-form-item><el-form-item label="Cookie"><el-input v-model="siteForm.cookie" type="password" show-password autocomplete="off" /></el-form-item></div>
        <el-form-item label="自定义 Header JSON"><el-input v-model="siteForm.headersJson" type="textarea" placeholder='{"Referer":"https://example.com/"}' /></el-form-item>
        <div class="switch-row"><el-switch v-model="siteForm.enabled" active-text="启用网站"/><el-switch v-model="siteForm.autoUpdate" active-text="自动检查更新"/><el-switch v-model="siteForm.autoCrawl" active-text="自动采集"/><el-switch v-model="siteForm.autoImportLibrary" active-text="自动入库"/></div>
        <div class="rule-block"><p class="eyebrow">CSS SELECTORS</p><h3>详情与章节规则</h3><p>属性读取可写为 <code>.cover::data-src</code>，链接与图片默认读取 href/src。</p></div>
        <div class="form-grid"><el-form-item label="书名 Selector"><el-input v-model="siteForm.rule.titleSelector" placeholder="h1.book-title" /></el-form-item><el-form-item label="作者 Selector"><el-input v-model="siteForm.rule.authorSelector" placeholder=".author" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="简介 Selector"><el-input v-model="siteForm.rule.descriptionSelector" placeholder="#intro" /></el-form-item><el-form-item label="目录页链接 Selector"><el-input v-model="siteForm.rule.chapterListUrlSelector" placeholder="a.catalog" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="章节项 Selector"><el-input v-model="siteForm.rule.chapterItemSelector" placeholder="#list dd" /></el-form-item><el-form-item label="章节链接 Selector"><el-input v-model="siteForm.rule.chapterUrlSelector" placeholder="a" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="正文 Selector"><el-input v-model="siteForm.rule.contentSelector" placeholder="#content" /></el-form-item><el-form-item label="章节标题 Selector"><el-input v-model="siteForm.rule.contentTitleSelector" placeholder="h1" /></el-form-item></div>
        <el-form-item label="删除节点（逗号或换行分隔）"><el-input v-model="siteForm.rule.removeSelectors" type="textarea" placeholder=".ads, .navigation" /></el-form-item>
        <el-form-item label="正则替换 JSON"><el-input v-model="siteForm.rule.regexReplacementsJson" type="textarea" placeholder='{"www\\.example\\.com":""}' /></el-form-item>
        <el-alert type="warning" :closable="false" title="仅采集你有权阅读的免费内容；系统不会绕过登录、付费、验证码或访问控制。" />
      </el-form>
      <template #footer><el-button @click="siteDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveSite">保存网站与规则</el-button></template>
    </el-dialog>

    <el-dialog v-model="crawlDialog" title="URL 手动采集" width="min(560px, 94vw)">
      <el-form label-position="top"><el-form-item label="采集网站"><el-select v-model="crawlForm.siteId" placeholder="选择已启用网站"><el-option v-for="site in sites.filter(s=>s.enabled)" :key="site.id" :label="site.siteName" :value="site.id" /></el-select></el-form-item><el-form-item label="书籍详情 URL"><el-input v-model="crawlForm.url" placeholder="https://example.com/book/123/" /></el-form-item></el-form>
      <template #footer><el-button @click="crawlDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="startCrawl">创建采集任务</el-button></template>
    </el-dialog>

    <el-drawer v-model="bookDrawer" size="min(760px, 96vw)" :title="selectedBook?.bookName || '采集书籍详情'">
      <template v-if="selectedBook"><div class="book-summary"><div class="large-cover">{{ selectedBook.bookName.slice(0,1) }}</div><div><h2>{{ selectedBook.bookName }}</h2><p>{{ selectedBook.author || '未知作者' }} · {{ selectedBook.siteName }}</p><el-progress :percentage="progress(selectedBook)"/><small>{{ selectedBook.crawledChapterCount }} / {{ selectedBook.chapterCount }} 章，失败 {{ selectedBook.failedChapterCount }}</small></div></div><div class="drawer-actions"><el-button @click="continueCrawl(selectedBook)">继续采集</el-button><el-button :disabled="!selectedBook.failedChapterCount" @click="retryFailures(selectedBook)">重试失败</el-button><el-button type="primary" :disabled="selectedBook.crawlStatus!=='COMPLETED'" @click="generate(selectedBook)">生成 TXT + EPUB</el-button></div><el-table :data="chapters" max-height="560" @row-click="openChapter"><el-table-column prop="chapterIndex" label="#" width="65"/><el-table-column prop="chapterName" label="章节" min-width="220"/><el-table-column prop="wordCount" label="字数" width="90"/><el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="statusType(row.crawlStatus)">{{ statusLabel(row.crawlStatus) }}</el-tag></template></el-table-column></el-table></template>
    </el-drawer>
    <el-dialog v-model="chapterDialog" :title="chapterDetail?.title || '章节正文'" width="min(760px, 94vw)"><a v-if="chapterDetail" :href="chapterDetail.url" target="_blank">查看原始网页</a><pre class="chapter-content">{{ chapterDetail?.content || chapterDetail?.errorMessage }}</pre></el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Collection, Connection, DataAnalysis, Document, Link, List, Plus, Refresh, Search, Tickets, Warning } from '@element-plus/icons-vue'
import { ElButton, ElProgress, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { crawlerApi, type CrawlerBook, type CrawlerChapter, type CrawlerDashboard, type CrawlerSite, type CrawlerSitePayload, type CrawlerTask } from '@/utils/crawler'
import { confirm, message } from '@/utils/message'

const TaskTable = defineComponent({ props:{ tasks:{type:Array as ()=>CrawlerTask[],required:true}}, emits:['command'], setup(props,{emit}) { return () => h(ElTable,{data:props.tasks,class:'data-table'},()=>[
  h(ElTableColumn,{label:'任务',minWidth:240},{default:({row}:{row:CrawlerTask})=>h('div',{class:'task-name'},[h('strong',row.bookName||row.type),h('p',`${row.siteName} · ${row.type}`)])}),
  h(ElTableColumn,{label:'进度',minWidth:180},{default:({row}:{row:CrawlerTask})=>h(ElProgress,{percentage:row.totalCount?Math.round(row.successCount/row.totalCount*100):0,strokeWidth:7})}),
  h(ElTableColumn,{label:'状态',width:130},{default:({row}:{row:CrawlerTask})=>h(ElTag,{type:statusType(row.status)},()=>statusLabel(row.status))}),
  h(ElTableColumn,{label:'当前章节',prop:'currentChapter',minWidth:150}),
  h(ElTableColumn,{label:'操作',width:170},{default:({row}:{row:CrawlerTask})=>[row.status==='RUNNING'?h(ElButton,{text:true,onClick:()=>emit('command',row,'pause')},()=> '暂停'):null,row.status==='PAUSED'?h(ElButton,{text:true,onClick:()=>emit('command',row,'resume')},()=> '继续'):null,['RUNNING','WAITING','PAUSED'].includes(row.status)?h(ElButton,{text:true,type:'danger',onClick:()=>emit('command',row,'cancel')},()=> '取消'):null]})
]) }})

type TabKey='overview'|'sites'|'books'|'tasks'|'failed'
const activeTab=ref<TabKey>('overview'), dashboard=ref<CrawlerDashboard>(), sites=ref<CrawlerSite[]>([]), books=ref<CrawlerBook[]>([]), tasks=ref<CrawlerTask[]>([])
const siteDialog=ref(false), crawlDialog=ref(false), bookDrawer=ref(false), chapterDialog=ref(false), saving=ref(false), editingSite=ref<CrawlerSite>(), selectedBook=ref<CrawlerBook>(), chapters=ref<CrawlerChapter[]>([]), chapterDetail=ref<{title:string;url:string;content:string;errorMessage:string}>(), bookKeyword=ref('')
const crawlForm=reactive<{siteId?:number;url:string}>({url:''})
const emptySite=():CrawlerSitePayload=>({siteName:'',siteCode:'',baseUrl:'',homeUrl:'',enabled:false,autoScan:false,autoCrawl:false,autoUpdate:true,autoImportLibrary:false,requestIntervalMillis:1500,randomDelayMillis:1000,maxConcurrency:1,timeoutMillis:15000,retryCount:2,encoding:'UTF-8',rule:{titleSelector:'',authorSelector:'',descriptionSelector:'',chapterListUrlSelector:'',chapterItemSelector:'',chapterTitleSelector:':scope',chapterUrlSelector:'a',contentTitleSelector:'h1',contentSelector:'',removeSelectors:'',regexReplacementsJson:'',minChapterLength:100}})
const siteForm=reactive<CrawlerSitePayload>(emptySite())
const tabs=computed(()=>[{key:'overview' as const,label:'采集概览',icon:DataAnalysis,count:0},{key:'sites' as const,label:'采集网站',icon:Connection,count:sites.value.length},{key:'books' as const,label:'采集书籍',icon:Collection,count:books.value.length},{key:'tasks' as const,label:'采集任务',icon:List,count:tasks.value.filter(t=>['RUNNING','WAITING','PAUSED'].includes(t.status)).length},{key:'failed' as const,label:'失败任务',icon:Warning,count:failedTasks.value.length}])
const activeIndex=computed(()=>tabs.value.findIndex(t=>t.key===activeTab.value)), failedTasks=computed(()=>tasks.value.filter(t=>['FAILED','PARTIAL_SUCCESS'].includes(t.status)))
const filteredBooks=computed(()=>{const q=bookKeyword.value.trim().toLowerCase();return q?books.value.filter(b=>[b.bookName,b.author,b.siteName,b.externalBookId].some(v=>v?.toLowerCase().includes(q))):books.value})
const metrics=computed(()=>[{label:'采集网站',value:dashboard.value?.siteCount||0,note:`${dashboard.value?.enabledSiteCount||0} 个启用`,icon:Connection},{label:'采集书籍',value:dashboard.value?.bookCount||0,note:`今日 +${dashboard.value?.todayNewBooks||0}`,icon:Collection},{label:'已采集完成',value:dashboard.value?.completedBookCount||0,note:`${dashboard.value?.readyToImportCount||0} 本待入库`,icon:Document},{label:'今日新增章节',value:dashboard.value?.todayNewChapters||0,note:`${dashboard.value?.crawlingBookCount||0} 本采集中`,icon:Tickets}])
let timer:number|undefined
onMounted(async()=>{await refresh();timer=window.setInterval(()=>{if(tasks.value.some(t=>['RUNNING','WAITING'].includes(t.status))) void refresh()},4000)})
onUnmounted(()=>{if(timer)window.clearInterval(timer)})
async function refresh(){[dashboard.value,sites.value,books.value,tasks.value]=await Promise.all([crawlerApi.dashboard(),crawlerApi.sites(),crawlerApi.books(),crawlerApi.tasks()])}
function openSite(site?:CrawlerSite){editingSite.value=site;Object.assign(siteForm,emptySite(),site?JSON.parse(JSON.stringify(site)):{});siteDialog.value=true}
function openCrawl(id:number){crawlForm.siteId=id;crawlDialog.value=true}
async function saveSite(){if(!siteForm.siteName||!siteForm.siteCode||!siteForm.baseUrl||!siteForm.rule.titleSelector||!siteForm.rule.chapterItemSelector||!siteForm.rule.contentSelector)return message.warning('请填写网站、书名、章节列表和正文的必要规则');saving.value=true;try{editingSite.value?await crawlerApi.updateSite(editingSite.value.id,siteForm):await crawlerApi.createSite(siteForm);message.success('网站与解析规则已保存');siteDialog.value=false;await refresh()}finally{saving.value=false}}
async function removeSite(site:CrawlerSite){if(await confirm(`确定删除采集网站“${site.siteName}”吗？`)){await crawlerApi.deleteSite(site.id);message.success('采集网站已删除');await refresh()}}
async function startCrawl(){if(!crawlForm.siteId||!crawlForm.url)return message.warning('请选择网站并填写书籍 URL');saving.value=true;try{await crawlerApi.crawlUrl(crawlForm.siteId,crawlForm.url);crawlDialog.value=false;activeTab.value='tasks';message.success('采集任务已创建');await refresh()}finally{saving.value=false}}
async function openBook(book:CrawlerBook){selectedBook.value=book;chapters.value=await crawlerApi.chapters(book.id);bookDrawer.value=true}
async function continueCrawl(book:CrawlerBook){await crawlerApi.continueBook(book.id);message.success('续采任务已创建');await refresh()}
async function retryFailures(book:CrawlerBook){await crawlerApi.retryFailures(book.id);message.success('失败章节已进入重试队列');await refresh()}
async function generate(book:CrawlerBook){await crawlerApi.generate(book.id,['TXT','EPUB']);message.success('TXT 与 EPUB 已生成并保留在采集中心')}
async function importBook(book:CrawlerBook){const result=await crawlerApi.importBook(book.id,'EPUB');message.success(`已加入书库，书籍 ID：${result.bookId}`);await refresh()}
async function runTaskCommand(task:CrawlerTask,command:'pause'|'resume'|'cancel'){await crawlerApi.taskCommand(task.id,command);await refresh()}
async function openChapter(chapter:CrawlerChapter){if(!selectedBook.value)return;chapterDetail.value=await crawlerApi.chapter(selectedBook.value.id,chapter.id);chapterDialog.value=true}
function progress(book:CrawlerBook){return book.chapterCount?Math.round(book.crawledChapterCount/book.chapterCount*100):0}
function statusLabel(status:string){return ({WAITING:'等待中',RUNNING:'运行中',PAUSED:'已暂停',SUCCESS:'成功',PARTIAL_SUCCESS:'部分成功',FAILED:'失败',CANCELLED:'已取消',DISCOVERED:'已发现',CRAWLING_METADATA:'解析元信息',CRAWLING_CHAPTER_LIST:'解析目录',CRAWLING_CONTENT:'采集正文',COMPLETED:'已完成',NOT_CRAWLED:'未采集',CRAWLING:'采集中',CONTENT_SUSPECTED:'内容异常'} as Record<string,string>)[status]||status}
function statusType(status:string):''|'success'|'warning'|'info'|'danger'{if(['SUCCESS','COMPLETED'].includes(status))return'success';if(['FAILED','CONTENT_SUSPECTED'].includes(status))return'danger';if(['PARTIAL_SUCCESS','PAUSED'].includes(status))return'warning';return'info'}
function handleTabKey(e:KeyboardEvent){const keys=tabs.value.map(t=>t.key);let i=activeIndex.value;if(['ArrowRight','ArrowDown'].includes(e.key))i=(i+1)%keys.length;else if(['ArrowLeft','ArrowUp'].includes(e.key))i=(i-1+keys.length)%keys.length;else if(e.key==='Home')i=0;else if(e.key==='End')i=keys.length-1;else return;e.preventDefault();activeTab.value=keys[i];requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.segment')[i]?.focus())}
</script>

<style scoped>
.crawler-page{display:grid;gap:22px;padding-bottom:50px}.hero{position:relative;display:flex;align-items:flex-end;justify-content:space-between;min-height:180px;padding:32px;overflow:hidden;border:1px solid var(--border-color);border-radius:28px;background:linear-gradient(125deg,var(--surface-elevated),var(--surface-card));box-shadow:var(--shadow-lg)}.hero:after{position:absolute;right:8%;bottom:-90px;width:280px;height:280px;border:42px solid var(--primary-alpha-10);border-radius:50%;content:''}.hero>*{position:relative;z-index:1}.eyebrow{margin-bottom:6px;color:var(--primary);font-size:11px;font-weight:800;letter-spacing:.16em}.hero h1{font-family:'Iowan Old Style','Songti SC',serif;font-size:42px;letter-spacing:-.04em}.subtitle{max-width:600px;margin-top:10px;color:var(--text-secondary)}.segmented-wrap{position:relative;display:grid;grid-template-columns:repeat(5,minmax(120px,1fr));overflow-x:auto;padding:5px;border:1px solid var(--border-color);border-radius:16px;background:var(--surface-card);isolation:isolate}.segment-indicator{position:absolute;top:5px;bottom:5px;left:5px;z-index:-1;width:20%;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .28s cubic-bezier(.2,.8,.2,1)}.segment{display:flex;align-items:center;justify-content:center;gap:7px;min-width:120px;padding:11px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.segment.active{color:var(--primary);font-weight:700}.segment:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:12px}.segment b{min-width:19px;padding:1px 5px;border-radius:99px;background:var(--primary-alpha-10);font-size:11px}.panel{min-height:430px;padding:26px;border:1px solid var(--border-color);border-radius:22px;background:var(--surface-card);box-shadow:var(--shadow-md)}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:34px}.metric-card{display:grid;grid-template-columns:auto 1fr;gap:13px;padding:20px;border:1px solid var(--border-color-light);border-radius:18px;background:linear-gradient(145deg,var(--surface-elevated),var(--surface-card))}.metric-icon{display:grid;width:42px;height:42px;place-items:center;border-radius:13px;background:var(--primary-alpha-10);color:var(--primary);font-size:20px}.metric-card strong{font-size:27px}.metric-card p,.metric-card small,.book-cell p,.task-name p{color:var(--text-secondary);font-size:12px}.metric-card small{grid-column:2}.section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.section-heading h2{font-family:'Iowan Old Style','Songti SC',serif;font-size:25px}.search{width:280px}.site-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16px}.site-card{padding:20px;border:1px solid var(--border-color-light);border-radius:18px;background:var(--surface-elevated)}.site-top{display:grid;grid-template-columns:auto 1fr auto;gap:12px;align-items:center}.site-mark,.mini-cover,.large-cover{display:grid;place-items:center;background:linear-gradient(145deg,var(--primary),var(--primary-light));color:white;font-family:'Songti SC',serif}.site-mark{width:44px;height:44px;border-radius:14px;font-size:22px}.site-top a{display:block;max-width:280px;overflow:hidden;color:var(--text-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.site-stats{display:flex;gap:22px;margin:20px 0;color:var(--text-secondary);font-size:12px}.site-stats b{color:var(--text-primary);font-size:17px}.automation{display:flex;gap:8px}.automation span{padding:4px 9px;border-radius:99px;background:var(--bg-page);color:var(--text-tertiary);font-size:11px}.automation span.on{background:var(--success-alpha-15);color:var(--success)}.site-card footer{display:flex;justify-content:flex-end;margin-top:16px;border-top:1px solid var(--border-color-light);padding-top:10px}.book-cell,.book-summary{display:flex;align-items:center;gap:12px}.mini-cover{width:38px;height:50px;border-radius:6px}.data-table{cursor:default}.task-name p{margin-top:3px}.site-form{max-height:66vh;overflow:auto;padding-right:8px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.switch-row{display:flex;flex-wrap:wrap;gap:20px;margin-bottom:22px}.rule-block{margin:10px 0 18px;padding-top:18px;border-top:1px solid var(--border-color)}.rule-block p:last-child{margin-top:5px;color:var(--text-secondary);font-size:12px}.book-summary{padding:18px;margin-bottom:16px;border-radius:18px;background:var(--primary-alpha-10)}.book-summary>div:last-child{flex:1}.large-cover{width:82px;height:108px;border-radius:10px;font-size:32px}.drawer-actions{display:flex;gap:8px;margin-bottom:16px}.chapter-content{max-height:60vh;margin-top:14px;overflow:auto;padding:20px;border-radius:14px;background:var(--bg-page);color:var(--text-primary);font:15px/1.8 'Songti SC',serif;white-space:pre-wrap}.el-select{width:100%}
@media(max-width:900px){.metric-grid{grid-template-columns:repeat(2,1fr)}.site-grid{grid-template-columns:1fr}}@media(max-width:640px){.hero{align-items:flex-start;flex-direction:column;gap:22px;padding:24px}.hero h1{font-size:34px}.panel{padding:16px}.metric-grid,.form-grid{grid-template-columns:1fr}.segmented-wrap{justify-content:start}.section-heading{align-items:flex-start;flex-direction:column;gap:12px}.search{width:100%}}@media(prefers-reduced-motion:reduce){.segment-indicator{transition:none}}
</style>
