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
          <div class="site-top"><span class="site-mark">{{ site.siteName.slice(0, 1) }}</span><div><h3>{{ site.siteName }}</h3><a :href="site.homeUrl || site.baseUrl" target="_blank">{{ site.baseUrl }}</a></div><el-tag :type="site.status==='RULE_ERROR'?'danger':!site.ruleVersion?'warning':site.enabled?'success':'info'">{{ site.status==='RULE_ERROR'?'规则异常':!site.ruleVersion?'待配置规则':site.enabled?'启用':'停用' }}</el-tag></div>
          <div class="site-stats"><span><b>{{ site.bookCount }}</b> 本书</span><span><b>{{ site.requestIntervalMillis }}</b> ms 间隔</span><span><b>{{ site.maxConcurrency }}</b> 并发</span></div>
          <div class="automation"><span :class="{on:site.autoScan}">发现</span><span :class="{on:site.autoUpdate}">更新</span><span :class="{on:site.autoCrawl}">自动采集</span><span :class="{on:site.autoImportLibrary}">自动入库</span></div>
          <p class="health-line" :class="{error:site.status==='RULE_ERROR'}">{{ site.ruleVersion ? `生效规则 v${site.ruleVersion}` : '暂无生效规则' }} · 共 {{ site.ruleCount }} 个版本</p>
          <footer><el-button text :disabled="!site.enabled || !site.rule?.discoveryItemSelector" @click="scanSite(site)">扫描首页</el-button><el-button type="primary" plain @click="openRuleManager(site)">规则管理</el-button><el-button text @click="openSite(site)">编辑网站</el-button><el-button text @click="openCrawl(site.id)">采集 URL</el-button><el-button text type="danger" @click="removeSite(site)">删除</el-button></footer>
        </article>
      </div>
      <el-empty v-else description="尚未配置采集网站" />
    </section>

    <section v-else-if="activeTab === 'discovered'" class="panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">DISCOVERY INBOX</p><h2>发现书籍</h2></div><div class="batch-actions"><el-button :disabled="!selectedDiscoveries.length" @click="batchDiscovery('IGNORED')">忽略</el-button><el-button :disabled="!selectedDiscoveries.length" @click="batchDiscovery('BLACKLISTED')">加入黑名单</el-button><el-button type="primary" :disabled="!selectedDiscoveries.length" @click="batchCrawl">批量采集</el-button></div></div>
      <el-table :data="discoveredBooks" class="data-table" @selection-change="selectedDiscoveries=$event">
        <el-table-column type="selection" width="48" />
        <el-table-column label="书籍" min-width="260"><template #default="{row}"><div class="book-cell"><div class="mini-cover">{{ row.bookName.slice(0,1) }}</div><div><strong>{{ row.bookName }}</strong><p>{{ row.author || '未知作者' }} · {{ row.siteName }}</p></div></div></template></el-table-column>
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column prop="latestChapter" label="最新章节" min-width="180" />
        <el-table-column label="发现时间" width="170"><template #default="{row}">{{ formatTime(row.discoverTime) }}</template></el-table-column>
        <el-table-column label="操作" width="310"><template #default="{row}"><el-button text type="primary" @click="crawlDiscovered(row)">开始采集</el-button><a class="source-link" :href="row.bookUrl" target="_blank" rel="noopener noreferrer">查看网站</a><el-button text @click="batchDiscovery('IGNORED',[row.id])">忽略</el-button><el-button text type="danger" @click="batchDiscovery('BLACKLISTED',[row.id])">黑名单</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!discoveredBooks.length" description="扫描站点首页后，新发现的书籍会出现在这里" />
    </section>

    <section v-else-if="activeTab === 'books'" class="panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">STRUCTURED BOOKS</p><h2>采集书籍</h2></div><el-input v-model="bookKeyword" clearable placeholder="搜索书名、作者或网站" :prefix-icon="Search" class="search" /></div>
      <el-table :data="filteredBooks" @row-click="openBook" class="data-table">
        <el-table-column label="书籍" min-width="260"><template #default="{row}"><div class="book-cell"><div class="mini-cover">{{ row.bookName.slice(0,1) }}</div><div><strong>{{ row.bookName }}</strong><p>{{ row.author || '未知作者' }} · {{ row.siteName }}</p></div></div></template></el-table-column>
        <el-table-column label="进度" min-width="190"><template #default="{row}"><el-progress :percentage="progress(row)" :stroke-width="7" /><small>{{ row.crawledChapterCount }} / {{ row.chapterCount }} 章</small></template></el-table-column>
        <el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="statusType(row.crawlStatus)">{{ statusLabel(row.crawlStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="失败" width="80" prop="failedChapterCount" />
        <el-table-column label="操作" width="250"><template #default="{row}"><el-button text @click.stop="checkUpdates(row)">检查更新</el-button><el-button text @click.stop="continueCrawl(row)">继续</el-button><el-button text :disabled="row.crawlStatus!=='COMPLETED'" @click.stop="generate(row)">生成</el-button><el-button text type="primary" :disabled="row.crawlStatus!=='COMPLETED' || row.importStatus==='IMPORTED'" @click.stop="importBook(row)">入库</el-button></template></el-table-column>
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
        <div class="form-grid"><el-form-item label="首页扫描间隔（分钟）"><el-input-number v-model="siteForm.scanIntervalMinutes" :min="1" /></el-form-item><el-form-item label="更新检查间隔（分钟）"><el-input-number v-model="siteForm.updateIntervalMinutes" :min="1" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="单次最多扫描页数"><el-input-number v-model="siteForm.maxDiscoveryPages" :min="1" :max="50" /></el-form-item><el-form-item label="自动入库格式"><el-select v-model="siteForm.autoImportFormat"><el-option label="EPUB" value="EPUB"/><el-option label="TXT" value="TXT"/><el-option label="TXT + EPUB" value="BOTH"/></el-select></el-form-item></div>
        <div class="switch-row"><el-switch v-model="siteForm.enabled" active-text="启用网站"/><el-switch v-model="siteForm.autoScan" active-text="自动扫描首页"/><el-switch v-model="siteForm.autoUpdate" active-text="自动检查更新"/><el-switch v-model="siteForm.autoCrawl" active-text="自动采集"/><el-switch v-model="siteForm.autoImportLibrary" active-text="自动入库"/></div>
        <el-alert type="warning" :closable="false" title="仅采集你有权阅读的免费内容；系统不会绕过登录、付费、验证码或访问控制。" />
      </el-form>
      <template #footer><el-button @click="siteDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveSite">保存网站</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleManagerDialog" :title="`${ruleSite?.siteName || ''} · 规则管理`" width="min(960px, 96vw)" destroy-on-close>
      <div class="rule-manager-bar"><p>多个版本可以长期共存，但同一时间最多启用一条。</p><div><input ref="importInput" class="file-input" type="file" accept="application/json,.json" @change="importRuleFile"><el-button :icon="Upload" @click="importInput?.click()">导入 JSON</el-button><el-button type="primary" :icon="Plus" @click="openRuleEditor()">新建规则</el-button></div></div>
      <div v-if="ruleVersions.length" class="rule-grid">
        <article v-for="rule in ruleVersions" :key="rule.id" class="rule-card" :class="{active:rule.enabled}">
          <div class="rule-version"><span>v{{ rule.version }}</span><el-tag :type="rule.enabled?'success':'info'">{{ rule.enabled?'当前生效':'已禁用' }}</el-tag></div>
          <h3>{{ rule.changeSummary }}</h3><p>{{ formatTime(rule.updatedAt || rule.createdAt) }} · 正文 {{ rule.rule.contentSelector }}</p>
          <footer><el-button text :type="rule.enabled?'warning':'success'" @click="toggleRule(rule)">{{ rule.enabled?'禁用':'启用' }}</el-button><el-button text :icon="Edit" @click="openRuleEditor(rule)">编辑</el-button><el-button text :icon="Download" @click="exportRule(rule)">导出</el-button><el-button text @click="ruleSite && openRuleTest(ruleSite,rule.rule)">测试</el-button><el-button text type="danger" @click="removeRule(rule)">删除</el-button></footer>
        </article>
      </div>
      <el-empty v-else description="还没有规则版本，可新建或导入 JSON 规则" />
      <template #footer><el-button v-if="ruleSite?.rule" @click="checkHealth(ruleSite)">健康检查</el-button><el-button @click="ruleManagerDialog=false">完成</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleEditorDialog" :title="editingRule ? `编辑规则 v${editingRule.version}` : '新建规则版本'" width="min(800px, 96vw)" append-to-body destroy-on-close>
      <el-form label-position="top" class="site-form">
        <div class="form-grid"><el-form-item label="版本号"><el-input-number v-model="ruleForm.version" :min="1" /></el-form-item><el-form-item label="版本说明"><el-input v-model="ruleForm.changeSummary" placeholder="例如：适配新版目录结构" maxlength="300" /></el-form-item></div>
        <el-switch v-model="ruleForm.enabled" active-text="保存后设为唯一生效规则" />
        <div class="rule-block"><p class="eyebrow">DISCOVERY SELECTORS</p><h3>书籍列表规则</h3><p>自动扫描需要配置书籍项和详情链接；其余字段可选填。</p></div>
        <div class="form-grid"><el-form-item label="书籍项 Selector"><el-input v-model="ruleForm.rule.discoveryItemSelector" placeholder=".book-list .book" /></el-form-item><el-form-item label="详情链接 Selector"><el-input v-model="ruleForm.rule.discoveryUrlSelector" placeholder="a.book-link" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="列表书名 Selector"><el-input v-model="ruleForm.rule.discoveryTitleSelector" placeholder=".title" /></el-form-item><el-form-item label="列表作者 Selector"><el-input v-model="ruleForm.rule.discoveryAuthorSelector" placeholder=".author" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="列表封面 Selector"><el-input v-model="ruleForm.rule.discoveryCoverSelector" placeholder="img.cover::data-src" /></el-form-item><el-form-item label="列表分类 Selector"><el-input v-model="ruleForm.rule.discoveryCategorySelector" placeholder=".category" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="最新章节 Selector"><el-input v-model="ruleForm.rule.discoveryLatestChapterSelector" placeholder=".latest" /></el-form-item><el-form-item label="下一页 Selector"><el-input v-model="ruleForm.rule.discoveryNextPageSelector" placeholder="a.next" /></el-form-item></div>
        <div class="rule-block"><p class="eyebrow">DETAIL & CHAPTER</p><h3>详情、目录与正文规则</h3><p>属性读取可写为 <code>.cover::data-src</code>，链接与图片默认读取 href/src。</p></div>
        <div class="form-grid"><el-form-item label="书名 Selector"><el-input v-model="ruleForm.rule.titleSelector" placeholder="h1.book-title" /></el-form-item><el-form-item label="作者 Selector"><el-input v-model="ruleForm.rule.authorSelector" placeholder=".author" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="简介 Selector"><el-input v-model="ruleForm.rule.descriptionSelector" placeholder="#intro" /></el-form-item><el-form-item label="目录页链接 Selector"><el-input v-model="ruleForm.rule.chapterListUrlSelector" placeholder="a.catalog" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="章节项 Selector"><el-input v-model="ruleForm.rule.chapterItemSelector" placeholder="#list dd" /></el-form-item><el-form-item label="章节链接 Selector"><el-input v-model="ruleForm.rule.chapterUrlSelector" placeholder="a" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="章节名称 Selector"><el-input v-model="ruleForm.rule.chapterTitleSelector" placeholder=":scope" /></el-form-item><el-form-item label="章节页标题 Selector"><el-input v-model="ruleForm.rule.contentTitleSelector" placeholder="h1" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="正文 Selector"><el-input v-model="ruleForm.rule.contentSelector" placeholder="#content" /></el-form-item><el-form-item label="最少正文字数"><el-input-number v-model="ruleForm.rule.minChapterLength" :min="0" /></el-form-item></div>
        <el-form-item label="删除节点（逗号或换行分隔）"><el-input v-model="ruleForm.rule.removeSelectors" type="textarea" placeholder=".ads, .navigation" /></el-form-item>
        <el-form-item label="XPath 删除规则（每行一条）"><el-input v-model="ruleForm.rule.xpathRemoveSelectors" type="textarea" placeholder="//div[contains(@class,'watermark')]" /></el-form-item>
        <el-form-item label="字符串替换 JSON"><el-input v-model="ruleForm.rule.stringReplacementsJson" type="textarea" placeholder='{"请下载本站APP":""}' /></el-form-item>
        <el-form-item label="正则替换 JSON"><el-input v-model="ruleForm.rule.regexReplacementsJson" type="textarea" placeholder='{"www\\.example\\.com":""}' /></el-form-item>
        <div class="switch-row"><el-switch v-model="ruleForm.rule.removeBlankLines" active-text="移除空白行"/><el-switch v-model="ruleForm.rule.saveOriginalHtml" active-text="保留原始正文 HTML"/></div>
      </el-form>
      <template #footer><el-button v-if="ruleSite" @click="openRuleTest(ruleSite,ruleForm.rule)">测试当前编辑内容</el-button><el-button @click="ruleEditorDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRule">保存规则</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleTestDialog" title="规则在线测试" width="min(720px, 94vw)">
      <el-form label-position="top"><el-form-item label="书籍详情测试 URL"><el-input v-model="ruleTestUrl" placeholder="https://example.com/book/123/" /></el-form-item></el-form>
      <div v-if="ruleTestResult" class="test-result" :class="{failed:!ruleTestResult.success}">
        <template v-if="ruleTestResult.success"><div class="test-facts"><span><small>书名</small><b>{{ ruleTestResult.title }}</b></span><span><small>作者</small><b>{{ ruleTestResult.author || '未知' }}</b></span><span><small>目录</small><b>{{ ruleTestResult.chapterCount }} 章</b></span><span><small>正文样本</small><b>{{ ruleTestResult.contentLength }} 字</b></span></div><p><b>{{ ruleTestResult.sampleChapter }}</b> · {{ ruleTestResult.durationMillis }} ms</p><pre>{{ ruleTestResult.contentPreview }}</pre></template>
        <el-alert v-else type="error" :closable="false" :title="ruleTestResult.errorMessage || '规则测试失败'" />
      </div>
      <template #footer><el-button @click="ruleTestDialog=false">关闭</el-button><el-button type="primary" :loading="testingRule" @click="runRuleTest">测试详情、目录与正文</el-button></template>
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
import { Collection, Connection, DataAnalysis, Document, Download, Edit, Link, List, Plus, Refresh, Search, Tickets, Upload, Warning } from '@element-plus/icons-vue'
import { ElButton, ElProgress, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { crawlerApi, type CrawlerBook, type CrawlerChapter, type CrawlerDashboard, type CrawlerRule, type CrawlerRuleExport, type CrawlerRuleTest, type CrawlerRuleVersion, type CrawlerSite, type CrawlerSitePayload, type CrawlerTask } from '@/utils/crawler'
import { confirm, message } from '@/utils/message'

const TaskTable = defineComponent({ props:{ tasks:{type:Array as ()=>CrawlerTask[],required:true}}, emits:['command'], setup(props,{emit}) { return () => h(ElTable,{data:props.tasks,class:'data-table'},()=>[
  h(ElTableColumn,{label:'任务',minWidth:240},{default:({row}:{row:CrawlerTask})=>h('div',{class:'task-name'},[h('strong',row.bookName||row.type),h('p',`${row.siteName} · ${row.type}`)])}),
  h(ElTableColumn,{label:'进度',minWidth:180},{default:({row}:{row:CrawlerTask})=>h(ElProgress,{percentage:row.totalCount?Math.round(row.successCount/row.totalCount*100):0,strokeWidth:7})}),
  h(ElTableColumn,{label:'状态',width:130},{default:({row}:{row:CrawlerTask})=>h(ElTag,{type:statusType(row.status)},()=>statusLabel(row.status))}),
  h(ElTableColumn,{label:'当前章节',prop:'currentChapter',minWidth:150}),
  h(ElTableColumn,{label:'操作',width:170},{default:({row}:{row:CrawlerTask})=>[row.status==='RUNNING'?h(ElButton,{text:true,onClick:()=>emit('command',row,'pause')},()=> '暂停'):null,row.status==='PAUSED'?h(ElButton,{text:true,onClick:()=>emit('command',row,'resume')},()=> '继续'):null,['RUNNING','WAITING','PAUSED'].includes(row.status)?h(ElButton,{text:true,type:'danger',onClick:()=>emit('command',row,'cancel')},()=> '取消'):null]})
]) }})

type TabKey='overview'|'sites'|'discovered'|'books'|'tasks'|'failed'
const activeTab=ref<TabKey>('overview'), dashboard=ref<CrawlerDashboard>(), sites=ref<CrawlerSite[]>([]), books=ref<CrawlerBook[]>([]), tasks=ref<CrawlerTask[]>([])
const siteDialog=ref(false), crawlDialog=ref(false), bookDrawer=ref(false), chapterDialog=ref(false), ruleTestDialog=ref(false), ruleManagerDialog=ref(false), ruleEditorDialog=ref(false), saving=ref(false), testingRule=ref(false), editingSite=ref<CrawlerSite>(), selectedBook=ref<CrawlerBook>(), selectedDiscoveries=ref<CrawlerBook[]>([]), chapters=ref<CrawlerChapter[]>([]), chapterDetail=ref<{title:string;url:string;content:string;errorMessage:string}>(), bookKeyword=ref('')
const ruleTestSite=ref<CrawlerSite>(), ruleTestDraft=ref<CrawlerRule>(), ruleSite=ref<CrawlerSite>(), editingRule=ref<CrawlerRuleVersion>(), ruleTestUrl=ref(''), ruleTestResult=ref<CrawlerRuleTest>(), ruleVersions=ref<CrawlerRuleVersion[]>([]), importInput=ref<HTMLInputElement>()
const crawlForm=reactive<{siteId?:number;url:string}>({url:''})
const emptyRule=():CrawlerRule=>({discoveryItemSelector:'',discoveryUrlSelector:'a',discoveryTitleSelector:'.title',discoveryAuthorSelector:'',discoveryCoverSelector:'',discoveryCategorySelector:'',discoveryLatestChapterSelector:'',discoveryNextPageSelector:'',titleSelector:'',authorSelector:'',descriptionSelector:'',chapterListUrlSelector:'',chapterItemSelector:'',chapterTitleSelector:':scope',chapterUrlSelector:'a',contentTitleSelector:'h1',contentSelector:'',removeSelectors:'',xpathRemoveSelectors:'',stringReplacementsJson:'',regexReplacementsJson:'',removeBlankLines:true,saveOriginalHtml:false,minChapterLength:100})
const emptySite=():CrawlerSitePayload=>({siteName:'',siteCode:'',baseUrl:'',homeUrl:'',enabled:false,autoScan:false,autoCrawl:false,autoUpdate:true,autoImportLibrary:false,scanIntervalMinutes:360,updateIntervalMinutes:30,maxDiscoveryPages:3,autoImportFormat:'EPUB',requestIntervalMillis:1500,randomDelayMillis:1000,maxConcurrency:1,timeoutMillis:15000,retryCount:2,encoding:'UTF-8',userAgent:'',cookie:'',headersJson:'',proxy:''})
const siteForm=reactive<CrawlerSitePayload>(emptySite())
const ruleForm=reactive<{version:number;changeSummary:string;enabled:boolean;rule:CrawlerRule}>({version:1,changeSummary:'',enabled:true,rule:emptyRule()})
const tabs=computed(()=>[{key:'overview' as const,label:'采集概览',icon:DataAnalysis,count:0},{key:'sites' as const,label:'采集网站',icon:Connection,count:sites.value.length},{key:'discovered' as const,label:'发现书籍',icon:Tickets,count:discoveredBooks.value.length},{key:'books' as const,label:'采集书籍',icon:Collection,count:structuredBooks.value.length},{key:'tasks' as const,label:'采集任务',icon:List,count:tasks.value.filter(t=>['RUNNING','WAITING','PAUSED'].includes(t.status)).length},{key:'failed' as const,label:'失败任务',icon:Warning,count:failedTasks.value.length}])
const activeIndex=computed(()=>tabs.value.findIndex(t=>t.key===activeTab.value)), failedTasks=computed(()=>tasks.value.filter(t=>['FAILED','PARTIAL_SUCCESS'].includes(t.status)))
const discoveredBooks=computed(()=>books.value.filter(b=>b.discoveryStatus==='ACTIVE'&&b.crawlStatus==='DISCOVERED'))
const structuredBooks=computed(()=>books.value.filter(b=>b.discoveryStatus==='ACTIVE'&&b.crawlStatus!=='DISCOVERED'))
const filteredBooks=computed(()=>{const q=bookKeyword.value.trim().toLowerCase();return q?structuredBooks.value.filter(b=>[b.bookName,b.author,b.siteName,b.externalBookId].some(v=>v?.toLowerCase().includes(q))):structuredBooks.value})
const metrics=computed(()=>[{label:'采集网站',value:dashboard.value?.siteCount||0,note:`${dashboard.value?.enabledSiteCount||0} 个启用`,icon:Connection},{label:'采集书籍',value:dashboard.value?.bookCount||0,note:`今日 +${dashboard.value?.todayNewBooks||0}`,icon:Collection},{label:'已采集完成',value:dashboard.value?.completedBookCount||0,note:`${dashboard.value?.readyToImportCount||0} 本待入库`,icon:Document},{label:'今日新增章节',value:dashboard.value?.todayNewChapters||0,note:`${dashboard.value?.crawlingBookCount||0} 本采集中`,icon:Tickets}])
let timer:number|undefined
onMounted(async()=>{await refresh();timer=window.setInterval(()=>{if(tasks.value.some(t=>['RUNNING','WAITING'].includes(t.status))) void refresh()},4000)})
onUnmounted(()=>{if(timer)window.clearInterval(timer)})
async function refresh(){[dashboard.value,sites.value,books.value,tasks.value]=await Promise.all([crawlerApi.dashboard(),crawlerApi.sites(),crawlerApi.books(),crawlerApi.tasks()])}
function openSite(site?:CrawlerSite){editingSite.value=site;const defaults=emptySite();Object.assign(siteForm,defaults);if(site)(Object.keys(defaults) as (keyof CrawlerSitePayload)[]).forEach(key=>{(siteForm as any)[key]=site[key]??defaults[key]});siteDialog.value=true}
function openCrawl(id:number){crawlForm.siteId=id;crawlDialog.value=true}
async function saveSite(){if(!siteForm.siteName||!siteForm.siteCode||!siteForm.baseUrl)return message.warning('请填写网站名称、唯一编码和根地址');saving.value=true;try{editingSite.value?await crawlerApi.updateSite(editingSite.value.id,siteForm):await crawlerApi.createSite(siteForm);message.success(editingSite.value?'网站信息已保存':'网站已创建，请在卡片的规则管理中添加规则');siteDialog.value=false;await refresh()}finally{saving.value=false}}
async function removeSite(site:CrawlerSite){if(await confirm(`确定删除采集网站“${site.siteName}”吗？`)){await crawlerApi.deleteSite(site.id);message.success('采集网站已删除');await refresh()}}
async function startCrawl(){if(!crawlForm.siteId||!crawlForm.url)return message.warning('请选择网站并填写书籍 URL');saving.value=true;try{await crawlerApi.crawlUrl(crawlForm.siteId,crawlForm.url);crawlDialog.value=false;activeTab.value='tasks';message.success('采集任务已创建');await refresh()}finally{saving.value=false}}
async function scanSite(site:CrawlerSite){await crawlerApi.scanSite(site.id);activeTab.value='tasks';message.success('首页扫描任务已创建');await refresh()}
function openRuleTest(site:CrawlerSite,draft?:CrawlerRule){ruleTestSite.value=site;ruleTestDraft.value=draft?JSON.parse(JSON.stringify(draft)):undefined;ruleTestResult.value=undefined;ruleTestUrl.value='';ruleTestDialog.value=true}
async function runRuleTest(){if(!ruleTestSite.value||!ruleTestUrl.value)return message.warning('请填写同站点的书籍详情 URL');testingRule.value=true;try{ruleTestResult.value=await crawlerApi.testRule(ruleTestSite.value.id,ruleTestUrl.value,ruleTestDraft.value)}finally{testingRule.value=false}}
async function checkHealth(site:CrawlerSite){const result=await crawlerApi.checkRule(site.id);if(result.success)message.success('规则健康检查通过');else message.warning(result.errorMessage||'规则健康检查失败');await refresh()}
async function openRuleManager(site:CrawlerSite){ruleSite.value=site;ruleVersions.value=await crawlerApi.rules(site.id);ruleManagerDialog.value=true}
function openRuleEditor(rule?:CrawlerRuleVersion){editingRule.value=rule;Object.assign(ruleForm,{version:rule?.version??Math.max(0,...ruleVersions.value.map(item=>item.version))+1,changeSummary:rule?.changeSummary||'',enabled:rule?.enabled??!ruleVersions.value.some(item=>item.enabled),rule:JSON.parse(JSON.stringify(rule?.rule||emptyRule()))});ruleEditorDialog.value=true}
async function saveRule(){if(!ruleSite.value)return;if(!ruleForm.changeSummary.trim()||!ruleForm.rule.titleSelector||!ruleForm.rule.chapterItemSelector||!ruleForm.rule.chapterUrlSelector||!ruleForm.rule.contentSelector)return message.warning('请填写版本说明、书名、章节项、章节链接和正文 Selector');saving.value=true;try{const data=JSON.parse(JSON.stringify(ruleForm));editingRule.value?await crawlerApi.updateRule(ruleSite.value.id,editingRule.value.id,data):await crawlerApi.createRule(ruleSite.value.id,data);message.success(editingRule.value?'规则已更新':'规则版本已创建');ruleEditorDialog.value=false;await reloadRules()}finally{saving.value=false}}
async function reloadRules(){if(!ruleSite.value)return;ruleVersions.value=await crawlerApi.rules(ruleSite.value.id);await refresh();ruleSite.value=sites.value.find(site=>site.id===ruleSite.value?.id)}
async function toggleRule(rule:CrawlerRuleVersion){if(!ruleSite.value)return;if(rule.enabled&&!await confirm(`禁用规则 v${rule.version} 后，该网站将没有生效规则，是否继续？`))return;await crawlerApi.setRuleStatus(ruleSite.value.id,rule.id,!rule.enabled);message.success(rule.enabled?'规则已禁用':`规则 v${rule.version} 已设为唯一生效规则`);await reloadRules()}
async function removeRule(rule:CrawlerRuleVersion){if(!ruleSite.value||!await confirm(`确定删除规则 v${rule.version} 吗？此操作不可恢复。`))return;await crawlerApi.deleteRule(ruleSite.value.id,rule.id);message.success('规则已删除');await reloadRules()}
async function exportRule(rule:CrawlerRuleVersion){if(!ruleSite.value)return;const data=await crawlerApi.exportRule(ruleSite.value.id,rule.id);const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`${ruleSite.value.siteCode}-rule-v${rule.version}.json`;a.click();URL.revokeObjectURL(url)}
async function importRuleFile(event:Event){if(!ruleSite.value)return;const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;try{const data=JSON.parse(await file.text()) as CrawlerRuleExport;await crawlerApi.importRule(ruleSite.value.id,{...data,enabled:false});message.success(`规则 v${data.version} 已导入（默认禁用）`);await reloadRules()}catch(error){message.error(error instanceof SyntaxError?'JSON 文件格式不正确':'规则导入失败，请检查版本号和配置内容')}}
async function crawlDiscovered(book:CrawlerBook){await crawlerApi.batchCrawl([book.id]);activeTab.value='tasks';message.success('采集任务已创建');await refresh()}
async function batchCrawl(){await crawlerApi.batchCrawl(selectedDiscoveries.value.map(b=>b.id));activeTab.value='tasks';message.success(`已创建 ${selectedDiscoveries.value.length} 个采集任务`);await refresh()}
async function batchDiscovery(status:'IGNORED'|'BLACKLISTED',ids=selectedDiscoveries.value.map(b=>b.id)){if(!ids.length)return;await crawlerApi.setDiscoveryStatus(ids,status);message.success(status==='IGNORED'?'已忽略所选书籍':'已加入黑名单，后续扫描不会重新收录');await refresh()}
async function openBook(book:CrawlerBook){selectedBook.value=book;chapters.value=await crawlerApi.chapters(book.id);bookDrawer.value=true}
async function continueCrawl(book:CrawlerBook){await crawlerApi.continueBook(book.id);message.success('续采任务已创建');await refresh()}
async function retryFailures(book:CrawlerBook){await crawlerApi.retryFailures(book.id);message.success('失败章节已进入重试队列');await refresh()}
async function checkUpdates(book:CrawlerBook){await crawlerApi.checkUpdates(book.id);message.success('增量更新检查已创建');await refresh()}
async function generate(book:CrawlerBook){await crawlerApi.generate(book.id,['TXT','EPUB']);message.success('TXT 与 EPUB 已生成并保留在采集中心')}
async function importBook(book:CrawlerBook){const result=await crawlerApi.importBook(book.id,'EPUB');message.success(`已加入书库，书籍 ID：${result.bookId}`);await refresh()}
async function runTaskCommand(task:CrawlerTask,command:'pause'|'resume'|'cancel'){await crawlerApi.taskCommand(task.id,command);await refresh()}
async function openChapter(chapter:CrawlerChapter){if(!selectedBook.value)return;chapterDetail.value=await crawlerApi.chapter(selectedBook.value.id,chapter.id);chapterDialog.value=true}
function progress(book:CrawlerBook){return book.chapterCount?Math.round(book.crawledChapterCount/book.chapterCount*100):0}
function statusLabel(status:string){return ({WAITING:'等待中',RUNNING:'运行中',PAUSED:'已暂停',SUCCESS:'成功',PARTIAL_SUCCESS:'部分成功',FAILED:'失败',CANCELLED:'已取消',DISCOVERED:'已发现',CRAWLING_METADATA:'解析元信息',CRAWLING_CHAPTER_LIST:'解析目录',CRAWLING_CONTENT:'采集正文',COMPLETED:'已完成',NOT_CRAWLED:'未采集',CRAWLING:'采集中',CONTENT_SUSPECTED:'内容异常'} as Record<string,string>)[status]||status}
function statusType(status:string):''|'success'|'warning'|'info'|'danger'{if(['SUCCESS','COMPLETED'].includes(status))return'success';if(['FAILED','CONTENT_SUSPECTED'].includes(status))return'danger';if(['PARTIAL_SUCCESS','PAUSED'].includes(status))return'warning';return'info'}
function formatTime(value?:string){return value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'—'}
function handleTabKey(e:KeyboardEvent){const keys=tabs.value.map(t=>t.key);let i=activeIndex.value;if(['ArrowRight','ArrowDown'].includes(e.key))i=(i+1)%keys.length;else if(['ArrowLeft','ArrowUp'].includes(e.key))i=(i-1+keys.length)%keys.length;else if(e.key==='Home')i=0;else if(e.key==='End')i=keys.length-1;else return;e.preventDefault();activeTab.value=keys[i];requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.segment')[i]?.focus())}
</script>

<style scoped>
.crawler-page{display:grid;gap:22px;padding-bottom:50px}.hero{position:relative;display:flex;align-items:flex-end;justify-content:space-between;min-height:180px;padding:32px;overflow:hidden;border:1px solid var(--border-color);border-radius:28px;background:linear-gradient(125deg,var(--surface-elevated),var(--surface-card));box-shadow:var(--shadow-lg)}.hero:after{position:absolute;right:8%;bottom:-90px;width:280px;height:280px;border:42px solid var(--primary-alpha-10);border-radius:50%;content:''}.hero>*{position:relative;z-index:1}.eyebrow{margin-bottom:6px;color:var(--primary);font-size:11px;font-weight:800;letter-spacing:.16em}.hero h1{font-family:'Iowan Old Style','Songti SC',serif;font-size:42px;letter-spacing:-.04em}.subtitle{max-width:600px;margin-top:10px;color:var(--text-secondary)}.segmented-wrap{position:relative;display:grid;grid-template-columns:repeat(5,minmax(120px,1fr));overflow-x:auto;padding:5px;border:1px solid var(--border-color);border-radius:16px;background:var(--surface-card);isolation:isolate}.segment-indicator{position:absolute;top:5px;bottom:5px;left:5px;z-index:-1;width:20%;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .28s cubic-bezier(.2,.8,.2,1)}.segment{display:flex;align-items:center;justify-content:center;gap:7px;min-width:120px;padding:11px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.segment.active{color:var(--primary);font-weight:700}.segment:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:12px}.segment b{min-width:19px;padding:1px 5px;border-radius:99px;background:var(--primary-alpha-10);font-size:11px}.panel{min-height:430px;padding:26px;border:1px solid var(--border-color);border-radius:22px;background:var(--surface-card);box-shadow:var(--shadow-md)}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:34px}.metric-card{display:grid;grid-template-columns:auto 1fr;gap:13px;padding:20px;border:1px solid var(--border-color-light);border-radius:18px;background:linear-gradient(145deg,var(--surface-elevated),var(--surface-card))}.metric-icon{display:grid;width:42px;height:42px;place-items:center;border-radius:13px;background:var(--primary-alpha-10);color:var(--primary);font-size:20px}.metric-card strong{font-size:27px}.metric-card p,.metric-card small,.book-cell p,.task-name p{color:var(--text-secondary);font-size:12px}.metric-card small{grid-column:2}.section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.section-heading h2{font-family:'Iowan Old Style','Songti SC',serif;font-size:25px}.search{width:280px}.site-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16px}.site-card{padding:20px;border:1px solid var(--border-color-light);border-radius:18px;background:var(--surface-elevated)}.site-top{display:grid;grid-template-columns:auto 1fr auto;gap:12px;align-items:center}.site-mark,.mini-cover,.large-cover{display:grid;place-items:center;background:linear-gradient(145deg,var(--primary),var(--primary-light));color:white;font-family:'Songti SC',serif}.site-mark{width:44px;height:44px;border-radius:14px;font-size:22px}.site-top a{display:block;max-width:280px;overflow:hidden;color:var(--text-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.site-stats{display:flex;gap:22px;margin:20px 0;color:var(--text-secondary);font-size:12px}.site-stats b{color:var(--text-primary);font-size:17px}.automation{display:flex;gap:8px}.automation span{padding:4px 9px;border-radius:99px;background:var(--bg-page);color:var(--text-tertiary);font-size:11px}.automation span.on{background:var(--success-alpha-15);color:var(--success)}.site-card footer{display:flex;justify-content:flex-end;margin-top:16px;border-top:1px solid var(--border-color-light);padding-top:10px}.book-cell,.book-summary{display:flex;align-items:center;gap:12px}.mini-cover{width:38px;height:50px;border-radius:6px}.data-table{cursor:default}.task-name p{margin-top:3px}.site-form{max-height:66vh;overflow:auto;padding-right:8px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.switch-row{display:flex;flex-wrap:wrap;gap:20px;margin-bottom:22px}.rule-block{margin:10px 0 18px;padding-top:18px;border-top:1px solid var(--border-color)}.rule-block p:last-child{margin-top:5px;color:var(--text-secondary);font-size:12px}.book-summary{padding:18px;margin-bottom:16px;border-radius:18px;background:var(--primary-alpha-10)}.book-summary>div:last-child{flex:1}.large-cover{width:82px;height:108px;border-radius:10px;font-size:32px}.drawer-actions{display:flex;gap:8px;margin-bottom:16px}.chapter-content{max-height:60vh;margin-top:14px;overflow:auto;padding:20px;border-radius:14px;background:var(--bg-page);color:var(--text-primary);font:15px/1.8 'Songti SC',serif;white-space:pre-wrap}.el-select{width:100%}
.segmented-wrap{grid-template-columns:repeat(6,minmax(120px,1fr))}.batch-actions{display:flex;flex-wrap:wrap;gap:8px}.source-link{margin:0 10px;color:var(--primary);font-size:14px;text-decoration:none}.source-link:hover{text-decoration:underline}
.crawler-page{width:100%;min-width:0;max-width:100%;overflow-x:clip;box-sizing:border-box}.crawler-page>*{min-width:0;max-width:100%;box-sizing:border-box}.panel{width:100%;min-width:0;max-width:100%;overflow:hidden;box-sizing:border-box}.metric-grid,.site-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.site-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.section-heading>*{min-width:0}.data-table,:deep(.el-table){width:100%!important;min-width:0;max-width:100%}.book-cell>div:last-child,.task-name{min-width:0}.book-cell strong,.book-cell p,.task-name strong,.task-name p{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.site-card footer{flex-wrap:wrap}.health-line{margin-top:12px;color:var(--text-tertiary);font-size:11px}.health-line.error{color:var(--danger)}.test-result{padding:18px;border:1px solid var(--success-alpha-15);border-radius:16px;background:var(--surface-elevated)}.test-result.failed{border-color:var(--danger)}.test-facts{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.test-facts span{display:grid;gap:4px}.test-facts small{color:var(--text-tertiary)}.test-result>p{margin:18px 0 8px}.test-result pre{max-height:260px;overflow:auto;padding:14px;border-radius:12px;background:var(--bg-page);font:14px/1.7 'Songti SC',serif;white-space:pre-wrap}.file-input{display:none}.rule-manager-bar{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-bottom:20px;padding:15px 18px;border-radius:14px;background:var(--primary-alpha-10);color:var(--text-secondary)}.rule-manager-bar>div{display:flex;flex-shrink:0;gap:8px}.rule-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;max-height:58vh;overflow:auto;padding:2px}.rule-card{padding:18px;border:1px solid var(--border-color-light);border-radius:16px;background:var(--surface-elevated);transition:border-color .2s,background .2s}.rule-card.active{border-color:var(--success);background:linear-gradient(145deg,var(--success-alpha-15),var(--surface-elevated))}.rule-version{display:flex;align-items:center;justify-content:space-between}.rule-version>span{font-family:'Iowan Old Style','Songti SC',serif;font-size:24px;font-weight:700}.rule-card h3{margin-top:12px;font-size:15px}.rule-card>p{height:36px;margin-top:5px;overflow:hidden;color:var(--text-secondary);font-size:12px}.rule-card footer{display:flex;flex-wrap:wrap;margin-top:14px;padding-top:10px;border-top:1px solid var(--border-color-light)}
@media(max-width:900px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.site-grid,.rule-grid{grid-template-columns:minmax(0,1fr)}}@media(max-width:640px){.hero{align-items:flex-start;flex-direction:column;gap:22px;padding:24px}.hero h1{font-size:34px}.panel{padding:16px}.metric-grid,.form-grid{grid-template-columns:minmax(0,1fr)}.segmented-wrap{justify-content:start}.section-heading,.rule-manager-bar{align-items:flex-start;flex-direction:column;gap:12px}.rule-manager-bar>div{width:100%;flex-wrap:wrap}.search{width:100%}}@media(prefers-reduced-motion:reduce){.segment-indicator,.rule-card{transition:none}}
</style>
