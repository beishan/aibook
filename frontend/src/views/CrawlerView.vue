<template>
  <main class="crawler-page">
    <header class="hero">
      <div>
        <p class="eyebrow">COLLECTION PIPELINE</p>
        <h1>书籍爬虫</h1>
        <p class="subtitle">从授权站点采集、清洗与校验，再安全发布到你的私人书库。</p>
      </div>
      <div class="hero-actions"><label class="polling-setting"><span><i/>自动刷新</span><el-select :model-value="pollingIntervalSeconds" size="small" aria-label="采集进度自动刷新间隔" @change="setPollingInterval(Number($event))"><el-option v-for="seconds in pollingIntervalOptions" :key="seconds" :label="`${seconds} 秒`" :value="seconds" /></el-select></label><el-button type="primary" :icon="Link" @click="crawlDialog = true">URL 手动采集</el-button></div>
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
      <TaskTable :tasks="dashboard?.recentTasks || []" @open="openTask" @command="runTaskCommand" @edit="openTaskEditor" @delete="removeTask" @scan-results="openScanResults" />
    </section>

    <section v-else-if="activeTab === 'sites'" class="panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">SOURCES</p><h2>采集网站</h2></div><el-button type="primary" :icon="Plus" @click="openSite()">新增网站</el-button></div>
      <div v-if="sites.length" class="site-grid">
        <article v-for="site in sites" :key="site.id" class="site-card">
          <div class="site-top"><span class="site-mark">{{ site.siteName.slice(0, 1) }}</span><div><h3>{{ site.siteName }}</h3><a :href="site.homeUrl || site.baseUrl" target="_blank">{{ site.baseUrl }}</a></div><el-tag :type="site.status==='RULE_ERROR'?'danger':!site.ruleVersion?'warning':site.enabled?'success':'info'">{{ site.status==='RULE_ERROR'?'规则异常':!site.ruleVersion?'待配置规则':site.enabled?'启用':'停用' }}</el-tag></div>
          <div class="site-stats"><span><b>{{ site.bookCount }}</b> 本书</span><span><b>{{ site.requestIntervalMillis }}</b> ms 间隔</span><span><b>{{ site.maxConcurrency }}</b> 并发</span></div>
          <p class="health-line" :class="{error:site.status==='RULE_ERROR'}">{{ site.ruleVersion ? `生效规则 v${site.ruleVersion}` : '暂无生效规则' }} · 共 {{ site.ruleCount }} 个版本</p>
          <footer><el-button type="primary" plain @click="openDiscoveryPages(site)">发现页管理（{{ discoveryPagesBySite[site.id]?.length || 0 }}）</el-button><el-button type="primary" plain @click="openRuleManager(site)">规则管理</el-button><el-button text @click="openSite(site)">编辑网站</el-button><el-button text @click="openCrawl(site.id)">采集 URL</el-button><el-button text type="danger" @click="removeSite(site)">删除</el-button></footer>
        </article>
      </div>
      <el-empty v-else description="尚未配置采集网站" />
    </section>

    <section v-else-if="activeTab === 'discovered'" class="panel" role="tabpanel">
      <div class="section-heading discovery-heading"><div><p class="eyebrow">DISCOVERY INBOX</p><h2>发现书籍</h2></div><div class="discovery-heading-actions"><div class="discovery-view-switch" role="tablist" aria-label="发现书籍视图" @keydown="handleDiscoveryViewKey"><span class="discovery-view-indicator" :style="{transform:`translateX(${discoveryViewMode==='card'?100:0}%)`}" aria-hidden="true"/><button type="button" role="tab" :aria-selected="discoveryViewMode==='table'" :tabindex="discoveryViewMode==='table'?0:-1" :class="{active:discoveryViewMode==='table'}" @click="setDiscoveryViewMode('table')">☰ 表格</button><button type="button" role="tab" :aria-selected="discoveryViewMode==='card'" :tabindex="discoveryViewMode==='card'?0:-1" :class="{active:discoveryViewMode==='card'}" @click="setDiscoveryViewMode('card')">▦ 卡片</button></div><div class="batch-actions"><el-button :disabled="!selectedDiscoveries.length" @click="batchDiscovery('IGNORED')">忽略</el-button><el-button :disabled="!selectedDiscoveries.length" @click="batchDiscovery('BLACKLISTED')">加入黑名单</el-button><el-button type="primary" :disabled="!selectedDiscoveries.length" @click="batchCrawl">批量采集</el-button></div></div></div>
      <div class="discovery-toolbar">
        <el-input v-model="discoveryKeyword" clearable :prefix-icon="Search" placeholder="搜索书名、作者、网站、发现页、编码或最新章节" @keyup.enter="applyDiscoveryFilters" />
        <el-select v-model="discoverySiteId" clearable placeholder="全部采集网站"><el-option v-for="site in sites" :key="site.id" :label="site.siteName" :value="site.id" /></el-select>
        <el-select v-model="discoverySort" aria-label="发现书籍排序"><el-option v-for="item in discoverySortOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-button type="primary" :icon="Search" @click="applyDiscoveryFilters">查询</el-button><el-button @click="resetDiscoveryFilters">重置</el-button>
      </div>
      <el-table v-if="discoveryViewMode==='table'" v-loading="discoveryLoading" :data="discoveredBooks" class="data-table" @selection-change="selectedDiscoveries=$event">
        <el-table-column type="selection" width="48" />
        <el-table-column label="书籍" min-width="260"><template #default="{row}"><div class="book-cell"><div class="mini-cover">{{ row.bookName.slice(0,1) }}</div><div><strong>{{ row.bookName }}</strong><p>{{ row.author || '未知作者' }} · {{ row.siteName }}</p></div></div></template></el-table-column>
        <el-table-column label="发现页" width="160"><template #default="{row}"><el-tag v-if="row.discoveryPageName" size="small" effect="plain">{{ row.discoveryPageName }}</el-tag><span v-else class="muted-text">站点首页 / 未记录</span></template></el-table-column>
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column prop="latestChapter" label="最新章节" min-width="180" />
        <el-table-column label="发现时间" width="170"><template #default="{row}">{{ formatTime(row.discoverTime) }}</template></el-table-column>
        <el-table-column label="操作" width="310"><template #default="{row}"><el-button text type="primary" @click="crawlDiscovered(row)">开始采集</el-button><a class="source-link" :href="row.bookUrl" target="_blank" rel="noopener noreferrer">查看网站</a><el-button text @click="batchDiscovery('IGNORED',[row.id])">忽略</el-button><el-button text type="danger" @click="batchDiscovery('BLACKLISTED',[row.id])">黑名单</el-button></template></el-table-column>
      </el-table>
      <div v-else v-loading="discoveryLoading" class="discovery-card-panel">
        <div v-if="discoveredBooks.length" class="discovery-card-selection"><el-checkbox :model-value="allDiscoveredSelected" :indeterminate="someDiscoveredSelected&&!allDiscoveredSelected" @change="toggleCurrentDiscoveryPage(Boolean($event))">选择当前页</el-checkbox><span>已选择 {{ selectedDiscoveries.length }} 本</span></div>
        <div class="discovery-card-grid">
          <article v-for="book in discoveredBooks" :key="book.id" class="discovery-card" :class="{selected:isDiscoverySelected(book)}">
            <div class="discovery-card-cover"><span>{{ book.bookName.slice(0,1) }}</span><img v-if="book.coverUrl && shouldLoadBookCover()" :src="getCoverUrl(book.coverUrl)" :alt="`${book.bookName}封面`" loading="lazy" @error="hideBrokenCover"/><el-checkbox class="discovery-card-check" :model-value="isDiscoverySelected(book)" :aria-label="`选择${book.bookName}`" @click.stop @change="toggleDiscoverySelection(book,Boolean($event))"/></div>
            <div class="discovery-card-body"><div class="discovery-card-title"><div><strong :title="book.bookName">{{ book.bookName }}</strong><p>{{ book.author || '未知作者' }}</p></div><el-tag v-if="book.category" size="small" effect="plain">{{ book.category }}</el-tag></div><dl><div><dt>来源网站</dt><dd>{{ book.siteName }}</dd></div><div><dt>发现页</dt><dd>{{ book.discoveryPageName || '站点首页 / 未记录' }}</dd></div><div><dt>最新章节</dt><dd :title="book.latestChapter">{{ book.latestChapter || '暂未识别' }}</dd></div><div><dt>发现时间</dt><dd>{{ formatTime(book.discoverTime) }}</dd></div></dl></div>
            <footer class="discovery-card-actions"><el-button text type="primary" @click="crawlDiscovered(book)">开始采集</el-button><a class="source-link" :href="book.bookUrl" target="_blank" rel="noopener noreferrer">查看网站</a><el-button text @click="batchDiscovery('IGNORED',[book.id])">忽略</el-button><el-button text type="danger" @click="batchDiscovery('BLACKLISTED',[book.id])">黑名单</el-button></footer>
          </article>
        </div>
      </div>
      <el-empty v-if="!discoveryLoading&&!discoveredBooks.length" description="扫描站点首页后，新发现的书籍会出现在这里" />
      <div v-if="discoveredTotal" class="discovery-pagination"><span>当前显示 {{ discoveredBooks.length }} 本</span><el-pagination v-model:current-page="discoveryPage" v-model:page-size="discoveryPageSize" :page-sizes="[20,50,100]" :total="discoveredTotal" layout="total, sizes, prev, pager, next, jumper" background @current-change="loadDiscoveredBooks()" @size-change="handleDiscoverySizeChange" /></div>
    </section>

    <section v-else-if="activeTab === 'books'" class="panel" role="tabpanel">
      <div class="section-heading discovery-heading"><div><p class="eyebrow">STRUCTURED BOOKS</p><h2>采集书籍</h2></div><div class="discovery-view-switch crawler-book-view-switch" role="tablist" aria-label="采集书籍视图" @keydown="handleBookViewKey"><span class="discovery-view-indicator" :style="{transform:`translateX(${bookViewMode==='card'?100:0}%)`}" aria-hidden="true"/><button type="button" role="tab" :aria-selected="bookViewMode==='table'" :tabindex="bookViewMode==='table'?0:-1" :class="{active:bookViewMode==='table'}" @click="setBookViewMode('table')">☰ 表格</button><button type="button" role="tab" :aria-selected="bookViewMode==='card'" :tabindex="bookViewMode==='card'?0:-1" :class="{active:bookViewMode==='card'}" @click="setBookViewMode('card')">▦ 卡片</button></div></div>
      <div class="book-filter-toolbar">
        <el-input v-model="bookKeyword" clearable placeholder="搜索书名、作者、网站或编码" :prefix-icon="Search" @keyup.enter="applyBookFilters" @clear="applyBookFilters" />
        <el-select v-model="bookSiteId" clearable placeholder="全部来源"><el-option v-for="site in sites" :key="site.id" :label="site.siteName" :value="site.id" /></el-select>
        <el-select v-model="bookCrawlStatus" clearable placeholder="全部书籍状态"><el-option v-for="item in managedBookStatusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-select v-model="bookImportStatus" clearable placeholder="全部入库状态"><el-option v-for="item in bookImportStatusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-select v-model="bookSort" aria-label="采集书籍排序"><el-option v-for="item in managedBookSortOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-button type="primary" :icon="Search" @click="applyBookFilters">查询</el-button>
        <el-button @click="resetBookFilters">重置</el-button>
      </div>
      <el-table v-if="bookViewMode==='table'" v-loading="bookLoading" :data="books" @row-click="openBook" class="data-table">
        <el-table-column label="书籍" min-width="260"><template #default="{row}"><div class="book-cell"><div class="mini-cover">{{ row.bookName.slice(0,1) }}</div><div><strong>{{ row.bookName }}</strong><p>{{ row.author || '未知作者' }} · {{ row.siteName }}</p></div></div></template></el-table-column>
        <el-table-column label="进度" min-width="190"><template #default="{row}"><el-progress :class="{'crawler-running-progress':isBookRunning(row)}" :percentage="progress(row)" :stroke-width="7" /><small>{{ row.crawledChapterCount }} / {{ row.chapterCount }} 章</small></template></el-table-column>
        <el-table-column label="状态" width="140"><template #default="{row}"><button class="status-editor" type="button" title="人工修改采集状态" @click.stop="openStatusEditor(row)"><el-tag :type="statusType(row.crawlStatus)">{{ statusLabel(row.crawlStatus) }}</el-tag><small>修改</small></button></template></el-table-column>
        <el-table-column label="创建时间" width="154"><template #default="{row}">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="开始爬取" width="154"><template #default="{row}">{{ row.lastCrawlStartedAt ? formatTime(row.lastCrawlStartedAt) : '暂无记录' }}</template></el-table-column>
        <el-table-column label="失败" width="80" prop="failedChapterCount" />
        <el-table-column label="操作" width="330"><template #default="{row}"><el-button text @click.stop="checkUpdates(row)">检查更新</el-button><el-button text @click.stop="continueCrawl(row)">继续</el-button><el-button text type="primary" :disabled="!row.crawledChapterCount" @click.stop="startTrial(row)">试读</el-button><el-button text @click.stop="generate(row)">生成</el-button><el-button text type="primary" @click.stop="importBook(row)">{{ row.importStatus==='IMPORTED'?'同步入库':'入库' }}</el-button></template></el-table-column>
      </el-table>
      <div v-else v-loading="bookLoading" class="discovery-card-panel">
        <div class="discovery-card-grid">
          <article v-for="book in books" :key="book.id" class="discovery-card crawler-book-card" @click="openBook(book)">
            <div class="discovery-card-cover"><span>{{ book.bookName.slice(0,1) }}</span><img v-if="book.coverUrl && shouldLoadBookCover()" :src="getCoverUrl(book.coverUrl)" :alt="`${book.bookName}封面`" loading="lazy" @error="hideBrokenCover"/><div class="crawler-book-cover-badges"><el-tag :type="statusType(book.crawlStatus)" effect="dark" size="small">{{ statusLabel(book.crawlStatus) }}</el-tag><el-tag v-if="book.importStatus==='IMPORTED'" type="success" effect="dark" size="small">已入库</el-tag></div></div>
            <div class="discovery-card-body crawler-book-card-body"><div class="discovery-card-title"><div><button type="button" class="crawler-book-title-button" :title="book.bookName" @click.stop="openBook(book)">{{ book.bookName }}</button><p>{{ book.author || '未知作者' }}</p></div><el-tag v-if="book.category" size="small" effect="plain">{{ book.category }}</el-tag></div><div class="crawler-book-progress"><div><span>采集进度</span><strong>{{ book.crawledChapterCount }} / {{ book.chapterCount }} 章</strong></div><el-progress :class="{'crawler-running-progress':isBookRunning(book)}" :percentage="progress(book)" :stroke-width="7" /></div><dl><div><dt>来源网站</dt><dd>{{ book.siteName }}</dd></div><div><dt>创建时间</dt><dd>{{ formatTime(book.createdAt) }}</dd></div><div><dt>开始爬取</dt><dd>{{ book.lastCrawlStartedAt ? formatTime(book.lastCrawlStartedAt) : '暂无记录' }}</dd></div><div><dt>失败章节</dt><dd>{{ book.failedChapterCount }} 章</dd></div></dl></div>
            <footer class="discovery-card-actions crawler-book-card-actions" @click.stop><el-button text @click="checkUpdates(book)">检查更新</el-button><el-button text @click="continueCrawl(book)">继续</el-button><el-button text type="primary" :disabled="!book.crawledChapterCount" @click="startTrial(book)">试读</el-button><el-button text @click="generate(book)">生成</el-button><el-button text type="primary" @click="importBook(book)">{{ book.importStatus==='IMPORTED'?'同步入库':'入库' }}</el-button><el-button text @click="openStatusEditor(book)">修改状态</el-button></footer>
          </article>
        </div>
      </div>
      <el-empty v-if="!bookLoading&&!books.length" description="暂无符合条件的采集书籍" />
      <div v-if="bookTotal" class="list-pagination"><span>当前显示 {{ books.length }} 本</span><el-pagination v-model:current-page="bookPage" v-model:page-size="bookPageSize" :page-sizes="[20,50,100]" :total="bookTotal" layout="total, sizes, prev, pager, next, jumper" background @current-change="loadBooks()" @size-change="handleBookSizeChange" /></div>
    </section>

    <section v-else class="panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">{{ activeTab === 'failed' ? 'NEEDS ATTENTION' : 'PERSISTENT QUEUE' }}</p><h2>{{ activeTab === 'failed' ? '失败任务' : '采集任务' }}</h2></div><div class="queue-heading-actions"><div v-if="taskQueueSettings" class="queue-runtime"><span><i class="running-dot"/>运行 {{ taskQueueSettings.runningCount }} / {{ taskQueueSettings.maxConcurrentTasks }}</span><button type="button" title="查看当前排队任务" @click="openQueuedTasks">排队 {{ taskQueueSettings.queuedCount }}</button></div><el-button v-if="activeTab==='tasks'" @click="openQueueSettings">并行设置</el-button><el-button text :icon="Refresh" @click="refresh">刷新</el-button></div></div>
      <div v-if="activeTab==='tasks'" class="task-filter-toolbar">
        <el-select v-model="taskStatusFilter" clearable placeholder="全部任务状态" aria-label="任务状态筛选" @change="handleTaskStatusFilter">
          <el-option v-for="status in taskStatusOptions" :key="status" :label="statusLabel(status)" :value="status" />
        </el-select>
        <el-button :type="taskStatusFilter==='RUNNING'?'primary':undefined" @click="showRunningTasks">只看进行中</el-button>
        <el-button v-if="taskStatusFilter" text @click="clearTaskStatusFilter">清除筛选</el-button>
      </div>
      <div v-loading="activeTab === 'failed' ? failedTaskLoading : taskLoading">
        <TaskTable :tasks="activeTab === 'failed' ? failedTasks : tasks" @open="openTask" @command="runTaskCommand" @edit="openTaskEditor" @delete="removeTask" @scan-results="openScanResults" />
      </div>
      <el-empty v-if="activeTab === 'tasks'&&!taskLoading&&!tasks.length" :description="taskStatusFilter?'暂无符合状态的采集任务':'暂无采集任务'" />
      <el-empty v-if="activeTab === 'failed'&&!failedTaskLoading&&!failedTasks.length" description="暂无失败任务" />
      <div v-if="activeTab === 'tasks'&&taskTotal" class="list-pagination"><span>当前显示 {{ tasks.length }} 项</span><el-pagination v-model:current-page="taskPage" v-model:page-size="taskPageSize" :page-sizes="[20,50,100]" :total="taskTotal" layout="total, sizes, prev, pager, next, jumper" background @current-change="loadTasks()" @size-change="handleTaskSizeChange" /></div>
      <div v-if="activeTab === 'failed'&&failedTaskTotal" class="list-pagination"><span>当前显示 {{ failedTasks.length }} 项</span><el-pagination v-model:current-page="failedTaskPage" v-model:page-size="failedTaskPageSize" :page-sizes="[20,50,100]" :total="failedTaskTotal" layout="total, sizes, prev, pager, next, jumper" background @current-change="loadFailedTasks()" @size-change="handleFailedTaskSizeChange" /></div>
    </section>

    <el-dialog v-model="siteDialog" :title="editingSite ? '编辑采集网站' : '新增采集网站'" width="min(860px, 96vw)" top="5vh" class="site-editor-dialog" append-to-body destroy-on-close>
      <el-form ref="siteFormRef" :model="siteForm" :rules="siteFormRules" label-position="top" class="site-form site-editor-form" scroll-to-error>
        <div class="site-editor-tab-scroll">
          <div class="site-editor-tabs" role="tablist" aria-label="采集网站配置分类" @keydown="handleSiteEditorTabKey">
            <span class="site-editor-tab-indicator" :style="{transform:`translateX(${activeSiteTabIndex*100}%)`}" />
            <button v-for="tab in siteEditorTabs" :id="`site-editor-tab-${tab.key}`" :key="tab.key" type="button" role="tab" :aria-selected="activeSiteTab===tab.key" :aria-controls="`site-editor-panel-${tab.key}`" :tabindex="activeSiteTab===tab.key?0:-1" :class="{active:activeSiteTab===tab.key}" @click="activeSiteTab=tab.key"><strong>{{ tab.label }}</strong><small>{{ tab.description }}</small></button>
          </div>
        </div>
        <div v-show="activeSiteTab==='basic'" id="site-editor-panel-basic" class="site-tab-panel" role="tabpanel" aria-labelledby="site-editor-tab-basic">
          <section class="site-form-section"><header><span>01</span><div><h3>基本信息</h3><p>网站身份和访问入口</p></div></header><div class="form-grid"><el-form-item label="网站名称" prop="siteName"><el-input v-model="siteForm.siteName" /></el-form-item><el-form-item label="唯一编码（选填）"><el-input v-model="siteForm.siteCode" placeholder="留空时根据网站域名自动生成" /><small class="field-hint">仅支持字母、数字、下划线和连字符</small></el-form-item></div><div class="form-grid"><el-form-item label="根地址" prop="baseUrl"><el-input v-model="siteForm.baseUrl" placeholder="https://example.com" /></el-form-item><el-form-item label="首页地址"><el-input v-model="siteForm.homeUrl" placeholder="留空时使用根地址" /></el-form-item></div><el-form-item label="字符编码"><el-select v-model="siteForm.encoding"><el-option label="UTF-8" value="UTF-8"/><el-option label="GBK" value="GBK"/><el-option label="GB18030" value="GB18030"/></el-select></el-form-item></section>
        </div>
        <div v-show="activeSiteTab==='request'" id="site-editor-panel-request" class="site-tab-panel" role="tabpanel" aria-labelledby="site-editor-tab-request">
          <section class="site-form-section"><header><span>02</span><div><h3>站点访问节奏</h3><p>控制当前网站的访问频率和并发</p></div></header><div class="form-grid"><el-form-item label="请求间隔（ms）"><el-input-number v-model="siteForm.requestIntervalMillis" :min="100" :step="100" /></el-form-item><el-form-item label="随机延迟（ms）"><el-input-number v-model="siteForm.randomDelayMillis" :min="0" :step="100" /></el-form-item></div><el-form-item label="最大并发"><el-input-number v-model="siteForm.maxConcurrency" :min="1" :max="8" /></el-form-item><el-alert type="info" :closable="false" title="请求超时、失败重试和请求头已统一迁移到“系统设置 → 爬虫设置”。" /></section>
        </div>
        <div v-show="activeSiteTab==='validation'" id="site-editor-panel-validation" class="site-tab-panel" role="tabpanel" aria-labelledby="site-editor-tab-validation">
          <section class="site-form-section"><header class="section-with-action"><span>04</span><div><h3>正文失败特征</h3><p>正文命中任意一条时，判定本章未成功拉取</p></div><el-button :icon="Plus" :disabled="siteForm.contentFailureMarkers.length>=50" @click="addContentFailureMarker">添加特征</el-button></header><div v-if="siteForm.contentFailureMarkers.length" class="marker-list"><el-form-item v-for="(_,index) in siteForm.contentFailureMarkers" :key="index" :prop="`contentFailureMarkers.${index}`" :rules="contentFailureMarkerRules" :label="`特征 ${index+1}`"><div class="marker-row"><el-input v-model="siteForm.contentFailureMarkers[index]" maxlength="500" show-word-limit placeholder="例如：以下内容为VIP专属，升级会员即可继续阅读"/><el-button text type="danger" @click="removeContentFailureMarker(index)">删除</el-button></div></el-form-item></div><el-empty v-else :image-size="48" description="未配置正文失败特征" /><el-alert type="info" :closable="false" title="采用忽略大小写的包含匹配；命中后记录具体特征，并将章节标记为失败和受限。" /></section>
        </div>
        <div v-show="activeSiteTab==='proxy'" id="site-editor-panel-proxy" class="site-tab-panel" role="tabpanel" aria-labelledby="site-editor-tab-proxy">
          <section class="site-form-section"><header class="section-with-action"><span>05</span><div><h3>代理配置</h3><p>可保存多组代理，同一时间最多生效一组</p></div><el-button :icon="Plus" @click="addProxy">添加代理</el-button></header><div v-if="siteForm.proxies.length" class="proxy-list"><article v-for="(proxy,index) in siteForm.proxies" :key="index" class="proxy-row" :class="{active:proxy.enabled}"><el-form-item label="代理名称" :prop="`proxies.${index}.name`" :rules="proxyNameRules"><el-input v-model="proxy.name" placeholder="例如：家庭网关" /></el-form-item><el-form-item label="代理地址" :prop="`proxies.${index}.url`" :rules="proxyUrlRules"><el-input v-model="proxy.url" placeholder="http://127.0.0.1:7890" /></el-form-item><div class="proxy-state"><el-switch v-model="proxy.enabled" active-text="生效" inactive-text="停用" @change="setActiveProxy(index,Boolean($event))"/><el-button text type="danger" @click="removeProxy(index)">删除</el-button></div></article></div><el-empty v-else :image-size="54" description="未配置代理，将直接连接目标网站" /></section>
        </div>
        <div v-show="activeSiteTab==='automation'" id="site-editor-panel-automation" class="site-tab-panel" role="tabpanel" aria-labelledby="site-editor-tab-automation">
          <section class="site-form-section"><header><span>06</span><div><h3>运行设置</h3><p>网站总开关与任务范围</p></div></header><el-form-item label="网站状态"><el-switch v-model="siteForm.enabled" active-text="启用网站" inactive-text="停用网站" /></el-form-item><el-alert type="info" :closable="false" title="发现页可独立开启自动扫描；书籍正文采集、检查更新、生成文件和入库仍只由人工触发。" /></section>
          <el-alert type="warning" :closable="false" title="仅采集你有权阅读的免费内容；系统不会绕过登录、付费、验证码或访问控制。" />
        </div>
      </el-form>
      <template #footer><el-button @click="siteDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveSite">保存网站</el-button></template>
    </el-dialog>

    <el-dialog v-model="discoveryManagerDialog" :title="`${discoveryManagerSite?.siteName || ''} · 发现页管理`" width="min(820px, 96vw)" append-to-body destroy-on-close>
      <div class="discovery-manager-toolbar"><div><strong>发现页</strong><p>各发现页可独立手动扫描或开启自动扫描。</p></div><el-button type="primary" :icon="Plus" @click="discoveryManagerSite && openDiscoveryPage(discoveryManagerSite)">新增发现页</el-button></div>
      <div v-if="managedDiscoveryPages.length" class="discovery-pages discovery-pages-dialog-list">
        <article v-for="page in managedDiscoveryPages" :key="page.id" class="discovery-page-row">
          <div><strong>{{ page.pageName }}</strong><a :href="page.pageUrl" target="_blank" rel="noopener noreferrer">{{ page.pageUrl }}</a><small>最多 {{ page.maxPages }} 页 · 每 {{ page.scanIntervalMinutes }} 分钟 · {{ page.lastScanAt ? `上次 ${formatTime(page.lastScanAt)}` : '尚未扫描' }}</small></div>
          <el-switch :model-value="page.autoScanEnabled" :disabled="!discoveryManagerSite?.enabled" inline-prompt active-text="自动" inactive-text="手动" aria-label="自动扫描发现页" @change="toggleDiscoveryPageAuto(page,Boolean($event))" />
          <div class="discovery-page-actions"><el-button text size="small" :disabled="!discoveryManagerSite?.enabled || !discoveryManagerSite?.rule?.discoveryItemSelector" @click="scanDiscoveryPage(page)">扫描</el-button><el-button text size="small" @click="discoveryManagerSite && openDiscoveryPage(discoveryManagerSite,page)">编辑</el-button><el-button text size="small" type="danger" @click="removeDiscoveryPage(page)">删除</el-button></div>
        </article>
      </div>
      <el-empty v-else description="尚未添加发现页" />
      <template #footer><el-button @click="discoveryManagerDialog=false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="discoveryPageDialog" :title="editingDiscoveryPage ? '编辑发现页' : '新增发现页'" width="min(620px, 94vw)" append-to-body destroy-on-close>
      <el-form label-position="top" class="discovery-page-form">
        <el-form-item label="发现页名称"><el-input v-model="discoveryPageForm.pageName" maxlength="100" placeholder="例如：热门小说" /></el-form-item>
        <el-form-item label="起始页面 URL"><el-input v-model="discoveryPageForm.pageUrl" placeholder="https://www.chunxiaoge.com/rank/hot/" /><small class="field-hint">扫描会按规则中的“下一页 Selector”持续翻页，直到末页或达到安全上限。</small></el-form-item>
        <div class="form-grid"><el-form-item label="最多扫描页数"><el-input-number v-model="discoveryPageForm.maxPages" :min="1" :max="500" /></el-form-item><el-form-item label="自动扫描间隔（分钟）"><el-input-number v-model="discoveryPageForm.scanIntervalMinutes" :min="5" :max="10080" :disabled="!discoveryPageForm.autoScanEnabled" /></el-form-item></div>
        <el-switch v-model="discoveryPageForm.autoScanEnabled" active-text="开启自动扫描" inactive-text="仅手动扫描" />
        <el-alert type="info" :closable="false" title="自动扫描仅提取书籍到“发现书籍”，不会自动采集正文或加入书库。" />
      </el-form>
      <template #footer><el-button @click="discoveryPageDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDiscoveryPage">保存发现页</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleManagerDialog" :title="`${ruleSite?.siteName || ''} · 规则管理`" width="min(1120px, 96vw)" destroy-on-close>
      <div class="rule-manager-bar"><p>多个版本可以长期共存，但同一时间最多启用一条。</p><div><el-button :icon="Upload" @click="openImportDialog">导入 JSON</el-button><el-button type="primary" :icon="Plus" @click="openRuleEditor()">新建规则</el-button></div></div>
      <el-table v-if="ruleVersions.length" :data="ruleVersions" class="rule-table" max-height="58vh" :row-class-name="({row}: {row:CrawlerRuleVersion}) => row.enabled ? 'active-rule-row' : ''">
        <el-table-column label="版本" width="70"><template #default="{row}"><strong class="rule-version-text">v{{ row.version }}</strong></template></el-table-column>
        <el-table-column label="状态" width="88"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'" effect="light">{{ row.enabled?'生效':'禁用' }}</el-tag></template></el-table-column>
        <el-table-column prop="changeSummary" label="版本说明" width="150" show-overflow-tooltip />
        <el-table-column label="书籍列表" width="130" show-overflow-tooltip><template #default="{row}"><code>{{ row.rule.discoveryItemSelector || '未配置' }}</code></template></el-table-column>
        <el-table-column label="章节目录" width="130" show-overflow-tooltip><template #default="{row}"><code>{{ row.rule.chapterItemSelector }}</code></template></el-table-column>
        <el-table-column label="正文" width="120" show-overflow-tooltip><template #default="{row}"><code>{{ row.rule.contentSelector }}</code></template></el-table-column>
        <el-table-column label="更新时间" width="150"><template #default="{row}">{{ formatTime(row.updatedAt || row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="230"><template #default="{row}"><div class="rule-actions"><el-button text size="small" :type="row.enabled?'warning':'success'" @click="toggleRule(row)">{{ row.enabled?'禁用':'启用' }}</el-button><el-button text size="small" :icon="Edit" @click="openRuleEditor(row)">编辑</el-button><el-button text size="small" :icon="Download" @click="exportRule(row)">导出</el-button><el-button text size="small" @click="ruleSite && openRuleTest(ruleSite,row.rule)">测试</el-button><el-button text size="small" type="danger" @click="removeRule(row)">删除</el-button></div></template></el-table-column>
      </el-table>
      <el-empty v-else description="还没有规则版本，可新建或导入 JSON 规则" />
      <template #footer><el-button v-if="ruleSite?.rule" @click="checkHealth(ruleSite)">健康检查</el-button><el-button @click="ruleManagerDialog=false">完成</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleImportDialog" title="导入规则 JSON" width="min(680px, 94vw)" append-to-body destroy-on-close>
      <div class="import-mode" role="tablist" aria-label="规则 JSON 导入方式" @keydown="handleImportModeKey">
        <span class="import-mode-indicator" :style="{transform:`translateX(${importMode==='text'?0:100}%)`}" />
        <button :class="{active:importMode==='text'}" role="tab" :aria-selected="importMode==='text'" :tabindex="importMode==='text'?0:-1" @click="importMode='text'">粘贴文本</button>
        <button :class="{active:importMode==='file'}" role="tab" :aria-selected="importMode==='file'" :tabindex="importMode==='file'?0:-1" @click="importMode='file'">选择文件</button>
      </div>
      <div v-if="importMode==='text'" class="import-pane">
        <el-input v-model="importJsonText" type="textarea" :rows="14" resize="vertical" spellcheck="false" placeholder='粘贴规则 JSON，例如：&#10;{&#10;  "schemaVersion": 1,&#10;  "version": 2,&#10;  "changeSummary": "新版规则",&#10;  "rule": { ... }&#10;}' />
        <p>导入后的规则默认处于禁用状态，请确认内容后再手动启用。</p>
      </div>
      <div v-else class="import-pane file-pane">
        <input ref="importInput" class="file-input" type="file" accept="application/json,.json" @change="handleImportFile">
        <button class="file-picker" type="button" @click="importInput?.click()"><el-icon><Upload /></el-icon><strong>{{ importFileName || '选择 JSON 文件' }}</strong><span>{{ importFileName ? '已读取文件，可点击重新选择' : '支持 .json 文件' }}</span></button>
        <el-input v-if="importJsonText" v-model="importJsonText" type="textarea" :rows="8" resize="vertical" spellcheck="false" />
      </div>
      <template #footer><el-button @click="ruleImportDialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!importJsonText.trim()" @click="submitRuleImport">导入规则</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleEditorDialog" :title="editingRule ? `编辑规则 v${editingRule.version}` : '新建规则版本'" width="min(800px, 96vw)" append-to-body destroy-on-close>
      <el-form label-position="top" class="site-form">
        <div class="form-grid"><el-form-item label="版本号"><el-input-number v-model="ruleForm.version" :min="1" /></el-form-item><el-form-item label="版本说明"><el-input v-model="ruleForm.changeSummary" placeholder="例如：适配新版目录结构" maxlength="300" /></el-form-item></div>
        <el-switch v-model="ruleForm.enabled" active-text="保存后设为唯一生效规则" />
        <div class="rule-block"><p class="eyebrow">DISCOVERY SELECTORS</p><h3>书籍列表规则</h3><p>扫描发现页时需要配置书籍项和详情链接；其余字段可选填。</p></div>
        <div class="form-grid"><el-form-item label="书籍项 Selector"><el-input v-model="ruleForm.rule.discoveryItemSelector" placeholder=".book-list .book" /></el-form-item><el-form-item label="详情链接 Selector"><el-input v-model="ruleForm.rule.discoveryUrlSelector" placeholder="a.book-link" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="列表书名 Selector"><el-input v-model="ruleForm.rule.discoveryTitleSelector" placeholder=".title" /></el-form-item><el-form-item label="列表作者 Selector"><el-input v-model="ruleForm.rule.discoveryAuthorSelector" placeholder=".author" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="列表封面 Selector"><el-input v-model="ruleForm.rule.discoveryCoverSelector" placeholder="img.cover::data-src" /></el-form-item><el-form-item label="列表分类 Selector"><el-input v-model="ruleForm.rule.discoveryCategorySelector" placeholder=".category" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="最新章节 Selector"><el-input v-model="ruleForm.rule.discoveryLatestChapterSelector" placeholder=".latest" /></el-form-item><el-form-item label="下一页 Selector"><el-input v-model="ruleForm.rule.discoveryNextPageSelector" placeholder="a.next" /></el-form-item></div>
        <div class="rule-block"><p class="eyebrow">DETAIL & CHAPTER</p><h3>详情、目录与正文规则</h3><p>属性读取可写为 <code>.cover::data-src</code>，链接与图片默认读取 href/src。</p></div>
        <div class="form-grid"><el-form-item label="书名 Selector"><el-input v-model="ruleForm.rule.titleSelector" placeholder="h1.book-title" /></el-form-item><el-form-item label="作者 Selector"><el-input v-model="ruleForm.rule.authorSelector" placeholder=".author" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="分类 Selector"><el-input v-model="ruleForm.rule.categorySelector" placeholder=".book-meta .category" /></el-form-item><el-form-item label="书籍状态 Selector"><el-input v-model="ruleForm.rule.statusSelector" placeholder=".book-meta .status" /></el-form-item></div>
        <el-form-item label="标签 Selector"><el-input v-model="ruleForm.rule.tagsSelector" placeholder=".book-meta .tags a" /><small class="field-hint">会提取所有匹配节点的文本，并自动去重。</small></el-form-item>
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
        <template v-if="ruleTestResult.success"><div class="test-facts"><span><small>书名</small><b>{{ ruleTestResult.title }}</b></span><span><small>作者</small><b>{{ ruleTestResult.author || '未知' }}</b></span><span><small>分类</small><b>{{ ruleTestResult.category || '未识别' }}</b></span><span><small>来源状态</small><b>{{ ruleTestResult.bookStatus || '未识别' }}</b></span><span><small>目录</small><b>{{ ruleTestResult.chapterCount }} 章</b></span><span><small>正文样本</small><b>{{ ruleTestResult.contentLength }} 字</b></span></div><div v-if="ruleTestResult.tags?.length" class="metadata-tags"><el-tag v-for="tag in ruleTestResult.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag></div><p><b>{{ ruleTestResult.sampleChapter }}</b> · {{ ruleTestResult.durationMillis }} ms</p><pre>{{ ruleTestResult.contentPreview }}</pre></template>
        <el-alert v-else type="error" :closable="false" :title="ruleTestResult.errorMessage || '规则测试失败'" />
      </div>
      <template #footer><el-button @click="ruleTestDialog=false">关闭</el-button><el-button type="primary" :loading="testingRule" @click="runRuleTest">测试详情、目录与正文</el-button></template>
    </el-dialog>

    <el-dialog v-model="crawlDialog" title="URL 手动采集" width="min(560px, 94vw)">
      <el-form label-position="top"><el-form-item label="采集网站"><el-select v-model="crawlForm.siteId" placeholder="选择已启用网站"><el-option v-for="site in sites.filter(s=>s.enabled)" :key="site.id" :label="site.siteName" :value="site.id" /></el-select></el-form-item><el-form-item label="书籍详情 URL"><el-input v-model="crawlForm.url" placeholder="https://example.com/book/123/" /></el-form-item></el-form>
      <template #footer><el-button @click="crawlDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="startCrawl">创建采集任务</el-button></template>
    </el-dialog>

    <el-dialog v-model="importDialog" :title="importTarget?.importStatus==='IMPORTED'?'同步到书库':'加入书库'" width="min(560px, 94vw)" append-to-body>
      <div v-if="importTarget" class="import-book-dialog">
        <div class="status-book"><span class="mini-cover">{{ importTarget.bookName.slice(0,1) }}</span><div><strong>{{ importTarget.bookName }}</strong><p>{{ importTarget.author || '未知作者' }} · 已有正文 {{ importTarget.crawledChapterCount }} / {{ importTarget.chapterCount }} 章</p></div></div>
        <div><p class="field-label">选择入库格式（可多选）</p><el-checkbox-group v-model="importFormats" class="format-options"><el-checkbox-button v-for="option in importFormatOptions" :key="option.value" :value="option.value"><strong>{{ option.label }}</strong><small>{{ option.description }}</small></el-checkbox-button></el-checkbox-group></div>
        <el-alert v-if="importTarget.crawlStatus!=='COMPLETED'" type="warning" :closable="false" title="当前书籍尚未完整采集，所选格式只会包含已有正文。" />
        <el-alert v-else type="info" :closable="false" :title="importTarget.importStatus==='IMPORTED'?'只同步所选格式；内容未变化时不会重复创建版本。':'多个格式会归入同一本书，并显示为不同书籍版本。'" />
      </div>
      <template #footer><el-button @click="importDialog=false">取消</el-button><el-button type="primary" :loading="importing" :disabled="!importFormats.length" @click="submitImport">{{ importTarget?.importStatus==='IMPORTED'?'同步所选格式':'确认入库' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="statusDialog" title="人工修改采集状态" width="min(520px, 94vw)" append-to-body>
      <div v-if="statusBook" class="status-dialog-content">
        <div class="status-book"><span class="mini-cover">{{ statusBook.bookName.slice(0,1) }}</span><div><strong>{{ statusBook.bookName }}</strong><p>当前状态：{{ statusLabel(statusBook.crawlStatus) }} · 已有正文 {{ statusBook.crawledChapterCount }} / {{ statusBook.chapterCount }} 章</p></div></div>
        <el-form label-position="top"><el-form-item label="修改为"><el-select v-model="manualStatus"><el-option v-for="item in manualStatusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-form>
        <el-alert type="warning" :closable="false" title="只调整整本书状态，不会补齐缺失章节或改变章节统计；后续任务仍需人工触发。" />
      </div>
      <template #footer><el-button @click="statusDialog=false">取消</el-button><el-button type="primary" :loading="savingStatus" @click="saveBookStatus">确认修改</el-button></template>
    </el-dialog>

    <el-dialog v-model="taskEditDialog" title="修改采集任务" width="min(520px, 94vw)" append-to-body>
      <div v-if="editingTask" class="task-edit-content">
        <div class="task-edit-summary"><span>{{ taskTypeLabel(editingTask.type) }}</span><div><strong>{{ editingTask.bookName || editingTask.discoveryPageName || editingTask.siteName }}</strong><p>{{ editingTask.siteName }} · {{ statusLabel(editingTask.status) }}</p></div></div>
        <div><p class="field-label">任务优先级</p><div class="priority-segment" role="radiogroup" aria-label="任务优先级" @keydown="handlePriorityKey"><span class="priority-indicator" :style="{transform:`translateX(${taskPriorityIndex*100}%)`}"/><button v-for="item in taskPriorityOptions" :key="item.value" type="button" role="radio" :aria-checked="taskPriority===item.value" :tabindex="taskPriority===item.value?0:-1" :class="{active:taskPriority===item.value}" @click="taskPriority=item.value">{{ item.label }}</button></div></div>
        <el-alert v-if="editingTask.status==='FAILED'" type="info" :closable="false" title="失败任务保存后不会自动执行；新优先级将在后续重新采集时使用。" />
        <el-alert v-else type="info" :closable="false" title="等待中的任务保存后会立即按新优先级重新排队；暂停任务将在恢复时使用新优先级。" />
      </div>
      <template #footer><el-button @click="taskEditDialog=false">取消</el-button><el-button type="primary" :loading="savingTask" @click="saveTask">保存修改</el-button></template>
    </el-dialog>

    <el-drawer v-model="taskDrawer" size="min(720px, 96vw)" :title="selectedTask ? `${taskTypeLabel(selectedTask.type)}详情` : '采集任务详情'" class="task-detail-drawer">
      <div v-if="selectedTask" v-loading="taskDetailLoading" class="task-detail-content">
        <header class="task-detail-hero">
          <div class="task-detail-mark"><span :class="`state-${selectedTask.status.toLowerCase()}`"/><b>{{ taskProgress(selectedTask) }}%</b></div>
          <div><p class="eyebrow">{{ selectedTask.id }}</p><h2>{{ selectedTask.bookName || selectedTask.discoveryPageName || selectedTask.siteName }}</h2><p>{{ selectedTask.siteName }} · {{ taskTypeLabel(selectedTask.type) }}</p></div>
          <el-tag :type="statusType(selectedTask.status)" effect="light">{{ statusLabel(selectedTask.status) }}</el-tag>
        </header>

        <section class="task-progress-card">
          <div><strong>任务进度</strong><span v-if="['RUNNING','WAITING'].includes(selectedTask.status)" class="live-state"><i/>每 {{ pollingIntervalSeconds }} 秒更新</span></div>
          <el-progress v-if="selectedTask.status!=='SUCCESS'" :class="{'crawler-running-progress':selectedTask.status==='RUNNING'}" :percentage="taskProgress(selectedTask)" :status="selectedTask.status==='FAILED'?'exception':undefined" :stroke-width="12" />
          <div v-else class="task-success-progress"><strong>✓ 已完成</strong><span>{{ selectedTask.type==='SITE_SCAN' ? `扫描 ${selectedTask.scannedPageCount} 页，发现 ${selectedTask.totalCount} 本` : `成功处理 ${selectedTask.successCount} 项` }}</span></div>
          <p>{{ taskProgressDescription(selectedTask) }}</p>
        </section>

        <section class="task-stat-grid">
          <span><small>总数</small><strong>{{ selectedTask.totalCount }}</strong></span>
          <span><small>成功</small><strong>{{ selectedTask.successCount }}</strong></span>
          <span v-if="selectedTask.type==='SITE_SCAN'"><small>新增</small><strong>{{ selectedTask.newBookCount }}</strong></span>
          <span v-if="selectedTask.type==='SITE_SCAN'"><small>重复</small><strong>{{ selectedTask.duplicateCount }}</strong></span>
          <span><small>失败</small><strong>{{ selectedTask.failedCount }}</strong></span>
          <span><small>等待</small><strong>{{ selectedTask.waitingCount }}</strong></span>
        </section>

        <section class="task-detail-list">
          <div><span>当前处理</span><strong>{{ selectedTask.currentChapter || (['SUCCESS','PARTIAL_SUCCESS'].includes(selectedTask.status) ? '任务已完成' : '暂无') }}</strong></div>
          <div v-if="selectedTask.type==='SITE_SCAN'"><span>分页进度</span><strong>{{ selectedTask.scannedPageCount }} / {{ selectedTask.scanMaxPages || '—' }} 页</strong></div>
          <div><span>优先级</span><strong>{{ priorityLabel(selectedTask.priority) }}</strong></div>
          <div><span>平均请求耗时</span><strong>{{ selectedTask.averageRequestMillis ? `${selectedTask.averageRequestMillis} ms` : '暂无' }}</strong></div>
          <div><span>创建时间</span><strong>{{ formatTime(selectedTask.createdAt) }}</strong></div>
          <div><span>开始时间</span><strong>{{ formatTime(selectedTask.startedAt) }}</strong></div>
          <div><span>完成时间</span><strong>{{ formatTime(selectedTask.finishedAt) }}</strong></div>
        </section>

        <el-alert v-if="selectedTask.errorMessage" type="error" :closable="false" title="任务错误" :description="selectedTask.errorMessage" show-icon />
        <div class="task-detail-actions">
          <el-button v-if="selectedTask.bookId" @click="openTaskBook(selectedTask)">查看采集书籍</el-button>
          <el-button v-if="selectedTask.type==='SITE_SCAN'" @click="openScanResults(selectedTask)">查看扫描结果</el-button>
          <el-button v-if="['WAITING','PAUSED','FAILED'].includes(selectedTask.status)" @click="openTaskEditor(selectedTask)">修改优先级</el-button>
          <el-button v-if="selectedTask.status==='RUNNING'" @click="runTaskCommand(selectedTask,'pause')">暂停</el-button>
          <el-button v-if="['PAUSED','FAILED'].includes(selectedTask.status)" type="primary" @click="runTaskCommand(selectedTask,'resume')">继续</el-button>
          <el-button v-if="['RUNNING','WAITING','PAUSED'].includes(selectedTask.status)" type="danger" plain @click="runTaskCommand(selectedTask,'cancel')">取消</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="queueSettingsDialog" title="采集任务并行设置" width="min(520px, 94vw)" append-to-body>
      <div class="queue-settings-content">
        <div class="queue-settings-summary"><span><small>正在运行</small><strong>{{ taskQueueSettings?.runningCount || 0 }}</strong></span><span><small>等待队列</small><strong>{{ taskQueueSettings?.queuedCount || 0 }}</strong></span></div>
        <el-form label-position="top"><el-form-item label="最多同时运行任务数"><el-input-number v-model="queueLimit" :min="1" :max="16" controls-position="right" /><small class="field-hint">新任务超过上限后保持等待状态；有任务结束时，按高、中、低优先级及入队顺序自动开始。调低上限不会强制中断已运行任务。</small></el-form-item></el-form>
      </div>
      <template #footer><el-button @click="queueSettingsDialog=false">取消</el-button><el-button type="primary" :loading="savingQueueSettings" @click="saveQueueSettings">保存设置</el-button></template>
    </el-dialog>

    <el-dialog v-model="queuedTasksDialog" :title="`当前排队任务 · ${queuedTasks.length}`" width="min(760px, 94vw)" append-to-body>
      <el-table v-loading="queuedTasksLoading" :data="queuedTasks" max-height="56vh" class="queued-task-table">
        <el-table-column type="index" label="#" width="54" />
        <el-table-column label="任务" min-width="250"><template #default="{row}"><button type="button" class="queue-task-link" @click="openQueuedTask(row)"><strong>{{ row.bookName || row.discoveryPageName || taskTypeLabel(row.type) }}</strong><small>{{ row.siteName }} · {{ taskTypeLabel(row.type) }}</small></button></template></el-table-column>
        <el-table-column label="优先级" width="90"><template #default="{row}"><el-tag :type="priorityType(row.priority)" effect="plain">{{ priorityLabel(row.priority) }}</el-tag></template></el-table-column>
        <el-table-column label="进入队列" width="160"><template #default="{row}">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="86"><template #default="{row}"><el-button text type="primary" @click="openQueuedTask(row)">详情</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!queuedTasksLoading&&!queuedTasks.length" description="当前没有排队任务" />
      <template #footer><el-button :loading="queuedTasksLoading" @click="loadQueuedTasks">刷新</el-button><el-button @click="queuedTasksDialog=false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="scanResultsDialog" :title="`${scanResultsTask?.discoveryPageName || '发现页'} · 扫描结果`" width="min(920px, 96vw)" append-to-body>
      <div v-if="scanResultsTask" class="scan-result-summary"><span><small>扫描到</small><strong>{{ scanResultsTask.totalCount }}</strong></span><span><small>成功</small><strong>{{ scanResultsTask.successCount }}</strong></span><span><small>新增</small><strong>{{ scanResultsTask.newBookCount }}</strong></span><span><small>重复</small><strong>{{ scanResultsTask.duplicateCount }}</strong></span><span><small>失败</small><strong>{{ scanResultsTask.failedCount }}</strong></span></div>
      <el-table v-loading="scanResultsLoading" :data="scanResults" max-height="52vh" class="scan-results-table">
        <el-table-column prop="bookName" label="书籍" min-width="220" show-overflow-tooltip />
        <el-table-column label="结果" width="100"><template #default="{row}"><el-tag :type="scanResultType(row.resultStatus)" effect="light">{{ scanResultLabel(row.resultStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="地址" min-width="280" show-overflow-tooltip><template #default="{row}"><a :href="row.bookUrl" target="_blank">{{ row.bookUrl }}</a></template></el-table-column>
        <el-table-column prop="errorMessage" label="说明" min-width="180" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!scanResultsLoading&&!scanResults.length" description="任务尚未产生书籍扫描结果" />
      <div v-if="scanResultsTotal" class="scan-results-pagination"><span>共 {{ scanResultsTotal }} 条</span><el-pagination v-model:current-page="scanResultsPage" v-model:page-size="scanResultsPageSize" :page-sizes="[20,50,100]" :total="scanResultsTotal" layout="sizes, prev, pager, next" background small @current-change="loadScanResults" @size-change="handleScanResultSizeChange" /></div>
      <template #footer><el-button @click="scanResultsDialog=false">关闭</el-button></template>
    </el-dialog>

    <el-drawer v-model="bookDrawer" size="min(760px, 96vw)" :title="selectedBook?.bookName || '采集书籍详情'" class="book-detail-drawer">
      <div v-if="selectedBook" class="book-drawer-content">
        <div class="book-summary"><div class="large-cover">{{ selectedBook.bookName.slice(0,1) }}</div><div><h2>{{ selectedBook.bookName }}</h2><p>{{ selectedBook.author || '未知作者' }} · {{ selectedBook.siteName }}</p><el-progress :class="{'crawler-running-progress':isBookRunning(selectedBook)}" :percentage="progress(selectedBook)"/><small>{{ selectedBook.crawledChapterCount }} / {{ selectedBook.chapterCount }} 章，失败 {{ selectedBook.failedChapterCount }}</small></div></div>
        <div class="crawler-book-metadata"><span><small>来源状态</small><strong>{{ selectedBook.bookStatus || '未识别' }}</strong></span><span><small>分类</small><strong>{{ selectedBook.category || '未分类' }}</strong></span><span class="metadata-tag-row"><small>标签</small><span v-if="selectedBook.tags?.length" class="metadata-tags"><el-tag v-for="tag in selectedBook.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag></span><strong v-else>无</strong></span></div>
        <div v-if="selectedBook.libraryBookId" class="library-sync-control"><div><strong>自动同步到书库</strong><p>检查更新或续采成功后，自动发布为同一本书的新版本。</p></div><el-switch :model-value="selectedBook.autoSyncLibrary" @change="toggleLibrarySync(selectedBook,Boolean($event))" /></div>
        <div class="drawer-actions"><el-button @click="continueCrawl(selectedBook)">继续采集</el-button><el-button :disabled="!selectedBook.failedChapterCount" @click="retryFailures(selectedBook)">重试失败</el-button><el-button @click="openStatusEditor(selectedBook)">修改状态</el-button><el-button type="primary" plain :disabled="!selectedBook.crawledChapterCount" @click="startTrial(selectedBook)">临时试读</el-button><el-button type="primary" @click="generate(selectedBook)">生成 TXT + EPUB</el-button><el-button type="primary" plain @click="importBook(selectedBook)">{{ selectedBook.importStatus==='IMPORTED'?'立即同步书库':'入库' }}</el-button></div>
        <div class="book-detail-tabs" role="tablist" aria-label="书籍采集详情" @keydown="handleBookDetailTabKey"><span class="book-detail-tab-indicator" :style="{transform:`translateX(${bookDetailTabIndex*100}%)`}"/><button v-for="item in bookDetailTabs" :key="item.value" type="button" role="tab" :aria-selected="bookDetailTab===item.value" :tabindex="bookDetailTab===item.value?0:-1" :class="{active:bookDetailTab===item.value}" @click="bookDetailTab=item.value"><span>{{ item.label }}</span><b>{{ item.value==='chapters'?chapterTotal:crawlerLogs.length }}</b></button></div>
        <section v-show="bookDetailTab==='chapters'" class="drawer-detail-panel chapter-progress-panel" role="tabpanel">
          <div class="chapter-list-heading"><div><p class="eyebrow">CHAPTER INDEX</p><h3>章节进度</h3></div><div class="chapter-heading-tools"><label class="chapter-follow" :class="{active:followCurrentChapter}"><strong>跟随采集</strong><el-switch :model-value="followCurrentChapter" aria-label="自动跟随当前采集章节" @change="setFollowCurrentChapter(Boolean($event))" /></label><div class="chapter-sort" :class="{disabled:followCurrentChapter}" role="tablist" aria-label="章节排序方式" @keydown="handleChapterSortKey"><span class="chapter-sort-indicator" :style="{transform:`translateX(${chapterSortIndex*100}%)`}"/><button v-for="item in chapterSortOptions" :key="item.value" type="button" role="tab" :disabled="followCurrentChapter" :aria-selected="chapterSort===item.value" :tabindex="chapterSort===item.value?0:-1" :class="{active:chapterSort===item.value}" @click="changeChapterSort(item.value)">{{ item.label }}</button></div></div></div>
          <el-table v-loading="chapterLoading" :data="chapters" height="100%" class="chapters-table" :row-class-name="chapterRowClassName" @row-click="openChapter"><el-table-column prop="chapterIndex" label="#" width="65"/><el-table-column label="章节" min-width="220"><template #default="{row}"><div class="chapter-name-cell"><span>{{ row.chapterName }}</span><em v-if="row.id===currentCrawlingChapter?.id"><i/>当前采集</em></div></template></el-table-column><el-table-column prop="wordCount" label="字数" width="90"/><el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="statusType(row.crawlStatus)">{{ statusLabel(row.crawlStatus) }}</el-tag></template></el-table-column></el-table>
          <div v-if="chapterTotal" class="chapter-pagination"><span>第 {{ chapterPage }} / {{ Math.max(1,Math.ceil(chapterTotal/chapterPageSize)) }} 页 · 共 {{ chapterTotal }} 章</span><el-pagination v-model:current-page="chapterPage" :page-size="chapterPageSize" :page-sizes="[20,50,100]" :total="chapterTotal" layout="sizes, prev, pager, next" background small @current-change="loadChapterPage" @size-change="handleChapterSizeChange" /></div>
        </section>
        <section v-show="bookDetailTab==='logs'" class="drawer-detail-panel realtime-log-panel" role="tabpanel">
          <div class="realtime-log-heading"><div><p class="eyebrow">LIVE CRAWLER FEED</p><h3>实时日志</h3></div><span class="live-state"><i/>每 {{ pollingIntervalSeconds }} 秒同步</span></div>
          <div v-if="crawlerLogs.length" class="crawler-log-stream">
            <article v-for="logItem in crawlerLogs" :key="logItem.id" class="crawler-log-item"><time>{{ formatTime(logItem.createdAt) }}</time><div><strong>{{ logItem.description }}</strong><p>{{ logItem.details || '无详细信息' }}</p></div></article>
          </div>
          <el-empty v-else :image-size="64" description="暂无该书籍的爬取日志" />
        </section>
      </div>
    </el-drawer>
    <el-dialog v-model="chapterDialog" :title="chapterDetail?.title || '章节正文'" width="min(760px, 94vw)"><a v-if="chapterDetail" :href="chapterDetail.url" target="_blank">查看原始网页</a><pre class="chapter-content">{{ chapterDetail?.content || chapterDetail?.errorMessage }}</pre></el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { Collection, Connection, DataAnalysis, Document, Download, Edit, Link, List, Plus, Refresh, Search, Tickets, Upload, Warning } from '@element-plus/icons-vue'
import { ElButton, ElProgress, ElTable, ElTableColumn, ElTag, type FormInstance, type FormItemRule, type FormRules } from 'element-plus'
import { crawlerApi, type CrawlerBook, type CrawlerChapter, type CrawlerDashboard, type CrawlerDiscoveryPage, type CrawlerDiscoveryPagePayload, type CrawlerLog, type CrawlerRule, type CrawlerRuleExport, type CrawlerRuleTest, type CrawlerRuleVersion, type CrawlerScanResult, type CrawlerSite, type CrawlerSitePayload, type CrawlerTask, type CrawlerTaskQueueSettings } from '@/utils/crawler'
import { usePreferencesStore } from '@/stores/preferences'
import { getCoverUrl } from '@/utils/cover'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'
import { confirm, message } from '@/utils/message'

const TaskTable = defineComponent({ props:{ tasks:{type:Array as ()=>CrawlerTask[],required:true}}, emits:['open','command','edit','delete','scan-results'], setup(props,{emit}) { return () => h(ElTable,{data:props.tasks,class:'data-table'},()=>[
  h(ElTableColumn,{label:'任务',minWidth:240},{default:({row}:{row:CrawlerTask})=>h('div',{class:'task-open',role:'button',tabindex:0,onClick:()=>emit('open',row),onKeydown:(event:KeyboardEvent)=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();emit('open',row)}}},[h('strong',row.bookName||row.discoveryPageName||taskTypeLabel(row.type)),h('p',`${row.siteName} · ${taskTypeLabel(row.type)}`)])}),
  h(ElTableColumn,{label:'进度 / 扫描结果',minWidth:300},{default:({row}:{row:CrawlerTask})=>row.status==='SUCCESS'?h('div',{class:'task-success-progress'},[h('strong','✓ 已完成'),h('span',row.type==='SITE_SCAN'?`扫描 ${row.scannedPageCount} 页，发现 ${row.totalCount} 本`:`成功处理 ${row.successCount} 项`)]):row.type==='SITE_SCAN'?h('div',{class:'task-scan-progress'},[h(ElProgress,{class:row.status==='RUNNING'?'crawler-running-progress':undefined,percentage:scanTaskPercentage(row),strokeWidth:7}),h('small',scanTaskProgressText(row)),h('div',{class:'task-scan-summary'},[`扫描到 ${row.totalCount}`,h('b',`成功 ${row.successCount}`),h('i',`新增 ${row.newBookCount}`),h('em',`重复 ${row.duplicateCount}`),row.failedCount?h('strong',`失败 ${row.failedCount}`):null])]):h(ElProgress,{class:row.status==='RUNNING'?'crawler-running-progress':undefined,percentage:row.totalCount?Math.round(row.successCount/row.totalCount*100):0,strokeWidth:7})}),
  h(ElTableColumn,{label:'状态',width:130},{default:({row}:{row:CrawlerTask})=>h(ElTag,{type:statusType(row.status)},()=>statusLabel(row.status))}),
  h(ElTableColumn,{label:'优先级',width:90},{default:({row}:{row:CrawlerTask})=>h(ElTag,{type:priorityType(row.priority),effect:'plain'},()=>priorityLabel(row.priority))}),
  h(ElTableColumn,{label:'当前章节',prop:'currentChapter',minWidth:150}),
  h(ElTableColumn,{label:'操作',width:390},{default:({row}:{row:CrawlerTask})=>[h(ElButton,{text:true,type:'primary',onClick:()=>emit('open',row)},()=> '详情'),row.type==='SITE_SCAN'?h(ElButton,{text:true,onClick:()=>emit('scan-results',row)},()=> '扫描结果'):null,['WAITING','PAUSED','FAILED'].includes(row.status)?h(ElButton,{text:true,onClick:()=>emit('edit',row)},()=> '修改'):null,row.status==='RUNNING'?h(ElButton,{text:true,onClick:()=>emit('command',row,'pause')},()=> '暂停'):null,['PAUSED','FAILED'].includes(row.status)?h(ElButton,{text:true,type:'primary',onClick:()=>emit('command',row,'resume')},()=> '继续'):null,['RUNNING','WAITING','PAUSED'].includes(row.status)?h(ElButton,{text:true,type:'danger',onClick:()=>emit('command',row,'cancel')},()=> '取消'):null,row.status!=='RUNNING'?h(ElButton,{text:true,type:'danger',onClick:()=>emit('delete',row)},()=> '删除'):null]})
]) }})

type TabKey='overview'|'sites'|'discovered'|'books'|'tasks'|'failed'
type ChapterSort='indexAsc'|'indexDesc'|'createdDesc'
type BookDetailTab='chapters'|'logs'
type SiteEditorTab='basic'|'request'|'validation'|'proxy'|'automation'
type DiscoveryViewMode='table'|'card'
type LoadOptions={silent?:boolean;preserveSelection?:boolean}
const router=useRouter()
const preferencesStore=usePreferencesStore()
const {crawlerFollowCurrentChapter:followCurrentChapter,crawlerChapterPageSize:chapterPageSize,crawlerDiscoveryViewMode:discoveryViewMode,crawlerBookViewMode:bookViewMode}=storeToRefs(preferencesStore)
const POLLING_INTERVAL_KEY='aibook.crawler.pollingIntervalSeconds'
const pollingIntervalOptions=[1,3,5,10,30] as const
type PollingIntervalSeconds=typeof pollingIntervalOptions[number]
const storedPollingInterval=Number(localStorage.getItem(POLLING_INTERVAL_KEY))
const pollingIntervalSeconds=ref<PollingIntervalSeconds>(pollingIntervalOptions.includes(storedPollingInterval as PollingIntervalSeconds)?storedPollingInterval as PollingIntervalSeconds:3)
const activeTab=ref<TabKey>('overview'), dashboard=ref<CrawlerDashboard>(), sites=ref<CrawlerSite[]>([]), books=ref<CrawlerBook[]>([]), discoveredBooks=ref<CrawlerBook[]>([]), tasks=ref<CrawlerTask[]>([]), failedTasks=ref<CrawlerTask[]>([])
const siteDialog=ref(false), discoveryManagerDialog=ref(false), discoveryPageDialog=ref(false), crawlDialog=ref(false), bookDrawer=ref(false), taskDrawer=ref(false), chapterDialog=ref(false), ruleTestDialog=ref(false), ruleManagerDialog=ref(false), ruleEditorDialog=ref(false), ruleImportDialog=ref(false), statusDialog=ref(false), taskEditDialog=ref(false), saving=ref(false), savingStatus=ref(false), savingTask=ref(false), testingRule=ref(false), discoveryLoading=ref(false), taskDetailLoading=ref(false), editingSite=ref<CrawlerSite>(), discoveryManagerSite=ref<CrawlerSite>(), discoveryPageSite=ref<CrawlerSite>(), editingDiscoveryPage=ref<CrawlerDiscoveryPage>(), selectedBook=ref<CrawlerBook>(), selectedTask=ref<CrawlerTask>(), statusBook=ref<CrawlerBook>(), editingTask=ref<CrawlerTask>(), selectedDiscoveries=ref<CrawlerBook[]>([]), chapters=ref<CrawlerChapter[]>([]), crawlerLogs=ref<CrawlerLog[]>([]), chapterDetail=ref<{title:string;url:string;content:string;errorMessage:string}>(), bookKeyword=ref(''), manualStatus=ref('COMPLETED'), taskPriority=ref<'LOW'|'NORMAL'|'HIGH'>('NORMAL'), discoveryPage=ref(1), discoveryPageSize=ref(20), discoveredTotal=ref(0), discoveryKeyword=ref(''), discoverySiteId=ref<number>(), discoverySort=ref('DISCOVER_TIME_DESC')
const discoveryPagesBySite=ref<Record<number,CrawlerDiscoveryPage[]>>({})
const bookLoading=ref(false), bookPage=ref(1), bookPageSize=ref(20), bookTotal=ref(0)
const bookSiteId=ref<number>(), bookCrawlStatus=ref(''), bookImportStatus=ref(''), bookSort=ref('CREATED_DESC')
const chapterLoading=ref(false), chapterPage=ref(1), chapterTotal=ref(0)
const currentCrawlingChapter=ref<CrawlerChapter>()
const taskLoading=ref(false), taskPage=ref(1), taskPageSize=ref(20), taskTotal=ref(0)
const taskStatusFilter=ref('')
const scanResultsDialog=ref(false), scanResultsLoading=ref(false), scanResultsTask=ref<CrawlerTask>(), scanResults=ref<CrawlerScanResult[]>([]), scanResultsPage=ref(1), scanResultsPageSize=ref(50), scanResultsTotal=ref(0)
const queueSettingsDialog=ref(false), savingQueueSettings=ref(false), taskQueueSettings=ref<CrawlerTaskQueueSettings>(), queueLimit=ref(4), queuedTasksDialog=ref(false), queuedTasksLoading=ref(false), queuedTasks=ref<CrawlerTask[]>([])
const failedTaskLoading=ref(false), failedTaskPage=ref(1), failedTaskPageSize=ref(20), failedTaskTotal=ref(0)
const ruleTestSite=ref<CrawlerSite>(), ruleTestDraft=ref<CrawlerRule>(), ruleSite=ref<CrawlerSite>(), editingRule=ref<CrawlerRuleVersion>(), ruleTestUrl=ref(''), ruleTestResult=ref<CrawlerRuleTest>(), ruleVersions=ref<CrawlerRuleVersion[]>([]), importInput=ref<HTMLInputElement>(), importMode=ref<'text'|'file'>('text'), importJsonText=ref(''), importFileName=ref('')
const importDialog=ref(false), importTarget=ref<CrawlerBook>(), importFormats=ref<string[]>(['EPUB','TXT']), importing=ref(false)
const importFormatOptions=[{value:'EPUB',label:'EPUB',description:'目录与章节阅读体验更佳'},{value:'TXT',label:'TXT',description:'通用纯文本，便于备份'}]
const crawlForm=reactive<{siteId?:number;url:string}>({url:''})
const discoveryPageForm=reactive<CrawlerDiscoveryPagePayload>({pageName:'',pageUrl:'',autoScanEnabled:false,scanIntervalMinutes:360,maxPages:50})
const emptyRule=():CrawlerRule=>({discoveryItemSelector:'',discoveryUrlSelector:'a',discoveryTitleSelector:'.title',discoveryAuthorSelector:'',discoveryCoverSelector:'',discoveryCategorySelector:'',discoveryLatestChapterSelector:'',discoveryNextPageSelector:'',titleSelector:'',authorSelector:'',descriptionSelector:'',categorySelector:'',tagsSelector:'',statusSelector:'',chapterListUrlSelector:'',chapterItemSelector:'',chapterTitleSelector:':scope',chapterUrlSelector:'a',contentTitleSelector:'h1',contentSelector:'',removeSelectors:'',xpathRemoveSelectors:'',stringReplacementsJson:'',regexReplacementsJson:'',removeBlankLines:true,saveOriginalHtml:false,minChapterLength:100})
const emptySite=():CrawlerSitePayload=>({siteName:'',siteCode:'',baseUrl:'',homeUrl:'',enabled:false,autoScan:false,autoCrawl:false,autoUpdate:false,autoImportLibrary:false,scanIntervalMinutes:360,updateIntervalMinutes:30,maxDiscoveryPages:3,autoImportFormat:'EPUB',requestIntervalMillis:1500,randomDelayMillis:1000,maxConcurrency:1,encoding:'UTF-8',proxies:[],contentFailureMarkers:[]})
const siteForm=reactive<CrawlerSitePayload>(emptySite())
const siteFormRef=ref<FormInstance>()
const siteFormRules:FormRules={
  siteName:[{required:true,whitespace:true,message:'请输入网站名称',trigger:['blur','change']}],
  baseUrl:[{required:true,whitespace:true,message:'请输入网站根地址',trigger:['blur','change']}],
}
const proxyNameRules:FormItemRule[]=[{required:true,whitespace:true,message:'请输入代理名称',trigger:['blur','change']}]
const proxyUrlRules:FormItemRule[]=[{required:true,whitespace:true,message:'请输入代理地址',trigger:['blur','change']}]
const contentFailureMarkerRules:FormItemRule[]=[{required:true,whitespace:true,message:'请输入正文失败特征',trigger:['blur','change']},{max:500,message:'单条特征不能超过 500 个字符',trigger:['blur','change']}]
const ruleForm=reactive<{version:number;changeSummary:string;enabled:boolean;rule:CrawlerRule}>({version:1,changeSummary:'',enabled:true,rule:emptyRule()})
const manualStatusOptions=[{value:'COMPLETED',label:'已完成'},{value:'PARTIAL_SUCCESS',label:'部分成功'},{value:'FAILED',label:'失败'},{value:'PAUSED',label:'已暂停'},{value:'WAITING',label:'等待中'},{value:'DISCOVERED',label:'已发现'}]
const chapterSort=ref<ChapterSort>('indexAsc')
const bookDetailTab=ref<BookDetailTab>('chapters')
const bookDetailTabs:{value:BookDetailTab;label:string}[]=[{value:'chapters',label:'章节进度'},{value:'logs',label:'实时日志'}]
const chapterSortOptions:{value:ChapterSort;label:string}[]=[{value:'indexAsc',label:'章节正序'},{value:'indexDesc',label:'章节倒序'},{value:'createdDesc',label:'添加时间'}]
const taskPriorityOptions:{value:'LOW'|'NORMAL'|'HIGH';label:string}[]=[{value:'LOW',label:'低'},{value:'NORMAL',label:'普通'},{value:'HIGH',label:'高'}]
const taskStatusOptions=['WAITING','RUNNING','PAUSED','SUCCESS','PARTIAL_SUCCESS','FAILED','CANCELLED']
const discoverySortOptions=[{value:'DISCOVER_TIME_DESC',label:'发现时间：最新优先'},{value:'DISCOVER_TIME_ASC',label:'发现时间：最早优先'},{value:'BOOK_NAME_ASC',label:'书名：正序'},{value:'BOOK_NAME_DESC',label:'书名：倒序'},{value:'AUTHOR_ASC',label:'作者：正序'},{value:'AUTHOR_DESC',label:'作者：倒序'}]
const managedBookStatusOptions=[{value:'WAITING',label:'等待中'},{value:'CRAWLING_METADATA',label:'采集元数据'},{value:'CRAWLING_CHAPTER_LIST',label:'采集目录'},{value:'CRAWLING_CONTENT',label:'采集正文'},{value:'PARTIAL_SUCCESS',label:'部分成功'},{value:'COMPLETED',label:'已完成'},{value:'FAILED',label:'失败'},{value:'UPDATING',label:'更新中'},{value:'PAUSED',label:'已暂停'}]
const bookImportStatusOptions=[{value:'NOT_IMPORTED',label:'未入库'},{value:'READY',label:'待入库'},{value:'IMPORTED',label:'已入库'}]
const managedBookSortOptions=[{value:'CREATED_DESC',label:'创建时间：最新优先'},{value:'CREATED_ASC',label:'创建时间：最早优先'},{value:'CRAWL_STARTED_DESC',label:'开始爬取：最新优先'},{value:'CRAWL_STARTED_ASC',label:'开始爬取：最早优先'},{value:'LAST_CRAWL_DESC',label:'完成爬取：最新优先'},{value:'LAST_CRAWL_ASC',label:'完成爬取：最早优先'},{value:'BOOK_NAME_ASC',label:'书名：正序'},{value:'BOOK_NAME_DESC',label:'书名：倒序'}]
const siteEditorTabs:{key:SiteEditorTab;label:string;description:string}[]=[{key:'basic',label:'基本信息',description:'身份与入口'},{key:'request',label:'请求访问',description:'频率与请求头'},{key:'validation',label:'内容校验',description:'失败特征'},{key:'proxy',label:'代理配置',description:'站点代理'},{key:'automation',label:'运行设置',description:'任务范围'}]
const activeSiteTab=ref<SiteEditorTab>('basic')
const tabs=computed(()=>[{key:'overview' as const,label:'采集概览',icon:DataAnalysis,count:0},{key:'sites' as const,label:'采集网站',icon:Connection,count:sites.value.length},{key:'discovered' as const,label:'发现书籍',icon:Tickets,count:discoveredTotal.value},{key:'books' as const,label:'采集书籍',icon:Collection,count:bookTotal.value},{key:'tasks' as const,label:'采集任务',icon:List,count:taskTotal.value},{key:'failed' as const,label:'失败任务',icon:Warning,count:failedTaskTotal.value}])
const activeIndex=computed(()=>tabs.value.findIndex(t=>t.key===activeTab.value))
const selectedDiscoveryIds=computed(()=>new Set(selectedDiscoveries.value.map(book=>book.id)))
const allDiscoveredSelected=computed(()=>discoveredBooks.value.length>0&&discoveredBooks.value.every(book=>selectedDiscoveryIds.value.has(book.id)))
const someDiscoveredSelected=computed(()=>discoveredBooks.value.some(book=>selectedDiscoveryIds.value.has(book.id)))
const managedDiscoveryPages=computed(()=>discoveryManagerSite.value?discoveryPagesBySite.value[discoveryManagerSite.value.id]||[]:[])
const chapterSortIndex=computed(()=>chapterSortOptions.findIndex(item=>item.value===chapterSort.value))
const bookDetailTabIndex=computed(()=>bookDetailTabs.findIndex(item=>item.value===bookDetailTab.value))
const taskPriorityIndex=computed(()=>taskPriorityOptions.findIndex(item=>item.value===taskPriority.value))
const activeSiteTabIndex=computed(()=>siteEditorTabs.findIndex(tab=>tab.key===activeSiteTab.value))
const metrics=computed(()=>[{label:'采集网站',value:dashboard.value?.siteCount||0,note:`${dashboard.value?.enabledSiteCount||0} 个启用`,icon:Connection},{label:'采集书籍',value:dashboard.value?.bookCount||0,note:`今日 +${dashboard.value?.todayNewBooks||0}`,icon:Collection},{label:'已采集完成',value:dashboard.value?.completedBookCount||0,note:`${dashboard.value?.readyToImportCount||0} 本待入库`,icon:Document},{label:'今日新增章节',value:dashboard.value?.todayNewChapters||0,note:`${dashboard.value?.crawlingBookCount||0} 本采集中`,icon:Tickets}])
let timer:number|undefined
let progressPolling=false
let chapterRequestSequence=0
onMounted(async()=>{await preferencesStore.hydrate();await refresh();restartPolling()})
onUnmounted(()=>{if(timer)window.clearInterval(timer)})
function restartPolling(){if(timer)window.clearInterval(timer);timer=window.setInterval(()=>{void pollCrawlerProgress()},pollingIntervalSeconds.value*1000)}
function setPollingInterval(value:number){if(!pollingIntervalOptions.includes(value as PollingIntervalSeconds))return;pollingIntervalSeconds.value=value as PollingIntervalSeconds;localStorage.setItem(POLLING_INTERVAL_KEY,String(value));restartPolling();void pollCrawlerProgress()}
async function refresh(){const [dashboardData,siteData]=await Promise.all([crawlerApi.dashboard(),crawlerApi.sites(),loadBooks(),loadTasks(),loadFailedTasks(),loadDiscoveredBooks(),loadTaskQueueSettings()]);dashboard.value=dashboardData;sites.value=siteData;await loadDiscoveryPages()}
async function loadDiscoveryPages(){const pages=await crawlerApi.discoveryPages();discoveryPagesBySite.value=pages.reduce<Record<number,CrawlerDiscoveryPage[]>>((groups,page)=>{(groups[page.siteId]??=[]).push(page);return groups},{})}
async function pollCrawlerProgress(){
  if(progressPolling)return
  const hasActiveTask=[...tasks.value,...(dashboard.value?.recentTasks||[])].some(task=>['RUNNING','WAITING'].includes(task.status))
  if(!hasActiveTask&&!bookDrawer.value&&!taskDrawer.value)return
  progressPolling=true
  try{
    const requests:Promise<unknown>[]=[]
    if(hasActiveTask){
      requests.push(crawlerApi.dashboard().then(data=>{dashboard.value=data}))
      requests.push(loadTasks({silent:true}))
      requests.push(loadTaskQueueSettings())
      if(queuedTasksDialog.value)requests.push(loadQueuedTasks({silent:true}))
      if(activeTab.value==='sites')requests.push(crawlerApi.sites().then(data=>{sites.value=data}))
      if(activeTab.value==='discovered')requests.push(loadDiscoveredBooks({silent:true,preserveSelection:true}))
      if(activeTab.value==='books')requests.push(loadBooks({silent:true}))
      if(activeTab.value==='failed')requests.push(loadFailedTasks({silent:true}))
    }
    if(bookDrawer.value)requests.push(syncOpenBookProgress({silent:true}))
    if(taskDrawer.value&&selectedTask.value)requests.push(syncOpenTask({silent:true}))
    await Promise.all(requests)
  }catch{
    // 后台轮询失败不打断当前页面交互，下一轮会自动重试。
  }finally{progressPolling=false}
}
async function syncOpenBookProgress(options:LoadOptions={}){
  const bookId=selectedBook.value?.id
  if(!bookDrawer.value||!bookId)return
  const requestSequence=++chapterRequestSequence
  if(!options.silent)chapterLoading.value=true
  try{
    const [latestBook,latestChapters,latestLogs,latestFocus]=await Promise.all([crawlerApi.book(bookId),crawlerApi.chapters(bookId,{page:chapterPage.value-1,size:chapterPageSize.value,sort:chapterSortApiValue(chapterSort.value)}),crawlerApi.logs(bookId),followCurrentChapter.value?crawlerApi.currentChapter(bookId,chapterPageSize.value):Promise.resolve(undefined)])
    if(!bookDrawer.value||selectedBook.value?.id!==bookId||requestSequence!==chapterRequestSequence)return
    currentCrawlingChapter.value=followCurrentChapter.value?latestFocus?.chapter:undefined
    const focusedPage=latestFocus?latestFocus.page+1:undefined
    if(followCurrentChapter.value&&focusedPage&&focusedPage!==chapterPage.value){chapterPage.value=focusedPage;return await syncOpenBookProgress(options)}
    const lastPage=Math.max(1,latestChapters.totalPages)
    if(chapterPage.value>lastPage){chapterPage.value=lastPage;return await syncOpenBookProgress(options)}
    selectedBook.value=latestBook
    chapters.value=latestChapters.content
    chapterTotal.value=latestChapters.totalElements
    crawlerLogs.value=latestLogs
    books.value=books.value.map(book=>book.id===bookId?latestBook:book)
    if(followCurrentChapter.value&&currentCrawlingChapter.value)await scrollToCurrentCrawlingChapter()
  }finally{
    if(!options.silent&&requestSequence===chapterRequestSequence)chapterLoading.value=false
  }
}
async function syncOpenTask(options:LoadOptions={}){
  const taskId=selectedTask.value?.id
  if(!taskDrawer.value||!taskId)return
  if(!options.silent)taskDetailLoading.value=true
  try{
    const latest=await crawlerApi.task(taskId)
    if(!taskDrawer.value||selectedTask.value?.id!==taskId)return
    selectedTask.value=latest
    tasks.value=tasks.value.map(task=>task.id===taskId?latest:task)
    failedTasks.value=failedTasks.value.map(task=>task.id===taskId?latest:task)
    if(dashboard.value)dashboard.value.recentTasks=dashboard.value.recentTasks.map(task=>task.id===taskId?latest:task)
  }finally{if(!options.silent)taskDetailLoading.value=false}
}
async function loadTaskQueueSettings(){taskQueueSettings.value=await crawlerApi.taskQueueSettings();queueLimit.value=taskQueueSettings.value.maxConcurrentTasks}
function openQueueSettings(){if(taskQueueSettings.value)queueLimit.value=taskQueueSettings.value.maxConcurrentTasks;queueSettingsDialog.value=true}
async function saveQueueSettings(){savingQueueSettings.value=true;try{taskQueueSettings.value=await crawlerApi.updateTaskQueueSettings(queueLimit.value);queueLimit.value=taskQueueSettings.value.maxConcurrentTasks;queueSettingsDialog.value=false;message.success(`最多同时运行 ${queueLimit.value} 个采集任务`);await loadTasks({silent:true})}finally{savingQueueSettings.value=false}}
async function openQueuedTasks(){queuedTasksDialog.value=true;await loadQueuedTasks()}
async function loadQueuedTasks(options:LoadOptions={}){if(!options.silent)queuedTasksLoading.value=true;try{queuedTasks.value=await crawlerApi.queuedTasks()}finally{if(!options.silent)queuedTasksLoading.value=false}}
async function openQueuedTask(task:CrawlerTask){queuedTasksDialog.value=false;await openTask(task)}
function chapterSortApiValue(value:ChapterSort){return value==='indexDesc'?'INDEX_DESC':value==='createdDesc'?'CREATED_DESC':'INDEX_ASC'}
async function loadChapterPage(){await syncOpenBookProgress()}
async function handleChapterSizeChange(size:number){preferencesStore.setCrawlerChapterPageSize(size);chapterPage.value=1;await syncOpenBookProgress()}
async function changeChapterSort(value:ChapterSort){if(followCurrentChapter.value||chapterSort.value===value)return;chapterSort.value=value;chapterPage.value=1;await syncOpenBookProgress()}
async function setFollowCurrentChapter(enabled:boolean){preferencesStore.setCrawlerFollowCurrentChapter(enabled);currentCrawlingChapter.value=undefined;if(enabled){chapterSort.value='indexAsc';chapterPage.value=1}await syncOpenBookProgress()}
function chapterRowClassName({row}:{row:CrawlerChapter}){return row.id===currentCrawlingChapter.value?.id?'current-crawling-row':''}
async function scrollToCurrentCrawlingChapter(){await nextTick();document.querySelector<HTMLElement>('.book-detail-drawer .current-crawling-row')?.scrollIntoView({block:'center',behavior:'smooth'})}
async function loadBooks(options:LoadOptions={}){if(!options.silent)bookLoading.value=true;try{const result=await crawlerApi.books({page:bookPage.value-1,size:bookPageSize.value,keyword:bookKeyword.value.trim()||undefined,siteId:bookSiteId.value,crawlStatus:bookCrawlStatus.value||undefined,importStatus:bookImportStatus.value||undefined,sort:bookSort.value});const lastPage=Math.max(1,Math.ceil(result.totalElements/bookPageSize.value));if(bookPage.value>lastPage){bookPage.value=lastPage;return await loadBooks(options)}books.value=result.content;bookTotal.value=result.totalElements}finally{if(!options.silent)bookLoading.value=false}}
async function handleBookSizeChange(){bookPage.value=1;await loadBooks()}
async function applyBookFilters(){bookPage.value=1;await loadBooks()}
async function resetBookFilters(){bookKeyword.value='';bookSiteId.value=undefined;bookCrawlStatus.value='';bookImportStatus.value='';bookSort.value='CREATED_DESC';bookPage.value=1;await loadBooks()}
async function loadTasks(options:LoadOptions={}){if(!options.silent)taskLoading.value=true;try{const result=await crawlerApi.tasks({page:taskPage.value-1,size:taskPageSize.value,status:taskStatusFilter.value||undefined});const lastPage=Math.max(1,Math.ceil(result.totalElements/taskPageSize.value));if(taskPage.value>lastPage){taskPage.value=lastPage;return await loadTasks(options)}tasks.value=result.content;taskTotal.value=result.totalElements}finally{if(!options.silent)taskLoading.value=false}}
async function handleTaskSizeChange(){taskPage.value=1;await loadTasks()}
async function handleTaskStatusFilter(){taskPage.value=1;await loadTasks()}
async function showRunningTasks(){taskStatusFilter.value='RUNNING';await handleTaskStatusFilter()}
async function clearTaskStatusFilter(){taskStatusFilter.value='';await handleTaskStatusFilter()}
async function openScanResults(task:CrawlerTask){scanResultsTask.value=task;scanResults.value=[];scanResultsPage.value=1;scanResultsTotal.value=0;scanResultsDialog.value=true;await loadScanResults()}
async function loadScanResults(){if(!scanResultsTask.value)return;scanResultsLoading.value=true;try{const result=await crawlerApi.scanResults(scanResultsTask.value.id,scanResultsPage.value-1,scanResultsPageSize.value);scanResults.value=result.content;scanResultsTotal.value=result.totalElements}finally{scanResultsLoading.value=false}}
async function handleScanResultSizeChange(){scanResultsPage.value=1;await loadScanResults()}
async function loadFailedTasks(options:LoadOptions={}){if(!options.silent)failedTaskLoading.value=true;try{const result=await crawlerApi.tasks({page:failedTaskPage.value-1,size:failedTaskPageSize.value,failedOnly:true});const lastPage=Math.max(1,Math.ceil(result.totalElements/failedTaskPageSize.value));if(failedTaskPage.value>lastPage){failedTaskPage.value=lastPage;return await loadFailedTasks(options)}failedTasks.value=result.content;failedTaskTotal.value=result.totalElements}finally{if(!options.silent)failedTaskLoading.value=false}}
async function handleFailedTaskSizeChange(){failedTaskPage.value=1;await loadFailedTasks()}
async function loadDiscoveredBooks(options:LoadOptions={}){if(!options.silent)discoveryLoading.value=true;if(!options.preserveSelection)selectedDiscoveries.value=[];try{const result=await crawlerApi.discoveredBooks({page:discoveryPage.value-1,size:discoveryPageSize.value,keyword:discoveryKeyword.value.trim()||undefined,siteId:discoverySiteId.value,sort:discoverySort.value});const lastPage=Math.max(1,Math.ceil(result.totalElements/discoveryPageSize.value));if(discoveryPage.value>lastPage){discoveryPage.value=lastPage;return await loadDiscoveredBooks(options)}discoveredBooks.value=result.content;discoveredTotal.value=result.totalElements}finally{if(!options.silent)discoveryLoading.value=false}}
function setDiscoveryViewMode(mode:DiscoveryViewMode){if(discoveryViewMode.value===mode)return;preferencesStore.setCrawlerDiscoveryViewMode(mode);selectedDiscoveries.value=[]}
function handleDiscoveryViewKey(event:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();setDiscoveryViewMode(event.key==='ArrowLeft'||event.key==='Home'?'table':'card');requestAnimationFrame(()=>document.querySelector<HTMLButtonElement>(`.discovery-view-switch button:nth-of-type(${discoveryViewMode.value==='table'?1:2})`)?.focus())}
function setBookViewMode(mode:DiscoveryViewMode){if(bookViewMode.value===mode)return;preferencesStore.setCrawlerBookViewMode(mode)}
function handleBookViewKey(event:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();setBookViewMode(event.key==='ArrowLeft'||event.key==='Home'?'table':'card');requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.crawler-book-view-switch button')[bookViewMode.value==='table'?0:1]?.focus())}
function isDiscoverySelected(book:CrawlerBook){return selectedDiscoveryIds.value.has(book.id)}
function toggleDiscoverySelection(book:CrawlerBook,selected:boolean){selectedDiscoveries.value=selected?[...selectedDiscoveries.value.filter(item=>item.id!==book.id),book]:selectedDiscoveries.value.filter(item=>item.id!==book.id)}
function toggleCurrentDiscoveryPage(selected:boolean){selectedDiscoveries.value=selected?[...discoveredBooks.value]:[]}
function hideBrokenCover(event:Event){(event.target as HTMLImageElement).style.display='none'}
async function handleDiscoverySizeChange(){discoveryPage.value=1;await loadDiscoveredBooks()}
async function applyDiscoveryFilters(){discoveryPage.value=1;await loadDiscoveredBooks()}
async function resetDiscoveryFilters(){discoveryKeyword.value='';discoverySiteId.value=undefined;discoverySort.value='DISCOVER_TIME_DESC';discoveryPage.value=1;await loadDiscoveredBooks()}
function openSite(site?:CrawlerSite){
  editingSite.value=site
  activeSiteTab.value='basic'
  const defaults=emptySite()
  Object.assign(siteForm,defaults)
  if(site){
    (Object.keys(defaults) as (keyof CrawlerSitePayload)[]).forEach(key=>{
      const value=site[key]??defaults[key]
      ;(siteForm as any)[key]=key==='proxies'
        ? (value as CrawlerSitePayload['proxies']).map(proxy=>({...proxy}))
        : key==='contentFailureMarkers' ? [...(value as string[])] : value
    })
  }
  siteDialog.value=true
}
function openCrawl(id:number){crawlForm.siteId=id;crawlDialog.value=true}
function openDiscoveryPages(site:CrawlerSite){discoveryManagerSite.value=site;discoveryManagerDialog.value=true}
function openDiscoveryPage(site:CrawlerSite,page?:CrawlerDiscoveryPage){discoveryPageSite.value=site;editingDiscoveryPage.value=page;Object.assign(discoveryPageForm,page?{pageName:page.pageName,pageUrl:page.pageUrl,autoScanEnabled:page.autoScanEnabled,scanIntervalMinutes:page.scanIntervalMinutes,maxPages:page.maxPages}:{pageName:'',pageUrl:site.homeUrl||site.baseUrl,autoScanEnabled:false,scanIntervalMinutes:360,maxPages:50});discoveryPageDialog.value=true}
async function saveDiscoveryPage(){if(!discoveryPageSite.value||!discoveryPageForm.pageName.trim()||!discoveryPageForm.pageUrl.trim())return message.warning('请填写发现页名称和起始页面 URL');saving.value=true;try{editingDiscoveryPage.value?await crawlerApi.updateDiscoveryPage(editingDiscoveryPage.value.id,discoveryPageForm):await crawlerApi.createDiscoveryPage(discoveryPageSite.value.id,discoveryPageForm);discoveryPageDialog.value=false;message.success(editingDiscoveryPage.value?'发现页已更新':'发现页已添加');await loadDiscoveryPages()}finally{saving.value=false}}
async function toggleDiscoveryPageAuto(page:CrawlerDiscoveryPage,enabled:boolean){try{const updated=await crawlerApi.updateDiscoveryPage(page.id,{pageName:page.pageName,pageUrl:page.pageUrl,autoScanEnabled:enabled,scanIntervalMinutes:page.scanIntervalMinutes,maxPages:page.maxPages});discoveryPagesBySite.value[page.siteId]=discoveryPagesBySite.value[page.siteId].map(item=>item.id===page.id?updated:item);message.success(enabled?'已开启自动扫描':'已关闭自动扫描')}catch{await loadDiscoveryPages()}}
async function scanDiscoveryPage(page:CrawlerDiscoveryPage){await crawlerApi.scanDiscoveryPage(page.id);discoveryManagerDialog.value=false;activeTab.value='tasks';message.success(`“${page.pageName}”扫描任务已创建`);await refresh()}
async function removeDiscoveryPage(page:CrawlerDiscoveryPage){if(!await confirm(`确定删除发现页“${page.pageName}”吗？历史扫描任务不会被删除。`))return;await crawlerApi.deleteDiscoveryPage(page.id);message.success('发现页已删除');await loadDiscoveryPages()}
function siteTabForField(field:string):SiteEditorTab{
  if(field.startsWith('contentFailureMarkers'))return'validation'
  if(field.startsWith('proxies'))return'proxy'
  if(['requestIntervalMillis','randomDelayMillis','maxConcurrency'].includes(field))return'request'
  if(['enabled','maxDiscoveryPages'].includes(field))return'automation'
  return'basic'
}
async function saveSite(){
  try{await siteFormRef.value?.validate()}
  catch(error){
    const firstField=Object.keys((error as Record<string,unknown>)||{})[0]||''
    activeSiteTab.value=siteTabForField(firstField)
    await nextTick()
    if(firstField)siteFormRef.value?.scrollToField(firstField)
    message.warning('请先补全当前配置页中的必填项')
    return
  }
  saving.value=true
  try{editingSite.value?await crawlerApi.updateSite(editingSite.value.id,siteForm):await crawlerApi.createSite(siteForm);message.success(editingSite.value?'网站信息已保存':'网站已创建，请在卡片的规则管理中添加规则');siteDialog.value=false;await refresh()}finally{saving.value=false}
}
function addProxy(){siteForm.proxies.push({name:`代理 ${siteForm.proxies.length+1}`,url:'',enabled:false})}
function removeProxy(index:number){siteForm.proxies.splice(index,1)}
function addContentFailureMarker(){if(siteForm.contentFailureMarkers.length<50)siteForm.contentFailureMarkers.push('')}
function removeContentFailureMarker(index:number){siteForm.contentFailureMarkers.splice(index,1)}
function setActiveProxy(index:number,enabled:boolean){siteForm.proxies.forEach((proxy,current)=>{proxy.enabled=enabled&&current===index})}
async function removeSite(site:CrawlerSite){if(await confirm(`确定删除采集网站“${site.siteName}”吗？`)){await crawlerApi.deleteSite(site.id);message.success('采集网站已删除');await refresh()}}
async function startCrawl(){if(!crawlForm.siteId||!crawlForm.url)return message.warning('请选择网站并填写书籍 URL');saving.value=true;try{await crawlerApi.crawlUrl(crawlForm.siteId,crawlForm.url);crawlDialog.value=false;activeTab.value='tasks';message.success('采集任务已创建');await refresh()}finally{saving.value=false}}
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
function openImportDialog(){importMode.value='text';importJsonText.value='';importFileName.value='';ruleImportDialog.value=true}
async function handleImportFile(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;importFileName.value=file.name;importJsonText.value=await file.text()}
async function submitRuleImport(){if(!ruleSite.value||!importJsonText.value.trim())return;let data:CrawlerRuleExport;try{data=JSON.parse(importJsonText.value) as CrawlerRuleExport}catch{return message.error('JSON 字符串格式不正确，请检查后重试')}saving.value=true;try{await crawlerApi.importRule(ruleSite.value.id,{...data,enabled:false});message.success(`规则 v${data.version} 已导入（默认禁用）`);ruleImportDialog.value=false;await reloadRules()}finally{saving.value=false}}
async function crawlDiscovered(book:CrawlerBook){await crawlerApi.batchCrawl([book.id]);activeTab.value='tasks';message.success('采集任务已创建');await refresh()}
async function batchCrawl(){await crawlerApi.batchCrawl(selectedDiscoveries.value.map(b=>b.id));activeTab.value='tasks';message.success(`已创建 ${selectedDiscoveries.value.length} 个采集任务`);await refresh()}
async function batchDiscovery(status:'IGNORED'|'BLACKLISTED',ids=selectedDiscoveries.value.map(b=>b.id)){if(!ids.length)return;await crawlerApi.setDiscoveryStatus(ids,status);message.success(status==='IGNORED'?'已忽略所选书籍':'已加入黑名单，后续扫描不会重新收录');await refresh()}
async function openBook(book:CrawlerBook){selectedBook.value=book;chapters.value=[];chapterTotal.value=book.chapterCount;chapterPage.value=1;if(followCurrentChapter.value)chapterSort.value='indexAsc';currentCrawlingChapter.value=undefined;crawlerLogs.value=[];bookDetailTab.value='chapters';bookDrawer.value=true;await syncOpenBookProgress()}
function startTrial(book:CrawlerBook){if(!book.crawledChapterCount)return;void router.push({name:'CrawlerTrialReader',params:{id:book.id}})}
async function continueCrawl(book:CrawlerBook){await crawlerApi.continueBook(book.id);message.success('续采任务已创建');await refresh()}
async function retryFailures(book:CrawlerBook){await crawlerApi.retryFailures(book.id);message.success('失败章节已进入重试队列');await refresh()}
async function checkUpdates(book:CrawlerBook){await crawlerApi.checkUpdates(book.id);message.success('增量更新检查已创建');await refresh()}
function openStatusEditor(book:CrawlerBook){statusBook.value=book;manualStatus.value=manualStatusOptions.some(item=>item.value===book.crawlStatus)?book.crawlStatus:'COMPLETED';statusDialog.value=true}
async function saveBookStatus(){if(!statusBook.value)return;savingStatus.value=true;try{const updated=await crawlerApi.setBookStatus(statusBook.value.id,manualStatus.value,false);books.value=books.value.map(book=>book.id===updated.id?updated:book);if(selectedBook.value?.id===updated.id)selectedBook.value=updated;statusDialog.value=false;message.success(`采集状态已改为“${statusLabel(updated.crawlStatus)}”，后续任务仅支持人工触发`);await refresh()}finally{savingStatus.value=false}}
async function toggleLibrarySync(book:CrawlerBook,enabled:boolean){const updated=await crawlerApi.setLibrarySync(book.id,enabled);books.value=books.value.map(item=>item.id===updated.id?updated:item);if(selectedBook.value?.id===updated.id)selectedBook.value=updated;message.success(enabled?'已开启追更后自动同步书库':'已关闭自动同步，更新只保留在采集中心')}
async function generate(book:CrawlerBook){if(book.crawlStatus!=='COMPLETED'&&!await confirm(`“${book.bookName}”当前为${statusLabel(book.crawlStatus)}，生成文件将只包含已有正文，是否继续？`))return;await crawlerApi.generate(book.id,['TXT','EPUB']);message.success('已有正文已生成 TXT 与 EPUB，并保留在采集中心')}
function importBook(book:CrawlerBook){importTarget.value=book;importFormats.value=['EPUB','TXT'];importDialog.value=true}
async function submitImport(){if(!importTarget.value||!importFormats.value.length)return;const book=importTarget.value,syncing=book.importStatus==='IMPORTED';importing.value=true;try{const result=await crawlerApi.importBook(book.id,importFormats.value);importDialog.value=false;message.success(syncing?`所选格式已同步到书库（书籍 ID：${result.bookId}）`:`${importFormats.value.join(' + ')} 已加入书库（书籍 ID：${result.bookId}）`);await refresh()}finally{importing.value=false}}
async function openTask(task:CrawlerTask){selectedTask.value=task;taskDrawer.value=true;await syncOpenTask()}
async function openTaskBook(task:CrawlerTask){if(!task.bookId)return;const book=await crawlerApi.book(task.bookId);taskDrawer.value=false;await openBook(book)}
async function runTaskCommand(task:CrawlerTask,command:'pause'|'resume'|'cancel'){const updated=await crawlerApi.taskCommand(task.id,command);if(selectedTask.value?.id===updated.id)selectedTask.value=updated;await refresh();if(taskDrawer.value&&selectedTask.value?.id===updated.id)await syncOpenTask({silent:true})}
function openTaskEditor(task:CrawlerTask){editingTask.value=task;taskPriority.value=(task.priority as 'LOW'|'NORMAL'|'HIGH')||'NORMAL';taskEditDialog.value=true}
async function saveTask(){if(!editingTask.value)return;savingTask.value=true;try{const updated=await crawlerApi.updateTask(editingTask.value.id,taskPriority.value);if(selectedTask.value?.id===updated.id)selectedTask.value=updated;taskEditDialog.value=false;message.success('任务优先级已更新');await refresh()}finally{savingTask.value=false}}
async function removeTask(task:CrawlerTask){if(!await confirm(`确定删除“${task.bookName||task.discoveryPageName||taskTypeLabel(task.type)}”的任务记录吗？关联书籍和章节不会被删除。`))return;await crawlerApi.deleteTask(task.id);if(selectedTask.value?.id===task.id){taskDrawer.value=false;selectedTask.value=undefined}message.success('任务记录已删除');await refresh()}
async function openChapter(chapter:CrawlerChapter){if(!selectedBook.value)return;chapterDetail.value=await crawlerApi.chapter(selectedBook.value.id,chapter.id);chapterDialog.value=true}
function progress(book:CrawlerBook){return book.chapterCount?Math.round(book.crawledChapterCount/book.chapterCount*100):0}
function isBookRunning(book:CrawlerBook){return ['CRAWLING_METADATA','CRAWLING_CHAPTER_LIST','CRAWLING_CONTENT','UPDATING'].includes(book.crawlStatus)}
function statusLabel(status:string){return ({WAITING:'等待中',RUNNING:'运行中',PAUSED:'已暂停',SUCCESS:'成功',PARTIAL_SUCCESS:'部分成功',FAILED:'失败',CANCELLED:'已取消',DISCOVERED:'已发现',CRAWLING_METADATA:'解析元信息',CRAWLING_CHAPTER_LIST:'解析目录',CRAWLING_CONTENT:'采集正文',COMPLETED:'已完成',NOT_CRAWLED:'未采集',CRAWLING:'采集中',CONTENT_SUSPECTED:'内容异常'} as Record<string,string>)[status]||status}
function statusType(status:string):''|'success'|'warning'|'info'|'danger'{if(['RUNNING','SUCCESS','COMPLETED'].includes(status))return'success';if(['FAILED','CONTENT_SUSPECTED'].includes(status))return'danger';if(['PARTIAL_SUCCESS','PAUSED'].includes(status))return'warning';return'info'}
function priorityLabel(priority:string){return({LOW:'低',NORMAL:'普通',HIGH:'高'} as Record<string,string>)[priority]||priority}
function priorityType(priority:string):''|'success'|'warning'|'info'|'danger'{return priority==='HIGH'?'warning':priority==='LOW'?'info':''}
function scanTaskPercentage(task:CrawlerTask){return Math.max(0,Math.min(100,task.progressPercent??(['SUCCESS','PARTIAL_SUCCESS'].includes(task.status)?100:0)))}
function taskProgress(task:CrawlerTask){return scanTaskPercentage(task)}
function taskProgressDescription(task:CrawlerTask){if(task.type==='SITE_SCAN')return scanTaskProgressText(task);if(task.currentChapter)return task.currentChapter;if(task.status==='WAITING')return '任务正在队列中等待执行';if(task.status==='SUCCESS')return `处理完成 · 成功 ${task.successCount} 项`;if(task.status==='PARTIAL_SUCCESS')return `处理完成 · 成功 ${task.successCount} 项，失败 ${task.failedCount} 项`;if(task.status==='FAILED')return task.errorMessage||'任务执行失败';if(task.status==='PAUSED')return '任务已暂停，可从下方继续执行';if(task.status==='CANCELLED')return '任务已取消';return `已处理 ${task.successCount+task.failedCount} / ${task.totalCount} 项`}
function scanTaskProgressText(task:CrawlerTask){if(task.status==='WAITING')return `等待扫描 · 分页上限 ${task.scanMaxPages||'—'} 页`;if(['SUCCESS','PARTIAL_SUCCESS'].includes(task.status))return task.scannedPageCount>0?`扫描完成 · 共扫描 ${task.scannedPageCount} 页`:'扫描完成';return task.currentChapter||`已扫描 ${task.scannedPageCount||0} / ${task.scanMaxPages||'—'} 页`}
function scanResultLabel(status:CrawlerScanResult['resultStatus']){return({NEW:'新增',DUPLICATE:'重复',BLACKLISTED:'黑名单',FAILED:'失败'} as const)[status]}
function scanResultType(status:CrawlerScanResult['resultStatus']):'success'|'warning'|'info'|'danger'{return status==='NEW'?'success':status==='DUPLICATE'?'warning':status==='BLACKLISTED'?'info':'danger'}
function taskTypeLabel(type:string){return({SITE_SCAN:'发现页扫描',BOOK_METADATA:'书籍信息',BOOK_CHAPTER_LIST:'章节目录',BOOK_CONTENT:'章节正文',BOOK_UPDATE_CHECK:'更新检查',BOOK_FULL_CRAWL:'全本采集',BOOK_EXPORT:'文件生成',BOOK_IMPORT:'加入书库'} as Record<string,string>)[type]||type}
function formatTime(value?:string){return value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'—'}
function handleTabKey(e:KeyboardEvent){const keys=tabs.value.map(t=>t.key);let i=activeIndex.value;if(['ArrowRight','ArrowDown'].includes(e.key))i=(i+1)%keys.length;else if(['ArrowLeft','ArrowUp'].includes(e.key))i=(i-1+keys.length)%keys.length;else if(e.key==='Home')i=0;else if(e.key==='End')i=keys.length-1;else return;e.preventDefault();activeTab.value=keys[i];requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.segment')[i]?.focus())}
function handleSiteEditorTabKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();let index=activeSiteTabIndex.value;if(e.key==='ArrowRight')index=(index+1)%siteEditorTabs.length;else if(e.key==='ArrowLeft')index=(index-1+siteEditorTabs.length)%siteEditorTabs.length;else index=e.key==='Home'?0:siteEditorTabs.length-1;activeSiteTab.value=siteEditorTabs[index].key;requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.site-editor-tabs button')[index]?.focus())}
function handleImportModeKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();importMode.value=e.key==='ArrowLeft'||e.key==='Home'?'text':'file';requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.import-mode button')[importMode.value==='text'?0:1]?.focus())}
function handleChapterSortKey(e:KeyboardEvent){if(followCurrentChapter.value||!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();let index=chapterSortIndex.value;if(e.key==='ArrowRight')index=(index+1)%chapterSortOptions.length;else if(e.key==='ArrowLeft')index=(index-1+chapterSortOptions.length)%chapterSortOptions.length;else index=e.key==='Home'?0:chapterSortOptions.length-1;void changeChapterSort(chapterSortOptions[index].value);requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.chapter-sort button')[index]?.focus())}
function handleBookDetailTabKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();let index=bookDetailTabIndex.value;if(e.key==='ArrowRight')index=(index+1)%bookDetailTabs.length;else if(e.key==='ArrowLeft')index=(index-1+bookDetailTabs.length)%bookDetailTabs.length;else index=e.key==='Home'?0:bookDetailTabs.length-1;bookDetailTab.value=bookDetailTabs[index].value;requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.book-detail-tabs button')[index]?.focus())}
function handlePriorityKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();let index=taskPriorityIndex.value;if(e.key==='ArrowRight')index=(index+1)%taskPriorityOptions.length;else if(e.key==='ArrowLeft')index=(index-1+taskPriorityOptions.length)%taskPriorityOptions.length;else index=e.key==='Home'?0:taskPriorityOptions.length-1;taskPriority.value=taskPriorityOptions[index].value;requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.priority-segment button')[index]?.focus())}
</script>

<style scoped>
.crawler-page{display:grid;gap:22px;padding-bottom:50px}.hero{position:relative;display:flex;align-items:flex-end;justify-content:space-between;min-height:180px;padding:32px;overflow:hidden;border:1px solid var(--border-color);border-radius:28px;background:linear-gradient(125deg,var(--surface-elevated),var(--surface-card));box-shadow:var(--shadow-lg)}.hero:after{position:absolute;right:8%;bottom:-90px;width:280px;height:280px;border:42px solid var(--primary-alpha-10);border-radius:50%;content:''}.hero>*{position:relative;z-index:1}.eyebrow{margin-bottom:6px;color:var(--primary);font-size:11px;font-weight:800;letter-spacing:.16em}.hero h1{font-family:'Iowan Old Style','Songti SC',serif;font-size:42px;letter-spacing:-.04em}.subtitle{max-width:600px;margin-top:10px;color:var(--text-secondary)}.segmented-wrap{position:relative;display:grid;grid-template-columns:repeat(5,minmax(120px,1fr));overflow-x:auto;padding:5px;border:1px solid var(--border-color);border-radius:16px;background:var(--surface-card);isolation:isolate}.segment-indicator{position:absolute;top:5px;bottom:5px;left:5px;z-index:-1;width:20%;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .28s cubic-bezier(.2,.8,.2,1)}.segment{display:flex;align-items:center;justify-content:center;gap:7px;min-width:120px;padding:11px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.segment.active{color:var(--primary);font-weight:700}.segment:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:12px}.segment b{min-width:19px;padding:1px 5px;border-radius:99px;background:var(--primary-alpha-10);font-size:11px}.panel{min-height:430px;padding:26px;border:1px solid var(--border-color);border-radius:22px;background:var(--surface-card);box-shadow:var(--shadow-md)}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:34px}.metric-card{display:grid;grid-template-columns:auto 1fr;gap:13px;padding:20px;border:1px solid var(--border-color-light);border-radius:18px;background:linear-gradient(145deg,var(--surface-elevated),var(--surface-card))}.metric-icon{display:grid;width:42px;height:42px;place-items:center;border-radius:13px;background:var(--primary-alpha-10);color:var(--primary);font-size:20px}.metric-card strong{font-size:27px}.metric-card p,.metric-card small,.book-cell p,.task-name p{color:var(--text-secondary);font-size:12px}.metric-card small{grid-column:2}.section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.section-heading h2{font-family:'Iowan Old Style','Songti SC',serif;font-size:25px}.search{width:280px}.site-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16px}.site-card{padding:20px;border:1px solid var(--border-color-light);border-radius:18px;background:var(--surface-elevated)}.site-top{display:grid;grid-template-columns:auto 1fr auto;gap:12px;align-items:center}.site-mark,.mini-cover,.large-cover{display:grid;place-items:center;background:linear-gradient(145deg,var(--primary),var(--primary-light));color:white;font-family:'Songti SC',serif}.site-mark{width:44px;height:44px;border-radius:14px;font-size:22px}.site-top a{display:block;max-width:280px;overflow:hidden;color:var(--text-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.site-stats{display:flex;gap:22px;margin:20px 0;color:var(--text-secondary);font-size:12px}.site-stats b{color:var(--text-primary);font-size:17px}.automation{display:flex;gap:8px}.automation span{padding:4px 9px;border-radius:99px;background:var(--bg-page);color:var(--text-tertiary);font-size:11px}.automation span.on{background:var(--success-alpha-15);color:var(--success)}.site-card footer{display:flex;justify-content:flex-end;margin-top:16px;border-top:1px solid var(--border-color-light);padding-top:10px}.book-cell,.book-summary{display:flex;align-items:center;gap:12px}.mini-cover{width:38px;height:50px;border-radius:6px}.data-table{cursor:default}.task-name p{margin-top:3px}.site-form{display:grid;gap:16px;max-height:70vh;overflow:auto;padding-right:8px}.site-form-section{padding:18px;border:1px solid var(--border-color-light);border-radius:16px;background:var(--surface-elevated)}.site-form-section>header{display:grid;grid-template-columns:auto 1fr;gap:10px;align-items:center;margin-bottom:16px}.site-form-section>header>span{display:grid;width:34px;height:34px;place-items:center;border-radius:10px;background:var(--primary-alpha-10);color:var(--primary);font-size:11px;font-weight:800}.site-form-section>header h3{font-size:16px}.site-form-section>header p{margin-top:2px;color:var(--text-tertiary);font-size:12px}.site-form-section>.section-with-action{grid-template-columns:auto 1fr auto}.field-hint{display:block;margin-top:5px;color:var(--text-tertiary);font-size:11px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.switch-row{display:flex;flex-wrap:wrap;gap:20px;margin-bottom:4px}.proxy-list{display:grid;gap:10px}.proxy-row{display:grid;grid-template-columns:minmax(130px,.7fr) minmax(240px,1.5fr) auto;gap:12px;align-items:end;padding:12px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-card)}.proxy-row.active{border-color:var(--success);background:var(--success-alpha-15)}.proxy-row .el-form-item{margin-bottom:0}.proxy-state{display:flex;align-items:center;gap:8px;min-height:32px}.rule-block{margin:10px 0 18px;padding-top:18px;border-top:1px solid var(--border-color)}.rule-block p:last-child{margin-top:5px;color:var(--text-secondary);font-size:12px}.book-summary{padding:18px;margin-bottom:16px;border-radius:18px;background:var(--primary-alpha-10)}.book-summary>div:last-child{flex:1}.large-cover{width:82px;height:108px;border-radius:10px;font-size:32px}.drawer-actions{display:flex;gap:8px;margin-bottom:16px}.chapter-content{max-height:60vh;margin-top:14px;overflow:auto;padding:20px;border-radius:14px;background:var(--bg-page);color:var(--text-primary);font:15px/1.8 'Songti SC',serif;white-space:pre-wrap}.el-select{width:100%}
.segmented-wrap{grid-template-columns:repeat(6,minmax(120px,1fr))}.batch-actions{display:flex;flex-wrap:wrap;gap:8px}.source-link{margin:0 10px;color:var(--primary);font-size:14px;text-decoration:none}.source-link:hover{text-decoration:underline}
.crawler-page{width:100%;min-width:0;max-width:100%;overflow-x:clip;box-sizing:border-box}.crawler-page>*{min-width:0;max-width:100%;box-sizing:border-box}.panel{width:100%;min-width:0;max-width:100%;overflow:hidden;box-sizing:border-box}.metric-grid,.site-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.site-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.section-heading>*{min-width:0}.data-table,:deep(.el-table){width:100%!important;min-width:0;max-width:100%}.book-cell>div:last-child,.task-name{min-width:0}.book-cell strong,.book-cell p,.task-name strong,.task-name p{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.site-card footer{flex-wrap:wrap}.health-line{margin-top:12px;color:var(--text-tertiary);font-size:11px}.health-line.error{color:var(--danger)}.test-result{padding:18px;border:1px solid var(--success-alpha-15);border-radius:16px;background:var(--surface-elevated)}.test-result.failed{border-color:var(--danger)}.test-facts{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.test-facts span{display:grid;gap:4px}.test-facts small{color:var(--text-tertiary)}.test-result>p{margin:18px 0 8px}.test-result pre{max-height:260px;overflow:auto;padding:14px;border-radius:12px;background:var(--bg-page);font:14px/1.7 'Songti SC',serif;white-space:pre-wrap}.file-input{display:none}.rule-manager-bar{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-bottom:20px;padding:15px 18px;border-radius:14px;background:var(--primary-alpha-10);color:var(--text-secondary)}.rule-manager-bar>div{display:flex;flex-shrink:0;gap:8px}.rule-table{width:100%;border:1px solid var(--border-color-light);border-radius:14px;overflow:hidden}.rule-version-text{color:var(--primary);font-family:'Iowan Old Style','Songti SC',serif;font-size:17px}.rule-table code{padding:3px 6px;border-radius:6px;background:var(--bg-page);color:var(--text-secondary);font-size:12px}.rule-actions{display:flex;flex-wrap:wrap;align-items:center;gap:2px 4px;white-space:normal}.rule-actions .el-button{margin-left:0;padding:5px 7px}:deep(.rule-table .active-rule-row td.el-table__cell){background:var(--success-alpha-15)!important}:deep(.rule-table .active-rule-row:hover td.el-table__cell){background:var(--success-alpha-15)!important}.import-mode{position:relative;display:grid;grid-template-columns:repeat(2,1fr);max-width:360px;margin:0 auto 20px;padding:4px;border:1px solid var(--border-color);border-radius:14px;background:var(--bg-page);isolation:isolate}.import-mode-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:-1;width:calc(50% - 4px);border:1px solid var(--border-color-light);border-radius:10px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .25s cubic-bezier(.2,.8,.2,1)}.import-mode button{padding:9px 16px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.import-mode button.active{color:var(--primary);font-weight:700}.import-mode button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:10px}.import-pane{display:grid;gap:10px}.import-pane>p{color:var(--text-tertiary);font-size:12px}.file-pane{display:grid;place-items:stretch}.file-picker{display:grid;min-height:150px;place-items:center;padding:24px;border:1px dashed var(--primary);border-radius:16px;background:var(--primary-alpha-10);color:var(--text-secondary);cursor:pointer}.file-picker .el-icon{color:var(--primary);font-size:28px}.file-picker strong{color:var(--text-primary);font-size:15px}.file-picker span{font-size:12px}
@media(max-width:900px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.site-grid{grid-template-columns:minmax(0,1fr)}.proxy-row{grid-template-columns:1fr 1.5fr}.proxy-state{grid-column:1/-1;justify-content:flex-end}}@media(max-width:640px){.hero{align-items:flex-start;flex-direction:column;gap:22px;padding:24px}.hero h1{font-size:34px}.panel{padding:16px}.metric-grid,.form-grid,.proxy-row{grid-template-columns:minmax(0,1fr)}.proxy-state{grid-column:auto}.site-form-section{padding:14px}.site-form-section>.section-with-action{grid-template-columns:auto 1fr}.section-with-action>.el-button{grid-column:1/-1}.segmented-wrap{justify-content:start}.section-heading,.rule-manager-bar{align-items:flex-start;flex-direction:column;gap:12px}.rule-manager-bar>div{width:100%;flex-wrap:wrap}.rule-table{max-width:100%;overflow-x:auto}.search{width:100%}}@media(prefers-reduced-motion:reduce){.segment-indicator,.import-mode-indicator{transition:none}}
.drawer-actions{flex-wrap:wrap}.library-sync-control{display:flex;align-items:center;justify-content:space-between;gap:18px;margin:-4px 0 16px;padding:13px 16px;border:1px solid var(--border-color-light);border-radius:14px;background:var(--surface-elevated)}.library-sync-control p{margin-top:3px;color:var(--text-secondary);font-size:12px}.status-editor{display:flex;align-items:center;gap:6px;padding:3px;border:0;background:transparent;cursor:pointer}.status-editor small{color:var(--text-tertiary)}.status-editor:hover small,.status-editor:focus-visible small{color:var(--primary)}.status-editor:focus-visible{outline:2px solid var(--primary);outline-offset:2px;border-radius:8px}.status-dialog-content{display:grid;gap:18px}.status-book{display:flex;align-items:center;gap:12px;padding:14px;border:1px solid var(--border-color-light);border-radius:14px;background:var(--surface-elevated)}.status-book .mini-cover{flex:0 0 auto}.status-book p{margin-top:4px;color:var(--text-secondary);font-size:12px}
:deep(.book-detail-drawer .el-drawer__body){min-height:0;overflow:hidden}.book-drawer-content{display:flex;height:100%;min-height:0;flex-direction:column}.chapter-list-heading{display:flex;align-items:end;justify-content:space-between;gap:16px;margin:2px 0 12px}.chapter-list-heading h3{font-family:'Iowan Old Style','Songti SC',serif;font-size:20px}.chapter-sort{position:relative;display:grid;grid-template-columns:repeat(3,minmax(82px,1fr));padding:4px;border:1px solid var(--border-color);border-radius:13px;background:var(--bg-page);isolation:isolate}.chapter-sort-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/3);border:1px solid var(--border-color-light);border-radius:9px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .25s cubic-bezier(.2,.8,.2,1)}.chapter-sort button{position:relative;z-index:1;padding:7px 10px;border:0;background:transparent;color:var(--text-secondary);font-size:12px;cursor:pointer}.chapter-sort button.active{color:var(--primary);font-weight:700}.chapter-sort button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:9px}.chapters-table{flex:1;min-height:220px}.chapter-pagination{display:flex;flex:0 0 auto;align-items:center;justify-content:space-between;gap:12px;padding:12px 2px 0;color:var(--text-tertiary);font-size:12px}.chapter-pagination :deep(.el-pagination){min-width:0}@media(max-width:640px){.chapter-list-heading{align-items:stretch;flex-direction:column}.chapter-sort{width:100%;overflow-x:auto}.chapter-pagination{align-items:flex-start;flex-direction:column}.chapter-pagination :deep(.el-pagination){flex-wrap:wrap;justify-content:flex-start}}@media(prefers-reduced-motion:reduce){.chapter-sort-indicator{transition:none}}
.book-detail-tabs{position:relative;display:grid;grid-template-columns:repeat(2,1fr);flex:0 0 auto;margin-bottom:14px;padding:4px;border:1px solid var(--border-color);border-radius:14px;background:var(--bg-page);isolation:isolate}.book-detail-tab-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/2);border:1px solid var(--border-color-light);border-radius:10px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .25s cubic-bezier(.2,.8,.2,1)}.book-detail-tabs button{position:relative;z-index:1;display:flex;align-items:center;justify-content:center;gap:8px;padding:9px 14px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.book-detail-tabs button b{min-width:22px;padding:2px 7px;border-radius:99px;background:var(--primary-alpha-10);font-size:11px}.book-detail-tabs button.active{color:var(--primary);font-weight:700}.book-detail-tabs button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:10px}.drawer-detail-panel{flex:1;min-height:0}.chapter-progress-panel{display:flex;flex-direction:column}.realtime-log-panel{overflow:hidden;border:1px solid var(--border-color-light);border-radius:16px;background:var(--surface-elevated)}.realtime-log-heading{display:flex;align-items:center;justify-content:space-between;padding:16px 18px;border-bottom:1px solid var(--border-color-light)}.realtime-log-heading h3{font-family:'Iowan Old Style','Songti SC',serif;font-size:20px}.live-state{display:flex;align-items:center;gap:7px;color:var(--text-tertiary);font-size:11px}.live-state i{width:7px;height:7px;border-radius:50%;background:var(--success);box-shadow:0 0 0 5px var(--success-alpha-15);animation:live-pulse 1.8s ease-out infinite}.crawler-log-stream{height:calc(100% - 69px);overflow:auto;padding:4px 18px 18px}.crawler-log-item{display:grid;grid-template-columns:128px minmax(0,1fr);gap:14px;padding:14px 0;border-bottom:1px solid var(--border-color-light)}.crawler-log-item time{padding-top:2px;color:var(--text-tertiary);font:11px/1.5 ui-monospace,SFMono-Regular,Consolas,monospace}.crawler-log-item strong{display:block;color:var(--text-primary);font-size:13px}.crawler-log-item p{margin-top:5px;color:var(--text-secondary);font:12px/1.65 ui-monospace,SFMono-Regular,Consolas,monospace;overflow-wrap:anywhere}.realtime-log-panel>.el-empty{height:calc(100% - 69px)}@keyframes live-pulse{70%,100%{box-shadow:0 0 0 9px transparent}}@media(max-width:640px){.crawler-log-item{grid-template-columns:minmax(0,1fr);gap:5px}.crawler-log-item time{padding-top:0}}@media(prefers-reduced-motion:reduce){.book-detail-tab-indicator{transition:none}.live-state i{animation:none}}
.task-edit-content{display:grid;gap:20px}.task-edit-summary{display:flex;align-items:center;gap:12px;padding:15px;border:1px solid var(--border-color-light);border-radius:14px;background:var(--surface-elevated)}.task-edit-summary>span{display:grid;min-width:74px;height:42px;place-items:center;padding:0 10px;border-radius:11px;background:var(--primary-alpha-10);color:var(--primary);font-size:12px;font-weight:700}.task-edit-summary p{margin-top:4px;color:var(--text-secondary);font-size:12px}.field-label{margin-bottom:8px;color:var(--text-secondary);font-size:13px;font-weight:700}.priority-segment{position:relative;display:grid;grid-template-columns:repeat(3,1fr);padding:4px;border:1px solid var(--border-color);border-radius:14px;background:var(--bg-page);isolation:isolate}.priority-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/3);border:1px solid var(--border-color-light);border-radius:10px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .25s cubic-bezier(.2,.8,.2,1)}.priority-segment button{position:relative;z-index:1;padding:10px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.priority-segment button.active{color:var(--primary);font-weight:700}.priority-segment button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:10px}@media(prefers-reduced-motion:reduce){.priority-indicator{transition:none}}
.discovery-heading-actions{display:flex;align-items:center;gap:12px}.discovery-view-switch{position:relative;display:grid;grid-template-columns:repeat(2,1fr);min-width:172px;padding:4px;border:1px solid var(--border-color);border-radius:13px;background:var(--bg-page);isolation:isolate}.discovery-view-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/2);border:1px solid var(--border-color-light);border-radius:9px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .24s cubic-bezier(.2,.8,.2,1)}.discovery-view-switch button{position:relative;z-index:1;padding:7px 10px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer;font-size:12px}.discovery-view-switch button.active{color:var(--primary);font-weight:700}.discovery-view-switch button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:9px}.discovery-card-panel{min-height:180px}.discovery-card-selection{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;padding:10px 14px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated);color:var(--text-tertiary);font-size:12px}.discovery-card-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.discovery-card{min-width:0;overflow:hidden;border:1px solid var(--border-color-light);border-radius:18px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:border-color .18s ease,transform .18s ease,box-shadow .18s ease}.discovery-card:hover{transform:translateY(-2px);box-shadow:var(--shadow-md)}.discovery-card.selected{border-color:var(--primary);box-shadow:0 0 0 2px var(--primary-alpha-10),var(--shadow-md)}.discovery-card-cover{position:relative;display:grid;height:190px;place-items:center;overflow:hidden;background:linear-gradient(145deg,var(--primary-alpha-10),var(--surface-hover));color:var(--primary);font:48px 'Songti SC',serif}.discovery-card-cover img{position:absolute;width:100%;height:100%;object-fit:cover}.discovery-card-check{position:absolute;top:12px;right:12px;z-index:2;display:grid;width:30px;height:30px;place-items:center;border-radius:9px;background:color-mix(in srgb,var(--surface-card) 88%,transparent);backdrop-filter:blur(10px)}.discovery-card-body{display:grid;gap:14px;padding:16px}.discovery-card-title{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.discovery-card-title>div{min-width:0}.discovery-card-title strong,.discovery-card-title p{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.discovery-card-title strong{font-size:16px}.discovery-card-title p{margin-top:4px;color:var(--text-secondary);font-size:12px}.discovery-card-body dl{display:grid;gap:8px;margin:0}.discovery-card-body dl div{display:grid;grid-template-columns:58px minmax(0,1fr);gap:8px}.discovery-card-body dt{color:var(--text-tertiary);font-size:11px}.discovery-card-body dd{overflow:hidden;margin:0;color:var(--text-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.discovery-card-actions{display:flex;flex-wrap:wrap;align-items:center;gap:2px;padding:10px 12px;border-top:1px solid var(--border-color-light)}.discovery-card-actions .el-button{margin-left:0}.book-search{display:flex;align-items:center;gap:8px}.discovery-pagination,.list-pagination{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:18px 4px 2px;border-top:1px solid var(--border-color-light);color:var(--text-tertiary);font-size:12px}.discovery-pagination :deep(.el-pagination),.list-pagination :deep(.el-pagination){min-width:0}@media(max-width:1100px){.discovery-heading{align-items:flex-start;flex-direction:column;gap:14px}.discovery-heading-actions{width:100%;justify-content:space-between}.discovery-card-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:760px){.discovery-heading-actions{align-items:stretch;flex-direction:column}.discovery-view-switch{width:100%}.book-search{width:100%}.book-search .search{flex:1}.discovery-card-grid{grid-template-columns:minmax(0,1fr)}.discovery-pagination,.list-pagination{align-items:flex-start;flex-direction:column}.discovery-pagination :deep(.el-pagination),.list-pagination :deep(.el-pagination){flex-wrap:wrap;justify-content:flex-start;gap:8px 0}}@media(prefers-reduced-motion:reduce){.discovery-view-indicator,.discovery-card{transition:none}.discovery-card:hover{transform:none}}
/* 发现书籍紧凑卡片：沿用书库的封面型自适应网格 */
.discovery-card-grid{grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:var(--spacing-md)}
.discovery-card{contain:layout paint style;content-visibility:auto;contain-intrinsic-size:280px;border-radius:var(--radius-lg)}
.discovery-card:hover{transform:translateY(-4px)}
.discovery-card-cover{height:170px;font-size:38px}
.discovery-card-check{top:8px;right:8px;width:27px;height:27px;border-radius:8px}
.discovery-card-body{gap:8px;padding:10px 11px}
.discovery-card-title{display:grid;gap:5px}
.discovery-card-title :deep(.el-tag){justify-self:start;max-width:100%;overflow:hidden;text-overflow:ellipsis}
.discovery-card-title strong{font-size:13px}
.discovery-card-title p{margin-top:2px;font-size:11px}
.discovery-card-body dl{gap:4px}
.discovery-card-body dl div{grid-template-columns:34px minmax(0,1fr);gap:5px}
.discovery-card-body dt{font-size:9px}
.discovery-card-body dd{font-size:10px}
.discovery-card-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:2px;padding:6px;border-top-color:var(--border-color-light)}
.discovery-card-actions :deep(.el-button),.discovery-card-actions .source-link{display:inline-flex;min-width:0;min-height:26px;align-items:center;justify-content:center;margin:0;padding:4px 3px;font-size:10px;line-height:1.2;text-align:center}
.discovery-card-actions .source-link{border-radius:6px}
.crawler-book-card{cursor:pointer}
.crawler-book-title-button{display:block;width:100%;overflow:hidden;padding:0;border:0;background:transparent;color:var(--text-primary);cursor:pointer;font:inherit;font-size:13px;font-weight:700;text-align:left;text-overflow:ellipsis;white-space:nowrap}.crawler-book-title-button:hover{color:var(--primary)}.crawler-book-title-button:focus-visible{outline:2px solid var(--primary);outline-offset:2px;border-radius:4px}
.crawler-book-cover-badges{position:absolute;top:8px;left:8px;z-index:2;display:flex;max-width:calc(100% - 16px);flex-wrap:wrap;gap:5px}.crawler-book-cover-badges :deep(.el-tag){max-width:100%;border:0;box-shadow:0 3px 10px rgba(0,0,0,.16);backdrop-filter:blur(8px)}
.crawler-book-card-body{align-content:start}.crawler-book-progress{display:grid;gap:5px}.crawler-book-progress>div{display:flex;align-items:center;justify-content:space-between;gap:6px;color:var(--text-tertiary);font-size:9px}.crawler-book-progress strong{color:var(--text-secondary);font-size:9px;white-space:nowrap}.crawler-book-progress :deep(.el-progress__text){display:none}.crawler-book-progress :deep(.el-progress-bar){padding-right:0;margin-right:0}
.crawler-book-card-actions{grid-template-columns:repeat(2,minmax(0,1fr))}
@media(max-width:520px){.discovery-card-grid{grid-template-columns:repeat(auto-fill,minmax(132px,1fr));gap:10px}.discovery-card-cover{height:154px}}
.discovery-toolbar{display:grid;grid-template-columns:minmax(260px,1.6fr) minmax(160px,.8fr) minmax(190px,.9fr) auto auto;gap:10px;align-items:center;margin-bottom:16px;padding:14px;border:1px solid var(--border-color-light);border-radius:15px;background:var(--surface-elevated)}.book-filter-toolbar{display:grid;grid-template-columns:minmax(230px,1.5fr) repeat(3,minmax(140px,.8fr)) minmax(190px,1fr) auto auto;gap:10px;align-items:center;margin-bottom:16px;padding:14px;border:1px solid var(--border-color-light);border-radius:15px;background:var(--surface-elevated)}@media(max-width:1200px){.book-filter-toolbar{grid-template-columns:repeat(3,minmax(0,1fr))}.book-filter-toolbar .el-button{margin-left:0}}@media(max-width:980px){.discovery-toolbar{grid-template-columns:2fr 1fr 1fr}.discovery-toolbar .el-button{margin-left:0}}@media(max-width:640px){.discovery-toolbar,.book-filter-toolbar{grid-template-columns:minmax(0,1fr)}.discovery-toolbar .el-button,.book-filter-toolbar .el-button{width:100%}}
.marker-list{display:grid;gap:2px}.marker-row{display:grid;width:100%;grid-template-columns:minmax(0,1fr) auto;gap:8px;align-items:center}.marker-row .el-button{margin-left:0}@media(max-width:640px){.marker-row{grid-template-columns:minmax(0,1fr)}.marker-row .el-button{justify-self:end}}
:global(.site-editor-dialog){display:flex;width:min(860px,calc(100vw - 32px))!important;max-height:calc(100dvh - 32px);flex-direction:column;margin-top:max(16px,3vh)!important;margin-bottom:16px}
:global(.site-editor-dialog .el-dialog__body){display:flex;min-width:0;min-height:0;flex:1;flex-direction:column;overflow:hidden}
:global(.site-editor-dialog .el-dialog__header),:global(.site-editor-dialog .el-dialog__footer){flex:0 0 auto}
.site-editor-form{box-sizing:border-box;width:100%;min-width:0;min-height:0;flex:1 1 auto;align-content:start;overflow-x:hidden;overflow-y:auto;overscroll-behavior:contain}
.site-editor-tab-scroll{position:sticky;top:0;z-index:3;flex:0 0 auto;overflow-x:auto;padding-bottom:8px;background:var(--surface-card)}
.site-editor-tabs{position:relative;display:grid;width:max(100%,650px);grid-template-columns:repeat(5,minmax(130px,1fr));padding:4px;border:1px solid var(--border-color);border-radius:15px;background:var(--bg-page);isolation:isolate}.site-editor-tab-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/5);border:1px solid var(--border-color-light);border-radius:11px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .28s cubic-bezier(.2,.8,.2,1)}.site-editor-tabs button{position:relative;z-index:1;display:grid;gap:2px;padding:9px 12px;border:0;background:transparent;color:var(--text-secondary);text-align:center;cursor:pointer}.site-editor-tabs button strong{font-size:13px}.site-editor-tabs button small{color:var(--text-tertiary);font-size:10px}.site-editor-tabs button.active,.site-editor-tabs button.active small{color:var(--primary)}.site-editor-tabs button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:11px}.site-tab-panel{display:grid;gap:14px}
@media(max-width:640px){:global(.site-editor-dialog){width:calc(100vw - 20px)!important;max-height:calc(100dvh - 20px);margin-top:10px!important;margin-bottom:10px}:global(.site-editor-dialog .el-dialog__body){padding-right:14px;padding-left:14px}.site-editor-form{padding-right:0}}
@media(prefers-reduced-motion:reduce){.site-editor-tab-indicator{transition:none}}
.chapter-heading-tools{display:flex;align-items:center;gap:10px}.chapter-follow{box-sizing:border-box;display:flex;min-width:132px;height:38px;align-items:center;justify-content:space-between;gap:10px;padding:2px 10px;border:1px solid var(--border-color);border-radius:12px;background:var(--bg-page);cursor:pointer;transition:border-color .2s ease,background .2s ease}.chapter-follow strong{color:var(--text-secondary);font-size:11px}.chapter-follow.active{border-color:color-mix(in srgb,var(--primary) 38%,var(--border-color));background:var(--primary-alpha-10)}.chapter-follow.active strong{color:var(--primary)}.chapter-sort.disabled{opacity:.5}.chapter-sort button:disabled{cursor:not-allowed}.chapter-name-cell{display:flex;min-width:0;align-items:center;gap:8px}.chapter-name-cell>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.chapter-name-cell em{display:inline-flex;flex:0 0 auto;align-items:center;gap:5px;padding:3px 7px;border-radius:99px;background:var(--primary-alpha-10);color:var(--primary);font-size:9px;font-style:normal;font-weight:800}.chapter-name-cell em i{width:6px;height:6px;border-radius:50%;background:var(--primary);box-shadow:0 0 0 4px var(--primary-alpha-10);animation:chapter-follow-pulse 1.5s ease-out infinite}.chapters-table :deep(.current-crawling-row>td.el-table__cell){background:color-mix(in srgb,var(--primary) 11%,var(--surface-elevated))!important}.chapters-table :deep(.current-crawling-row:hover>td.el-table__cell){background:color-mix(in srgb,var(--primary) 15%,var(--surface-elevated))!important}@keyframes chapter-follow-pulse{70%,100%{box-shadow:0 0 0 8px transparent}}@media(max-width:720px){.chapter-heading-tools{align-items:stretch;flex-direction:column}.chapter-follow{width:100%}}@media(prefers-reduced-motion:reduce){.chapter-follow{transition:none}.chapter-name-cell em i{animation:none}}
.hero-actions{display:flex;align-items:center;gap:10px}.polling-setting{display:flex;align-items:center;gap:10px;padding:7px 9px 7px 12px;border:1px solid var(--border-color);border-radius:13px;background:color-mix(in srgb,var(--surface-elevated) 88%,transparent);box-shadow:var(--shadow-sm);backdrop-filter:blur(14px)}.polling-setting>span{display:flex;align-items:center;gap:7px;color:var(--text-secondary);font-size:11px;font-weight:700;white-space:nowrap}.polling-setting>span i{width:7px;height:7px;border-radius:50%;background:var(--success);box-shadow:0 0 0 4px var(--success-alpha-15);animation:live-pulse 1.8s ease-out infinite}.polling-setting :deep(.el-select){width:84px}.polling-setting :deep(.el-select__wrapper){border-radius:9px;background:var(--surface-card);box-shadow:none}@media(max-width:640px){.hero-actions{width:100%;align-items:stretch;flex-direction:column}.polling-setting{justify-content:space-between}.polling-setting :deep(.el-select){width:100px}.hero-actions>.el-button{width:100%;margin-left:0}}@media(prefers-reduced-motion:reduce){.polling-setting>span i{animation:none}}
.import-book-dialog{display:grid;gap:20px}.format-options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.format-options :deep(.el-checkbox-button){width:100%}.format-options :deep(.el-checkbox-button__inner){display:grid;width:100%;gap:4px;padding:16px;border:1px solid var(--border-color)!important;border-radius:13px!important;background:var(--surface-elevated);box-shadow:none!important;text-align:left}.format-options :deep(.el-checkbox-button__inner strong){font-size:15px}.format-options :deep(.el-checkbox-button__inner small){color:var(--text-tertiary);font-size:11px;font-weight:400}.format-options :deep(.el-checkbox-button.is-checked .el-checkbox-button__inner){border-color:var(--primary)!important;background:var(--primary-alpha-10);color:var(--primary)}.format-options :deep(.el-checkbox-button.is-focus .el-checkbox-button__inner){outline:2px solid var(--primary);outline-offset:2px}@media(max-width:480px){.format-options{grid-template-columns:minmax(0,1fr)}}
.discovery-manager-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 16px;margin-bottom:14px;border-radius:14px;background:var(--primary-alpha-10)}.discovery-manager-toolbar>div{display:grid;gap:3px}.discovery-manager-toolbar p{color:var(--text-secondary);font-size:12px}.discovery-pages{display:grid;gap:8px}.discovery-pages-dialog-list{max-height:58vh;overflow-y:auto;padding-right:4px}.discovery-page-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px 12px;align-items:center;padding:12px 14px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-card)}.discovery-page-row>div:first-child{display:grid;min-width:0;gap:3px}.discovery-page-row a{overflow:hidden;color:var(--text-secondary);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.discovery-page-row small{color:var(--text-tertiary);font-size:10px}.discovery-page-actions{grid-column:1/-1;display:flex;justify-content:flex-end}.discovery-page-actions .el-button{margin-left:0}.discovery-page-form{display:grid;gap:14px}.discovery-page-form .el-form-item{margin-bottom:0}@media(max-width:520px){.discovery-manager-toolbar{align-items:stretch;flex-direction:column}.discovery-page-row{grid-template-columns:minmax(0,1fr)}.discovery-page-row>.el-switch{justify-self:start}.discovery-page-actions{justify-content:flex-start}}
.task-scan-progress{display:grid;gap:5px}.task-scan-progress>small{color:var(--text-tertiary);font-size:10px}.task-scan-summary{display:flex;flex-wrap:wrap;gap:5px;color:var(--text-secondary);font-size:11px}.task-scan-summary>*{padding:2px 6px;border-radius:99px;font-style:normal;font-weight:700}.task-scan-summary b{background:var(--success-alpha-15);color:var(--success)}.task-scan-summary i{background:var(--primary-alpha-10);color:var(--primary)}.task-scan-summary em{background:color-mix(in srgb,var(--warning) 15%,transparent);color:var(--warning)}.task-scan-summary strong{background:color-mix(in srgb,var(--danger) 10%,transparent);color:var(--danger)}.scan-result-summary{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px;margin-bottom:16px}.scan-result-summary span{display:grid;gap:3px;padding:12px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated)}.scan-result-summary small{color:var(--text-tertiary);font-size:10px}.scan-result-summary strong{font-size:20px}.scan-results-table a{color:var(--primary)}.scan-results-pagination{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-top:14px;color:var(--text-tertiary);font-size:12px}@media(max-width:640px){.scan-result-summary{grid-template-columns:repeat(2,minmax(0,1fr))}.scan-results-pagination{align-items:flex-start;flex-direction:column}}
.crawler-book-metadata{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-bottom:16px}.crawler-book-metadata>span{display:grid;gap:4px;padding:11px 13px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated)}.crawler-book-metadata small{color:var(--text-tertiary);font-size:10px}.crawler-book-metadata .metadata-tag-row{grid-column:1/-1}.metadata-tags{display:flex;flex-wrap:wrap;gap:6px}.test-result>.metadata-tags{margin-top:14px}@media(max-width:520px){.crawler-book-metadata{grid-template-columns:minmax(0,1fr)}.crawler-book-metadata .metadata-tag-row{grid-column:auto}}
:deep(.task-open){display:grid;width:100%;margin:0;gap:3px;padding:5px 0;appearance:none;border:0;background:transparent;color:var(--text-primary);font:inherit;text-align:left;cursor:pointer}:deep(.task-open p){margin:3px 0 0;color:var(--text-secondary);font-size:12px}:deep(.task-open:hover strong){color:var(--primary)}:deep(.task-open:focus-visible){outline:2px solid var(--primary);outline-offset:2px;border-radius:6px}.task-detail-content{display:grid;gap:16px}.task-detail-hero{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:14px;align-items:center;padding:18px;border-radius:18px;background:var(--primary-alpha-10)}.task-detail-hero h2{overflow:hidden;margin-bottom:4px;text-overflow:ellipsis;white-space:nowrap}.task-detail-hero>div:nth-child(2)>p:last-child{color:var(--text-secondary);font-size:12px}.task-detail-mark{position:relative;display:grid;width:72px;height:72px;place-items:center;border:7px solid var(--surface-card);border-radius:50%;background:var(--surface-elevated);box-shadow:var(--shadow-sm)}.task-detail-mark>span{position:absolute;top:4px;right:4px;width:10px;height:10px;border-radius:50%;background:var(--text-tertiary)}.task-detail-mark>span.state-running{background:var(--success);box-shadow:0 0 0 5px var(--success-alpha-15);animation:live-pulse 1.8s ease-out infinite}.task-detail-mark>span.state-failed{background:var(--danger)}.task-detail-mark>span.state-paused,.task-detail-mark>span.state-partial_success{background:var(--warning)}.task-progress-card{display:grid;gap:11px;padding:16px;border:1px solid var(--border-color-light);border-radius:15px;background:var(--surface-elevated)}.task-progress-card>div{display:flex;align-items:center;justify-content:space-between}.task-progress-card>p{color:var(--text-secondary);font-size:12px}.task-stat-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.task-stat-grid>span{display:grid;gap:3px;padding:12px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-card)}.task-stat-grid small,.task-detail-list span{color:var(--text-tertiary);font-size:10px}.task-stat-grid strong{font-size:20px}.task-detail-list{overflow:hidden;border:1px solid var(--border-color-light);border-radius:14px}.task-detail-list>div{display:grid;grid-template-columns:130px minmax(0,1fr);gap:12px;padding:11px 14px;border-bottom:1px solid var(--border-color-light)}.task-detail-list>div:last-child{border-bottom:0}.task-detail-list strong{overflow-wrap:anywhere;font-size:12px}.task-detail-actions{display:flex;flex-wrap:wrap;gap:8px;padding-top:4px}.task-detail-actions .el-button{margin-left:0}@media(max-width:520px){.task-detail-hero{grid-template-columns:auto minmax(0,1fr)}.task-detail-hero>.el-tag{grid-column:1/-1;justify-self:start}.task-stat-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.task-detail-list>div{grid-template-columns:90px minmax(0,1fr)}}@media(prefers-reduced-motion:reduce){.task-detail-mark>span.state-running{animation:none}}
.queue-heading-actions{display:flex;align-items:center;gap:10px}.queue-runtime{display:flex;gap:8px}.queue-runtime span,.queue-runtime button{box-sizing:border-box;display:flex;min-height:32px;align-items:center;gap:7px;padding:7px 12px;border:0;border-radius:99px;background:var(--surface-elevated);color:var(--text-secondary);font-family:inherit;font-size:12px;font-weight:700;line-height:1}.queue-runtime button{border:1px solid var(--border-color-light);cursor:pointer;transition:border-color .18s ease,color .18s ease,background .18s ease}.queue-runtime button:hover{border-color:var(--primary);background:var(--primary-alpha-10);color:var(--primary)}.queue-runtime button:focus-visible{outline:2px solid var(--primary);outline-offset:2px}.running-dot{width:8px;height:8px;border-radius:50%;background:var(--success);box-shadow:0 0 0 3px var(--success-alpha-15)}.queue-settings-content{display:grid;gap:18px}.queue-settings-summary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.queue-settings-summary span{display:grid;gap:4px;padding:14px;border:1px solid var(--border-color-light);border-radius:13px;background:var(--surface-elevated)}.queue-settings-summary small{color:var(--text-tertiary);font-size:10px}.queue-settings-summary strong{font-size:24px}.queue-task-link{display:grid;width:100%;gap:3px;padding:4px 0;border:0;background:transparent;color:var(--text-primary);font:inherit;text-align:left;cursor:pointer}.queue-task-link small{color:var(--text-tertiary);font-size:11px}.queue-task-link:hover strong{color:var(--primary)}.queue-task-link:focus-visible{outline:2px solid var(--primary);outline-offset:2px;border-radius:6px}@media(max-width:720px){.queue-heading-actions{align-items:flex-end;flex-direction:column}.queue-runtime{order:2}}@media(max-width:520px){.queue-runtime{width:100%}.queue-runtime span,.queue-runtime button{flex:1;justify-content:center}}
.muted-text{color:var(--text-tertiary);font-size:11px}
.task-filter-toolbar{display:flex;align-items:center;gap:10px;margin-bottom:16px;padding:12px 14px;border:1px solid var(--border-color-light);border-radius:14px;background:var(--surface-elevated)}.task-filter-toolbar :deep(.el-select){width:210px}.task-filter-toolbar .el-button{margin-left:0}@media(max-width:520px){.task-filter-toolbar{align-items:stretch;flex-direction:column}.task-filter-toolbar :deep(.el-select),.task-filter-toolbar .el-button{width:100%}}
:deep(.task-success-progress){display:flex;align-items:center;gap:10px;min-height:24px;color:var(--success)}:deep(.task-success-progress strong){font-size:13px}:deep(.task-success-progress span){color:var(--text-tertiary);font-size:11px}@keyframes crawler-progress-stripes{from{background-position:0 0}to{background-position:24px 0}}:deep(.crawler-running-progress .el-progress-bar__inner){background-color:var(--success)!important;background-image:linear-gradient(45deg,rgba(255,255,255,.38) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.38) 50%,rgba(255,255,255,.38) 75%,transparent 75%,transparent);background-size:24px 24px;animation:crawler-progress-stripes .7s linear infinite}:deep(.crawler-running-progress .el-progress__text){color:var(--success)}@media(prefers-reduced-motion:reduce){:deep(.crawler-running-progress .el-progress-bar__inner){animation:none}}
</style>
