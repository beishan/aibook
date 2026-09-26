<template>
  <main class="crawler-page">
    <header class="crawler-header">
      <div>
        <h1 class="crawler-page-title">书籍爬虫</h1>
        <p class="crawler-page-subtitle">共 {{ dashboard?.bookCount || 0 }} 本采集书籍</p>
      </div>
      <div class="crawler-header-actions"><label class="polling-setting"><span><i/>自动刷新</span><el-select :model-value="pollingIntervalSeconds" size="small" aria-label="采集进度自动刷新间隔" @change="setPollingInterval(Number($event))"><el-option v-for="seconds in pollingIntervalOptions" :key="seconds" :label="`${seconds} 秒`" :value="seconds" /></el-select></label><el-button type="primary" :icon="Link" @click="crawlDialog = true">URL 手动采集</el-button></div>
    </header>

    <div class="segmented-wrap" role="tablist" aria-label="采集中心栏目" :style="{'--tab-count':tabs.length}" @keydown="handleTabKey">
      <span class="segment-indicator" :style="{ transform: `translateX(${activeIndex * 100}%)` }" />
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
      <div class="section-heading"><div><p class="eyebrow">LIVE QUEUE</p><h2>最近任务</h2></div><el-button text :icon="Refresh" @click="refreshOverview">刷新</el-button></div>
      <TaskTable :tasks="dashboard?.recentTasks || []" @open="openTask" @command="runTaskCommand" @edit="openTaskEditor" @delete="removeTask" @scan-results="openScanResults" />
    </section>

    <section v-else-if="activeTab === 'statistics'" v-loading="statisticsLoading" class="panel crawler-statistics" role="tabpanel" aria-labelledby="crawler-statistics-title">
      <header class="crawler-statistics-heading"><div><p class="eyebrow">COLLECTION ANALYTICS</p><h2 id="crawler-statistics-title">采集统计</h2><span>观察新增、处理能力与入库转化</span></div><div class="statistics-range" role="tablist" aria-label="统计时间范围" @keydown="handleStatisticsRangeKey"><span :style="{transform:`translateX(${statisticsRangeIndex*100}%)`}" aria-hidden="true"/><button v-for="days in statisticsRangeOptions" :key="days" type="button" role="tab" :aria-selected="statisticsDays===days" :tabindex="statisticsDays===days?0:-1" :class="{active:statisticsDays===days}" @click="setStatisticsDays(days)">近 {{ days }} 天</button></div></header>
      <div v-if="statistics" class="crawler-statistics-grid">
        <article class="statistics-card statistics-card-wide"><header><div><h3>章节新增与采集趋势</h3><p>新增章节与成功获取正文的变化对比</p></div><div class="statistics-summary"><span><i class="stat-dot stat-dot-primary"/>新增 <b>{{ statisticsChapterTotals.newChapters }}</b></span><span><i class="stat-dot stat-dot-success"/>成功 <b>{{ statisticsChapterTotals.successfulChapters }}</b></span></div></header><div ref="chapterTrendChartRef" class="statistics-chart statistics-chart-large" role="img" aria-label="每日新增章节数与成功采集章节数双折线图"/></article>
        <article class="statistics-card statistics-card-wide">
          <header>
            <div>
              <h3>采集任务数量</h3>
              <p>按完成日期统计；总数包含部分成功任务，取消任务不计入</p>
            </div>
            <div class="statistics-summary">
              <span><i class="stat-dot stat-dot-primary"/>总数 <b>{{ statisticsTaskTotals.finished }}</b></span>
              <span><i class="stat-dot stat-dot-success"/>成功 <b>{{ statisticsTaskTotals.successful }}</b></span>
              <span><i class="stat-dot stat-dot-danger"/>失败 <b>{{ statisticsTaskTotals.failed }}</b></span>
            </div>
          </header>
          <div ref="taskCountChartRef" class="statistics-chart statistics-chart-large" role="img" aria-label="每日采集任务总数折线图，以及成功和失败任务数柱形图"/>
        </article>
        <article class="statistics-card"><header><div><h3>每日新增书籍</h3><p>首次进入采集中心的书籍</p></div><strong>{{ statisticsBookTotal }}</strong></header><div ref="bookTrendChartRef" class="statistics-chart" role="img" aria-label="每日新增书籍数折线图"/></article>
        <article class="statistics-card"><header><div><h3>每日采集成功书籍</h3><p>按完整采集成功时间统计</p></div><strong>{{ statisticsSuccessfulBooksTotal }}</strong></header><div ref="successfulBooksChartRef" class="statistics-chart" role="img" aria-label="每日采集成功书籍数量折线图"/></article>
        <article class="statistics-card"><header><div><h3>采集成功率</h3><p>成功任务占已结束任务比例</p></div><strong>{{ statisticsSuccessRate }}%</strong></header><div ref="successRateChartRef" class="statistics-chart" role="img" aria-label="每日采集任务成功率折线图"/></article>
        <article class="statistics-card"><header><div><h3>来源网站贡献</h3><p>周期内新增内容最多的前五个网站</p></div></header><div ref="siteContributionChartRef" class="statistics-chart" role="img" aria-label="来源网站新增书籍和章节贡献横向柱状图"/></article>
        <article class="statistics-card"><header><div><h3>采集转化漏斗</h3><p>累计发现、建任务、完成与入库</p></div></header><div ref="funnelChartRef" class="statistics-chart" role="img" aria-label="采集书籍转化漏斗图"/></article>
      </div>
      <el-empty v-else-if="!statisticsLoading" description="暂无采集统计数据" />
    </section>

    <section v-else-if="activeTab === 'sites'" class="panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">SOURCES</p><h2>采集网站</h2></div><div class="site-heading-actions"><el-button :icon="Upload" @click="openSiteConfigurationImport">导入配置</el-button><el-button type="primary" :icon="Plus" @click="openSite()">新增网站</el-button></div></div>
      <div v-if="sites.length" class="site-grid">
        <article v-for="site in sites" :key="site.id" class="site-card">
          <div class="site-top"><span class="site-mark">{{ site.siteName.slice(0, 1) }}</span><div><h3>{{ site.siteName }}</h3><a :href="site.homeUrl || site.baseUrl" target="_blank">{{ site.baseUrl }}</a></div><el-tag :type="site.status==='RULE_ERROR'?'danger':!site.ruleVersion?'warning':site.enabled?'success':'info'">{{ site.status==='RULE_ERROR'?'规则异常':!site.ruleVersion?'待配置规则':site.enabled?'启用':'停用' }}</el-tag></div>
          <div class="site-stats"><span><b>{{ site.bookCount }}</b> 本书</span><span><b>{{ site.requestIntervalMillis }}</b> ms 间隔</span><span><b>{{ site.maxConcurrency }}</b> 并发</span></div>
          <div v-if="hasSiteProtection(site)" class="site-protection" :class="{cooling:site.protection.coolingDown}"><div><strong>{{ site.protection.coolingDown ? '站点保护冷却中' : '请求节奏正在恢复' }}</strong><span>{{ site.protection.reason || '近期请求失败，系统已自动降低访问频率' }}</span></div><small v-if="site.protection.coolingDown&&site.protection.blockedUntil">恢复时间 {{ formatTime(site.protection.blockedUntil) }}</small><small v-else>附加延迟 {{ site.protection.adaptiveDelayMillis }} ms · 连续失败 {{ site.protection.consecutiveFailures }} 次</small></div>
          <p class="health-line" :class="{error:site.status==='RULE_ERROR'}">{{ site.ruleVersion ? `生效规则 v${site.ruleVersion}` : '暂无生效规则' }} · 共 {{ site.ruleCount }} 个版本</p>
          <footer><el-button v-if="hasSiteProtection(site)" text type="warning" @click="resetSiteProtection(site)">解除保护</el-button><el-button type="primary" plain @click="openDiscoveryPages(site)">发现页管理（{{ discoveryPagesBySite[site.id]?.length || 0 }}）</el-button><el-button type="primary" plain @click="openRuleManager(site)">规则管理</el-button><el-button text :icon="Download" @click="exportSiteConfiguration(site)">导出配置</el-button><el-button text @click="openSite(site)">编辑网站</el-button><el-button text @click="openCrawl(site.id)">采集 URL</el-button><el-button text type="danger" @click="removeSite(site)">删除</el-button></footer>
        </article>
      </div>
      <el-empty v-else description="尚未配置采集网站" />
    </section>

    <section v-else-if="activeTab === 'discovered'" class="panel" role="tabpanel">
      <div class="section-heading discovery-heading"><div><p class="eyebrow">DISCOVERY INBOX</p><h2>发现书籍</h2></div><div class="discovery-heading-actions"><div class="discovery-view-switch" role="tablist" aria-label="发现书籍视图" @keydown="handleDiscoveryViewKey"><span class="discovery-view-indicator" :style="{transform:`translateX(${discoveryViewMode==='card'?100:0}%)`}" aria-hidden="true"/><button type="button" role="tab" :aria-selected="discoveryViewMode==='table'" :tabindex="discoveryViewMode==='table'?0:-1" :class="{active:discoveryViewMode==='table'}" @click="setDiscoveryViewMode('table')">☰ 表格</button><button type="button" role="tab" :aria-selected="discoveryViewMode==='card'" :tabindex="discoveryViewMode==='card'?0:-1" :class="{active:discoveryViewMode==='card'}" @click="setDiscoveryViewMode('card')">▦ 卡片</button></div><div class="batch-actions"><el-button :loading="discoveryMetadataRefreshing" :disabled="!selectedDiscoveries.some(book=>!isBookTaskActive(book))" @click="batchRefreshDiscoveryMetadata">刷新分类标签</el-button><el-button :disabled="!selectedDiscoveries.length||discoveryMetadataRefreshing" @click="batchDiscovery('IGNORED')">忽略</el-button><el-button :disabled="!selectedDiscoveries.length||discoveryMetadataRefreshing" @click="batchDiscovery('BLACKLISTED')">加入黑名单</el-button><el-button type="primary" :disabled="!selectedDiscoveries.length||discoveryMetadataRefreshing" @click="batchCrawl">批量采集</el-button></div></div></div>
      <div class="discovery-toolbar">
        <el-input v-model="discoveryKeyword" clearable :prefix-icon="Search" placeholder="搜索书名、作者、网站、发现页、编码或最新章节" @keyup.enter="applyDiscoveryFilters" />
        <el-select v-model="discoverySiteId" clearable placeholder="全部采集网站"><el-option v-for="site in sites" :key="site.id" :label="site.siteName" :value="site.id" /></el-select>
        <el-select v-model="discoverySort" aria-label="发现书籍排序"><el-option v-for="item in discoverySortOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-checkbox v-model="discoveryFavoriteOnly" border @change="applyDiscoveryFilters">只看已收藏</el-checkbox>
        <el-button type="primary" :icon="Search" @click="applyDiscoveryFilters">查询</el-button><el-button @click="resetDiscoveryFilters">重置</el-button>
      </div>
      <el-table v-if="discoveryViewMode==='table'" v-loading="discoveryLoading" :data="discoveredBooks" class="data-table" @selection-change="selectedDiscoveries=$event">
        <el-table-column type="selection" width="48" fixed="left" />
        <el-table-column label="收藏" width="58" fixed="left" align="center"><template #default="{row}"><el-button size="small" circle :icon="row.favorite?StarFilled:Star" class="favorite-action row-hover-action" :class="{'is-favorite':row.favorite}" :aria-label="row.favorite?'取消收藏':'加入收藏'" :title="row.favorite?'取消收藏':'加入收藏'" @click.stop="toggleFavorite(row)"/></template></el-table-column>
        <el-table-column label="快捷操作" width="88" fixed="left" align="center">
          <template #default="{row}">
            <el-button
              size="small"
              type="primary"
              plain
              :loading="submittingBookTaskIds.has(row.id)"
              :disabled="isBookTaskActive(row)"
              @click.stop="crawlDiscovered(row)"
            >采集</el-button>
          </template>
        </el-table-column>
        <el-table-column label="书籍" min-width="260"><template #default="{row}"><div class="book-cell"><div class="mini-cover">{{ row.bookName.slice(0,1) }}</div><div><strong>{{ row.bookName }}</strong><p>{{ row.author || '未知作者' }} · {{ row.siteName }}</p></div></div></template></el-table-column>
        <el-table-column label="发现页" width="160"><template #default="{row}"><el-tag v-if="row.discoveryPageName" size="small" effect="plain">{{ row.discoveryPageName }}</el-tag><span v-else class="muted-text">站点首页 / 未记录</span></template></el-table-column>
        <el-table-column label="分类 / 标签" min-width="190"><template #default="{row}"><div class="crawler-book-tag-list table-tags"><el-tag v-if="row.category" size="small" type="info" effect="plain">{{ row.category }}</el-tag><el-tag v-for="tag in row.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag><span v-if="!row.category&&!row.tags?.length">暂无</span></div></template></el-table-column>
        <el-table-column prop="latestChapter" label="最新章节" min-width="180" />
        <el-table-column label="发现时间" width="170"><template #default="{row}">{{ formatTime(row.discoverTime) }}</template></el-table-column>
        <el-table-column label="操作" width="340" fixed="right" align="right"><template #default="{row}"><div class="crawler-book-action-cluster discovery-book-action-cluster row-hover-action" @click.stop><el-button size="small" round class="crawler-book-action-button crawler-book-action-details" @click="openBook(row)">详情</el-button><el-button size="small" round class="crawler-book-action-button crawler-book-action-import" @click="crawlDiscovered(row)">采集</el-button><el-dropdown trigger="click" placement="bottom-end" popper-class="discovery-more-popper" @command="handleDiscoveryCardMore($event,row)"><el-button size="small" circle :icon="MoreFilled" class="crawler-book-action-more" aria-label="更多操作" title="更多操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item command="book-lists">加入书单</el-dropdown-item><el-dropdown-item command="metadata" :disabled="isBookTaskActive(row)">刷新分类标签</el-dropdown-item><el-dropdown-item command="website">查看网站</el-dropdown-item><el-dropdown-item command="ignore" divided>忽略</el-dropdown-item><el-dropdown-item command="blacklist" class="danger-dropdown-item">加入黑名单</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></template></el-table-column>
      </el-table>
      <div v-else v-loading="discoveryLoading" class="discovery-card-panel">
        <div v-if="discoveredBooks.length" class="discovery-card-selection"><el-checkbox :model-value="allDiscoveredSelected" :indeterminate="someDiscoveredSelected&&!allDiscoveredSelected" @change="toggleCurrentDiscoveryPage(Boolean($event))">选择当前页</el-checkbox><span>已选择 {{ selectedDiscoveries.length }} 本</span></div>
        <div class="discovery-card-grid">
          <article v-for="book in discoveredBooks" :key="book.id" class="discovery-card" :class="{selected:isDiscoverySelected(book)}">
            <div class="discovery-card-action-overlay" role="group" :aria-label="`${book.bookName}快捷操作`" @click.stop>
              <el-button size="small" circle :icon="book.favorite?StarFilled:Star" class="favorite-action" :class="{'is-favorite':book.favorite}" :aria-label="book.favorite?'取消收藏':'加入收藏'" :title="book.favorite?'取消收藏':'加入收藏'" @click="toggleFavorite(book)"/>
              <el-button size="small" type="primary" :loading="submittingBookTaskIds.has(book.id)" :disabled="isBookTaskActive(book)" @click="crawlDiscovered(book)">采集</el-button>
              <el-button size="small" @click="batchDiscovery('IGNORED',[book.id])">忽略</el-button>
              <el-dropdown trigger="click" placement="bottom-end" popper-class="discovery-more-popper" @command="handleDiscoveryCardMore($event,book)">
                <el-button size="small" aria-label="更多操作">更多</el-button>
                <template #dropdown><el-dropdown-menu><el-dropdown-item command="details">详细信息</el-dropdown-item><el-dropdown-item command="book-lists">加入书单</el-dropdown-item><el-dropdown-item command="website">查看网站</el-dropdown-item><el-dropdown-item command="metadata" :disabled="isBookTaskActive(book)">刷新分类标签</el-dropdown-item><el-dropdown-item command="blacklist" divided class="danger-dropdown-item">加入黑名单</el-dropdown-item></el-dropdown-menu></template>
              </el-dropdown>
            </div>
            <div class="discovery-card-cover"><span>{{ book.bookName.slice(0,1) }}</span><img v-if="book.coverUrl && shouldLoadBookCover()" :src="getCoverUrl(book.coverUrl)" :alt="`${book.bookName}封面`" loading="lazy" @error="hideBrokenCover"/><span class="discovery-source-badge" :title="book.siteName">{{ book.siteName }}</span><el-checkbox class="discovery-card-check" :model-value="isDiscoverySelected(book)" :aria-label="`选择${book.bookName}`" @click.stop @change="toggleDiscoverySelection(book,Boolean($event))"/></div>
            <div v-if="book.category||book.tags?.length" class="discovery-card-body discovery-card-category crawler-book-tag-list"><el-tag v-if="book.category" size="small" type="info" effect="plain">{{ book.category }}</el-tag><el-tag v-for="tag in book.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag></div>
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
        <el-checkbox v-model="bookFavoriteOnly" border @change="applyBookFilters">只看已收藏</el-checkbox>
        <el-button type="primary" :icon="Search" @click="applyBookFilters">查询</el-button>
        <el-button @click="resetBookFilters">重置</el-button>
      </div>
      <transition name="task-batch-rise">
        <div v-if="selectedBooks.length" class="task-batch-bar book-batch-bar" role="toolbar" aria-label="采集书籍批量修改">
          <div class="task-batch-summary"><span>{{ selectedBooks.length }}</span><div><strong>已选择书籍</strong><small>批量创建任务、加入书库或修改状态</small></div></div>
          <div class="book-batch-actions"><div class="book-batch-task-actions"><el-button :loading="batchBookAction==='continue'" :disabled="batchBookBusy||!selectedBooks.some(book=>!isBookTaskActive(book))" @click="batchContinueBooks">继续</el-button><el-button :loading="batchBookAction==='updates'" :disabled="batchBookBusy||!selectedBooks.some(book=>!isBookTaskActive(book))" @click="batchCheckUpdates">检查更新</el-button><el-button :loading="batchBookAction==='metadata'" :disabled="batchBookBusy||!selectedBooks.some(book=>!isBookTaskActive(book))" @click="batchRefreshBookMetadata">刷新分类标签</el-button><el-button type="primary" plain :disabled="batchBookBusy" @click="openBatchImport">入库</el-button></div><div class="book-batch-status-actions"><el-select v-model="batchBookStatus" aria-label="批量设置采集状态"><el-option v-for="item in manualStatusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-button type="primary" :loading="batchBookStatusSaving" :disabled="batchBookBusy" @click="saveBatchBookStatus">统一更改状态</el-button></div></div>
        </div>
      </transition>
      <el-table v-if="bookViewMode==='table'" ref="bookTableRef" v-loading="bookLoading" :data="books" row-key="id" @selection-change="selectedBooks=$event" @row-click="handleBookTableRowClick" class="data-table">
        <el-table-column type="selection" width="48" reserve-selection fixed="left" />
        <el-table-column label="收藏" width="58" fixed="left" align="center"><template #default="{row}"><el-button size="small" circle :icon="row.favorite?StarFilled:Star" class="favorite-action row-hover-action" :class="{'is-favorite':row.favorite}" :aria-label="row.favorite?'取消收藏':'加入收藏'" :title="row.favorite?'取消收藏':'加入收藏'" @click.stop="toggleFavorite(row)"/></template></el-table-column>
        <el-table-column label="书籍" min-width="260"><template #default="{row}"><div class="book-cell"><div class="mini-cover">{{ row.bookName.slice(0,1) }}</div><div><strong>{{ row.bookName }}</strong><p>{{ row.author || '未知作者' }} · {{ row.siteName }}</p></div></div></template></el-table-column>
        <el-table-column label="分类 / 标签" min-width="210"><template #default="{row}"><div class="crawler-book-tag-list table-tags"><el-tag v-if="row.category" size="small" type="info" effect="plain">{{ row.category }}</el-tag><el-tag v-for="tag in row.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag><span v-if="!row.category&&!row.tags?.length">暂无</span></div></template></el-table-column>
        <el-table-column label="进度 / 采集结果" min-width="250"><template #default="{row}"><div v-if="isBookCompleted(row)" class="book-crawl-result table-result"><div><span>采集结果</span><strong>采集完成</strong></div><small>正文 {{ row.crawledChapterCount }} · 待开放 {{ row.pendingReleaseChapterCount }} · 失败 {{ row.failedChapterCount }}</small><el-button text type="primary" size="small" @click.stop="openBook(row)">采集结果详情</el-button></div><template v-else><el-progress :class="{'crawler-running-progress':isBookRunning(row)}" :percentage="progress(row)" :stroke-width="7" /><small>正文 {{ row.crawledChapterCount }}，待开放 {{ row.pendingReleaseChapterCount }} / 共 {{ row.chapterCount }} 章</small></template></template></el-table-column>
        <el-table-column label="状态" width="140"><template #default="{row}"><button class="status-editor" type="button" title="人工修改采集状态" @click.stop="openStatusEditor(row)"><el-tag :type="statusType(row.crawlStatus)">{{ statusLabel(row.crawlStatus) }}</el-tag><small>修改</small></button></template></el-table-column>
        <el-table-column label="创建时间" width="154"><template #default="{row}">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="开始爬取" width="154"><template #default="{row}">{{ row.lastCrawlStartedAt ? formatTime(row.lastCrawlStartedAt) : '暂无记录' }}</template></el-table-column>
        <el-table-column label="失败" width="80" prop="failedChapterCount" />
        <el-table-column label="操作" width="340" fixed="right" align="right"><template #default="{row}"><div class="crawler-book-action-cluster row-hover-action" @click.stop><el-button size="small" round class="crawler-book-action-button crawler-book-action-details" @click="openBook(row)">详情</el-button><el-button size="small" round class="crawler-book-action-button crawler-book-action-continue" :disabled="isBookTaskActive(row)" @click="continueCrawl(row)">{{ isBookTaskActive(row)?'任务中':'继续' }}</el-button><el-button size="small" round class="crawler-book-action-button crawler-book-action-import" @click="importBook(row)">{{ row.importStatus==='IMPORTED'?'同步入库':'入库' }}</el-button><el-dropdown trigger="click" placement="bottom-end" popper-class="discovery-more-popper" @command="handleCrawlerBookTableMore($event,row)"><el-button size="small" circle :icon="MoreFilled" class="crawler-book-action-more" aria-label="更多操作" title="更多操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item command="book-lists">加入书单</el-dropdown-item><el-dropdown-item command="updates">检查更新</el-dropdown-item><el-dropdown-item command="metadata" :disabled="isBookTaskActive(row)">刷新分类标签</el-dropdown-item><el-dropdown-item command="trial" :disabled="!row.crawledChapterCount">试读</el-dropdown-item><el-dropdown-item command="generate">生成文件</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></template></el-table-column>
      </el-table>
      <div v-else v-loading="bookLoading" class="discovery-card-panel">
        <div v-if="books.length" class="discovery-card-selection"><el-checkbox :model-value="allBooksSelected" :indeterminate="someBooksSelected&&!allBooksSelected" @change="toggleCurrentBookPage(Boolean($event))">选择当前页</el-checkbox><span>已选择 {{ selectedBooks.length }} 本</span></div>
        <div class="discovery-card-grid">
          <article v-for="book in books" :key="book.id" class="discovery-card crawler-book-card" :class="{selected:isBookSelected(book)}" @click="openBook(book)">
            <div class="discovery-card-action-overlay crawler-book-action-overlay" role="group" :aria-label="`${book.bookName}快捷操作`" @click.stop>
              <el-button size="small" type="primary" :disabled="isBookTaskActive(book)" @click="continueCrawl(book)">{{ isBookTaskActive(book)?'任务中':'继续' }}</el-button>
              <el-button size="small" circle :icon="book.favorite?StarFilled:Star" class="favorite-action" :class="{'is-favorite':book.favorite}" :aria-label="book.favorite?'取消收藏':'加入收藏'" :title="book.favorite?'取消收藏':'加入收藏'" @click="toggleFavorite(book)"/>
              <el-button size="small" :disabled="!book.crawledChapterCount" @click="startTrial(book)">试读</el-button>
              <el-dropdown trigger="click" placement="bottom-end" popper-class="discovery-more-popper" @command="handleCrawlerBookCardMore($event,book)">
                <el-button size="small" aria-label="更多操作">更多</el-button>
                <template #dropdown><el-dropdown-menu><el-dropdown-item command="details">详细信息</el-dropdown-item><el-dropdown-item command="book-lists">加入书单</el-dropdown-item><el-dropdown-item command="website">查看网站</el-dropdown-item><el-dropdown-item command="updates" divided>检查更新</el-dropdown-item><el-dropdown-item command="metadata" :disabled="isBookTaskActive(book)">刷新分类标签</el-dropdown-item><el-dropdown-item command="generate">生成文件</el-dropdown-item><el-dropdown-item command="import">{{ book.importStatus==='IMPORTED'?'同步入库':'加入书库' }}</el-dropdown-item><el-dropdown-item command="status">修改状态</el-dropdown-item></el-dropdown-menu></template>
              </el-dropdown>
            </div>
            <div class="discovery-card-cover"><span>{{ book.bookName.slice(0,1) }}</span><img v-if="book.coverUrl && shouldLoadBookCover()" :src="getCoverUrl(book.coverUrl)" :alt="`${book.bookName}封面`" loading="lazy" @error="hideBrokenCover"/><div class="crawler-book-cover-badges"><el-tag :type="statusType(book.crawlStatus)" effect="dark" size="small">{{ statusLabel(book.crawlStatus) }}</el-tag><el-tag v-if="book.importStatus==='IMPORTED'" type="success" effect="dark" size="small">已入库</el-tag></div><el-checkbox class="discovery-card-check crawler-book-card-check" :model-value="isBookSelected(book)" :aria-label="`选择${book.bookName}`" @click.stop @change="toggleBookSelection(book,Boolean($event))"/></div>
            <div class="discovery-card-body crawler-book-card-body"><div class="discovery-card-title"><div><button type="button" class="crawler-book-title-button" :title="book.bookName" @click.stop="openBook(book)">{{ book.bookName }}</button><p>{{ book.author || '未知作者' }}</p></div><el-tag v-if="book.category" size="small" effect="plain">{{ book.category }}</el-tag></div><div v-if="book.tags?.length" class="crawler-book-tag-list"><el-tag v-for="tag in book.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag></div><dl><div><dt>来源网站</dt><dd>{{ book.siteName }}</dd></div></dl><div v-if="isBookCompleted(book)" class="book-crawl-result card-result"><div><span>采集结果</span><strong>采集完成</strong></div><small>正文 {{ book.crawledChapterCount }} · 待开放 {{ book.pendingReleaseChapterCount }} · 失败 {{ book.failedChapterCount }}</small><el-button text type="primary" size="small" @click.stop="openBook(book)">采集结果详情</el-button></div><div v-else class="crawler-book-progress"><div><span>采集进度</span><strong>正文 {{ book.crawledChapterCount }} · 待开放 {{ book.pendingReleaseChapterCount }} / 共 {{ book.chapterCount }} 章</strong></div><el-progress :class="{'crawler-running-progress':isBookRunning(book)}" :percentage="progress(book)" :stroke-width="7" /></div></div>
          </article>
        </div>
      </div>
      <el-empty v-if="!bookLoading&&!books.length" description="暂无符合条件的采集书籍" />
      <div v-if="bookTotal" class="list-pagination"><span>当前显示 {{ books.length }} 本</span><el-pagination v-model:current-page="bookPage" v-model:page-size="bookPageSize" :page-sizes="[20,50,100]" :total="bookTotal" layout="total, sizes, prev, pager, next, jumper" background @current-change="loadBooks()" @size-change="handleBookSizeChange" /></div>
    </section>

    <section v-else class="panel" role="tabpanel">
      <div class="section-heading"><div><p class="eyebrow">{{ activeTab === 'failed' ? 'NEEDS ATTENTION' : 'PERSISTENT QUEUE' }}</p><h2>{{ activeTab === 'failed' ? '失败任务' : '采集任务' }}</h2></div><div class="queue-heading-actions"><div v-if="taskQueues.length" class="queue-runtime"><span><i class="running-dot"/>运行 {{ totalQueueRunning }} / {{ totalQueueConcurrency }}</span><button type="button" title="查看各网站的队列和任务" @click="openQueuedTasks">任务队列（{{ runningCurrentTaskCount }}/{{ currentCrawlerTasks.length }}）</button></div><el-button v-if="activeTab==='tasks'" @click="openQueueSettings">队列配置</el-button><el-button text :icon="Refresh" @click="refresh">刷新</el-button></div></div>
      <div v-if="activeTab==='tasks'" class="task-filter-toolbar">
        <el-select v-model="taskStatusFilter" clearable placeholder="全部任务状态" aria-label="任务状态筛选" @change="handleTaskStatusFilter">
          <el-option v-for="status in taskStatusOptions" :key="status" :label="statusLabel(status)" :value="status" />
        </el-select>
        <el-select v-model="taskTypeFilter" clearable placeholder="全部任务类型" aria-label="任务类型筛选" @change="handleTaskTypeFilter">
          <el-option v-for="type in taskTypeOptions" :key="type" :label="taskTypeLabel(type)" :value="type" />
        </el-select>
        <el-checkbox v-model="taskFavoriteOnly" border @change="handleTaskFavoriteFilter">只看已收藏</el-checkbox>
        <el-button
          type="primary"
          plain
          :loading="batchTaskManaging && batchTaskAction==='resume-all'"
          :disabled="batchTaskManaging || failedTaskTotal===0"
          @click="resumeAllFailedTasks"
        >恢复所有失败</el-button>
        <el-button :type="taskStatusFilter==='RUNNING'?'primary':undefined" @click="showRunningTasks">只看进行中</el-button>
        <el-button v-if="taskStatusFilter||taskTypeFilter||taskFavoriteOnly" text @click="clearTaskFilters">清除筛选</el-button>
      </div>
      <transition name="task-batch-rise">
        <div v-if="selectedTaskRows.length" class="task-batch-bar" role="toolbar" aria-label="采集任务批量管理">
          <div class="task-batch-summary"><span>{{ selectedTaskRows.length }}</span><div><strong>已选择任务</strong><small>批量操作仅在所选任务状态兼容时可用</small></div></div>
          <div class="task-batch-actions">
            <el-dropdown trigger="click" :disabled="batchTaskManaging || !canBatchSetPriority" @command="handleBatchTaskPriority">
              <el-button :loading="batchTaskManaging && batchTaskAction==='priority'">设置优先级</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="item in taskPriorityOptions" :key="item.value" :command="item.value">{{ item.label }}优先级</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
            <el-button :icon="VideoPause" :disabled="batchTaskManaging || !canBatchPause" :loading="batchTaskManaging && batchTaskAction==='pause'" @click="manageSelectedTasks('pause')">暂停</el-button>
            <el-button type="primary" plain :icon="VideoPlay" :disabled="batchTaskManaging || !canBatchResume" :loading="batchTaskManaging && batchTaskAction==='resume'" @click="manageSelectedTasks('resume')">继续</el-button>
            <el-button type="warning" plain :disabled="batchTaskManaging || !canBatchCancel" :loading="batchTaskManaging && batchTaskAction==='cancel'" @click="manageSelectedTasks('cancel')">取消</el-button>
            <el-button type="danger" plain :disabled="batchTaskManaging || !canBatchDelete" :loading="batchTaskManaging && batchTaskAction==='delete'" @click="manageSelectedTasks('delete')">删除</el-button>
          </div>
        </div>
      </transition>
      <div v-loading="activeTab === 'failed' ? failedTaskLoading : taskLoading">
        <TaskTable
          :key="`${activeTab}-${taskTableSelectionVersion}`"
          :tasks="activeTab === 'failed' ? failedTasks : tasks"
          :show-failure-reason="activeTab === 'failed'"
          :prioritizing-task-id="queuedTaskPrioritizingId"
          :batch-managing="batchTaskManaging"
          selectable
          @selection-change="handleTaskSelectionChange"
          @prioritize="prioritizeQueuedTask"
          @open="openTask"
          @command="runTaskCommand"
          @edit="openTaskEditor"
          @delete="removeTask"
          @scan-results="openScanResults"
          @toggle-favorite="toggleTaskFavorite"
          @book-lists="openTaskBookLists"
        />
      </div>
      <el-empty v-if="activeTab === 'tasks'&&!taskLoading&&!tasks.length" :description="taskStatusFilter||taskTypeFilter||taskFavoriteOnly?'暂无符合筛选条件的采集任务':'暂无采集任务'" />
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
          <section class="site-form-section"><header><span>02</span><div><h3>站点访问节奏</h3><p>控制当前网站的访问频率和并发</p></div></header><div class="form-grid"><el-form-item label="请求间隔（ms）"><el-input-number v-model="siteForm.requestIntervalMillis" :min="100" :step="100" /></el-form-item><el-form-item label="随机延迟（ms）"><el-input-number v-model="siteForm.randomDelayMillis" :min="0" :step="100" /></el-form-item></div><el-form-item label="最大并发"><el-input-number v-model="siteForm.maxConcurrency" :min="1" :max="8" /></el-form-item><el-form-item label="robots.txt 策略"><el-switch v-model="siteForm.respectRobotsTxt" active-text="遵守 robots.txt" inactive-text="忽略 robots.txt" /></el-form-item><el-alert type="warning" :closable="false" title="默认遵守网站的 robots.txt；关闭前请确认已获得目标网站授权。" /><el-alert type="info" :closable="false" title="请求超时、失败重试和请求头已统一迁移到“系统设置 → 爬虫设置”。" /></section>
        </div>
        <div v-show="activeSiteTab==='validation'" id="site-editor-panel-validation" class="site-tab-panel" role="tabpanel" aria-labelledby="site-editor-tab-validation">
          <section class="site-form-section"><header class="section-with-action"><span>04</span><div><h3>正文特征</h3><p>为每条特征指定命中后的章节状态</p></div><el-button :icon="Plus" :disabled="siteForm.contentMarkers.length>=50" @click="addContentMarker">添加特征</el-button></header><div v-if="siteForm.contentMarkers.length" class="marker-list"><el-form-item v-for="(_,index) in siteForm.contentMarkers" :key="index" :prop="`contentMarkers.${index}.marker`" :rules="contentMarkerRules" :label="`特征 ${index+1}`"><div class="marker-row"><el-input v-model="siteForm.contentMarkers[index].marker" maxlength="500" show-word-limit placeholder="例如：以下内容为VIP专属，升级会员即可继续阅读"/><el-select v-model="siteForm.contentMarkers[index].status" class="marker-status" aria-label="命中后的正文状态"><el-option label="采集失败" value="FAILED"/><el-option label="待开放" value="PENDING_RELEASE"/></el-select><el-button text type="danger" @click="removeContentMarker(index)">删除</el-button></div></el-form-item></div><el-empty v-else :image-size="48" description="未配置正文特征" /><el-alert type="info" :closable="false" title="采用忽略大小写的包含匹配；“待开放”章节不计为失败，整本书仍可采集成功和入库。" /></section>
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
        <div class="form-grid"><el-form-item label="最多扫描页数"><el-input-number v-model="discoveryPageForm.maxPages" :min="1" /></el-form-item><el-form-item label="自动扫描间隔（分钟）"><el-input-number v-model="discoveryPageForm.scanIntervalMinutes" :min="5" :max="10080" :disabled="!discoveryPageForm.autoScanEnabled" /></el-form-item></div>
        <el-switch v-model="discoveryPageForm.autoScanEnabled" active-text="开启自动扫描" inactive-text="仅手动扫描" />
        <el-alert type="info" :closable="false" title="自动扫描仅提取书籍到“发现书籍”，不会自动采集正文或加入书库。" />
      </el-form>
      <template #footer><el-button @click="discoveryPageDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDiscoveryPage">保存发现页</el-button></template>
    </el-dialog>

    <el-dialog v-model="siteConfigurationImportDialog" title="导入网站配置" width="min(720px, 94vw)" append-to-body destroy-on-close>
      <el-alert type="info" :closable="false" title="导入会创建一个新网站，并同时导入全部规则版本、正文特征、代理和发现页。站点编码已存在时不会覆盖。" />
      <div class="import-mode site-configuration-import-mode" role="tablist" aria-label="网站配置 JSON 导入方式" @keydown="handleSiteConfigurationImportModeKey">
        <span class="import-mode-indicator" :style="{transform:`translateX(${siteConfigurationImportMode==='text'?0:100}%)`}" />
        <button :class="{active:siteConfigurationImportMode==='text'}" role="tab" :aria-selected="siteConfigurationImportMode==='text'" :tabindex="siteConfigurationImportMode==='text'?0:-1" @click="siteConfigurationImportMode='text'">粘贴文本</button>
        <button :class="{active:siteConfigurationImportMode==='file'}" role="tab" :aria-selected="siteConfigurationImportMode==='file'" :tabindex="siteConfigurationImportMode==='file'?0:-1" @click="siteConfigurationImportMode='file'">选择文件</button>
      </div>
      <div v-if="siteConfigurationImportMode==='text'" class="import-pane">
        <el-input v-model="siteConfigurationJsonText" type="textarea" :rows="16" resize="vertical" spellcheck="false" placeholder="粘贴网站配置 JSON，或点击下方“导入模板”查看完整示例" />
        <p>JSON 可能包含代理地址或凭据，导出和传递时请妥善保管。</p>
      </div>
      <div v-else class="import-pane file-pane">
        <input ref="siteConfigurationImportInput" class="file-input" type="file" accept="application/json,.json" @change="handleSiteConfigurationImportFile">
        <button class="file-picker" type="button" @click="siteConfigurationImportInput?.click()"><el-icon><Upload /></el-icon><strong>{{ siteConfigurationImportFileName || '选择网站配置 JSON 文件' }}</strong><span>{{ siteConfigurationImportFileName ? '已读取文件，可点击重新选择' : '支持 .json 文件' }}</span></button>
        <el-input v-if="siteConfigurationJsonText" v-model="siteConfigurationJsonText" type="textarea" :rows="8" resize="vertical" spellcheck="false" />
      </div>
      <template #footer><el-button @click="loadSiteConfigurationTemplate">导入模板</el-button><el-button @click="siteConfigurationImportDialog=false">取消</el-button><el-button type="primary" :loading="savingSiteConfiguration" :disabled="!siteConfigurationJsonText.trim()" @click="submitSiteConfigurationImport">导入网站</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleManagerDialog" :title="`${ruleSite?.siteName || ''} · 规则管理`" width="min(1120px, 96vw)" append-to-body destroy-on-close>
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

    <el-dialog v-model="ruleTestDialog" title="规则在线测试" width="min(720px, 94vw)" append-to-body>
      <el-form label-position="top"><el-form-item label="书籍详情测试 URL"><el-input v-model="ruleTestUrl" placeholder="https://example.com/book/123/" /></el-form-item></el-form>
      <div v-if="ruleTestResult" class="test-result" :class="{failed:!ruleTestResult.success}">
        <template v-if="ruleTestResult.success"><div class="test-facts"><span><small>书名</small><b>{{ ruleTestResult.title }}</b></span><span><small>作者</small><b>{{ ruleTestResult.author || '未知' }}</b></span><span><small>分类</small><b>{{ ruleTestResult.category || '未识别' }}</b></span><span><small>来源状态</small><b>{{ ruleTestResult.bookStatus || '未识别' }}</b></span><span><small>目录</small><b>{{ ruleTestResult.chapterCount }} 章</b></span><span><small>正文样本</small><b>{{ ruleTestResult.contentLength }} 字</b></span></div><div v-if="ruleTestResult.tags?.length" class="metadata-tags"><el-tag v-for="tag in ruleTestResult.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag></div><p><b>{{ ruleTestResult.sampleChapter }}</b> · {{ ruleTestResult.durationMillis }} ms</p><pre>{{ ruleTestResult.contentPreview }}</pre></template>
        <el-alert v-else type="error" :closable="false" :title="ruleTestResult.errorMessage || '规则测试失败'" />
      </div>
      <template #footer><el-button @click="ruleTestDialog=false">关闭</el-button><el-button type="primary" :loading="testingRule" @click="runRuleTest">测试详情、目录与正文</el-button></template>
    </el-dialog>

    <el-dialog v-model="crawlDialog" title="URL 手动采集" width="min(560px, 94vw)" append-to-body>
      <el-form label-position="top"><el-form-item label="采集网站"><el-select v-model="crawlForm.siteId" placeholder="选择已启用网站"><el-option v-for="site in sites.filter(s=>s.enabled)" :key="site.id" :label="site.siteName" :value="site.id" /></el-select></el-form-item><el-form-item label="书籍详情 URL"><el-input v-model="crawlForm.url" placeholder="https://example.com/book/123/" /></el-form-item></el-form>
      <template #footer><el-button @click="crawlDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="startCrawl">创建采集任务</el-button></template>
    </el-dialog>

    <el-dialog v-model="importDialog" :title="importTargets.length>1?`批量加入书库（${importTargets.length} 本）`:importTarget?.importStatus==='IMPORTED'?'同步到书库':'加入书库'" width="min(560px, 94vw)" append-to-body>
      <div v-if="importTargets.length" class="import-book-dialog">
        <div v-if="importTarget" class="status-book"><span class="mini-cover">{{ importTarget.bookName.slice(0,1) }}</span><div><strong>{{ importTarget.bookName }}</strong><p>{{ importTarget.author || '未知作者' }} · 已有正文 {{ importTarget.crawledChapterCount }} / {{ importTarget.chapterCount }} 章</p></div></div>
        <div v-else class="status-book"><span class="mini-cover">批</span><div><strong>批量处理 {{ importTargets.length }} 本书</strong><p>其中 {{ importTargets.filter(book=>book.importStatus==='IMPORTED').length }} 本已入库，将同步为新版本</p></div></div>
        <div><p class="field-label">选择入库方式与附加导出</p><el-checkbox-group v-model="importFormats" class="format-options"><el-checkbox-button v-for="option in importFormatOptions" :key="option.value" :value="option.value"><strong>{{ option.label }}</strong><small>{{ option.description }}</small></el-checkbox-button></el-checkbox-group></div>
        <el-alert v-if="importTargets.some(book=>book.crawlStatus!=='COMPLETED')" type="warning" :closable="false" :title="importTargets.length>1?'未完整采集的书籍只会加入已有正文；没有可用正文的书籍会跳过。':'当前书籍尚未完整采集，所选格式只会包含已有正文。'" />
        <el-alert v-else type="info" :closable="false" :title="importTarget?.importStatus==='IMPORTED'?'结构化版本直接同步章节快照；内容未变化时不会重复创建版本。':'结构化章节可直接阅读，不生成书籍文件；TXT/EPUB 仅在额外勾选时生成。'" />
      </div>
      <template #footer><el-button @click="importDialog=false">取消</el-button><el-button type="primary" :loading="importing" :disabled="!importFormats.length||!importTargets.some(book=>book.crawledChapterCount>0)" @click="submitImport">{{ importTargets.length>1?'确认批量入库':importTarget?.importStatus==='IMPORTED'?'同步所选格式':'确认入库' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="bookListDialog" :title="`预选书单 · ${bookListTarget?.bookName || ''}`" width="min(560px, 94vw)" append-to-body>
      <div v-loading="bookListLoading" class="crawler-book-list-dialog">
        <el-alert type="info" :closable="false" title="现在选择书单；书籍入库时会自动加入。已入库书籍会立即同步书单。" />
        <el-empty v-if="!bookListLoading&&!availableBookLists.length" description="暂无书单，请先在书库中创建书单" />
        <el-checkbox-group v-else v-model="selectedBookListIds" class="crawler-book-list-options">
          <el-checkbox v-for="list in availableBookLists" :key="list.id" :value="list.id" border>
            <strong>{{ list.name }}</strong><small>{{ list.description || '暂无描述' }}</small>
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer><el-button @click="bookListDialog=false">取消</el-button><el-button type="primary" :loading="bookListSaving" @click="saveCrawlerBookLists">保存书单</el-button></template>
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
          <el-progress
            class="task-detail-circle"
            type="circle"
            :percentage="taskProgress(selectedTask)"
            :status="taskCircleStatus(selectedTask.status)"
            :width="72"
            :stroke-width="7"
          />
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
          <el-button v-if="selectedTask.status==='RUNNING'" :icon="VideoPause" title="暂停任务" @click="runTaskCommand(selectedTask,'pause')">暂停</el-button>
          <el-button v-if="['PAUSED','FAILED'].includes(selectedTask.status)" type="primary" :icon="VideoPlay" title="继续任务" @click="runTaskCommand(selectedTask,'resume')">继续</el-button>
          <el-button v-if="['RUNNING','WAITING','PAUSED'].includes(selectedTask.status)" type="danger" plain @click="runTaskCommand(selectedTask,'cancel')">取消</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="queueOverviewDialog" title="网站任务队列" width="min(860px, 94vw)" append-to-body>
      <div class="queue-overview-summary">
        <span><small>队列总数</small><strong>{{ taskQueues.length }}</strong></span>
        <span><small>正在运行</small><strong>{{ totalQueueRunning }} / {{ totalQueueConcurrency }}</strong></span>
        <span><small>等待 / 暂停</small><strong>{{ totalQueueWaiting }} / {{ totalQueuePaused }}</strong></span>
        <span><small>队列总进度</small><strong>{{ totalQueueProgress }}%</strong></span>
      </div>
      <div v-if="taskQueues.length" class="site-queue-list">
        <article v-for="queue in taskQueues" :key="queue.id" class="site-queue-card">
          <header><div><p class="eyebrow">{{ queue.siteName }}</p><strong>{{ queue.runningCount }} / {{ queue.maxConcurrentTasks }} 个任务运行中</strong></div><el-tag effect="plain">{{ queue.progressPercent }}%</el-tag></header>
          <el-progress :percentage="queue.progressPercent" :stroke-width="7" :show-text="false" />
          <div class="site-queue-stats"><span>等待 {{ queue.waitingCount }}</span><span>暂停 {{ queue.pausedCount }}</span><span>间隔 {{ queue.taskIntervalSeconds }} 秒</span></div>
          <footer><el-button text @click="openQueueSettings(queue)">队列配置</el-button><el-button type="primary" plain @click="openQueueDetails(queue)">查看队列任务</el-button></footer>
        </article>
      </div>
      <el-empty v-else description="还没有任务队列，请先添加采集网站" />
      <template #footer><el-button :loading="queuedTasksLoading" @click="loadTaskQueues">刷新队列</el-button><el-button @click="openCreateQueue">手动创建队列</el-button><el-button @click="queueOverviewDialog=false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="queueSettingsDialog" :title="`${queueSettingsTarget?.siteName || '网站'} · 队列配置`" width="min(520px, 94vw)" append-to-body>
      <div class="queue-settings-content">
        <div class="queue-settings-summary"><span><small>正在运行</small><strong>{{ queueSettingsTarget?.runningCount || 0 }}</strong></span><span><small>等待队列</small><strong>{{ queueSettingsTarget?.waitingCount || 0 }}</strong></span></div>
        <el-form label-position="top">
          <el-form-item label="同时运行任务数">
            <el-input-number class="queue-limit-stepper" v-model="queueLimit" :min="1" :max="16" :step="1" step-strictly />
            <small class="field-hint">仅影响此网站的任务队列。调低上限时，超出的运行任务会保留进度并转回等待队列。</small>
          </el-form-item>
          <el-form-item label="任务启动间隔">
            <el-input-number v-model="queueIntervalSeconds" :min="0" :max="3600" :step="1" step-strictly />
            <small class="field-hint">每次启动此队列的新任务后，至少等待指定秒数再启动下一个任务；0 表示不额外等待。</small>
          </el-form-item>
        </el-form>
      </div>
      <template #footer><el-button @click="queueSettingsDialog=false">取消</el-button><el-button type="primary" :loading="savingQueueSettings" @click="saveQueueSettings">保存设置</el-button></template>
    </el-dialog>

    <el-dialog v-model="createQueueDialog" title="手动创建网站队列" width="min(480px, 94vw)" append-to-body>
      <el-form label-position="top"><el-form-item label="采集网站"><el-select v-model="createQueueSiteId" class="queue-site-select" placeholder="选择尚未创建队列的网站"><el-option v-for="site in sitesWithoutQueue" :key="site.id" :label="site.siteName" :value="site.id" /></el-select></el-form-item></el-form>
      <el-empty v-if="!sitesWithoutQueue.length" :image-size="64" description="所有网站都已有队列" />
      <template #footer><el-button @click="createQueueDialog=false">取消</el-button><el-button type="primary" :disabled="!createQueueSiteId" :loading="creatingQueue" @click="createQueue">创建队列</el-button></template>
    </el-dialog>

    <el-dialog v-model="queuedTasksDialog" :title="`${activeQueue?.siteName || '网站'} · 队列任务 · ${queuedTasks.length}`" width="min(1040px, calc(100vw - 32px))" class="queued-tasks-dialog" append-to-body>
      <div v-if="queuedTasks.some(task=>task.status==='WAITING')" class="queued-order-note"><span class="queue-drag-mark">⠿</span><div><strong>运行中任务固定在最前，等待任务可调整顺序</strong><small>拖动等待任务或聚焦排序按钮后使用上下方向键；需要置顶时可点击“优先”。</small></div></div>
      <el-table v-loading="queuedTasksLoading||queuedTasksReordering||!!queuedTaskPrioritizingId" :data="pagedQueuedTasks" row-key="id" max-height="56vh" class="queued-task-table" :row-class-name="queuedTaskRowClassName">
        <el-table-column label="排序" width="62" align="center"><template #default="{row}"><button type="button" class="queue-drag-handle" :class="{dragging:queuedTaskDraggingId===row.id,disabled:row.status!=='WAITING'}" :disabled="row.status!=='WAITING'||queuedTasksReordering||Boolean(queuedTaskPrioritizingId)||Boolean(queuedTaskCommandId)" :draggable="row.status==='WAITING'&&!queuedTasksReordering&&!queuedTaskPrioritizingId&&!queuedTaskCommandId" :aria-label="row.status==='WAITING'?`拖动排序：${row.bookName||row.discoveryPageName||taskTypeLabel(row.type)}`:'当前状态不可排序'" :title="row.status==='WAITING'?'拖动调整顺序；也可使用上下方向键':'只有等待中的任务可以排序'" @dragstart="startQueuedTaskDrag(row,$event)" @dragenter.prevent="moveDraggedQueuedTask(row)" @dragover.prevent @dragend="finishQueuedTaskDrag" @keydown.up.prevent="moveQueuedTaskByKeyboard(row,-1)" @keydown.down.prevent="moveQueuedTaskByKeyboard(row,1)">⠿</button></template></el-table-column>
        <el-table-column type="index" label="#" width="54" :index="index=>index+(queuedTaskPage-1)*queuedTaskPageSize+1" />
        <el-table-column label="任务" min-width="250"><template #default="{row}"><button type="button" class="queue-task-link" @dragenter.prevent="moveDraggedQueuedTask(row)" @click="openQueuedTask(row)"><strong>{{ row.bookName || row.discoveryPageName || taskTypeLabel(row.type) }}</strong></button></template></el-table-column>
        <el-table-column label="任务类型" width="120"><template #default="{row}">{{ taskTypeLabel(row.type) }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="优先级" width="90"><template #default="{row}"><el-tag :type="priorityType(row.priority)" effect="plain">{{ priorityLabel(row.priority) }}</el-tag></template></el-table-column>
        <el-table-column label="创建时间" width="160"><template #default="{row}">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="250" fixed="right" align="right"><template #default="{row}"><div class="queued-task-actions row-hover-action"><el-button size="small" round class="queued-task-action-details" :disabled="Boolean(queuedTaskCommandId)||queuedTasksReordering||Boolean(queuedTaskPrioritizingId)" @click="openQueuedTask(row)">详情</el-button><el-button v-if="['RUNNING','WAITING'].includes(row.status)" size="small" circle :icon="VideoPause" class="queued-task-action-pause" aria-label="暂停任务" title="暂停任务" :loading="queuedTaskCommandId===row.id&&queuedTaskCommand==='pause'" :disabled="Boolean(queuedTaskCommandId&&queuedTaskCommandId!==row.id)||queuedTasksReordering||Boolean(queuedTaskPrioritizingId)" @click="commandCurrentTask(row,'pause')"/><el-button v-if="row.status==='PAUSED'" size="small" circle :icon="VideoPlay" class="queued-task-action-resume" aria-label="继续任务" title="继续任务" :loading="queuedTaskCommandId===row.id&&queuedTaskCommand==='resume'" :disabled="Boolean(queuedTaskCommandId&&queuedTaskCommandId!==row.id)||queuedTasksReordering||Boolean(queuedTaskPrioritizingId)" @click="commandCurrentTask(row,'resume')"/><el-button v-if="row.status==='WAITING'" size="small" round class="queued-task-action-prioritize" :loading="queuedTaskPrioritizingId===row.id" :disabled="queuedTasksReordering||Boolean(queuedTaskCommandId)||Boolean(queuedTaskPrioritizingId&&queuedTaskPrioritizingId!==row.id)" @click="prioritizeQueuedTask(row)">优先</el-button><el-button v-if="['RUNNING','WAITING','PAUSED'].includes(row.status)" size="small" round class="queued-task-action-cancel" :loading="queuedTaskCommandId===row.id&&queuedTaskCommand==='cancel'" :disabled="Boolean(queuedTaskCommandId&&queuedTaskCommandId!==row.id)||queuedTasksReordering||Boolean(queuedTaskPrioritizingId)" @click="commandCurrentTask(row,'cancel')">取消</el-button></div></template></el-table-column>
      </el-table>
      <el-empty v-if="!queuedTasksLoading&&!queuedTasks.length" description="当前没有运行、等待或暂停的任务" />
      <div v-if="queuedTasks.length" class="queued-task-pagination"><span>当前显示 {{ pagedQueuedTasks.length }} 项</span><el-pagination v-model:current-page="queuedTaskPage" v-model:page-size="queuedTaskPageSize" :page-sizes="[10,20,50]" :total="queuedTasks.length" layout="total, sizes, prev, pager, next, jumper" background small @size-change="handleQueuedTaskSizeChange" /></div>
      <template #footer><el-button @click="returnToQueueOverview">返回队列列表</el-button><el-button :loading="queuedTasksLoading" :disabled="queuedTasksReordering" @click="loadQueuedTasks">刷新</el-button><el-button @click="queuedTasksDialog=false">关闭</el-button></template>
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
        <div class="book-summary"><div class="large-cover">{{ selectedBook.bookName.slice(0,1) }}</div><div><h2>{{ selectedBook.bookName }}</h2><p>{{ selectedBook.author || '未知作者' }} · {{ selectedBook.siteName }}</p><div v-if="isBookCompleted(selectedBook)" class="book-crawl-result drawer-result"><div><span>采集结果详情</span><strong>采集完成</strong></div><small>正文 {{ selectedBook.crawledChapterCount }} 章 · 待开放 {{ selectedBook.pendingReleaseChapterCount }} 章 · 失败 {{ selectedBook.failedChapterCount }} 章</small></div><template v-else><el-progress :class="{'crawler-running-progress':isBookRunning(selectedBook)}" :percentage="progress(selectedBook)"/><small>正文 {{ selectedBook.crawledChapterCount }} / {{ selectedBook.chapterCount }} 章，待开放 {{ selectedBook.pendingReleaseChapterCount }}，失败 {{ selectedBook.failedChapterCount }}</small></template></div></div>
        <div class="crawler-book-metadata"><span><small>来源状态</small><strong>{{ selectedBook.bookStatus || '未识别' }}</strong></span><span><small>分类</small><strong>{{ selectedBook.category || '未分类' }}</strong></span><span class="metadata-tag-row"><small>标签</small><span v-if="selectedBook.tags?.length" class="metadata-tags"><el-tag v-for="tag in selectedBook.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag></span><strong v-else>无</strong></span></div>
        <div v-if="selectedBook.libraryBookId" class="library-sync-control"><div><strong>自动同步到书库</strong><p>检查更新或续采成功后，自动发布为同一本书的新版本。</p></div><el-switch :model-value="selectedBook.autoSyncLibrary" @change="toggleLibrarySync(selectedBook,Boolean($event))" /></div>
        <div class="drawer-actions"><el-button size="small" circle :icon="selectedBook.favorite?StarFilled:Star" class="favorite-action" :class="{'is-favorite':selectedBook.favorite}" :aria-label="selectedBook.favorite?'取消收藏':'加入收藏'" :title="selectedBook.favorite?'取消收藏':'加入收藏'" @click="toggleFavorite(selectedBook)"/><el-button @click="openCrawlerBookLists(selectedBook)">加入书单</el-button><el-button :disabled="isBookTaskActive(selectedBook)" @click="continueCrawl(selectedBook)">{{ isBookTaskActive(selectedBook)?'任务中':'继续采集' }}</el-button><el-button :disabled="isBookTaskActive(selectedBook)" @click="refreshBookMetadata(selectedBook)">刷新分类标签</el-button><el-button :disabled="!selectedBook.failedChapterCount" @click="retryFailures(selectedBook)">重试失败</el-button><el-button @click="openStatusEditor(selectedBook)">修改状态</el-button><el-button type="primary" plain :disabled="!selectedBook.crawledChapterCount" @click="startTrial(selectedBook)">临时试读</el-button><el-button type="primary" @click="generate(selectedBook)">生成 TXT + EPUB</el-button><el-button type="primary" plain @click="importBook(selectedBook)">{{ selectedBook.importStatus==='IMPORTED'?'立即同步书库':'入库' }}</el-button></div>
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
    <el-dialog v-model="chapterDialog" width="min(760px, 94vw)" class="chapter-reader-dialog" :show-close="false" append-to-body destroy-on-close @closed="closeChapterReader">
      <div class="chapter-reader" :class="`chapter-reader--${chapterReaderTheme}`">
        <header class="chapter-reader-toolbar">
          <div class="chapter-reader-heading">
            <strong>{{ chapterDetail?.title || chapterReaderActive?.chapterName || '章节正文' }}</strong>
            <p><span>临时阅读</span> · {{ selectedBook?.bookName || '采集书籍' }}<template v-if="chapterReaderPosition"> · {{ chapterReaderPosition }} / {{ chapterReaderChapters.length }}</template></p>
          </div>
          <div class="chapter-reader-actions">
            <a v-if="chapterDetail?.url" :href="chapterDetail.url" target="_blank" rel="noopener noreferrer">原始网页 ↗</a>
            <button type="button" :aria-expanded="chapterReaderSettingsOpen" aria-controls="chapter-reader-settings" @click="chapterReaderSettingsOpen=!chapterReaderSettingsOpen">Aa <span>阅读设置</span></button>
            <button class="chapter-reader-close" type="button" aria-label="关闭章节阅读" title="关闭" @click="chapterDialog=false">×</button>
          </div>
        </header>
        <div class="chapter-reader-stage">
          <section ref="chapterReaderSurface" class="chapter-reader-surface" :style="chapterReaderArticleStyle">
            <div v-if="chapterReaderLoading" class="chapter-reader-state" role="status"><i/><p>正在展开书页…</p></div>
            <article v-else>
              <p class="chapter-reader-kicker">{{ selectedBook?.author || '未知作者' }} · {{ selectedBook?.siteName }}</p>
              <div class="chapter-reader-rule"><span>◆</span></div>
              <p v-for="(paragraph,index) in chapterReaderParagraphs" :key="index" class="chapter-reader-paragraph">{{ paragraph }}</p>
              <p v-if="!chapterReaderParagraphs.length" class="chapter-reader-empty">{{ chapterDetail?.errorMessage || '本章暂无可阅读正文' }}</p>
            </article>
          </section>
          <Transition name="chapter-settings-slide">
            <aside v-if="chapterReaderSettingsOpen" id="chapter-reader-settings" class="chapter-reader-settings" aria-label="章节阅读设置">
              <div class="chapter-reader-settings-heading"><div><small>READING ROOM</small><h3>阅读设置</h3></div><button type="button" aria-label="关闭阅读设置" @click="chapterReaderSettingsOpen=false">×</button></div>
              <div class="chapter-reader-setting"><label>阅读主题</label><div class="chapter-reader-theme" role="radiogroup" aria-label="阅读主题"><span :style="{transform:`translateX(${chapterReaderThemeIndex*100}%)`}"/><button v-for="item in chapterReaderThemes" :key="item.value" type="button" role="radio" :aria-checked="chapterReaderTheme===item.value" :class="{active:chapterReaderTheme===item.value}" @click="setChapterReaderTheme(item.value)">{{ item.label }}</button></div></div>
              <div class="chapter-reader-setting"><label for="chapter-reader-font-size">字号 <b>{{ readerSettings.fontSize }}px</b></label><input id="chapter-reader-font-size" :value="readerSettings.fontSize" type="range" min="14" max="30" @input="setChapterReaderNumber('fontSize',$event)" /></div>
              <div class="chapter-reader-setting"><label for="chapter-reader-line-height">行距 <b>{{ readerSettings.lineHeight }}</b></label><input id="chapter-reader-line-height" :value="readerSettings.lineHeight" type="range" min="1.4" max="2.5" step="0.1" @input="setChapterReaderNumber('lineHeight',$event)" /></div>
              <div class="chapter-reader-setting"><label>版心宽度</label><div class="chapter-reader-widths"><button v-for="item in chapterReaderWidths" :key="item.value" type="button" :class="{active:readerSettings.contentWidth===item.value}" @click="setChapterReaderWidth(item.value)">{{ item.label }}</button></div></div>
              <p>阅读设置会保存到当前账户，并同步用于网页阅读器。</p>
            </aside>
          </Transition>
        </div>
        <footer class="chapter-reader-navigation">
          <button type="button" :disabled="chapterReaderPosition<=1||chapterReaderLoading" @click="moveChapterReader(-1)"><span>←</span><small>上一章</small></button>
          <div><span class="chapter-reader-progress"><i :style="{width:`${chapterReaderProgress}%`}"/></span><small>{{ chapterReaderPosition ? `第 ${chapterReaderPosition} 章` : '章节预览' }}</small></div>
          <button type="button" :disabled="!chapterReaderPosition||chapterReaderPosition>=chapterReaderChapters.length||chapterReaderLoading" @click="moveChapterReader(1)"><small>下一章</small><span>→</span></button>
        </footer>
      </div>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { Collection, Connection, DataAnalysis, Document, Download, Edit, Link, List, MoreFilled, Plus, Refresh, Search, Star, StarFilled, Tickets, TrendCharts, Upload, VideoPause, VideoPlay, Warning } from '@element-plus/icons-vue'
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElProgress, ElTable, ElTableColumn, ElTag, ElTooltip, type FormInstance, type FormItemRule, type FormRules } from 'element-plus'
import { crawlerApi, type CrawlerBook, type CrawlerChapter, type CrawlerDashboard, type CrawlerDashboardStatistics, type CrawlerDiscoveryPage, type CrawlerDiscoveryPagePayload, type CrawlerLog, type CrawlerRule, type CrawlerRuleExport, type CrawlerRuleTest, type CrawlerRuleVersion, type CrawlerScanResult, type CrawlerSite, type CrawlerSiteConfiguration, type CrawlerSitePayload, type CrawlerTask, type CrawlerTaskQueue } from '@/utils/crawler'
import {
  CRAWLER_POLLING_INTERVAL_OPTIONS,
  usePreferencesStore,
  type CrawlerPollingIntervalSeconds,
  type ReaderContentWidth,
  type ReaderSettings,
} from '@/stores/preferences'
import { getCoverUrl } from '@/utils/cover'
import { shouldLoadBookCover } from '@/utils/imagePrivacy'
import { confirm, message } from '@/utils/message'
import api from '@/utils/api'

interface BookListOption { id:number; name:string; description?:string }

type TaskMoreCommand='scan-results'|'book-lists'|'edit'|'resume'|'delete'
type TaskMoreAction={command:TaskMoreCommand;label:string;danger?:boolean;divided?:boolean}

const TaskTable = defineComponent({ props:{ tasks:{type:Array as ()=>CrawlerTask[],required:true},selectable:{type:Boolean,default:false},showFailureReason:{type:Boolean,default:false},prioritizingTaskId:{type:String,default:''},batchManaging:{type:Boolean,default:false}}, emits:['open','command','edit','delete','scan-results','selection-change','toggle-favorite','book-lists','prioritize'], setup(props,{emit}) {
  const moreActions=(row:CrawlerTask):TaskMoreAction[]=>[
    row.type==='SITE_SCAN'?{command:'scan-results',label:'扫描结果'}:null,
    row.bookId?{command:'book-lists',label:'加入书单'}:null,
    row.status==='FAILED'?{command:'resume',label:'继续'}:null,
    ['WAITING','PAUSED','FAILED'].includes(row.status)?{command:'edit',label:'修改'}:null,
    row.status!=='RUNNING'?{command:'delete',label:'删除',danger:true,divided:true}:null,
  ].filter((action):action is TaskMoreAction=>Boolean(action))
  const handleMoreCommand=(row:CrawlerTask,command:TaskMoreCommand)=>{
    if(command==='resume')emit('command',row,'resume')
    else emit(command,row)
  }
  return () => h(ElTable,{data:props.tasks,rowKey:'id',class:'data-table',onSelectionChange:(rows:CrawlerTask[])=>emit('selection-change',rows)},()=>[
  props.selectable?h(ElTableColumn,{type:'selection',width:48,reserveSelection:true,fixed:'left'}):null,
  h(ElTableColumn,{label:'收藏',width:58,fixed:'left',align:'center'},{default:({row}:{row:CrawlerTask})=>row.bookId?h(ElButton,{size:'small',circle:true,icon:row.favorite?StarFilled:Star,class:['favorite-action','row-hover-action',row.favorite?'is-favorite':''],'aria-label':row.favorite?'取消收藏':'加入收藏',title:row.favorite?'取消收藏':'加入收藏',onClick:(event:MouseEvent)=>{event.stopPropagation();emit('toggle-favorite',row)}}):null}),
  h(ElTableColumn,{label:'任务',minWidth:240,fixed:'left'},{default:({row}:{row:CrawlerTask})=>h('div',{class:'task-open',role:'button',tabindex:0,onClick:()=>emit('open',row),onKeydown:(event:KeyboardEvent)=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();emit('open',row)}}},[row.siteName?h('span',{class:'task-site-mark',title:row.siteName,'aria-label':`来源网站：${row.siteName}`},row.siteName.slice(0,1)):null,h('strong',{title:row.bookName||row.discoveryPageName||taskTypeLabel(row.type)},row.bookName||row.discoveryPageName||taskTypeLabel(row.type))])}),
  h(ElTableColumn,{label:'任务类型',width:120},{default:({row}:{row:CrawlerTask})=>taskTypeLabel(row.type)}),
  h(ElTableColumn,{label:'进度 / 扫描结果',minWidth:300},{default:({row}:{row:CrawlerTask})=>row.status==='SUCCESS'&&row.type==='SITE_SCAN'?h('div',{class:'task-scan-progress task-scan-completed'},[h('div',{class:'task-success-progress'},[h('strong','✓ 已完成'),h('span',`扫描 ${row.scannedPageCount} 页，发现 ${row.totalCount} 本`)]),h('div',{class:'task-scan-summary'},[h('i',`新增 ${row.newBookCount}`),h('em',`重复 ${row.duplicateCount}`),row.failedCount?h('strong',`失败 ${row.failedCount}`):null])]):row.status==='SUCCESS'?h('div',{class:'task-success-progress'},[h('strong','✓ 已完成'),h('span',`成功处理 ${row.successCount} 项`)]):row.type==='SITE_SCAN'?h('div',{class:'task-scan-progress'},[h(ElProgress,{class:row.status==='RUNNING'?'crawler-running-progress':undefined,percentage:scanTaskPercentage(row),strokeWidth:7}),h('small',scanTaskProgressText(row)),h('div',{class:'task-scan-summary'},[`扫描到 ${row.totalCount}`,h('b',`成功 ${row.successCount}`),h('i',`新增 ${row.newBookCount}`),h('em',`重复 ${row.duplicateCount}`),row.failedCount?h('strong',`失败 ${row.failedCount}`):null])]):h(ElProgress,{class:row.status==='RUNNING'?'crawler-running-progress':undefined,percentage:row.totalCount?Math.round(row.successCount/row.totalCount*100):0,strokeWidth:7})}),
  h(ElTableColumn,{label:'状态',width:130},{default:({row}:{row:CrawlerTask})=>h(ElTag,{type:statusType(row.status)},()=>statusLabel(row.status))}),
  props.showFailureReason?h(ElTableColumn,{label:'失败原因',minWidth:280},{default:({row}:{row:CrawlerTask})=>{
    const reason=row.errorMessage?.trim()||'任务执行失败，未记录详细原因'
    return h(ElTooltip,{content:reason,placement:'top',showAfter:250,popperClass:'task-failure-tooltip'},()=>
      h('div',{class:'task-failure-reason',tabindex:0,'aria-label':`失败原因：${reason}`},[
        h(Warning,{class:'task-failure-reason-icon'}),
        h('span',reason),
      ]))
  }}):null,
  h(ElTableColumn,{label:'优先级',width:90},{default:({row}:{row:CrawlerTask})=>h(ElTag,{type:priorityType(row.priority),effect:'plain'},()=>priorityLabel(row.priority))}),
  h(ElTableColumn,{label:'当前章节',prop:'currentChapter',minWidth:150}),
  h(ElTableColumn,{label:'创建时间',width:170},{default:({row}:{row:CrawlerTask})=>formatTime(row.createdAt)}),
  h(ElTableColumn,{label:'完成时间',width:170},{default:({row}:{row:CrawlerTask})=>formatTime(row.finishedAt)}),
  h(ElTableColumn,{label:'操作',width:340,fixed:'right',align:'right'},{default:({row}:{row:CrawlerTask})=>{
    const actions=moreActions(row)
    return h('div',{class:'task-action-cluster row-hover-action'},[
      h(ElButton,{size:'small',round:true,class:'task-action-button task-action-details',onClick:()=>emit('open',row)},()=> '详情'),
      ['RUNNING','WAITING'].includes(row.status)?h(ElButton,{size:'small',circle:true,icon:VideoPause,class:'task-action-button task-action-pause','aria-label':'暂停任务',title:'暂停任务',onClick:()=>emit('command',row,'pause')}):null,
      row.status==='WAITING'?h(ElButton,{size:'small',round:true,class:'task-action-button task-action-prioritize',loading:props.prioritizingTaskId===row.id,disabled:props.batchManaging||Boolean(props.prioritizingTaskId&&props.prioritizingTaskId!==row.id),onClick:()=>emit('prioritize',row)},()=> '优先'):null,
      row.status==='PAUSED'?h(ElButton,{size:'small',circle:true,icon:VideoPlay,class:'task-action-button task-action-resume','aria-label':'继续任务',title:'继续任务',onClick:()=>emit('command',row,'resume')}):null,
      ['RUNNING','WAITING','PAUSED'].includes(row.status)?h(ElButton,{size:'small',round:true,class:'task-action-button task-action-cancel',onClick:()=>emit('command',row,'cancel')},()=> '取消'):null,
      actions.length?h(ElDropdown,{trigger:'click',placement:'bottom-end',onCommand:(command:TaskMoreCommand)=>handleMoreCommand(row,command)},{
        default:()=>h(ElButton,{size:'small',circle:true,icon:MoreFilled,class:'task-action-more','aria-label':'更多操作',title:'更多操作'}),
        dropdown:()=>h(ElDropdownMenu,{},()=>actions.map(action=>h(ElDropdownItem,{command:action.command,divided:action.divided,class:action.danger?'danger-dropdown-item':undefined},()=>action.label))),
      }):null,
    ])
  }})
]) }})

type TabKey='overview'|'statistics'|'sites'|'discovered'|'books'|'tasks'|'failed'
type ChapterSort='indexAsc'|'indexDesc'|'createdDesc'
type BookDetailTab='chapters'|'logs'
type SiteEditorTab='basic'|'request'|'validation'|'proxy'|'automation'
type DiscoveryViewMode='table'|'card'
type LoadOptions={silent?:boolean;preserveSelection?:boolean}
type ChapterReaderTheme='paper'|'light'|'night'
type StatisticsDays=7|30|90
const router=useRouter()
const preferencesStore=usePreferencesStore()
const {crawlerFollowCurrentChapter:followCurrentChapter,crawlerChapterPageSize:chapterPageSize,crawlerDiscoveryViewMode:discoveryViewMode,crawlerBookViewMode:bookViewMode,crawlerPollingIntervalSeconds:pollingIntervalSeconds,readerSettings}=storeToRefs(preferencesStore)
const pollingIntervalOptions=CRAWLER_POLLING_INTERVAL_OPTIONS
const activeTab=ref<TabKey>('overview'), dashboard=ref<CrawlerDashboard>(), sites=ref<CrawlerSite[]>([]), books=ref<CrawlerBook[]>([]), discoveredBooks=ref<CrawlerBook[]>([]), tasks=ref<CrawlerTask[]>([]), failedTasks=ref<CrawlerTask[]>([])
const statistics=ref<CrawlerDashboardStatistics>(), statisticsLoading=ref(false), statisticsDays=ref<StatisticsDays>(30)
const chapterTrendChartRef=ref<HTMLElement>(), taskCountChartRef=ref<HTMLElement>(), bookTrendChartRef=ref<HTMLElement>(), successfulBooksChartRef=ref<HTMLElement>(), successRateChartRef=ref<HTMLElement>(), siteContributionChartRef=ref<HTMLElement>(), funnelChartRef=ref<HTMLElement>()
const currentCrawlerTasks=ref<CrawlerTask[]>([]), submittingBookTaskIds=ref(new Set<number>())
const siteDialog=ref(false), discoveryManagerDialog=ref(false), discoveryPageDialog=ref(false), crawlDialog=ref(false), bookDrawer=ref(false), taskDrawer=ref(false), chapterDialog=ref(false), ruleTestDialog=ref(false), ruleManagerDialog=ref(false), ruleEditorDialog=ref(false), ruleImportDialog=ref(false), siteConfigurationImportDialog=ref(false), statusDialog=ref(false), taskEditDialog=ref(false), saving=ref(false), savingSiteConfiguration=ref(false), savingStatus=ref(false), savingTask=ref(false), testingRule=ref(false), discoveryLoading=ref(false), taskDetailLoading=ref(false), editingSite=ref<CrawlerSite>(), discoveryManagerSite=ref<CrawlerSite>(), discoveryPageSite=ref<CrawlerSite>(), editingDiscoveryPage=ref<CrawlerDiscoveryPage>(), selectedBook=ref<CrawlerBook>(), selectedTask=ref<CrawlerTask>(), statusBook=ref<CrawlerBook>(), editingTask=ref<CrawlerTask>(), selectedDiscoveries=ref<CrawlerBook[]>([]), selectedBooks=ref<CrawlerBook[]>([]), batchBookStatus=ref('COMPLETED'), batchBookStatusSaving=ref(false), batchBookAction=ref<'continue'|'updates'|'metadata'>(), chapters=ref<CrawlerChapter[]>([]), crawlerLogs=ref<CrawlerLog[]>([]), chapterDetail=ref<{title:string;url:string;content:string;errorMessage:string}>(), bookKeyword=ref(''), manualStatus=ref('COMPLETED'), taskPriority=ref<'LOW'|'NORMAL'|'HIGH'>('NORMAL'), discoveryPage=ref(1), discoveryPageSize=ref(20), discoveredTotal=ref(0), discoveryKeyword=ref(''), discoverySiteId=ref<number>(), discoverySort=ref('DISCOVER_TIME_DESC')
const chapterReaderSurface=ref<HTMLElement>(), chapterReaderActive=ref<CrawlerChapter>(), chapterReaderChapters=ref<CrawlerChapter[]>([]), chapterReaderBookId=ref<number>(), chapterReaderLoading=ref(false), chapterReaderSettingsOpen=ref(false)
const discoveryPagesBySite=ref<Record<number,CrawlerDiscoveryPage[]>>({})
const discoveryMetadataRefreshing=ref(false)
const bookLoading=ref(false), bookPage=ref(1), bookPageSize=ref(20), bookTotal=ref(0)
const bookTableRef=ref<InstanceType<typeof ElTable>>()
const bookSiteId=ref<number>(), bookCrawlStatus=ref(''), bookImportStatus=ref(''), bookFavoriteOnly=ref(false), bookSort=ref('CREATED_DESC')
const chapterLoading=ref(false), chapterPage=ref(1), chapterTotal=ref(0)
const currentCrawlingChapter=ref<CrawlerChapter>()
const taskLoading=ref(false), taskPage=ref(1), taskPageSize=ref(20), taskTotal=ref(0)
const taskStatusFilter=ref(''), taskTypeFilter=ref(''), taskFavoriteOnly=ref(false)
const selectedTasks=ref<CrawlerTask[]>([]), selectedFailedTasks=ref<CrawlerTask[]>([]), taskTableSelectionVersion=ref(0), batchTaskManaging=ref(false), batchTaskAction=ref<'pause'|'resume'|'cancel'|'delete'|'priority'|'resume-all'>()
const scanResultsDialog=ref(false), scanResultsLoading=ref(false), scanResultsTask=ref<CrawlerTask>(), scanResults=ref<CrawlerScanResult[]>([]), scanResultsPage=ref(1), scanResultsPageSize=ref(50), scanResultsTotal=ref(0)
const queueOverviewDialog=ref(false), queueSettingsDialog=ref(false), createQueueDialog=ref(false), savingQueueSettings=ref(false), creatingQueue=ref(false), taskQueues=ref<CrawlerTaskQueue[]>([]), activeQueue=ref<CrawlerTaskQueue>(), queueSettingsTarget=ref<CrawlerTaskQueue>(), queueLimit=ref(4), queueIntervalSeconds=ref(0), createQueueSiteId=ref<number>(), queuedTasksDialog=ref(false), queuedTasksLoading=ref(false), queuedTasksReordering=ref(false), queuedTaskPrioritizingId=ref<string>(), queuedTaskCommandId=ref<string>(), queuedTaskCommand=ref<'pause'|'resume'|'cancel'>(), queuedTaskDraggingId=ref<string>(), queuedTaskDragStartOrder=ref<string[]>([]), queuedTasks=ref<CrawlerTask[]>([]), queuedTaskPage=ref(1), queuedTaskPageSize=ref(10)
const totalQueueRunning=computed(()=>taskQueues.value.reduce((total,queue)=>total+queue.runningCount,0))
const totalQueueConcurrency=computed(()=>taskQueues.value.reduce((total,queue)=>total+queue.maxConcurrentTasks,0))
const totalQueueWaiting=computed(()=>taskQueues.value.reduce((total,queue)=>total+queue.waitingCount,0))
const totalQueuePaused=computed(()=>taskQueues.value.reduce((total,queue)=>total+queue.pausedCount,0))
const totalQueueProgress=computed(()=>{
  const taskCount=taskQueues.value.reduce((total,queue)=>total+queue.activeTaskCount,0)
  if(!taskCount)return 0
  const weightedProgress=taskQueues.value.reduce((total,queue)=>total+queue.progressPercent*queue.activeTaskCount,0)
  return Math.round(weightedProgress/taskCount)
})
const sitesWithoutQueue=computed(()=>sites.value.filter(site=>!taskQueues.value.some(queue=>queue.siteId===site.id)))
const failedTaskLoading=ref(false), failedTaskPage=ref(1), failedTaskPageSize=ref(20), failedTaskTotal=ref(0)
const ruleTestSite=ref<CrawlerSite>(), ruleTestDraft=ref<CrawlerRule>(), ruleSite=ref<CrawlerSite>(), editingRule=ref<CrawlerRuleVersion>(), ruleTestUrl=ref(''), ruleTestResult=ref<CrawlerRuleTest>(), ruleVersions=ref<CrawlerRuleVersion[]>([]), importInput=ref<HTMLInputElement>(), importMode=ref<'text'|'file'>('text'), importJsonText=ref(''), importFileName=ref('')
const siteConfigurationImportInput=ref<HTMLInputElement>(), siteConfigurationImportMode=ref<'text'|'file'>('text'), siteConfigurationJsonText=ref(''), siteConfigurationImportFileName=ref('')
const importDialog=ref(false), importTargets=ref<CrawlerBook[]>([]), importFormats=ref<string[]>(['STRUCTURED']), importing=ref(false)
const discoveryFavoriteOnly=ref(false)
const bookListDialog=ref(false), bookListLoading=ref(false), bookListSaving=ref(false), bookListTarget=ref<CrawlerBook>(), availableBookLists=ref<BookListOption[]>([]), selectedBookListIds=ref<number[]>([])
const importTarget=computed(()=>importTargets.value.length===1?importTargets.value[0]:undefined)
const batchBookBusy=computed(()=>Boolean(batchBookAction.value)||batchBookStatusSaving.value||importing.value)
const importFormatOptions=[{value:'STRUCTURED',label:'结构化章节',description:'推荐：无需生成文件即可入库阅读'},{value:'EPUB',label:'附加 EPUB',description:'供下载与第三方阅读器使用'},{value:'TXT',label:'附加 TXT',description:'通用纯文本备份'}]
const crawlForm=reactive<{siteId?:number;url:string}>({url:''})
const discoveryPageForm=reactive<CrawlerDiscoveryPagePayload>({pageName:'',pageUrl:'',autoScanEnabled:false,scanIntervalMinutes:360,maxPages:50})
const emptyRule=():CrawlerRule=>({discoveryItemSelector:'',discoveryUrlSelector:'a',discoveryTitleSelector:'.title',discoveryAuthorSelector:'',discoveryCoverSelector:'',discoveryCategorySelector:'',discoveryLatestChapterSelector:'',discoveryNextPageSelector:'',titleSelector:'',authorSelector:'',descriptionSelector:'',categorySelector:'',tagsSelector:'',statusSelector:'',chapterListUrlSelector:'',chapterItemSelector:'',chapterTitleSelector:':scope',chapterUrlSelector:'a',contentTitleSelector:'h1',contentSelector:'',removeSelectors:'',xpathRemoveSelectors:'',stringReplacementsJson:'',regexReplacementsJson:'',removeBlankLines:true,saveOriginalHtml:false,minChapterLength:100})
const emptySite=():CrawlerSitePayload=>({siteName:'',siteCode:'',baseUrl:'',homeUrl:'',enabled:false,autoScan:false,autoCrawl:false,autoUpdate:false,autoImportLibrary:false,scanIntervalMinutes:360,updateIntervalMinutes:30,maxDiscoveryPages:3,autoImportFormat:'EPUB',requestIntervalMillis:1500,randomDelayMillis:1000,maxConcurrency:1,respectRobotsTxt:true,encoding:'UTF-8',proxies:[],contentMarkers:[]})
const siteConfigurationTemplate:CrawlerSiteConfiguration={
  schemaVersion:1,
  type:'AIBOOK_CRAWLER_SITE',
  site:{siteName:'示例小说站',siteCode:'example_novel',baseUrl:'https://www.example.com',homeUrl:'https://www.example.com',enabled:false,autoScan:false,autoCrawl:false,autoUpdate:false,autoImportLibrary:false,requestIntervalMillis:1500,randomDelayMillis:1000,maxConcurrency:1,respectRobotsTxt:true,encoding:'UTF-8',proxies:[{name:'备用代理',url:'http://127.0.0.1:7890',enabled:false}],scanIntervalMinutes:360,updateIntervalMinutes:30,maxDiscoveryPages:3,autoImportFormat:'EPUB',contentMarkers:[{marker:'以下内容为VIP专属，升级会员即可继续阅读',status:'PENDING_RELEASE'},{marker:'Access Denied',status:'FAILED'}]},
  rules:[{version:1,changeSummary:'初始规则',enabled:true,rule:{discoveryItemSelector:'.book-list .book',discoveryUrlSelector:'a.book-link',discoveryTitleSelector:'.title',discoveryAuthorSelector:'.author',discoveryCoverSelector:'img.cover::data-src',discoveryCategorySelector:'.category',discoveryLatestChapterSelector:'.latest',discoveryNextPageSelector:'a.next',titleSelector:'h1.book-title',authorSelector:'.author',coverSelector:'img.cover::data-src',descriptionSelector:'#intro',categorySelector:'.book-meta .category',tagsSelector:'.book-meta .tags a',statusSelector:'.book-meta .status',latestChapterSelector:'.latest',chapterListUrlSelector:'a.catalog',chapterItemSelector:'#chapter-list a',chapterTitleSelector:':scope',chapterUrlSelector:':scope',contentTitleSelector:'h1',contentSelector:'#content',removeSelectors:'.ads, .navigation',xpathRemoveSelectors:'',stringReplacementsJson:'',regexReplacementsJson:'',removeBlankLines:true,saveOriginalHtml:false,minChapterLength:100}}],
  discoveryPages:[{pageName:'热门小说',pageUrl:'https://www.example.com/rank/hot',autoScanEnabled:false,scanIntervalMinutes:360,maxPages:50}],
}
const siteForm=reactive<CrawlerSitePayload>(emptySite())
const siteFormRef=ref<FormInstance>()
const siteFormRules:FormRules={
  siteName:[{required:true,whitespace:true,message:'请输入网站名称',trigger:['blur','change']}],
  baseUrl:[{required:true,whitespace:true,message:'请输入网站根地址',trigger:['blur','change']}],
}
const proxyNameRules:FormItemRule[]=[{required:true,whitespace:true,message:'请输入代理名称',trigger:['blur','change']}]
const proxyUrlRules:FormItemRule[]=[{required:true,whitespace:true,message:'请输入代理地址',trigger:['blur','change']}]
const contentMarkerRules:FormItemRule[]=[{required:true,whitespace:true,message:'请输入正文特征',trigger:['blur','change']},{max:500,message:'单条特征不能超过 500 个字符',trigger:['blur','change']}]
const ruleForm=reactive<{version:number;changeSummary:string;enabled:boolean;rule:CrawlerRule}>({version:1,changeSummary:'',enabled:true,rule:emptyRule()})
const manualStatusOptions=[{value:'COMPLETED',label:'已完成'},{value:'PARTIAL_SUCCESS',label:'部分成功'},{value:'FAILED',label:'失败'},{value:'PAUSED',label:'已暂停'},{value:'WAITING',label:'等待中'},{value:'DISCOVERED',label:'已发现'}]
const chapterSort=ref<ChapterSort>('indexAsc')
const bookDetailTab=ref<BookDetailTab>('chapters')
const bookDetailTabs:{value:BookDetailTab;label:string}[]=[{value:'chapters',label:'章节进度'},{value:'logs',label:'实时日志'}]
const chapterSortOptions:{value:ChapterSort;label:string}[]=[{value:'indexAsc',label:'章节正序'},{value:'indexDesc',label:'章节倒序'},{value:'createdDesc',label:'添加时间'}]
const chapterReaderThemes:{value:ChapterReaderTheme;label:string}[]=[{value:'paper',label:'宣纸'},{value:'light',label:'明亮'},{value:'night',label:'夜读'}]
const chapterReaderWidths:{value:ReaderContentWidth;label:string}[]=[{value:'narrow',label:'窄'},{value:'medium',label:'适中'},{value:'wide',label:'宽'}]
const taskPriorityOptions:{value:'LOW'|'NORMAL'|'HIGH';label:string}[]=[{value:'LOW',label:'低'},{value:'NORMAL',label:'普通'},{value:'HIGH',label:'高'}]
const taskStatusOptions=['WAITING','RUNNING','PAUSED','SUCCESS','PARTIAL_SUCCESS','FAILED','CANCELLED']
const taskTypeOptions=['SITE_SCAN','BOOK_METADATA','BOOK_CHAPTER_LIST','BOOK_CONTENT','BOOK_UPDATE_CHECK','BOOK_FULL_CRAWL','BOOK_EXPORT','BOOK_IMPORT']
const discoverySortOptions=[{value:'DISCOVER_TIME_DESC',label:'发现时间：最新优先'},{value:'DISCOVER_TIME_ASC',label:'发现时间：最早优先'},{value:'BOOK_NAME_ASC',label:'书名：正序'},{value:'BOOK_NAME_DESC',label:'书名：倒序'},{value:'AUTHOR_ASC',label:'作者：正序'},{value:'AUTHOR_DESC',label:'作者：倒序'}]
const managedBookStatusOptions=[{value:'WAITING',label:'等待中'},{value:'CRAWLING_METADATA',label:'采集元数据'},{value:'CRAWLING_CHAPTER_LIST',label:'采集目录'},{value:'CRAWLING_CONTENT',label:'采集正文'},{value:'PARTIAL_SUCCESS',label:'部分成功'},{value:'COMPLETED',label:'已完成'},{value:'FAILED',label:'失败'},{value:'UPDATING',label:'更新中'},{value:'PAUSED',label:'已暂停'}]
const bookImportStatusOptions=[{value:'NOT_IMPORTED',label:'未入库'},{value:'READY',label:'待入库'},{value:'IMPORTED',label:'已入库'}]
const managedBookSortOptions=[{value:'CREATED_DESC',label:'创建时间：最新优先'},{value:'CREATED_ASC',label:'创建时间：最早优先'},{value:'CRAWL_STARTED_DESC',label:'开始爬取：最新优先'},{value:'CRAWL_STARTED_ASC',label:'开始爬取：最早优先'},{value:'LAST_CRAWL_DESC',label:'完成爬取：最新优先'},{value:'LAST_CRAWL_ASC',label:'完成爬取：最早优先'},{value:'BOOK_NAME_ASC',label:'书名：正序'},{value:'BOOK_NAME_DESC',label:'书名：倒序'}]
const siteEditorTabs:{key:SiteEditorTab;label:string;description:string}[]=[{key:'basic',label:'基本信息',description:'身份与入口'},{key:'request',label:'请求访问',description:'频率与请求头'},{key:'validation',label:'内容校验',description:'正文特征'},{key:'proxy',label:'代理配置',description:'站点代理'},{key:'automation',label:'运行设置',description:'任务范围'}]
const activeSiteTab=ref<SiteEditorTab>('basic')
const tabs=computed(()=>[{key:'overview' as const,label:'采集概览',icon:DataAnalysis,count:0},{key:'statistics' as const,label:'采集统计',icon:TrendCharts,count:0},{key:'sites' as const,label:'采集网站',icon:Connection,count:sites.value.length},{key:'discovered' as const,label:'发现书籍',icon:Tickets,count:discoveredTotal.value},{key:'books' as const,label:'采集书籍',icon:Collection,count:bookTotal.value},{key:'tasks' as const,label:'采集任务',icon:List,count:taskTotal.value},{key:'failed' as const,label:'失败任务',icon:Warning,count:failedTaskTotal.value}])
const activeIndex=computed(()=>tabs.value.findIndex(t=>t.key===activeTab.value))
const selectedTaskRows=computed(()=>activeTab.value==='failed'?selectedFailedTasks.value:selectedTasks.value)
const canBatchPause=computed(()=>selectedTaskRows.value.length>0&&selectedTaskRows.value.every(task=>['RUNNING','WAITING'].includes(task.status)))
const canBatchResume=computed(()=>selectedTaskRows.value.length>0&&selectedTaskRows.value.every(task=>['PAUSED','FAILED'].includes(task.status)))
const canBatchCancel=computed(()=>selectedTaskRows.value.length>0&&selectedTaskRows.value.every(task=>['RUNNING','WAITING','PAUSED'].includes(task.status)))
const canBatchDelete=computed(()=>selectedTaskRows.value.length>0&&selectedTaskRows.value.every(task=>task.status!=='RUNNING'))
const canBatchSetPriority=computed(()=>selectedTaskRows.value.length>0&&selectedTaskRows.value.every(task=>['WAITING','PAUSED','FAILED'].includes(task.status)))
const selectedDiscoveryIds=computed(()=>new Set(selectedDiscoveries.value.map(book=>book.id)))
const selectedBookIds=computed(()=>new Set(selectedBooks.value.map(book=>book.id)))
const allBooksSelected=computed(()=>books.value.length>0&&books.value.every(book=>selectedBookIds.value.has(book.id)))
const someBooksSelected=computed(()=>books.value.some(book=>selectedBookIds.value.has(book.id)))
const allDiscoveredSelected=computed(()=>discoveredBooks.value.length>0&&discoveredBooks.value.every(book=>selectedDiscoveryIds.value.has(book.id)))
const someDiscoveredSelected=computed(()=>discoveredBooks.value.some(book=>selectedDiscoveryIds.value.has(book.id)))
const managedDiscoveryPages=computed(()=>discoveryManagerSite.value?discoveryPagesBySite.value[discoveryManagerSite.value.id]||[]:[])
const chapterSortIndex=computed(()=>chapterSortOptions.findIndex(item=>item.value===chapterSort.value))
const chapterReaderPosition=computed(()=>chapterReaderChapters.value.findIndex(item=>item.id===chapterReaderActive.value?.id)+1)
const chapterReaderProgress=computed(()=>chapterReaderPosition.value&&chapterReaderChapters.value.length?chapterReaderPosition.value/chapterReaderChapters.value.length*100:0)
const chapterReaderParagraphs=computed(()=>{const text=(chapterDetail.value?.content||'').trim();if(!text)return[];const blocks=text.split(/\r?\n\s*\r?\n+/).map(value=>value.trim()).filter(Boolean);return blocks.length>1?blocks:text.split(/\r?\n/).map(value=>value.trim()).filter(Boolean)})
const chapterReaderTheme=computed<ChapterReaderTheme>(()=>readerSettings.value.backgroundColor==='#2d2d2d'||readerSettings.value.backgroundColor==='#1a1a2e'?'night':readerSettings.value.backgroundColor==='#ffffff'?'light':'paper')
const chapterReaderThemeIndex=computed(()=>chapterReaderThemes.findIndex(item=>item.value===chapterReaderTheme.value))
const chapterReaderContentWidth=computed(()=>({narrow:560,medium:640,wide:700,wider:720,full:740} as Record<ReaderContentWidth,number>)[readerSettings.value.contentWidth])
const chapterReaderArticleStyle=computed(()=>({'--chapter-reader-font-size':`${readerSettings.value.fontSize}px`,'--chapter-reader-line-height':String(readerSettings.value.lineHeight),'--chapter-reader-content-width':`${chapterReaderContentWidth.value}px`,'--chapter-reader-font-family':readerSettings.value.fontFamily==='default'?"'Iowan Old Style','Songti SC','STSong',serif":readerSettings.value.fontFamily}))
const bookDetailTabIndex=computed(()=>bookDetailTabs.findIndex(item=>item.value===bookDetailTab.value))
const taskPriorityIndex=computed(()=>taskPriorityOptions.findIndex(item=>item.value===taskPriority.value))
const activeSiteTabIndex=computed(()=>siteEditorTabs.findIndex(tab=>tab.key===activeSiteTab.value))
const activeBookTaskIds=computed(()=>new Set(currentCrawlerTasks.value.flatMap(task=>task.bookId?[task.bookId]:[])))
const runningCurrentTaskCount=computed(()=>currentCrawlerTasks.value.filter(task=>task.status==='RUNNING').length)
const pagedQueuedTasks=computed(()=>queuedTasks.value.slice((queuedTaskPage.value-1)*queuedTaskPageSize.value,queuedTaskPage.value*queuedTaskPageSize.value))
const metrics=computed(()=>[{label:'采集网站',value:dashboard.value?.siteCount||0,note:`${dashboard.value?.enabledSiteCount||0} 个启用`,icon:Connection},{label:'采集书籍',value:dashboard.value?.bookCount||0,note:`今日 +${dashboard.value?.todayNewBooks||0}`,icon:Collection},{label:'已采集完成',value:dashboard.value?.completedBookCount||0,note:`${dashboard.value?.readyToImportCount||0} 本待入库`,icon:Document},{label:'今日新增章节',value:dashboard.value?.todayNewChapters||0,note:`${dashboard.value?.crawlingBookCount||0} 本采集中`,icon:Tickets}])
const statisticsRangeOptions:StatisticsDays[]=[7,30,90]
const statisticsRangeIndex=computed(()=>statisticsRangeOptions.indexOf(statisticsDays.value))
const statisticsChapterTotals=computed(()=>statistics.value?.daily.reduce((totals,item)=>({newChapters:totals.newChapters+item.newChapters,successfulChapters:totals.successfulChapters+item.successfulChapters}),{newChapters:0,successfulChapters:0})||{newChapters:0,successfulChapters:0})
const statisticsBookTotal=computed(()=>statistics.value?.daily.reduce((total,item)=>total+item.newBooks,0)||0)
const statisticsSuccessfulBooksTotal=computed(()=>statistics.value?.daily.reduce((total,item)=>total+item.successfulBooks,0)||0)
const statisticsTaskTotals=computed(()=>statistics.value?.daily.reduce((totals,item)=>({finished:totals.finished+item.finishedTasks,successful:totals.successful+item.successfulTasks,failed:totals.failed+item.failedTasks}),{finished:0,successful:0,failed:0})||{finished:0,successful:0,failed:0})
const statisticsSuccessRate=computed(()=>statisticsTaskTotals.value.finished?Math.round(statisticsTaskTotals.value.successful/statisticsTaskTotals.value.finished*100):0)
let timer:number|undefined
let progressPolling=false
let chapterRequestSequence=0
let chapterReaderRequestSequence=0
let statisticsCharts:import('echarts').ECharts[]=[]
onMounted(async()=>{document.addEventListener('keydown',handleChapterReaderKeydown);window.addEventListener('resize',resizeStatisticsCharts);await preferencesStore.hydrate();await Promise.all([refresh(),loadCrawlerStatistics()]);restartPolling()})
onUnmounted(()=>{if(timer)window.clearInterval(timer);document.removeEventListener('keydown',handleChapterReaderKeydown);window.removeEventListener('resize',resizeStatisticsCharts);disposeStatisticsCharts()})
watch(activeTab,async value=>{if(value==='statistics'&&statistics.value){await nextTick();await renderStatisticsCharts()}})
function restartPolling(){if(timer)window.clearInterval(timer);timer=window.setInterval(()=>{void pollCrawlerProgress()},pollingIntervalSeconds.value*1000)}
function setPollingInterval(value:number){if(!pollingIntervalOptions.includes(value as CrawlerPollingIntervalSeconds))return;preferencesStore.setCrawlerPollingIntervalSeconds(value);restartPolling();void pollCrawlerProgress()}
async function refreshOverview(){await Promise.all([refresh(),loadCrawlerStatistics()])}
async function setStatisticsDays(days:StatisticsDays){if(statisticsDays.value===days)return;statisticsDays.value=days;await loadCrawlerStatistics()}
function handleStatisticsRangeKey(event:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();let index=statisticsRangeIndex.value;if(event.key==='ArrowRight')index=(index+1)%statisticsRangeOptions.length;else if(event.key==='ArrowLeft')index=(index-1+statisticsRangeOptions.length)%statisticsRangeOptions.length;else index=event.key==='Home'?0:statisticsRangeOptions.length-1;void setStatisticsDays(statisticsRangeOptions[index]);requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.statistics-range button')[index]?.focus())}
async function loadCrawlerStatistics(options:{silent?:boolean}={}){if(!options.silent)statisticsLoading.value=true;try{statistics.value=await crawlerApi.dashboardStatistics(statisticsDays.value);if(activeTab.value==='statistics'){await nextTick();await renderStatisticsCharts()}}catch(error:any){if(!options.silent)message.error(error.response?.data?.message||'采集统计加载失败')}finally{if(!options.silent)statisticsLoading.value=false}}
function disposeStatisticsCharts(){statisticsCharts.forEach(chart=>chart.dispose());statisticsCharts=[]}
function resizeStatisticsCharts(){statisticsCharts.forEach(chart=>chart.resize())}
function chartCssColor(name:string,fallback:string){return getComputedStyle(document.documentElement).getPropertyValue(name).trim()||fallback}
async function renderStatisticsCharts(){
  if(!statistics.value||activeTab.value!=='statistics')return
  const echarts=await import('echarts')
  if(!statistics.value||activeTab.value!=='statistics')return
  disposeStatisticsCharts()
  const text=chartCssColor('--text-secondary','#667085'),line=chartCssColor('--border-color-light','#e4e7ec'),primary=chartCssColor('--primary','#4f6bed'),success=chartCssColor('--success','#23a26d'),warning=chartCssColor('--warning','#d9901a'),danger=chartCssColor('--danger','#dc4c64')
  const animation=!window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const dates=statistics.value.daily.map(item=>item.date.slice(5).replace('-','/'))
  const axis={axisLine:{lineStyle:{color:line}},axisTick:{show:false},axisLabel:{color:text,fontSize:10},splitLine:{lineStyle:{color:line,type:'dashed' as const}}}
  const tooltip={trigger:'axis' as const,backgroundColor:chartCssColor('--surface-elevated','#fff'),borderColor:line,textStyle:{color:chartCssColor('--text-primary','#1d2939')}}
  const init=(element:HTMLElement|undefined,option:Record<string,unknown>)=>{if(!element)return;const chart=echarts.init(element);chart.setOption({animation,...option});statisticsCharts.push(chart)}
  init(chapterTrendChartRef.value,{color:[primary,success],tooltip,legend:{top:0,right:0,textStyle:{color:text,fontSize:11}},grid:{left:45,right:18,top:38,bottom:28},xAxis:{type:'category',data:dates,boundaryGap:false,...axis,axisLabel:{...axis.axisLabel,interval:statisticsDays.value===90?9:statisticsDays.value===30?3:0}},yAxis:{type:'value',minInterval:1,...axis},series:[{name:'新增章节',type:'line',smooth:true,symbol:'circle',symbolSize:5,data:statistics.value.daily.map(item=>item.newChapters),areaStyle:{opacity:.08},lineStyle:{width:2}},{name:'成功采集',type:'line',smooth:true,symbol:'circle',symbolSize:5,data:statistics.value.daily.map(item=>item.successfulChapters),areaStyle:{opacity:.07},lineStyle:{width:2}}]})
  init(taskCountChartRef.value,{color:[primary,success,danger],tooltip,legend:{top:0,right:0,textStyle:{color:text,fontSize:11}},grid:{left:42,right:18,top:38,bottom:28},xAxis:{type:'category',data:dates,boundaryGap:true,...axis,axisLabel:{...axis.axisLabel,interval:statisticsDays.value===90?9:statisticsDays.value===30?3:0}},yAxis:{type:'value',minInterval:1,...axis},series:[{name:'任务总数',type:'line',smooth:true,symbol:'circle',symbolSize:5,data:statistics.value.daily.map(item=>item.finishedTasks),lineStyle:{width:2},z:3},{name:'成功任务',type:'bar',barMaxWidth:14,data:statistics.value.daily.map(item=>item.successfulTasks),itemStyle:{borderRadius:[4,4,0,0]}},{name:'失败任务',type:'bar',barMaxWidth:14,data:statistics.value.daily.map(item=>item.failedTasks),itemStyle:{borderRadius:[4,4,0,0]}}]})
  init(bookTrendChartRef.value,{color:[warning],tooltip,grid:{left:42,right:14,top:18,bottom:28},xAxis:{type:'category',data:dates,boundaryGap:false,...axis,axisLabel:{...axis.axisLabel,interval:statisticsDays.value===90?14:statisticsDays.value===30?5:0}},yAxis:{type:'value',minInterval:1,...axis},series:[{name:'新增书籍',type:'line',smooth:true,showSymbol:statisticsDays.value===7,symbolSize:6,data:statistics.value.daily.map(item=>item.newBooks),areaStyle:{opacity:.14},lineStyle:{width:2}}]})
  init(successfulBooksChartRef.value,{color:[success],tooltip,grid:{left:42,right:14,top:18,bottom:28},xAxis:{type:'category',data:dates,boundaryGap:false,...axis,axisLabel:{...axis.axisLabel,interval:statisticsDays.value===90?14:statisticsDays.value===30?5:0}},yAxis:{type:'value',minInterval:1,...axis},series:[{name:'采集成功书籍',type:'line',smooth:true,showSymbol:statisticsDays.value===7,symbolSize:6,data:statistics.value.daily.map(item=>item.successfulBooks),areaStyle:{opacity:.14},lineStyle:{width:2}}]})
  init(successRateChartRef.value,{color:[success],tooltip:{...tooltip,valueFormatter:(value:number)=>`${value}%`},grid:{left:42,right:18,top:18,bottom:28},xAxis:{type:'category',data:dates,boundaryGap:false,...axis,axisLabel:{...axis.axisLabel,interval:statisticsDays.value===90?14:statisticsDays.value===30?5:0}},yAxis:{type:'value',min:0,max:100,axisLabel:{color:text,fontSize:10,formatter:'{value}%'},axisLine:axis.axisLine,axisTick:axis.axisTick,splitLine:axis.splitLine},series:[{name:'成功率',type:'line',smooth:true,connectNulls:false,showSymbol:statisticsDays.value===7,symbolSize:6,data:statistics.value.daily.map(item=>item.finishedTasks?Math.round(item.successfulTasks/item.finishedTasks*100):null),areaStyle:{opacity:.12},lineStyle:{width:2}}]})
  const sites=[...statistics.value.siteContributions].reverse()
  init(siteContributionChartRef.value,{color:[primary,warning],tooltip:{trigger:'axis',axisPointer:{type:'shadow'},backgroundColor:chartCssColor('--surface-elevated','#fff'),borderColor:line,textStyle:{color:chartCssColor('--text-primary','#1d2939')}},legend:{top:0,right:0,textStyle:{color:text,fontSize:11}},grid:{left:92,right:16,top:38,bottom:18},xAxis:{type:'value',minInterval:1,...axis},yAxis:{type:'category',data:sites.map(item=>item.siteName),...axis,axisLabel:{color:text,fontSize:10,width:76,overflow:'truncate'}},series:[{name:'新增章节',type:'bar',barMaxWidth:13,data:sites.map(item=>item.newChapters),itemStyle:{borderRadius:[0,5,5,0]}},{name:'新增书籍',type:'bar',barMaxWidth:13,data:sites.map(item=>item.newBooks),itemStyle:{borderRadius:[0,5,5,0]}}]})
  const funnel=statistics.value.funnel
  init(funnelChartRef.value,{color:[primary,'#7088e8',success,warning],tooltip:{trigger:'item',formatter:'{b}：{c}',backgroundColor:chartCssColor('--surface-elevated','#fff'),borderColor:line,textStyle:{color:chartCssColor('--text-primary','#1d2939')}},series:[{type:'funnel',top:8,bottom:8,left:'8%',width:'84%',minSize:'28%',maxSize:'100%',sort:'descending',gap:3,label:{color:text,fontSize:11,formatter:'{b}  {c}'},labelLine:{lineStyle:{color:line}},itemStyle:{borderColor:chartCssColor('--surface-card','#fff'),borderWidth:2},data:[{name:'已发现',value:funnel.discoveredBooks},{name:'已建任务',value:funnel.taskedBooks},{name:'采集完成',value:funnel.completedBooks},{name:'已入库',value:funnel.importedBooks}]}]})
}
async function refresh(){const [dashboardData,siteData]=await Promise.all([crawlerApi.dashboard(),crawlerApi.sites(),loadBooks(),loadTasks(),loadFailedTasks(),loadDiscoveredBooks(),loadTaskQueues(),loadCurrentCrawlerTasks()]);dashboard.value=dashboardData;sites.value=siteData;await loadDiscoveryPages()}
async function loadCurrentCrawlerTasks(){currentCrawlerTasks.value=await crawlerApi.currentTasks()}
async function loadDiscoveryPages(){const pages=await crawlerApi.discoveryPages();discoveryPagesBySite.value=pages.reduce<Record<number,CrawlerDiscoveryPage[]>>((groups,page)=>{(groups[page.siteId]??=[]).push(page);return groups},{})}
async function pollCrawlerProgress(){
  if(progressPolling)return
  const hasActiveTask=currentCrawlerTasks.value.some(task=>['RUNNING','WAITING'].includes(task.status))||[...tasks.value,...(dashboard.value?.recentTasks||[])].some(task=>['RUNNING','WAITING'].includes(task.status))
  if(!hasActiveTask&&!bookDrawer.value&&!taskDrawer.value)return
  progressPolling=true
  try{
    const requests:Promise<unknown>[]=[]
    if(hasActiveTask){
      requests.push(crawlerApi.dashboard().then(data=>{dashboard.value=data}))
      requests.push(loadTasks({silent:true}))
      requests.push(loadTaskQueues())
      requests.push(loadCurrentCrawlerTasks())
      if(queuedTasksDialog.value)requests.push(loadQueuedTasks({silent:true}))
      if(activeTab.value==='sites')requests.push(crawlerApi.sites().then(data=>{sites.value=data}))
      if(activeTab.value==='books')requests.push(loadBooks({silent:true,preserveSelection:true}))
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
    const latestFocus=followCurrentChapter.value?await crawlerApi.currentChapter(bookId,chapterPageSize.value):undefined
    if(!bookDrawer.value||selectedBook.value?.id!==bookId||requestSequence!==chapterRequestSequence)return
    let targetPage=followCurrentChapter.value&&latestFocus?latestFocus.page+1:chapterPage.value
    if(targetPage!==chapterPage.value)chapterPage.value=targetPage
    const loadTargetPage=()=>crawlerApi.chapters(bookId,{page:targetPage-1,size:chapterPageSize.value,sort:chapterSortApiValue(chapterSort.value)})
    const [latestBook,latestLogs,initialChapters]=await Promise.all([crawlerApi.book(bookId),crawlerApi.logs(bookId),loadTargetPage()])
    if(!bookDrawer.value||selectedBook.value?.id!==bookId||requestSequence!==chapterRequestSequence)return
    let latestChapters=initialChapters
    const lastPage=Math.max(1,latestChapters.totalPages)
    if(targetPage>lastPage){targetPage=lastPage;chapterPage.value=lastPage;latestChapters=await loadTargetPage()}
    if(!bookDrawer.value||selectedBook.value?.id!==bookId||requestSequence!==chapterRequestSequence)return
    currentCrawlingChapter.value=followCurrentChapter.value?latestFocus?.chapter:undefined
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
async function loadTaskQueues(){taskQueues.value=await crawlerApi.taskQueues()}
function openQueueSettings(queue?:CrawlerTaskQueue){
  if(!queue){queueOverviewDialog.value=true;return}
  queueSettingsTarget.value=queue
  queueLimit.value=queue.maxConcurrentTasks
  queueIntervalSeconds.value=queue.taskIntervalSeconds
  queueSettingsDialog.value=true
}
async function saveQueueSettings(){
  const queue=queueSettingsTarget.value
  if(!queue)return
  savingQueueSettings.value=true
  try{
    const updated=await crawlerApi.updateTaskQueue(queue.siteId,{maxConcurrentTasks:queueLimit.value,taskIntervalSeconds:queueIntervalSeconds.value})
    taskQueues.value=taskQueues.value.map(item=>item.siteId===updated.siteId?updated:item)
    queueSettingsDialog.value=false
    message.success(`${updated.siteName} 队列配置已保存`)
    await Promise.all([loadTasks({silent:true}),loadCurrentCrawlerTasks(),loadTaskQueues()])
  }catch(error:any){
    message.error(error.response?.data?.message||'队列配置保存失败')
  }finally{
    savingQueueSettings.value=false
  }
}
async function openQueuedTasks(){queueOverviewDialog.value=true;await loadTaskQueues()}
async function openQueueDetails(queue:CrawlerTaskQueue){activeQueue.value=queue;queueOverviewDialog.value=false;queuedTasksDialog.value=true;queuedTaskPage.value=1;await loadQueuedTasks()}
async function returnToQueueOverview(){queuedTasksDialog.value=false;queueOverviewDialog.value=true;await loadTaskQueues()}
function openCreateQueue(){createQueueSiteId.value=sitesWithoutQueue.value[0]?.id;createQueueDialog.value=true}
async function createQueue(){
  if(!createQueueSiteId.value||creatingQueue.value)return
  creatingQueue.value=true
  try{
    await crawlerApi.createTaskQueue(createQueueSiteId.value)
    await loadTaskQueues()
    createQueueDialog.value=false
    message.success('网站队列已创建')
  }catch(error:any){
    message.error(error.response?.data?.message||'网站队列创建失败')
  }finally{
    creatingQueue.value=false
  }
}
function currentTaskStatusRank(task:CrawlerTask){return task.status==='RUNNING'?0:task.status==='WAITING'?1:task.status==='PAUSED'?2:3}
function orderCurrentTasks(items:CrawlerTask[]){return items.map((task,index)=>({task,index})).sort((left,right)=>currentTaskStatusRank(left.task)-currentTaskStatusRank(right.task)||left.index-right.index).map(item=>item.task)}
function clampQueuedTaskPage(){queuedTaskPage.value=Math.min(queuedTaskPage.value,Math.max(1,Math.ceil(queuedTasks.value.length/queuedTaskPageSize.value)))}
function handleQueuedTaskSizeChange(){queuedTaskPage.value=1;clampQueuedTaskPage()}
async function loadQueuedTasks(options:LoadOptions={}){if(options.silent&&(queuedTaskDraggingId.value||queuedTasksReordering.value||queuedTaskPrioritizingId.value||queuedTaskCommandId.value))return;if(!options.silent)queuedTasksLoading.value=true;try{queuedTasks.value=orderCurrentTasks(await crawlerApi.currentTasks(activeQueue.value?.siteId));clampQueuedTaskPage()}finally{if(!options.silent)queuedTasksLoading.value=false}}
async function openQueuedTask(task:CrawlerTask){queuedTasksDialog.value=false;await openTask(task)}
async function prioritizeQueuedTask(task:CrawlerTask){if(task.status!=='WAITING'||queuedTasksReordering.value||queuedTaskPrioritizingId.value||queuedTaskCommandId.value||batchTaskManaging.value)return;queuedTaskPrioritizingId.value=task.id;try{await crawlerApi.prioritizeQueuedTask(task.id);message.success(`“${task.bookName||task.discoveryPageName||taskTypeLabel(task.type)}”已移到等待队列首位`);await Promise.all([loadQueuedTasks(),loadTasks({silent:true}),loadTaskQueues()])}catch(error:any){message.error(error.response?.data?.message||'任务优先调整失败');await loadQueuedTasks()}finally{queuedTaskPrioritizingId.value=undefined}}
async function commandCurrentTask(task:CrawlerTask,command:'pause'|'resume'|'cancel'){if(queuedTaskCommandId.value||queuedTasksReordering.value||queuedTaskPrioritizingId.value)return;queuedTaskCommandId.value=task.id;queuedTaskCommand.value=command;const actionLabel={pause:'暂停',resume:'继续',cancel:'取消'}[command];try{await crawlerApi.taskCommand(task.id,command);message.success(`“${task.bookName||task.discoveryPageName||taskTypeLabel(task.type)}”已${actionLabel}`);const [dashboardData]=await Promise.all([crawlerApi.dashboard(),loadQueuedTasks(),loadTasks({silent:true}),loadFailedTasks({silent:true}),loadTaskQueues()]);dashboard.value=dashboardData}catch(error:any){message.error(error.response?.data?.message||`任务${actionLabel}失败，请稍后重试`);await loadQueuedTasks()}finally{queuedTaskCommandId.value=undefined;queuedTaskCommand.value=undefined}}
function startQueuedTaskDrag(task:CrawlerTask,event:DragEvent){if(task.status!=='WAITING'||queuedTasksReordering.value||queuedTaskCommandId.value){event.preventDefault();return}queuedTaskDraggingId.value=task.id;queuedTaskDragStartOrder.value=queuedTasks.value.filter(item=>item.status==='WAITING').map(item=>item.id);if(event.dataTransfer){event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',task.id)}}
function moveDraggedQueuedTask(target:CrawlerTask){const sourceIndex=queuedTasks.value.findIndex(item=>item.id===queuedTaskDraggingId.value),targetIndex=queuedTasks.value.findIndex(item=>item.id===target.id);if(sourceIndex<0||targetIndex<0||sourceIndex===targetIndex||target.status!=='WAITING'||queuedTasks.value[sourceIndex].status!=='WAITING'||queuedTasks.value[sourceIndex].priority!==target.priority)return;const next=[...queuedTasks.value],[source]=next.splice(sourceIndex,1);next.splice(targetIndex,0,source);queuedTasks.value=next}
async function persistQueuedTaskOrder(){queuedTasksReordering.value=true;try{await crawlerApi.reorderQueuedTasks(queuedTasks.value.filter(task=>task.status==='WAITING').map(task=>task.id),activeQueue.value?.siteId);message.success('等待队列顺序已更新');await loadQueuedTasks()}catch(error:any){message.error(error.response?.data?.message||'队列排序保存失败');await loadQueuedTasks()}finally{queuedTasksReordering.value=false}}
async function finishQueuedTaskDrag(){const currentOrder=queuedTasks.value.filter(task=>task.status==='WAITING').map(task=>task.id);const changed=queuedTaskDragStartOrder.value.join(',')!==currentOrder.join(',');queuedTaskDraggingId.value=undefined;queuedTaskDragStartOrder.value=[];if(changed)await persistQueuedTaskOrder()}
async function moveQueuedTaskByKeyboard(task:CrawlerTask,direction:-1|1){if(task.status!=='WAITING'||queuedTasksReordering.value||queuedTaskCommandId.value)return;const index=queuedTasks.value.findIndex(item=>item.id===task.id),target=index+direction;if(index<0||target<0||target>=queuedTasks.value.length||queuedTasks.value[target].status!=='WAITING')return;if(queuedTasks.value[target].priority!==task.priority)return message.warning('跨优先级排序请先修改任务优先级');const next=[...queuedTasks.value];[next[index],next[target]]=[next[target],next[index]];queuedTasks.value=next;await persistQueuedTaskOrder()}
function queuedTaskRowClassName({row}:{row:CrawlerTask}){return row.id===queuedTaskDraggingId.value?'queued-task-row-dragging':''}
function chapterSortApiValue(value:ChapterSort){return value==='indexDesc'?'INDEX_DESC':value==='createdDesc'?'CREATED_DESC':'INDEX_ASC'}
async function loadChapterPage(){await syncOpenBookProgress()}
async function handleChapterSizeChange(size:number){preferencesStore.setCrawlerChapterPageSize(size);chapterPage.value=1;await syncOpenBookProgress()}
async function changeChapterSort(value:ChapterSort){if(followCurrentChapter.value||chapterSort.value===value)return;chapterSort.value=value;chapterPage.value=1;await syncOpenBookProgress()}
async function setFollowCurrentChapter(enabled:boolean){preferencesStore.setCrawlerFollowCurrentChapter(enabled);currentCrawlingChapter.value=undefined;if(enabled){chapterSort.value='indexAsc';chapterPage.value=1}await syncOpenBookProgress()}
function chapterRowClassName({row}:{row:CrawlerChapter}){return row.id===currentCrawlingChapter.value?.id?'current-crawling-row':''}
async function scrollToCurrentCrawlingChapter(){await nextTick();const row=document.querySelector<HTMLElement>('.book-detail-drawer .chapters-table .current-crawling-row');if(!row)return;const viewport=row.closest<HTMLElement>('.el-scrollbar__wrap');if(!viewport)return row.scrollIntoView({block:'center',behavior:'smooth'});const rowRect=row.getBoundingClientRect(),viewportRect=viewport.getBoundingClientRect();viewport.scrollTo({top:viewport.scrollTop+rowRect.top-viewportRect.top-(viewport.clientHeight-rowRect.height)/2,behavior:'smooth'})}
async function loadBooks(options:LoadOptions={}){if(!options.silent)bookLoading.value=true;if(!options.preserveSelection){selectedBooks.value=[];bookTableRef.value?.clearSelection()}try{const result=await crawlerApi.books({page:bookPage.value-1,size:bookPageSize.value,keyword:bookKeyword.value.trim()||undefined,siteId:bookSiteId.value,crawlStatus:bookCrawlStatus.value||undefined,importStatus:bookImportStatus.value||undefined,favoriteOnly:bookFavoriteOnly.value||undefined,sort:bookSort.value});const lastPage=Math.max(1,Math.ceil(result.totalElements/bookPageSize.value));if(bookPage.value>lastPage){bookPage.value=lastPage;return await loadBooks(options)}books.value=result.content;bookTotal.value=result.totalElements}finally{if(!options.silent)bookLoading.value=false}}
async function handleBookSizeChange(){bookPage.value=1;await loadBooks()}
async function applyBookFilters(){bookPage.value=1;await loadBooks()}
async function resetBookFilters(){bookKeyword.value='';bookSiteId.value=undefined;bookCrawlStatus.value='';bookImportStatus.value='';bookFavoriteOnly.value=false;bookSort.value='CREATED_DESC';bookPage.value=1;await loadBooks()}
async function loadTasks(options:LoadOptions={}){if(!options.silent){taskLoading.value=true;selectedTasks.value=[]}try{const result=await crawlerApi.tasks({page:taskPage.value-1,size:taskPageSize.value,status:taskStatusFilter.value||undefined,type:taskTypeFilter.value||undefined,favoriteOnly:taskFavoriteOnly.value||undefined});const lastPage=Math.max(1,Math.ceil(result.totalElements/taskPageSize.value));if(taskPage.value>lastPage){taskPage.value=lastPage;return await loadTasks(options)}tasks.value=result.content;taskTotal.value=result.totalElements}finally{if(!options.silent)taskLoading.value=false}}
async function handleTaskSizeChange(){taskPage.value=1;await loadTasks()}
async function handleTaskStatusFilter(){taskPage.value=1;await loadTasks()}
async function handleTaskTypeFilter(){taskPage.value=1;await loadTasks()}
async function handleTaskFavoriteFilter(){taskPage.value=1;await loadTasks()}
async function showRunningTasks(){taskStatusFilter.value='RUNNING';await handleTaskStatusFilter()}
async function clearTaskFilters(){taskStatusFilter.value='';taskTypeFilter.value='';taskFavoriteOnly.value=false;taskPage.value=1;await loadTasks()}
function handleTaskSelectionChange(rows:CrawlerTask[]){if(activeTab.value==='failed')selectedFailedTasks.value=rows;else selectedTasks.value=rows}
async function resumeAllFailedTasks(){
  if(batchTaskManaging.value)return
  batchTaskManaging.value=true
  batchTaskAction.value='resume-all'
  let resumedCount=0
  try{
    const firstPage=await crawlerApi.tasks({page:0,size:100,status:'FAILED'})
    const failedTasks=[...firstPage.content]
    for(let page=1;page<firstPage.totalPages;page+=4){
      const pageNumbers=Array.from({length:Math.min(4,firstPage.totalPages-page)},(_,index)=>page+index)
      const results=await Promise.all(pageNumbers.map(pageNumber=>crawlerApi.tasks({page:pageNumber,size:100,status:'FAILED'})))
      failedTasks.push(...results.flatMap(result=>result.content))
    }
    const failedTaskIds=failedTasks.filter(task=>task.status==='FAILED').map(task=>task.id)
    if(!failedTaskIds.length){message.info('当前没有可恢复的失败任务');return}
    for(let offset=0;offset<failedTaskIds.length;offset+=200){
      const result=await crawlerApi.batchManageTasks(failedTaskIds.slice(offset,offset+200),'resume')
      resumedCount+=result.affectedCount
    }
    message.success(`已恢复 ${resumedCount} 个失败任务`)
  }catch(error:any){
    if(resumedCount)message.warning(`已恢复 ${resumedCount} 个任务，其余任务恢复失败，请刷新后重试`)
    else message.error(error.response?.data?.message||'恢复失败任务失败，请刷新后重试')
  }finally{
    if(resumedCount){
      selectedTasks.value=[]
      selectedFailedTasks.value=[]
      taskTableSelectionVersion.value++
      try{await refresh()}catch{message.warning('任务已恢复，但列表刷新失败，请手动刷新')}
    }
    batchTaskManaging.value=false
    batchTaskAction.value=undefined
  }
}
async function handleBatchTaskPriority(priority:'LOW'|'NORMAL'|'HIGH'){await manageSelectedTasks('priority',priority)}
async function manageSelectedTasks(action:'pause'|'resume'|'cancel'|'delete'|'priority',priority?:'LOW'|'NORMAL'|'HIGH'){
  const selected=[...selectedTaskRows.value]
  if(!selected.length||batchTaskManaging.value)return
  if(action==='cancel'&&!await confirm(`确定取消选中的 ${selected.length} 个采集任务吗？已采集的数据会保留。`))return
  if(action==='delete'&&!await confirm(`确定删除选中的 ${selected.length} 条任务记录吗？关联书籍和章节不会被删除。`))return
  batchTaskManaging.value=true
  batchTaskAction.value=action
  try{
    const result=await crawlerApi.batchManageTasks(selected.map(task=>task.id),action,priority)
    const actionLabel=action==='priority'?`设置为${priorityLabel(priority||'NORMAL')}优先级`:{pause:'暂停',resume:'继续',cancel:'取消',delete:'删除'}[action]
    message.success(`已批量${actionLabel} ${result.affectedCount} 个任务`)
    selectedTasks.value=[]
    selectedFailedTasks.value=[]
    taskTableSelectionVersion.value++
    await refresh()
  }catch(error:any){message.error(error.response?.data?.message||'批量操作失败，请刷新后重试')}
  finally{batchTaskManaging.value=false;batchTaskAction.value=undefined}
}
async function openScanResults(task:CrawlerTask){scanResultsTask.value=task;scanResults.value=[];scanResultsPage.value=1;scanResultsTotal.value=0;scanResultsDialog.value=true;await loadScanResults()}
async function loadScanResults(){if(!scanResultsTask.value)return;scanResultsLoading.value=true;try{const result=await crawlerApi.scanResults(scanResultsTask.value.id,scanResultsPage.value-1,scanResultsPageSize.value);scanResults.value=result.content;scanResultsTotal.value=result.totalElements}finally{scanResultsLoading.value=false}}
async function handleScanResultSizeChange(){scanResultsPage.value=1;await loadScanResults()}
async function loadFailedTasks(options:LoadOptions={}){if(!options.silent){failedTaskLoading.value=true;selectedFailedTasks.value=[]}try{const result=await crawlerApi.tasks({page:failedTaskPage.value-1,size:failedTaskPageSize.value,failedOnly:true});const lastPage=Math.max(1,Math.ceil(result.totalElements/failedTaskPageSize.value));if(failedTaskPage.value>lastPage){failedTaskPage.value=lastPage;return await loadFailedTasks(options)}failedTasks.value=result.content;failedTaskTotal.value=result.totalElements}finally{if(!options.silent)failedTaskLoading.value=false}}
async function handleFailedTaskSizeChange(){failedTaskPage.value=1;await loadFailedTasks()}
async function loadDiscoveredBooks(options:LoadOptions={}){if(!options.silent)discoveryLoading.value=true;if(!options.preserveSelection)selectedDiscoveries.value=[];try{const result=await crawlerApi.discoveredBooks({page:discoveryPage.value-1,size:discoveryPageSize.value,keyword:discoveryKeyword.value.trim()||undefined,siteId:discoverySiteId.value,favoriteOnly:discoveryFavoriteOnly.value||undefined,sort:discoverySort.value});const lastPage=Math.max(1,Math.ceil(result.totalElements/discoveryPageSize.value));if(discoveryPage.value>lastPage){discoveryPage.value=lastPage;return await loadDiscoveredBooks(options)}discoveredBooks.value=result.content;discoveredTotal.value=result.totalElements}finally{if(!options.silent)discoveryLoading.value=false}}
function setDiscoveryViewMode(mode:DiscoveryViewMode){if(discoveryViewMode.value===mode)return;preferencesStore.setCrawlerDiscoveryViewMode(mode);selectedDiscoveries.value=[]}
function handleDiscoveryViewKey(event:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();setDiscoveryViewMode(event.key==='ArrowLeft'||event.key==='Home'?'table':'card');requestAnimationFrame(()=>document.querySelector<HTMLButtonElement>(`.discovery-view-switch button:nth-of-type(${discoveryViewMode.value==='table'?1:2})`)?.focus())}
function setBookViewMode(mode:DiscoveryViewMode){if(bookViewMode.value===mode)return;preferencesStore.setCrawlerBookViewMode(mode);selectedBooks.value=[]}
function handleBookViewKey(event:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();setBookViewMode(event.key==='ArrowLeft'||event.key==='Home'?'table':'card');requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.crawler-book-view-switch button')[bookViewMode.value==='table'?0:1]?.focus())}
function isDiscoverySelected(book:CrawlerBook){return selectedDiscoveryIds.value.has(book.id)}
function toggleDiscoverySelection(book:CrawlerBook,selected:boolean){selectedDiscoveries.value=selected?[...selectedDiscoveries.value.filter(item=>item.id!==book.id),book]:selectedDiscoveries.value.filter(item=>item.id!==book.id)}
function toggleCurrentDiscoveryPage(selected:boolean){selectedDiscoveries.value=selected?[...discoveredBooks.value]:[]}
function isBookSelected(book:CrawlerBook){return selectedBookIds.value.has(book.id)}
function isBookCompleted(book:CrawlerBook){return book.crawlStatus==='COMPLETED'}
function toggleBookSelection(book:CrawlerBook,selected:boolean){selectedBooks.value=selected?[...selectedBooks.value.filter(item=>item.id!==book.id),book]:selectedBooks.value.filter(item=>item.id!==book.id)}
function toggleCurrentBookPage(selected:boolean){selectedBooks.value=selected?[...books.value]:[]}
function handleBookTableRowClick(row:CrawlerBook,column:{type?:string}){if(column.type!=='selection')void openBook(row)}
function hideBrokenCover(event:Event){(event.target as HTMLImageElement).style.display='none'}
async function handleDiscoverySizeChange(){discoveryPage.value=1;await loadDiscoveredBooks()}
async function applyDiscoveryFilters(){discoveryPage.value=1;await loadDiscoveredBooks()}
async function resetDiscoveryFilters(){discoveryKeyword.value='';discoverySiteId.value=undefined;discoveryFavoriteOnly.value=false;discoverySort.value='DISCOVER_TIME_DESC';discoveryPage.value=1;await loadDiscoveredBooks()}
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
        : key==='contentMarkers' ? (value as CrawlerSitePayload['contentMarkers']).map(marker=>({...marker})) : value
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
  if(field.startsWith('contentMarkers'))return'validation'
  if(field.startsWith('proxies'))return'proxy'
  if(['requestIntervalMillis','randomDelayMillis','maxConcurrency','respectRobotsTxt'].includes(field))return'request'
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
function addContentMarker(){if(siteForm.contentMarkers.length<50)siteForm.contentMarkers.push({marker:'',status:'FAILED'})}
function removeContentMarker(index:number){siteForm.contentMarkers.splice(index,1)}
function setActiveProxy(index:number,enabled:boolean){siteForm.proxies.forEach((proxy,current)=>{proxy.enabled=enabled&&current===index})}
async function removeSite(site:CrawlerSite){if(await confirm(`确定删除采集网站“${site.siteName}”吗？`)){await crawlerApi.deleteSite(site.id);message.success('采集网站已删除');await refresh()}}
function hasSiteProtection(site:CrawlerSite){return site.protection.coolingDown||site.protection.adaptiveDelayMillis>0||site.protection.consecutiveFailures>0}
async function resetSiteProtection(site:CrawlerSite){if(!await confirm(`确定解除“${site.siteName}”的请求保护吗？请先确认源站已允许恢复访问。`))return;const updated=await crawlerApi.resetSiteProtection(site.id);sites.value=sites.value.map(item=>item.id===site.id?updated:item);message.success('站点请求保护已解除')}
async function exportSiteConfiguration(site:CrawlerSite){const data=await crawlerApi.exportSiteConfiguration(site.id);downloadJson(data,`${site.siteCode}-site-config.json`);message.success('网站全部配置已导出')}
function openSiteConfigurationImport(){siteConfigurationImportMode.value='text';siteConfigurationJsonText.value='';siteConfigurationImportFileName.value='';siteConfigurationImportDialog.value=true}
function loadSiteConfigurationTemplate(){siteConfigurationImportMode.value='text';siteConfigurationImportFileName.value='';siteConfigurationJsonText.value=JSON.stringify(siteConfigurationTemplate,null,2);message.success('已载入模板，可直接修改后导入')}
async function handleSiteConfigurationImportFile(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;siteConfigurationImportFileName.value=file.name;siteConfigurationJsonText.value=await file.text()}
async function submitSiteConfigurationImport(){if(!siteConfigurationJsonText.value.trim())return;let data:CrawlerSiteConfiguration;try{data=JSON.parse(siteConfigurationJsonText.value) as CrawlerSiteConfiguration}catch{return message.error('JSON 字符串格式不正确，请检查后重试')}if(data.type!=='AIBOOK_CRAWLER_SITE'||!data.site?.siteName||!data.site?.baseUrl)return message.error('这不是有效的网站配置 JSON，请使用导出文件或导入模板');savingSiteConfiguration.value=true;try{const created=await crawlerApi.importSiteConfiguration(data);siteConfigurationImportDialog.value=false;message.success(`网站“${created.siteName}”及其全部配置已导入`);await refresh()}finally{savingSiteConfiguration.value=false}}
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
function downloadJson(data:unknown,fileName:string){const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=fileName;a.click();URL.revokeObjectURL(url)}
async function exportRule(rule:CrawlerRuleVersion){if(!ruleSite.value)return;const data=await crawlerApi.exportRule(ruleSite.value.id,rule.id);downloadJson(data,`${ruleSite.value.siteCode}-rule-v${rule.version}.json`)}
function openImportDialog(){importMode.value='text';importJsonText.value='';importFileName.value='';ruleImportDialog.value=true}
async function handleImportFile(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;importFileName.value=file.name;importJsonText.value=await file.text()}
async function submitRuleImport(){if(!ruleSite.value||!importJsonText.value.trim())return;let data:CrawlerRuleExport;try{data=JSON.parse(importJsonText.value) as CrawlerRuleExport}catch{return message.error('JSON 字符串格式不正确，请检查后重试')}saving.value=true;try{await crawlerApi.importRule(ruleSite.value.id,{...data,enabled:false});message.success(`规则 v${data.version} 已导入（默认禁用）`);ruleImportDialog.value=false;await reloadRules()}finally{saving.value=false}}
async function crawlDiscovered(book:CrawlerBook){
  if(isBookTaskActive(book))return
  setBookTaskSubmitting(book.id,true)
  try{
    const createdTasks=await crawlerApi.batchCrawl([book.id])
    const taskIds=new Set(createdTasks.map(task=>task.id))
    currentCrawlerTasks.value=[...createdTasks,...currentCrawlerTasks.value.filter(task=>!taskIds.has(task.id))]
    message.success('采集任务已创建')
    await refresh()
  }catch(error:any){message.error(error.response?.data?.message||'创建采集任务失败')}
  finally{setBookTaskSubmitting(book.id,false)}
}
async function batchCrawl(){await crawlerApi.batchCrawl(selectedDiscoveries.value.map(b=>b.id));message.success(`已创建 ${selectedDiscoveries.value.length} 个采集任务`);await refresh()}
async function batchDiscovery(status:'IGNORED'|'BLACKLISTED',ids=selectedDiscoveries.value.map(b=>b.id)){if(!ids.length)return;await crawlerApi.setDiscoveryStatus(ids,status);message.success(status==='IGNORED'?'已忽略所选书籍':'已加入黑名单，后续扫描不会重新收录');await refresh()}
function handleDiscoveryCardMore(command:string,book:CrawlerBook){if(command==='details')void openBook(book);else if(command==='book-lists')void openCrawlerBookLists(book);else if(command==='website')window.open(book.bookUrl,'_blank','noopener,noreferrer');else if(command==='metadata')void refreshBookMetadata(book);else if(command==='ignore')void batchDiscovery('IGNORED',[book.id]);else if(command==='blacklist')void batchDiscovery('BLACKLISTED',[book.id])}
async function batchRefreshDiscoveryMetadata(){
  if(discoveryMetadataRefreshing.value)return
  const selected=[...selectedDiscoveries.value],eligible=selected.filter(book=>!isBookTaskActive(book)),skipped=selected.length-eligible.length
  if(!eligible.length)return message.warning('所选书籍都已有活动任务')
  discoveryMetadataRefreshing.value=true
  eligible.forEach(book=>setBookTaskSubmitting(book.id,true))
  try{
    const tasks=await crawlerApi.batchRefreshMetadata(eligible.map(book=>book.id))
    const taskIds=new Set(tasks.map(task=>task.id))
    currentCrawlerTasks.value=[...tasks,...currentCrawlerTasks.value.filter(task=>!taskIds.has(task.id))]
    message.success(`已为 ${tasks.length} 本发现书籍创建分类和标签刷新任务${skipped?`，跳过已有任务 ${skipped} 本`:''}`)
    selectedDiscoveries.value=[]
    await refresh()
  }catch(error:any){message.error(error.response?.data?.message||'批量刷新分类和标签失败')}
  finally{eligible.forEach(book=>setBookTaskSubmitting(book.id,false));discoveryMetadataRefreshing.value=false}
}
function handleCrawlerBookCardMore(command:string,book:CrawlerBook){if(command==='details')void openBook(book);else if(command==='book-lists')void openCrawlerBookLists(book);else if(command==='website')window.open(book.bookUrl,'_blank','noopener,noreferrer');else if(command==='updates')void checkUpdates(book);else if(command==='metadata')void refreshBookMetadata(book);else if(command==='generate')void generate(book);else if(command==='import')importBook(book);else if(command==='status')openStatusEditor(book)}
function handleCrawlerBookTableMore(command:string,book:CrawlerBook){if(command==='book-lists')void openCrawlerBookLists(book);else if(command==='updates')void checkUpdates(book);else if(command==='metadata')void refreshBookMetadata(book);else if(command==='trial')void startTrial(book);else if(command==='generate')void generate(book)}

function applyCrawlerBookUpdate(updated:CrawlerBook){
  books.value=books.value.map(book=>book.id===updated.id?updated:book)
  discoveredBooks.value=discoveredBooks.value.map(book=>book.id===updated.id?updated:book)
  selectedBooks.value=selectedBooks.value.map(book=>book.id===updated.id?updated:book)
  selectedDiscoveries.value=selectedDiscoveries.value.map(book=>book.id===updated.id?updated:book)
  if(selectedBook.value?.id===updated.id)selectedBook.value=updated
  const applyTask=(task:CrawlerTask)=>task.bookId===updated.id?{...task,favorite:updated.favorite}:task
  tasks.value=tasks.value.map(applyTask);failedTasks.value=failedTasks.value.map(applyTask);currentCrawlerTasks.value=currentCrawlerTasks.value.map(applyTask);queuedTasks.value=queuedTasks.value.map(applyTask)
  if(selectedTask.value?.bookId===updated.id)selectedTask.value=applyTask(selectedTask.value)
  if(dashboard.value)dashboard.value.recentTasks=dashboard.value.recentTasks.map(applyTask)
}
async function toggleFavorite(book:CrawlerBook){
  const updated=await crawlerApi.setFavorite(book.id,!book.favorite)
  applyCrawlerBookUpdate(updated)
  message.success(updated.favorite?'已加入收藏，入库时会同步到书库收藏':'已取消收藏')
  await reloadFavoriteFilteredLists()
}
async function toggleTaskFavorite(task:CrawlerTask){
  if(!task.bookId)return
  const updated=await crawlerApi.setFavorite(task.bookId,!task.favorite)
  applyCrawlerBookUpdate(updated)
  message.success(updated.favorite?'已加入收藏，入库时会同步到书库收藏':'已取消收藏')
  await reloadFavoriteFilteredLists()
}
async function reloadFavoriteFilteredLists(){
  const requests:Promise<unknown>[]=[]
  if(discoveryFavoriteOnly.value)requests.push(loadDiscoveredBooks({silent:true,preserveSelection:true}))
  if(bookFavoriteOnly.value)requests.push(loadBooks({silent:true,preserveSelection:true}))
  if(taskFavoriteOnly.value)requests.push(loadTasks({silent:true}))
  await Promise.all(requests)
}
async function openCrawlerBookLists(book:CrawlerBook){
  bookListTarget.value=book;selectedBookListIds.value=[...(book.bookListIds||[])];bookListDialog.value=true;bookListLoading.value=true
  try{availableBookLists.value=(await api.get<BookListOption[]>('/api/booklists')).data}
  finally{bookListLoading.value=false}
}
async function openTaskBookLists(task:CrawlerTask){if(task.bookId)await openCrawlerBookLists(await crawlerApi.book(task.bookId))}
async function saveCrawlerBookLists(){
  if(!bookListTarget.value)return
  bookListSaving.value=true
  try{const updated=await crawlerApi.setBookLists(bookListTarget.value.id,selectedBookListIds.value);applyCrawlerBookUpdate(updated);bookListDialog.value=false;message.success(updated.libraryBookId?'书单已同步到已入库书籍':'已保存，书籍入库时会自动加入所选书单')}
  finally{bookListSaving.value=false}
}
async function openBook(book:CrawlerBook){selectedBook.value=book;chapters.value=[];chapterTotal.value=book.chapterCount;chapterPage.value=1;if(followCurrentChapter.value)chapterSort.value='indexAsc';currentCrawlingChapter.value=undefined;crawlerLogs.value=[];bookDetailTab.value='chapters';bookDrawer.value=true;await syncOpenBookProgress()}
function startTrial(book:CrawlerBook){if(!book.crawledChapterCount)return;void router.push({name:'CrawlerTrialReader',params:{id:book.id}})}
async function continueCrawl(book:CrawlerBook){if(isBookTaskActive(book))return;setBookTaskSubmitting(book.id,true);try{const task=await crawlerApi.continueBook(book.id);currentCrawlerTasks.value=[task,...currentCrawlerTasks.value.filter(item=>item.id!==task.id)];message.success('续采任务已创建');await refresh()}finally{setBookTaskSubmitting(book.id,false)}}
async function retryFailures(book:CrawlerBook){await crawlerApi.retryFailures(book.id);message.success('失败章节已进入重试队列');await refresh()}
async function checkUpdates(book:CrawlerBook){await crawlerApi.checkUpdates(book.id);message.success('增量更新检查已创建');await refresh()}
async function refreshBookMetadata(book:CrawlerBook){if(isBookTaskActive(book))return;setBookTaskSubmitting(book.id,true);try{const task=await crawlerApi.refreshMetadata(book.id);currentCrawlerTasks.value=[task,...currentCrawlerTasks.value.filter(item=>item.id!==task.id)];message.success('分类和标签刷新任务已创建');await refresh()}catch(error:any){message.error(error.response?.data?.message||'分类和标签刷新任务创建失败')}finally{setBookTaskSubmitting(book.id,false)}}
function openStatusEditor(book:CrawlerBook){statusBook.value=book;manualStatus.value=manualStatusOptions.some(item=>item.value===book.crawlStatus)?book.crawlStatus:'COMPLETED';statusDialog.value=true}
async function saveBookStatus(){if(!statusBook.value)return;savingStatus.value=true;try{const updated=await crawlerApi.setBookStatus(statusBook.value.id,manualStatus.value,false);books.value=books.value.map(book=>book.id===updated.id?updated:book);if(selectedBook.value?.id===updated.id)selectedBook.value=updated;statusDialog.value=false;message.success(`采集状态已改为“${statusLabel(updated.crawlStatus)}”，后续任务仅支持人工触发`);await refresh()}finally{savingStatus.value=false}}
async function saveBatchBookStatus(){if(!selectedBooks.value.length||batchBookStatusSaving.value)return;batchBookStatusSaving.value=true;try{const count=selectedBooks.value.length;await crawlerApi.setBookStatuses(selectedBooks.value.map(book=>book.id),batchBookStatus.value);message.success(`已将 ${count} 本书统一改为“${statusLabel(batchBookStatus.value)}”`);selectedBooks.value=[];await refresh()}catch(error:any){message.error(error.response?.data?.message||'批量状态修改失败，请刷新后重试')}finally{batchBookStatusSaving.value=false}}
async function runBatchBookTasks(action:'continue'|'updates'){
  if(batchBookBusy.value)return
  const selected=[...selectedBooks.value],eligible=selected.filter(book=>!isBookTaskActive(book)),skipped=selected.length-eligible.length
  if(!eligible.length)return message.warning('所选书籍都已有活动任务')
  batchBookAction.value=action
  eligible.forEach(book=>setBookTaskSubmitting(book.id,true))
  let succeeded=0,failed=0
  try{
    for(const book of eligible){try{const task=action==='continue'?await crawlerApi.continueBook(book.id):await crawlerApi.checkUpdates(book.id);currentCrawlerTasks.value=[task,...currentCrawlerTasks.value.filter(item=>item.id!==task.id)];succeeded++}catch{failed++}}
    const actionLabel=action==='continue'?'继续采集':'检查更新',details=[skipped?`跳过已有任务 ${skipped} 本`:null,failed?`失败 ${failed} 本`:null].filter(Boolean).join('，')
    if(succeeded&& !details)message.success(`已为 ${succeeded} 本书创建${actionLabel}任务`)
    else if(succeeded)message.warning(`已为 ${succeeded} 本书创建${actionLabel}任务，${details}`)
    else message.error(`${actionLabel}任务创建失败${details?`：${details}`:''}`)
    selectedBooks.value=[]
    await refresh()
  }finally{eligible.forEach(book=>setBookTaskSubmitting(book.id,false));batchBookAction.value=undefined}
}
async function batchContinueBooks(){await runBatchBookTasks('continue')}
async function batchCheckUpdates(){await runBatchBookTasks('updates')}
async function batchRefreshBookMetadata(){
  if(batchBookBusy.value)return
  const selected=[...selectedBooks.value],eligible=selected.filter(book=>!isBookTaskActive(book)),skipped=selected.length-eligible.length
  if(!eligible.length)return message.warning('所选书籍都已有活动任务')
  batchBookAction.value='metadata'
  eligible.forEach(book=>setBookTaskSubmitting(book.id,true))
  try{
    const tasks=await crawlerApi.batchRefreshMetadata(eligible.map(book=>book.id))
    const taskIds=new Set(tasks.map(task=>task.id))
    currentCrawlerTasks.value=[...tasks,...currentCrawlerTasks.value.filter(task=>!taskIds.has(task.id))]
    message.success(`已为 ${tasks.length} 本书创建分类和标签刷新任务${skipped?`，跳过已有任务 ${skipped} 本`:''}`)
    selectedBooks.value=[]
    await refresh()
  }catch(error:any){message.error(error.response?.data?.message||'批量刷新分类和标签失败')}
  finally{eligible.forEach(book=>setBookTaskSubmitting(book.id,false));batchBookAction.value=undefined}
}
async function toggleLibrarySync(book:CrawlerBook,enabled:boolean){const updated=await crawlerApi.setLibrarySync(book.id,enabled);books.value=books.value.map(item=>item.id===updated.id?updated:item);if(selectedBook.value?.id===updated.id)selectedBook.value=updated;message.success(enabled?'已开启追更后自动同步书库':'已关闭自动同步，更新只保留在采集中心')}
async function generate(book:CrawlerBook){if(book.crawlStatus!=='COMPLETED'&&!await confirm(`“${book.bookName}”当前为${statusLabel(book.crawlStatus)}，生成文件将只包含已有正文，是否继续？`))return;await crawlerApi.generate(book.id,['TXT','EPUB']);message.success('已有正文已生成 TXT 与 EPUB，并保留在采集中心')}
function importBook(book:CrawlerBook){importTargets.value=[book];importFormats.value=['STRUCTURED'];importDialog.value=true}
function openBatchImport(){importTargets.value=[...selectedBooks.value];importFormats.value=['STRUCTURED'];importDialog.value=true}
async function submitImport(){
  if(!importTargets.value.length||!importFormats.value.length)return
  const targets=[...importTargets.value]
  importing.value=true
  try{
    if(targets.length===1){const book=targets[0],syncing=book.importStatus==='IMPORTED',result=await crawlerApi.importBook(book.id,importFormats.value);importDialog.value=false;message.success(syncing?`所选内容已同步到书库（书籍 ID：${result.bookId}）`:`已通过${importFormats.value.includes('STRUCTURED')?'结构化章节':'文件版本'}加入书库（书籍 ID：${result.bookId}）`);await refresh();return}
    let succeeded=0,failed=0,skipped=0
    for(const book of targets){if(!book.crawledChapterCount){skipped++;continue}try{await crawlerApi.importBook(book.id,importFormats.value);succeeded++}catch{failed++}}
    importDialog.value=false
    const details=[skipped?`无可用正文 ${skipped} 本`:null,failed?`失败 ${failed} 本`:null].filter(Boolean).join('，')
    if(succeeded&&!details)message.success(`已将 ${succeeded} 本书批量加入或同步到书库`)
    else if(succeeded)message.warning(`已处理 ${succeeded} 本书，${details}`)
    else message.error(`没有书籍成功入库${details?`：${details}`:''}`)
    selectedBooks.value=[]
    await refresh()
  }finally{importing.value=false}
}
async function openTask(task:CrawlerTask){selectedTask.value=task;taskDrawer.value=true;await syncOpenTask()}
async function openTaskBook(task:CrawlerTask){if(!task.bookId)return;const book=await crawlerApi.book(task.bookId);taskDrawer.value=false;await openBook(book)}
async function runTaskCommand(task:CrawlerTask,command:'pause'|'resume'|'cancel'){const updated=await crawlerApi.taskCommand(task.id,command);if(selectedTask.value?.id===updated.id)selectedTask.value=updated;await refresh();if(taskDrawer.value&&selectedTask.value?.id===updated.id)await syncOpenTask({silent:true})}
function openTaskEditor(task:CrawlerTask){editingTask.value=task;taskPriority.value=(task.priority as 'LOW'|'NORMAL'|'HIGH')||'NORMAL';taskEditDialog.value=true}
async function saveTask(){if(!editingTask.value)return;savingTask.value=true;try{const updated=await crawlerApi.updateTask(editingTask.value.id,taskPriority.value);if(selectedTask.value?.id===updated.id)selectedTask.value=updated;taskEditDialog.value=false;message.success('任务优先级已更新');await refresh()}finally{savingTask.value=false}}
async function removeTask(task:CrawlerTask){if(!await confirm(`确定删除“${task.bookName||task.discoveryPageName||taskTypeLabel(task.type)}”的任务记录吗？关联书籍和章节不会被删除。`))return;await crawlerApi.deleteTask(task.id);if(selectedTask.value?.id===task.id){taskDrawer.value=false;selectedTask.value=undefined}message.success('任务记录已删除');await refresh()}
async function loadChapterReaderIndex(bookId:number){
  if(chapterReaderBookId.value===bookId&&chapterReaderChapters.value.length===chapterTotal.value)return
  const first=await crawlerApi.chapters(bookId,{page:0,size:100,sort:'INDEX_ASC'})
  const remaining=await Promise.all(Array.from({length:Math.max(0,first.totalPages-1)},(_,index)=>crawlerApi.chapters(bookId,{page:index+1,size:100,sort:'INDEX_ASC'})))
  chapterReaderBookId.value=bookId
  chapterReaderChapters.value=[...first.content,...remaining.flatMap(page=>page.content)]
}
async function selectChapterReader(chapter:CrawlerChapter){
  const bookId=selectedBook.value?.id
  if(!bookId)return
  const requestSequence=++chapterReaderRequestSequence
  chapterReaderActive.value=chapter
  chapterReaderLoading.value=true
  try{
    const detail=await crawlerApi.chapter(bookId,chapter.id)
    if(requestSequence!==chapterReaderRequestSequence)return
    chapterDetail.value=detail
    await nextTick()
    chapterReaderSurface.value?.scrollTo({top:0,behavior:'auto'})
  }catch(error:any){
    if(requestSequence!==chapterReaderRequestSequence)return
    chapterDetail.value={title:chapter.chapterName,url:chapter.chapterUrl,content:'',errorMessage:error.response?.data?.message||chapter.errorMessage||'章节正文加载失败'}
  }finally{if(requestSequence===chapterReaderRequestSequence)chapterReaderLoading.value=false}
}
async function openChapter(chapter:CrawlerChapter){
  const bookId=selectedBook.value?.id
  if(!bookId)return
  const openingSequence=++chapterReaderRequestSequence
  chapterDialog.value=true
  chapterReaderSettingsOpen.value=false
  chapterReaderActive.value=chapter
  chapterDetail.value=undefined
  chapterReaderLoading.value=true
  try{await loadChapterReaderIndex(bookId);if(openingSequence!==chapterReaderRequestSequence||!chapterDialog.value)return;await selectChapterReader(chapter)}
  catch(error:any){if(openingSequence!==chapterReaderRequestSequence||!chapterDialog.value)return;chapterDetail.value={title:chapter.chapterName,url:chapter.chapterUrl,content:'',errorMessage:error.response?.data?.message||'章节正文加载失败'};chapterReaderLoading.value=false}
}
function moveChapterReader(offset:-1|1){const index=chapterReaderPosition.value-1,target=chapterReaderChapters.value[index+offset];if(target)void selectChapterReader(target)}
function closeChapterReader(){chapterReaderRequestSequence++;chapterReaderSettingsOpen.value=false;chapterReaderLoading.value=false;chapterDetail.value=undefined;chapterReaderActive.value=undefined}
function updateChapterReaderSettings(patch:Partial<ReaderSettings>){preferencesStore.setReaderSettings({...readerSettings.value,...patch})}
function setChapterReaderTheme(theme:ChapterReaderTheme){updateChapterReaderSettings({backgroundColor:theme==='night'?'#2d2d2d':theme==='light'?'#ffffff':'#f5f5dc'})}
function setChapterReaderWidth(width:ReaderContentWidth){updateChapterReaderSettings({contentWidth:width})}
function setChapterReaderNumber(field:'fontSize'|'lineHeight',event:Event){const value=Number((event.target as HTMLInputElement).value);if(Number.isFinite(value))updateChapterReaderSettings({[field]:value})}
function handleChapterReaderKeydown(event:KeyboardEvent){if(!chapterDialog.value||event.target instanceof HTMLInputElement||event.target instanceof HTMLButtonElement||event.target instanceof HTMLAnchorElement)return;if(event.key==='ArrowLeft')moveChapterReader(-1);else if(event.key==='ArrowRight')moveChapterReader(1)}
function progress(book:CrawlerBook){return book.chapterCount?Math.round((book.crawledChapterCount+book.pendingReleaseChapterCount)/book.chapterCount*100):0}
function isBookRunning(book:CrawlerBook){return ['CRAWLING_METADATA','CRAWLING_CHAPTER_LIST','CRAWLING_CONTENT','UPDATING'].includes(book.crawlStatus)}
function isBookTaskActive(book:CrawlerBook){return submittingBookTaskIds.value.has(book.id)||activeBookTaskIds.value.has(book.id)}
function setBookTaskSubmitting(bookId:number,submitting:boolean){const next=new Set(submittingBookTaskIds.value);if(submitting)next.add(bookId);else next.delete(bookId);submittingBookTaskIds.value=next}
function statusLabel(status:string){return ({WAITING:'等待中',RUNNING:'运行中',PAUSED:'已暂停',SUCCESS:'成功',PARTIAL_SUCCESS:'部分成功',FAILED:'失败',CANCELLED:'已取消',DISCOVERED:'已发现',CRAWLING_METADATA:'解析元信息',CRAWLING_CHAPTER_LIST:'解析目录',CRAWLING_CONTENT:'采集正文',COMPLETED:'已完成',PENDING_RELEASE:'待开放',NOT_CRAWLED:'未采集',CRAWLING:'采集中',CONTENT_SUSPECTED:'内容异常'} as Record<string,string>)[status]||status}
function statusType(status:string):''|'success'|'warning'|'info'|'danger'{if(['RUNNING','SUCCESS','COMPLETED'].includes(status))return'success';if(['FAILED','CONTENT_SUSPECTED'].includes(status))return'danger';if(['PARTIAL_SUCCESS','PAUSED','PENDING_RELEASE'].includes(status))return'warning';return'info'}
function priorityLabel(priority:string){return({LOW:'低',NORMAL:'普通',HIGH:'高'} as Record<string,string>)[priority]||priority}
function priorityType(priority:string):''|'success'|'warning'|'info'|'danger'{return priority==='HIGH'?'warning':priority==='LOW'?'info':''}
function scanTaskPercentage(task:CrawlerTask){return Math.max(0,Math.min(100,task.progressPercent??(['SUCCESS','PARTIAL_SUCCESS'].includes(task.status)?100:0)))}
function taskProgress(task:CrawlerTask){return scanTaskPercentage(task)}
function taskCircleStatus(status:string):'success'|'exception'|'warning'|undefined{if(status==='SUCCESS')return'success';if(status==='FAILED')return'exception';if(['PAUSED','PARTIAL_SUCCESS'].includes(status))return'warning';return undefined}
function taskProgressDescription(task:CrawlerTask){if(task.type==='SITE_SCAN')return scanTaskProgressText(task);if(task.currentChapter)return task.currentChapter;if(task.status==='WAITING')return '任务正在队列中等待执行';if(task.status==='SUCCESS')return `处理完成 · 成功 ${task.successCount} 项`;if(task.status==='PARTIAL_SUCCESS')return `处理完成 · 成功 ${task.successCount} 项，失败 ${task.failedCount} 项`;if(task.status==='FAILED')return task.errorMessage||'任务执行失败';if(task.status==='PAUSED')return '任务已暂停，可从下方继续执行';if(task.status==='CANCELLED')return '任务已取消';return `已处理 ${task.successCount+task.failedCount} / ${task.totalCount} 项`}
function scanTaskProgressText(task:CrawlerTask){if(task.status==='WAITING')return `等待扫描 · 分页上限 ${task.scanMaxPages||'—'} 页`;if(['SUCCESS','PARTIAL_SUCCESS'].includes(task.status))return task.scannedPageCount>0?`扫描完成 · 共扫描 ${task.scannedPageCount} 页`:'扫描完成';return task.currentChapter||`已扫描 ${task.scannedPageCount||0} / ${task.scanMaxPages||'—'} 页`}
function scanResultLabel(status:CrawlerScanResult['resultStatus']){return({NEW:'新增',DUPLICATE:'重复',BLACKLISTED:'黑名单',FAILED:'失败'} as const)[status]}
function scanResultType(status:CrawlerScanResult['resultStatus']):'success'|'warning'|'info'|'danger'{return status==='NEW'?'success':status==='DUPLICATE'?'warning':status==='BLACKLISTED'?'info':'danger'}
function taskTypeLabel(type:string){return({SITE_SCAN:'发现页扫描',BOOK_METADATA:'书籍信息',BOOK_CHAPTER_LIST:'章节目录',BOOK_CONTENT:'章节正文',BOOK_UPDATE_CHECK:'更新检查',BOOK_FULL_CRAWL:'全本采集',BOOK_EXPORT:'文件生成',BOOK_IMPORT:'加入书库'} as Record<string,string>)[type]||type}
function formatTime(value?:string){return value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'—'}
function handleTabKey(e:KeyboardEvent){const keys=tabs.value.map(t=>t.key);let i=activeIndex.value;if(['ArrowRight','ArrowDown'].includes(e.key))i=(i+1)%keys.length;else if(['ArrowLeft','ArrowUp'].includes(e.key))i=(i-1+keys.length)%keys.length;else if(e.key==='Home')i=0;else if(e.key==='End')i=keys.length-1;else return;e.preventDefault();activeTab.value=keys[i];requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.segment')[i]?.focus())}
function handleSiteEditorTabKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();let index=activeSiteTabIndex.value;if(e.key==='ArrowRight')index=(index+1)%siteEditorTabs.length;else if(e.key==='ArrowLeft')index=(index-1+siteEditorTabs.length)%siteEditorTabs.length;else index=e.key==='Home'?0:siteEditorTabs.length-1;activeSiteTab.value=siteEditorTabs[index].key;requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.site-editor-tabs button')[index]?.focus())}
function handleImportModeKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();importMode.value=e.key==='ArrowLeft'||e.key==='Home'?'text':'file';requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.import-mode button')[importMode.value==='text'?0:1]?.focus())}
function handleSiteConfigurationImportModeKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();siteConfigurationImportMode.value=e.key==='ArrowLeft'||e.key==='Home'?'text':'file';requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.site-configuration-import-mode button')[siteConfigurationImportMode.value==='text'?0:1]?.focus())}
function handleChapterSortKey(e:KeyboardEvent){if(followCurrentChapter.value||!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();let index=chapterSortIndex.value;if(e.key==='ArrowRight')index=(index+1)%chapterSortOptions.length;else if(e.key==='ArrowLeft')index=(index-1+chapterSortOptions.length)%chapterSortOptions.length;else index=e.key==='Home'?0:chapterSortOptions.length-1;void changeChapterSort(chapterSortOptions[index].value);requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.chapter-sort button')[index]?.focus())}
function handleBookDetailTabKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();let index=bookDetailTabIndex.value;if(e.key==='ArrowRight')index=(index+1)%bookDetailTabs.length;else if(e.key==='ArrowLeft')index=(index-1+bookDetailTabs.length)%bookDetailTabs.length;else index=e.key==='Home'?0:bookDetailTabs.length-1;bookDetailTab.value=bookDetailTabs[index].value;requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.book-detail-tabs button')[index]?.focus())}
function handlePriorityKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();let index=taskPriorityIndex.value;if(e.key==='ArrowRight')index=(index+1)%taskPriorityOptions.length;else if(e.key==='ArrowLeft')index=(index-1+taskPriorityOptions.length)%taskPriorityOptions.length;else index=e.key==='Home'?0:taskPriorityOptions.length-1;taskPriority.value=taskPriorityOptions[index].value;requestAnimationFrame(()=>document.querySelectorAll<HTMLButtonElement>('.priority-segment button')[index]?.focus())}
</script>

<style scoped>
.crawler-page{display:grid;gap:22px;padding:var(--spacing-lg) 0 50px}.crawler-header{display:flex;align-items:flex-start;justify-content:space-between}.crawler-page-title{margin-bottom:var(--spacing-sm);color:var(--text-on-page-bg);font-size:var(--font-size-4xl);font-weight:700;text-shadow:var(--text-on-page-bg-shadow)}.crawler-page-subtitle{color:var(--text-on-page-bg-secondary);font-size:var(--font-size-base)}.eyebrow{margin-bottom:6px;color:var(--primary);font-size:11px;font-weight:800;letter-spacing:.16em}.segmented-wrap{position:relative;display:grid;grid-auto-flow:column;grid-auto-columns:max(120px,calc(100% / var(--tab-count)));grid-template-columns:none;grid-template-rows:minmax(0,1fr);overflow-x:auto;padding:5px;border:1px solid var(--border-color);border-radius:16px;background:var(--surface-card);isolation:isolate}.segment-indicator{position:absolute;top:5px;bottom:5px;left:5px;z-index:-1;width:max(120px,calc(100% / var(--tab-count)));border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .28s cubic-bezier(.2,.8,.2,1)}.segment{display:flex;align-items:center;justify-content:center;gap:7px;min-width:120px;padding:11px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.segment.active{color:var(--primary);font-weight:700}.segment:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:12px}.segment b{min-width:19px;padding:1px 5px;border-radius:99px;background:var(--primary-alpha-10);font-size:11px}.panel{min-height:430px;padding:26px;border:1px solid var(--border-color);border-radius:22px;background:var(--surface-card);box-shadow:var(--shadow-md)}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:34px}.metric-card{display:grid;grid-template-columns:auto 1fr;gap:13px;padding:20px;border:1px solid var(--border-color-light);border-radius:18px;background:linear-gradient(145deg,var(--surface-elevated),var(--surface-card))}.metric-icon{display:grid;width:42px;height:42px;place-items:center;border-radius:13px;background:var(--primary-alpha-10);color:var(--primary);font-size:20px}.metric-card strong{font-size:27px}.metric-card p,.metric-card small,.book-cell p,.task-name p{color:var(--text-secondary);font-size:12px}.metric-card small{grid-column:2}.section-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.section-heading h2{font-family:'Iowan Old Style','Songti SC',serif;font-size:25px}.crawler-statistics{margin-top:0;padding-top:26px;border-top:0}.crawler-statistics-heading{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-bottom:18px}.crawler-statistics-heading h2{font-family:'Iowan Old Style','Songti SC',serif;font-size:25px}.crawler-statistics-heading>div:first-child>span{color:var(--text-secondary);font-size:12px}.statistics-range{position:relative;display:grid;grid-template-columns:repeat(3,minmax(82px,1fr));min-width:278px;padding:4px;border:1px solid var(--border-color);border-radius:14px;background:var(--bg-page);isolation:isolate}.statistics-range>span{position:absolute;inset:4px auto 4px 4px;z-index:0;width:calc((100% - 8px)/3);border:1px solid var(--border-color-light);border-radius:10px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .25s cubic-bezier(.2,.8,.2,1)}.statistics-range button{position:relative;z-index:1;padding:8px 10px;border:0;background:transparent;color:var(--text-secondary);font-size:11px;cursor:pointer}.statistics-range button.active{color:var(--primary);font-weight:800}.statistics-range button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:10px}.crawler-statistics-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.statistics-card{min-width:0;padding:18px 18px 12px;border:1px solid var(--border-color-light);border-radius:18px;background:linear-gradient(145deg,var(--surface-elevated),var(--surface-card));box-shadow:0 7px 24px color-mix(in srgb,var(--text-primary) 4%,transparent)}.statistics-card-wide{grid-column:1/-1}.statistics-card>header{display:flex;min-height:42px;align-items:flex-start;justify-content:space-between;gap:14px}.statistics-card h3{font-size:15px}.statistics-card header p{margin-top:4px;color:var(--text-tertiary);font-size:11px}.statistics-card>header>strong{color:var(--primary);font:700 27px/1 'Iowan Old Style','Songti SC',serif}.statistics-summary{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.statistics-summary span{display:flex;align-items:center;gap:5px;padding:5px 8px;border-radius:99px;background:var(--bg-page);color:var(--text-secondary);font-size:10px}.statistics-summary b{color:var(--text-primary);font-size:12px}.stat-dot{width:7px;height:7px;border-radius:50%}.stat-dot-primary{background:var(--primary)}.stat-dot-success{background:var(--success)}.statistics-chart{width:100%;height:250px}.statistics-chart-large{height:300px}@media(max-width:760px){.crawler-statistics-heading{align-items:stretch;flex-direction:column}.statistics-range{width:100%;min-width:0}.crawler-statistics-grid{grid-template-columns:minmax(0,1fr)}.statistics-card-wide{grid-column:auto}.statistics-card{padding:15px 12px 8px}.statistics-card>header{align-items:flex-start;flex-direction:column}.statistics-summary{justify-content:flex-start}.statistics-chart,.statistics-chart-large{height:240px}}@media(prefers-reduced-motion:reduce){.statistics-range>span{transition:none}}.site-heading-actions{display:flex;flex-wrap:wrap;gap:8px}.search{width:280px}.site-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16px}.site-card{padding:20px;border:1px solid var(--border-color-light);border-radius:18px;background:var(--surface-elevated)}.site-top{display:grid;grid-template-columns:auto 1fr auto;gap:12px;align-items:center}.site-mark,.mini-cover,.large-cover{display:grid;place-items:center;background:linear-gradient(145deg,var(--primary),var(--primary-light));color:white;font-family:'Songti SC',serif}.site-mark{width:44px;height:44px;border-radius:14px;font-size:22px}.site-top a{display:block;max-width:280px;overflow:hidden;color:var(--text-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.site-stats{display:flex;gap:22px;margin:20px 0;color:var(--text-secondary);font-size:12px}.site-stats b{color:var(--text-primary);font-size:17px}.automation{display:flex;gap:8px}.automation span{padding:4px 9px;border-radius:99px;background:var(--bg-page);color:var(--text-tertiary);font-size:11px}.automation span.on{background:var(--success-alpha-15);color:var(--success)}.site-card footer{display:flex;justify-content:flex-end;margin-top:16px;border-top:1px solid var(--border-color-light);padding-top:10px}.book-cell,.book-summary{display:flex;align-items:center;gap:12px}.mini-cover{width:38px;height:50px;border-radius:6px}.data-table{cursor:default}.task-name p{margin-top:3px}.site-form{display:grid;gap:16px;max-height:70vh;overflow:auto;padding-right:8px}.site-form-section{padding:18px;border:1px solid var(--border-color-light);border-radius:16px;background:var(--surface-elevated)}.site-form-section>header{display:grid;grid-template-columns:auto 1fr;gap:10px;align-items:center;margin-bottom:16px}.site-form-section>header>span{display:grid;width:34px;height:34px;place-items:center;border-radius:10px;background:var(--primary-alpha-10);color:var(--primary);font-size:11px;font-weight:800}.site-form-section>header h3{font-size:16px}.site-form-section>header p{margin-top:2px;color:var(--text-tertiary);font-size:12px}.site-form-section>.section-with-action{grid-template-columns:auto 1fr auto}.field-hint{display:block;margin-top:5px;color:var(--text-tertiary);font-size:11px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.switch-row{display:flex;flex-wrap:wrap;gap:20px;margin-bottom:4px}.proxy-list{display:grid;gap:10px}.proxy-row{display:grid;grid-template-columns:minmax(130px,.7fr) minmax(240px,1.5fr) auto;gap:12px;align-items:end;padding:12px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-card)}.proxy-row.active{border-color:var(--success);background:var(--success-alpha-15)}.proxy-row .el-form-item{margin-bottom:0}.proxy-state{display:flex;align-items:center;gap:8px;min-height:32px}.rule-block{margin:10px 0 18px;padding-top:18px;border-top:1px solid var(--border-color)}.rule-block p:last-child{margin-top:5px;color:var(--text-secondary);font-size:12px}.book-summary{padding:18px;margin-bottom:16px;border-radius:18px;background:var(--primary-alpha-10)}.book-summary>div:last-child{flex:1}.large-cover{width:82px;height:108px;border-radius:10px;font-size:32px}.drawer-actions{display:flex;gap:8px;margin-bottom:16px}.chapter-content{max-height:60vh;margin-top:14px;overflow:auto;padding:20px;border-radius:14px;background:var(--bg-page);color:var(--text-primary);font:15px/1.8 'Songti SC',serif;white-space:pre-wrap}.el-select{width:100%}
.stat-dot-danger{background:var(--danger)}
.batch-actions{display:flex;flex-wrap:wrap;gap:8px}.source-link{margin:0 10px;color:var(--primary);font-size:14px;text-decoration:none}.source-link:hover{text-decoration:underline}
.crawler-page{width:100%;min-width:0;max-width:100%;overflow-x:clip;box-sizing:border-box}.crawler-page>*{min-width:0;max-width:100%;box-sizing:border-box}.panel{width:100%;min-width:0;max-width:100%;overflow:hidden;box-sizing:border-box}.metric-grid,.site-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.site-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.section-heading>*{min-width:0}.data-table,:deep(.el-table){width:100%!important;min-width:0;max-width:100%}.book-cell>div:last-child,.task-name{min-width:0}.book-cell strong,.book-cell p,.task-name strong,.task-name p{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.site-card footer{flex-wrap:wrap}.health-line{margin-top:12px;color:var(--text-tertiary);font-size:11px}.health-line.error{color:var(--danger)}.test-result{padding:18px;border:1px solid var(--success-alpha-15);border-radius:16px;background:var(--surface-elevated)}.test-result.failed{border-color:var(--danger)}.test-facts{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.test-facts span{display:grid;gap:4px}.test-facts small{color:var(--text-tertiary)}.test-result>p{margin:18px 0 8px}.test-result pre{max-height:260px;overflow:auto;padding:14px;border-radius:12px;background:var(--bg-page);font:14px/1.7 'Songti SC',serif;white-space:pre-wrap}.file-input{display:none}.rule-manager-bar{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-bottom:20px;padding:15px 18px;border-radius:14px;background:var(--primary-alpha-10);color:var(--text-secondary)}.rule-manager-bar>div{display:flex;flex-shrink:0;gap:8px}.rule-table{width:100%;border:1px solid var(--border-color-light);border-radius:14px;overflow:hidden}.rule-version-text{color:var(--primary);font-family:'Iowan Old Style','Songti SC',serif;font-size:17px}.rule-table code{padding:3px 6px;border-radius:6px;background:var(--bg-page);color:var(--text-secondary);font-size:12px}.rule-actions{display:flex;flex-wrap:wrap;align-items:center;gap:2px 4px;white-space:normal}.rule-actions .el-button{margin-left:0;padding:5px 7px}:deep(.rule-table .active-rule-row td.el-table__cell){background:var(--success-alpha-15)!important}:deep(.rule-table .active-rule-row:hover td.el-table__cell){background:var(--success-alpha-15)!important}.import-mode{position:relative;display:grid;grid-template-columns:repeat(2,1fr);max-width:360px;margin:0 auto 20px;padding:4px;border:1px solid var(--border-color);border-radius:14px;background:var(--bg-page);isolation:isolate}.site-configuration-import-mode{margin-top:18px}.import-mode-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:-1;width:calc(50% - 4px);border:1px solid var(--border-color-light);border-radius:10px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .25s cubic-bezier(.2,.8,.2,1)}.import-mode button{padding:9px 16px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.import-mode button.active{color:var(--primary);font-weight:700}.import-mode button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:10px}.import-pane{display:grid;gap:10px}.import-pane>p{color:var(--text-tertiary);font-size:12px}.file-pane{display:grid;place-items:stretch}.file-picker{display:grid;min-height:150px;place-items:center;padding:24px;border:1px dashed var(--primary);border-radius:16px;background:var(--primary-alpha-10);color:var(--text-secondary);cursor:pointer}.file-picker .el-icon{color:var(--primary);font-size:28px}.file-picker strong{color:var(--text-primary);font-size:15px}.file-picker span{font-size:12px}
@media(max-width:900px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.site-grid{grid-template-columns:minmax(0,1fr)}.proxy-row{grid-template-columns:1fr 1.5fr}.proxy-state{grid-column:1/-1;justify-content:flex-end}}@media(max-width:640px){.panel{padding:16px}.metric-grid,.form-grid,.proxy-row{grid-template-columns:minmax(0,1fr)}.proxy-state{grid-column:auto}.site-form-section{padding:14px}.site-form-section>.section-with-action{grid-template-columns:auto 1fr}.section-with-action>.el-button{grid-column:1/-1}.segmented-wrap{justify-content:start}.section-heading,.rule-manager-bar{align-items:flex-start;flex-direction:column;gap:12px}.rule-manager-bar>div{width:100%;flex-wrap:wrap}.rule-table{max-width:100%;overflow-x:auto}.search{width:100%}}@media(prefers-reduced-motion:reduce){.segment-indicator,.import-mode-indicator{transition:none}}
.drawer-actions{flex-wrap:wrap}.library-sync-control{display:flex;align-items:center;justify-content:space-between;gap:18px;margin:-4px 0 16px;padding:13px 16px;border:1px solid var(--border-color-light);border-radius:14px;background:var(--surface-elevated)}.library-sync-control p{margin-top:3px;color:var(--text-secondary);font-size:12px}.status-editor{display:flex;align-items:center;gap:6px;padding:3px;border:0;background:transparent;cursor:pointer}.status-editor small{color:var(--text-tertiary)}.status-editor:hover small,.status-editor:focus-visible small{color:var(--primary)}.status-editor:focus-visible{outline:2px solid var(--primary);outline-offset:2px;border-radius:8px}.status-dialog-content{display:grid;gap:18px}.status-book{display:flex;align-items:center;gap:12px;padding:14px;border:1px solid var(--border-color-light);border-radius:14px;background:var(--surface-elevated)}.status-book .mini-cover{flex:0 0 auto}.status-book p{margin-top:4px;color:var(--text-secondary);font-size:12px}
:deep(.book-detail-drawer .el-drawer__body){min-height:0;overflow:hidden}.book-drawer-content{display:flex;height:100%;min-height:0;flex-direction:column}.chapter-list-heading{display:flex;align-items:end;justify-content:space-between;gap:16px;margin:2px 0 12px}.chapter-list-heading h3{font-family:'Iowan Old Style','Songti SC',serif;font-size:20px}.chapter-sort{position:relative;display:grid;grid-template-columns:repeat(3,minmax(82px,1fr));padding:4px;border:1px solid var(--border-color);border-radius:13px;background:var(--bg-page);isolation:isolate}.chapter-sort-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/3);border:1px solid var(--border-color-light);border-radius:9px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .25s cubic-bezier(.2,.8,.2,1)}.chapter-sort button{position:relative;z-index:1;padding:7px 10px;border:0;background:transparent;color:var(--text-secondary);font-size:12px;cursor:pointer}.chapter-sort button.active{color:var(--primary);font-weight:700}.chapter-sort button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:9px}.chapters-table{flex:1;min-height:220px}.chapter-pagination{display:flex;flex:0 0 auto;align-items:center;justify-content:space-between;gap:12px;padding:12px 2px 0;color:var(--text-tertiary);font-size:12px}.chapter-pagination :deep(.el-pagination){min-width:0}@media(max-width:640px){.chapter-list-heading{align-items:stretch;flex-direction:column}.chapter-sort{width:100%;overflow-x:auto}.chapter-pagination{align-items:flex-start;flex-direction:column}.chapter-pagination :deep(.el-pagination){flex-wrap:wrap;justify-content:flex-start}}@media(prefers-reduced-motion:reduce){.chapter-sort-indicator{transition:none}}
.book-detail-tabs{position:relative;display:grid;grid-template-columns:repeat(2,1fr);flex:0 0 auto;margin-bottom:14px;padding:4px;border:1px solid var(--border-color);border-radius:14px;background:var(--bg-page);isolation:isolate}.book-detail-tab-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/2);border:1px solid var(--border-color-light);border-radius:10px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .25s cubic-bezier(.2,.8,.2,1)}.book-detail-tabs button{position:relative;z-index:1;display:flex;align-items:center;justify-content:center;gap:8px;padding:9px 14px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.book-detail-tabs button b{min-width:22px;padding:2px 7px;border-radius:99px;background:var(--primary-alpha-10);font-size:11px}.book-detail-tabs button.active{color:var(--primary);font-weight:700}.book-detail-tabs button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:10px}.drawer-detail-panel{flex:1;min-height:0}.chapter-progress-panel{display:flex;flex-direction:column}.realtime-log-panel{overflow:hidden;border:1px solid var(--border-color-light);border-radius:16px;background:var(--surface-elevated)}.realtime-log-heading{display:flex;align-items:center;justify-content:space-between;padding:16px 18px;border-bottom:1px solid var(--border-color-light)}.realtime-log-heading h3{font-family:'Iowan Old Style','Songti SC',serif;font-size:20px}.live-state{display:flex;align-items:center;gap:7px;color:var(--text-tertiary);font-size:11px}.live-state i{width:7px;height:7px;border-radius:50%;background:var(--success);box-shadow:0 0 0 5px var(--success-alpha-15);animation:live-pulse 1.8s ease-out infinite}.crawler-log-stream{height:calc(100% - 69px);overflow:auto;padding:4px 18px 18px}.crawler-log-item{display:grid;grid-template-columns:128px minmax(0,1fr);gap:14px;padding:14px 0;border-bottom:1px solid var(--border-color-light)}.crawler-log-item time{padding-top:2px;color:var(--text-tertiary);font:11px/1.5 ui-monospace,SFMono-Regular,Consolas,monospace}.crawler-log-item strong{display:block;color:var(--text-primary);font-size:13px}.crawler-log-item p{margin-top:5px;color:var(--text-secondary);font:12px/1.65 ui-monospace,SFMono-Regular,Consolas,monospace;overflow-wrap:anywhere}.realtime-log-panel>.el-empty{height:calc(100% - 69px)}@keyframes live-pulse{70%,100%{box-shadow:0 0 0 9px transparent}}@media(max-width:640px){.crawler-log-item{grid-template-columns:minmax(0,1fr);gap:5px}.crawler-log-item time{padding-top:0}}@media(prefers-reduced-motion:reduce){.book-detail-tab-indicator{transition:none}.live-state i{animation:none}}
.task-edit-content{display:grid;gap:20px}.task-edit-summary{display:flex;align-items:center;gap:12px;padding:15px;border:1px solid var(--border-color-light);border-radius:14px;background:var(--surface-elevated)}.task-edit-summary>span{display:grid;min-width:74px;height:42px;place-items:center;padding:0 10px;border-radius:11px;background:var(--primary-alpha-10);color:var(--primary);font-size:12px;font-weight:700}.task-edit-summary p{margin-top:4px;color:var(--text-secondary);font-size:12px}.field-label{margin-bottom:8px;color:var(--text-secondary);font-size:13px;font-weight:700}.priority-segment{position:relative;display:grid;grid-template-columns:repeat(3,1fr);padding:4px;border:1px solid var(--border-color);border-radius:14px;background:var(--bg-page);isolation:isolate}.priority-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/3);border:1px solid var(--border-color-light);border-radius:10px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .25s cubic-bezier(.2,.8,.2,1)}.priority-segment button{position:relative;z-index:1;padding:10px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer}.priority-segment button.active{color:var(--primary);font-weight:700}.priority-segment button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:10px}@media(prefers-reduced-motion:reduce){.priority-indicator{transition:none}}
.discovery-heading-actions{display:flex;align-items:center;gap:12px}.discovery-view-switch{position:relative;display:grid;grid-template-columns:repeat(2,1fr);min-width:172px;padding:4px;border:1px solid var(--border-color);border-radius:13px;background:var(--bg-page);isolation:isolate}.discovery-view-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/2);border:1px solid var(--border-color-light);border-radius:9px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .24s cubic-bezier(.2,.8,.2,1)}.discovery-view-switch button{position:relative;z-index:1;padding:7px 10px;border:0;background:transparent;color:var(--text-secondary);cursor:pointer;font-size:12px}.discovery-view-switch button.active{color:var(--primary);font-weight:700}.discovery-view-switch button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:9px}.discovery-card-panel{min-height:180px}.discovery-card-selection{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;padding:10px 14px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated);color:var(--text-tertiary);font-size:12px}.discovery-card-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.discovery-card{min-width:0;overflow:hidden;border:1px solid var(--border-color-light);border-radius:18px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:border-color .18s ease,transform .18s ease,box-shadow .18s ease}.discovery-card:hover{transform:translateY(-2px);box-shadow:var(--shadow-md)}.discovery-card.selected{border-color:var(--primary);box-shadow:0 0 0 2px var(--primary-alpha-10),var(--shadow-md)}.discovery-card-cover{position:relative;display:grid;height:190px;place-items:center;overflow:hidden;background:linear-gradient(145deg,var(--primary-alpha-10),var(--surface-hover));color:var(--primary);font:48px 'Songti SC',serif}.discovery-card-cover img{position:absolute;width:100%;height:100%;object-fit:cover}.discovery-card-check{position:absolute;top:12px;right:12px;z-index:2;display:grid;width:30px;height:30px;place-items:center;border-radius:9px;background:color-mix(in srgb,var(--surface-card) 88%,transparent);backdrop-filter:blur(10px)}.discovery-card-body{display:grid;gap:14px;padding:16px}.discovery-card-title{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.discovery-card-title>div{min-width:0}.discovery-card-title strong,.discovery-card-title p{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.discovery-card-title strong{font-size:16px}.discovery-card-title p{margin-top:4px;color:var(--text-secondary);font-size:12px}.discovery-card-body dl{display:grid;gap:8px;margin:0}.discovery-card-body dl div{display:grid;grid-template-columns:58px minmax(0,1fr);gap:8px}.discovery-card-body dt{color:var(--text-tertiary);font-size:11px}.discovery-card-body dd{overflow:hidden;margin:0;color:var(--text-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.discovery-card-actions{display:flex;flex-wrap:wrap;align-items:center;gap:2px;padding:10px 12px;border-top:1px solid var(--border-color-light)}.discovery-card-actions .el-button{margin-left:0}.book-search{display:flex;align-items:center;gap:8px}.discovery-pagination,.list-pagination{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:18px 4px 2px;border-top:1px solid var(--border-color-light);color:var(--text-tertiary);font-size:12px}.discovery-pagination :deep(.el-pagination),.list-pagination :deep(.el-pagination){min-width:0}@media(max-width:1100px){.discovery-heading{align-items:flex-start;flex-direction:column;gap:14px}.discovery-heading-actions{width:100%;justify-content:space-between}.discovery-card-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:760px){.discovery-heading-actions{align-items:stretch;flex-direction:column}.discovery-view-switch{width:100%}.book-search{width:100%}.book-search .search{flex:1}.discovery-card-grid{grid-template-columns:minmax(0,1fr)}.discovery-pagination,.list-pagination{align-items:flex-start;flex-direction:column}.discovery-pagination :deep(.el-pagination),.list-pagination :deep(.el-pagination){flex-wrap:wrap;justify-content:flex-start;gap:8px 0}}@media(prefers-reduced-motion:reduce){.discovery-view-indicator,.discovery-card{transition:none}.discovery-card:hover{transform:none}}
/* 发现书籍紧凑卡片：沿用书库的封面型自适应网格 */
.discovery-card-grid{grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:var(--spacing-md)}
.discovery-card{position:relative;contain:layout paint style;content-visibility:auto;contain-intrinsic-size:250px;border-radius:var(--radius-lg)}
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
.discovery-card-action-overlay{position:absolute;top:0;right:0;left:0;z-index:4;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:4px;padding:7px;opacity:0;background:linear-gradient(180deg,rgba(20,24,31,.94),rgba(20,24,31,.78));box-shadow:0 8px 24px rgba(0,0,0,.22);backdrop-filter:blur(12px);transform:translateY(calc(-100% - 2px));transition:transform .24s cubic-bezier(.2,.8,.2,1),opacity .18s ease}.discovery-card:hover>.discovery-card-action-overlay,.discovery-card:focus-within>.discovery-card-action-overlay{opacity:1;transform:translateY(0)}.discovery-card-action-overlay :deep(.el-button){width:100%;min-width:0;height:28px;margin:0;padding:0 5px;border-color:rgba(255,255,255,.24);background:rgba(255,255,255,.1);color:#fff;font-size:10px;font-weight:700;box-shadow:none}.discovery-card-action-overlay :deep(.el-button:hover),.discovery-card-action-overlay :deep(.el-button:focus-visible){border-color:rgba(255,255,255,.52);background:rgba(255,255,255,.2)}.discovery-card-action-overlay :deep(.el-button--primary){border-color:rgba(255,255,255,.9);background:rgba(255,255,255,.92);color:#20242c}.discovery-card-action-overlay :deep(.el-dropdown){display:block;min-width:0}.discovery-card-action-overlay :deep(.el-tooltip__trigger){width:100%}
.discovery-card-check{top:auto;bottom:8px;z-index:5}
.discovery-source-badge{position:absolute;bottom:8px;left:8px;z-index:3;box-sizing:border-box;max-width:calc(100% - 50px);overflow:hidden;padding:4px 8px;border:1px solid rgba(255,255,255,.28);border-radius:7px;background:rgba(20,24,31,.72);box-shadow:0 3px 10px rgba(0,0,0,.16);color:#fff;font-family:inherit;font-size:9px;font-weight:700;line-height:1.2;text-overflow:ellipsis;white-space:nowrap;backdrop-filter:blur(9px)}.discovery-card-category{display:flex;min-height:32px;align-items:center;padding:7px 9px}.discovery-card-category :deep(.el-tag){max-width:100%;overflow:hidden;text-overflow:ellipsis}
:global(.discovery-more-popper .danger-dropdown-item){color:var(--danger)}
.crawler-book-card{cursor:pointer}
.task-detail-circle{flex:0 0 72px}.task-detail-circle :deep(.el-progress__text){color:var(--text-primary);font-size:14px!important;font-weight:800}
.crawler-book-title-button{display:block;width:100%;overflow:hidden;padding:0;border:0;background:transparent;color:var(--text-primary);cursor:pointer;font:inherit;font-size:13px;font-weight:700;text-align:left;text-overflow:ellipsis;white-space:nowrap}.crawler-book-title-button:hover{color:var(--primary)}.crawler-book-title-button:focus-visible{outline:2px solid var(--primary);outline-offset:2px;border-radius:4px}
.crawler-book-cover-badges{position:absolute;bottom:8px;left:8px;z-index:2;display:flex;max-width:calc(100% - 54px);flex-wrap:wrap;gap:5px}.crawler-book-cover-badges :deep(.el-tag){max-width:100%;border:0;box-shadow:0 3px 10px rgba(0,0,0,.16);backdrop-filter:blur(8px)}.crawler-book-card-check{z-index:5}
.crawler-book-card-body{align-content:start}.crawler-book-progress{display:grid;gap:5px;padding-top:8px;border-top:1px solid var(--border-color-light)}.crawler-book-progress>div{display:flex;align-items:center;justify-content:space-between;gap:6px;color:var(--text-tertiary);font-size:9px}.crawler-book-progress strong{color:var(--text-secondary);font-size:9px;white-space:nowrap}.crawler-book-progress :deep(.el-progress__text){display:none}.crawler-book-progress :deep(.el-progress-bar){padding-right:0;margin-right:0}
.crawler-book-tag-list{display:flex;min-width:0;flex-wrap:wrap;gap:5px}.crawler-book-tag-list.table-tags{align-items:center}.crawler-book-tag-list.table-tags>span:not(.el-tag){color:var(--text-tertiary);font-size:12px}.crawler-book-card-body>.crawler-book-tag-list{margin-top:-2px}
.book-crawl-result{display:grid;min-width:0;gap:4px}.book-crawl-result>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.book-crawl-result span{color:var(--text-tertiary);font-size:10px}.book-crawl-result strong{color:var(--success);font-size:12px}.book-crawl-result small{color:var(--text-secondary);font-size:10px}.book-crawl-result :deep(.el-button){justify-self:start;height:auto;margin:0;padding:1px 0;font-size:10px}.book-crawl-result.table-result{padding:6px 0}.book-crawl-result.card-result{padding-top:8px;border-top:1px solid var(--border-color-light)}.book-crawl-result.drawer-result{margin-top:10px;padding:11px 13px;border:1px solid color-mix(in srgb,var(--success) 28%,var(--border-color-light));border-radius:12px;background:var(--success-alpha-15)}.book-crawl-result.drawer-result span{font-size:11px}.book-crawl-result.drawer-result strong{font-size:13px}.book-crawl-result.drawer-result small{font-size:11px}
@media(max-width:520px){.discovery-card-grid{grid-template-columns:repeat(auto-fill,minmax(132px,1fr));gap:10px}.discovery-card-cover{height:154px}}
@media(hover:none){.discovery-card-action-overlay{opacity:1;transform:translateY(0)}}
@media(prefers-reduced-motion:reduce){.discovery-card-action-overlay{transition:none}}
.discovery-toolbar{display:grid;grid-template-columns:minmax(260px,1.6fr) minmax(160px,.8fr) minmax(190px,.9fr) auto auto auto;gap:10px;align-items:center;margin-bottom:16px;padding:14px;border:1px solid var(--border-color-light);border-radius:15px;background:var(--surface-elevated)}.book-filter-toolbar{display:grid;grid-template-columns:minmax(230px,1.5fr) repeat(3,minmax(140px,.8fr)) minmax(190px,1fr) auto auto auto;gap:10px;align-items:center;margin-bottom:16px;padding:14px;border:1px solid var(--border-color-light);border-radius:15px;background:var(--surface-elevated)}@media(max-width:1200px){.book-filter-toolbar{grid-template-columns:repeat(3,minmax(0,1fr))}.book-filter-toolbar .el-button{margin-left:0}}@media(max-width:980px){.discovery-toolbar{grid-template-columns:2fr 1fr 1fr}.discovery-toolbar .el-button{margin-left:0}}@media(max-width:640px){.discovery-toolbar,.book-filter-toolbar{grid-template-columns:minmax(0,1fr)}.discovery-toolbar .el-button,.book-filter-toolbar .el-button{width:100%}}
.crawler-book-list-dialog{display:grid;gap:14px}.crawler-book-list-options{display:grid;max-height:50vh;gap:8px;overflow-y:auto}.crawler-book-list-options :deep(.el-checkbox){box-sizing:border-box;width:100%;height:auto;margin:0;padding:12px 14px}.crawler-book-list-options :deep(.el-checkbox__label){display:grid;min-width:0;gap:3px}.crawler-book-list-options strong{color:var(--text-primary)}.crawler-book-list-options small{overflow:hidden;color:var(--text-tertiary);font-size:11px;text-overflow:ellipsis;white-space:nowrap}
.marker-list{display:grid;gap:2px}.marker-row{display:grid;width:100%;grid-template-columns:minmax(0,1fr) 132px auto;gap:8px;align-items:center}.marker-status{width:132px}.marker-row .el-button{margin-left:0}@media(max-width:640px){.marker-row{grid-template-columns:minmax(0,1fr)}.marker-status{width:100%}.marker-row .el-button{justify-self:end}}
:global(.site-editor-dialog){display:flex;width:min(860px,calc(100vw - 32px))!important;max-height:calc(100dvh - 32px);flex-direction:column;margin-top:max(16px,3vh)!important;margin-bottom:16px}
:global(.site-editor-dialog .el-dialog__body){display:flex;min-width:0;min-height:0;flex:1;flex-direction:column;overflow:hidden}
:global(.site-editor-dialog .el-dialog__header),:global(.site-editor-dialog .el-dialog__footer){flex:0 0 auto}
.site-editor-form{box-sizing:border-box;width:100%;min-width:0;min-height:0;flex:1 1 auto;align-content:start;overflow-x:hidden;overflow-y:auto;overscroll-behavior:contain}
.site-editor-tab-scroll{position:sticky;top:0;z-index:3;flex:0 0 auto;overflow-x:auto;padding-bottom:8px;background:var(--surface-card)}
.site-editor-tabs{position:relative;display:grid;width:max(100%,650px);grid-template-columns:repeat(5,minmax(130px,1fr));padding:4px;border:1px solid var(--border-color);border-radius:15px;background:var(--bg-page);isolation:isolate}.site-editor-tab-indicator{position:absolute;top:4px;bottom:4px;left:4px;z-index:0;width:calc((100% - 8px)/5);border:1px solid var(--border-color-light);border-radius:11px;background:var(--surface-elevated);box-shadow:var(--shadow-sm);transition:transform .28s cubic-bezier(.2,.8,.2,1)}.site-editor-tabs button{position:relative;z-index:1;display:grid;gap:2px;padding:9px 12px;border:0;background:transparent;color:var(--text-secondary);text-align:center;cursor:pointer}.site-editor-tabs button strong{font-size:13px}.site-editor-tabs button small{color:var(--text-tertiary);font-size:10px}.site-editor-tabs button.active,.site-editor-tabs button.active small{color:var(--primary)}.site-editor-tabs button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px;border-radius:11px}.site-tab-panel{display:grid;gap:14px}
@media(max-width:640px){:global(.site-editor-dialog){width:calc(100vw - 20px)!important;max-height:calc(100dvh - 20px);margin-top:10px!important;margin-bottom:10px}:global(.site-editor-dialog .el-dialog__body){padding-right:14px;padding-left:14px}.site-editor-form{padding-right:0}}
@media(prefers-reduced-motion:reduce){.site-editor-tab-indicator{transition:none}}
.chapter-heading-tools{display:flex;align-items:center;gap:10px}.chapter-follow{box-sizing:border-box;display:flex;min-width:132px;height:38px;align-items:center;justify-content:space-between;gap:10px;padding:2px 10px;border:1px solid var(--border-color);border-radius:12px;background:var(--bg-page);cursor:pointer;transition:border-color .2s ease,background .2s ease}.chapter-follow strong{color:var(--text-secondary);font-size:11px}.chapter-follow.active{border-color:color-mix(in srgb,var(--primary) 38%,var(--border-color));background:var(--primary-alpha-10)}.chapter-follow.active strong{color:var(--primary)}.chapter-sort.disabled{opacity:.5}.chapter-sort button:disabled{cursor:not-allowed}.chapter-name-cell{display:flex;min-width:0;align-items:center;gap:8px}.chapter-name-cell>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.chapter-name-cell em{display:inline-flex;flex:0 0 auto;align-items:center;gap:5px;padding:3px 7px;border-radius:99px;background:var(--primary-alpha-10);color:var(--primary);font-size:9px;font-style:normal;font-weight:800}.chapter-name-cell em i{width:6px;height:6px;border-radius:50%;background:var(--primary);box-shadow:0 0 0 4px var(--primary-alpha-10);animation:chapter-follow-pulse 1.5s ease-out infinite}.chapters-table :deep(.current-crawling-row>td.el-table__cell){background:color-mix(in srgb,var(--primary) 11%,var(--surface-elevated))!important}.chapters-table :deep(.current-crawling-row:hover>td.el-table__cell){background:color-mix(in srgb,var(--primary) 15%,var(--surface-elevated))!important}@keyframes chapter-follow-pulse{70%,100%{box-shadow:0 0 0 8px transparent}}@media(max-width:720px){.chapter-heading-tools{align-items:stretch;flex-direction:column}.chapter-follow{width:100%}}@media(prefers-reduced-motion:reduce){.chapter-follow{transition:none}.chapter-name-cell em i{animation:none}}
.crawler-header-actions{display:flex;align-items:center;gap:10px}.polling-setting{display:flex;align-items:center;gap:10px;padding:7px 9px 7px 12px;border:1px solid var(--border-color);border-radius:13px;background:color-mix(in srgb,var(--surface-elevated) 88%,transparent);box-shadow:var(--shadow-sm);backdrop-filter:blur(14px)}.polling-setting>span{display:flex;align-items:center;gap:7px;color:var(--text-secondary);font-size:11px;font-weight:700;white-space:nowrap}.polling-setting>span i{width:7px;height:7px;border-radius:50%;background:var(--success);box-shadow:0 0 0 4px var(--success-alpha-15);animation:live-pulse 1.8s ease-out infinite}.polling-setting :deep(.el-select){width:84px}.polling-setting :deep(.el-select__wrapper){border-radius:9px;background:var(--surface-card);box-shadow:none}@media(max-width:640px){.crawler-header{gap:var(--spacing-md);flex-direction:column}.crawler-header-actions{width:100%;flex-wrap:wrap}.polling-setting{justify-content:space-between}.crawler-header-actions>.el-button{margin-left:0}}@media(prefers-reduced-motion:reduce){.polling-setting>span i{animation:none}}
.import-book-dialog{display:grid;gap:20px}.format-options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.format-options :deep(.el-checkbox-button){width:100%}.format-options :deep(.el-checkbox-button__inner){display:grid;width:100%;gap:4px;padding:16px;border:1px solid var(--border-color)!important;border-radius:13px!important;background:var(--surface-elevated);box-shadow:none!important;text-align:left}.format-options :deep(.el-checkbox-button__inner strong){font-size:15px}.format-options :deep(.el-checkbox-button__inner small){color:var(--text-tertiary);font-size:11px;font-weight:400}.format-options :deep(.el-checkbox-button.is-checked .el-checkbox-button__inner){border-color:var(--primary)!important;background:var(--primary-alpha-10);color:var(--primary)}.format-options :deep(.el-checkbox-button.is-focus .el-checkbox-button__inner){outline:2px solid var(--primary);outline-offset:2px}@media(max-width:480px){.format-options{grid-template-columns:minmax(0,1fr)}}
.discovery-manager-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 16px;margin-bottom:14px;border-radius:14px;background:var(--primary-alpha-10)}.discovery-manager-toolbar>div{display:grid;gap:3px}.discovery-manager-toolbar p{color:var(--text-secondary);font-size:12px}.discovery-pages{display:grid;gap:8px}.discovery-pages-dialog-list{max-height:58vh;overflow-y:auto;padding-right:4px}.discovery-page-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px 12px;align-items:center;padding:12px 14px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-card)}.discovery-page-row>div:first-child{display:grid;min-width:0;gap:3px}.discovery-page-row a{overflow:hidden;color:var(--text-secondary);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.discovery-page-row small{color:var(--text-tertiary);font-size:10px}.discovery-page-actions{grid-column:1/-1;display:flex;justify-content:flex-end}.discovery-page-actions .el-button{margin-left:0}.discovery-page-form{display:grid;gap:14px}.discovery-page-form .el-form-item{margin-bottom:0}@media(max-width:520px){.discovery-manager-toolbar{align-items:stretch;flex-direction:column}.discovery-page-row{grid-template-columns:minmax(0,1fr)}.discovery-page-row>.el-switch{justify-self:start}.discovery-page-actions{justify-content:flex-start}}
.task-scan-progress{display:grid;gap:5px}.task-scan-progress>small{color:var(--text-tertiary);font-size:10px}.task-scan-summary{display:flex;flex-wrap:wrap;gap:5px;color:var(--text-secondary);font-size:11px}.task-scan-summary>*{padding:2px 6px;border-radius:99px;font-style:normal;font-weight:700}.task-scan-summary b{background:var(--success-alpha-15);color:var(--success)}.task-scan-summary i{background:var(--primary-alpha-10);color:var(--primary)}.task-scan-summary em{background:color-mix(in srgb,var(--warning) 15%,transparent);color:var(--warning)}.task-scan-summary strong{background:color-mix(in srgb,var(--danger) 10%,transparent);color:var(--danger)}.scan-result-summary{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px;margin-bottom:16px}.scan-result-summary span{display:grid;gap:3px;padding:12px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated)}.scan-result-summary small{color:var(--text-tertiary);font-size:10px}.scan-result-summary strong{font-size:20px}.scan-results-table a{color:var(--primary)}.scan-results-pagination{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-top:14px;color:var(--text-tertiary);font-size:12px}@media(max-width:640px){.scan-result-summary{grid-template-columns:repeat(2,minmax(0,1fr))}.scan-results-pagination{align-items:flex-start;flex-direction:column}}
.crawler-book-metadata{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-bottom:16px}.crawler-book-metadata>span{display:grid;gap:4px;padding:11px 13px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-elevated)}.crawler-book-metadata small{color:var(--text-tertiary);font-size:10px}.crawler-book-metadata .metadata-tag-row{grid-column:1/-1}.metadata-tags{display:flex;flex-wrap:wrap;gap:6px}.test-result>.metadata-tags{margin-top:14px}@media(max-width:520px){.crawler-book-metadata{grid-template-columns:minmax(0,1fr)}.crawler-book-metadata .metadata-tag-row{grid-column:auto}}
:deep(.task-open){display:flex;width:100%;min-width:0;align-items:center;gap:8px;margin:0;padding:5px 0;appearance:none;border:0;background:transparent;color:var(--text-primary);font:inherit;text-align:left;cursor:pointer}:deep(.task-open strong){overflow:hidden;min-width:0;text-overflow:ellipsis;white-space:nowrap}:deep(.task-site-mark){display:inline-grid;width:22px;height:22px;flex:0 0 22px;place-items:center;border:1px solid color-mix(in srgb,var(--primary) 22%,var(--border-color-light));border-radius:7px;background:var(--primary-alpha-10);color:var(--primary);font-size:11px;font-weight:800}:deep(.task-open:hover strong){color:var(--primary)}:deep(.task-open:focus-visible){outline:2px solid var(--primary);outline-offset:2px;border-radius:6px}.task-detail-content{display:grid;gap:16px}.task-detail-hero{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:14px;align-items:center;padding:18px;border-radius:18px;background:var(--primary-alpha-10)}.task-detail-hero h2{overflow:hidden;margin-bottom:4px;text-overflow:ellipsis;white-space:nowrap}.task-detail-hero>div:nth-child(2)>p:last-child{color:var(--text-secondary);font-size:12px}.task-detail-mark{position:relative;display:grid;width:72px;height:72px;place-items:center;border:7px solid var(--surface-card);border-radius:50%;background:var(--surface-elevated);box-shadow:var(--shadow-sm)}.task-detail-mark>span{position:absolute;top:4px;right:4px;width:10px;height:10px;border-radius:50%;background:var(--text-tertiary)}.task-detail-mark>span.state-running{background:var(--success);box-shadow:0 0 0 5px var(--success-alpha-15);animation:live-pulse 1.8s ease-out infinite}.task-detail-mark>span.state-failed{background:var(--danger)}.task-detail-mark>span.state-paused,.task-detail-mark>span.state-partial_success{background:var(--warning)}.task-progress-card{display:grid;gap:11px;padding:16px;border:1px solid var(--border-color-light);border-radius:15px;background:var(--surface-elevated)}.task-progress-card>div{display:flex;align-items:center;justify-content:space-between}.task-progress-card>p{color:var(--text-secondary);font-size:12px}.task-stat-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.task-stat-grid>span{display:grid;gap:3px;padding:12px;border:1px solid var(--border-color-light);border-radius:12px;background:var(--surface-card)}.task-stat-grid small,.task-detail-list span{color:var(--text-tertiary);font-size:10px}.task-stat-grid strong{font-size:20px}.task-detail-list{overflow:hidden;border:1px solid var(--border-color-light);border-radius:14px}.task-detail-list>div{display:grid;grid-template-columns:130px minmax(0,1fr);gap:12px;padding:11px 14px;border-bottom:1px solid var(--border-color-light)}.task-detail-list>div:last-child{border-bottom:0}.task-detail-list strong{overflow-wrap:anywhere;font-size:12px}.task-detail-actions{display:flex;flex-wrap:wrap;gap:8px;padding-top:4px}.task-detail-actions .el-button{margin-left:0}@media(max-width:520px){.task-detail-hero{grid-template-columns:auto minmax(0,1fr)}.task-detail-hero>.el-tag{grid-column:1/-1;justify-self:start}.task-stat-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.task-detail-list>div{grid-template-columns:90px minmax(0,1fr)}}@media(prefers-reduced-motion:reduce){.task-detail-mark>span.state-running{animation:none}}
.queue-heading-actions{display:flex;align-items:center;gap:10px}.queue-runtime{display:flex;gap:8px}.queue-runtime span,.queue-runtime button{box-sizing:border-box;display:flex;min-height:32px;align-items:center;gap:7px;padding:7px 12px;border:0;border-radius:99px;background:var(--surface-elevated);color:var(--text-secondary);font-family:inherit;font-size:12px;font-weight:700;line-height:1}.queue-runtime button{border:1px solid var(--border-color-light);cursor:pointer;transition:border-color .18s ease,color .18s ease,background .18s ease}.queue-runtime button:hover{border-color:var(--primary);background:var(--primary-alpha-10);color:var(--primary)}.queue-runtime button:focus-visible{outline:2px solid var(--primary);outline-offset:2px}.running-dot{width:8px;height:8px;border-radius:50%;background:var(--success);box-shadow:0 0 0 3px var(--success-alpha-15)}.queue-settings-content{display:grid;gap:18px}.queue-settings-content :deep(.el-form-item){display:grid;justify-items:start}.queue-settings-content :deep(.el-form-item__content){display:grid;justify-items:start;gap:9px}.queue-settings-summary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.queue-settings-summary span{display:grid;gap:4px;padding:14px;border:1px solid var(--border-color-light);border-radius:13px;background:var(--surface-elevated)}.queue-settings-summary small{color:var(--text-tertiary);font-size:10px}.queue-settings-summary strong{font-size:24px}.queue-limit-stepper{width:min(300px,100%);height:64px}.queue-limit-stepper :deep(.el-input){height:64px}.queue-limit-stepper :deep(.el-input__wrapper){padding-right:64px;padding-left:64px;border-radius:8px;background:var(--surface-card);box-shadow:0 0 0 1px var(--border-color) inset}.queue-limit-stepper :deep(.el-input__inner){height:64px;color:var(--text-primary);font-size:25px;font-weight:500}.queue-limit-stepper :deep(.el-input-number__decrease),.queue-limit-stepper :deep(.el-input-number__increase){width:64px;height:62px;border-color:var(--border-color);background:var(--surface-elevated);color:var(--text-secondary);font-size:25px}.queue-limit-stepper :deep(.el-input-number__decrease){border-radius:8px 0 0 8px}.queue-limit-stepper :deep(.el-input-number__increase){border-radius:0 8px 8px 0}.queue-limit-stepper :deep(.el-input-number__decrease:hover),.queue-limit-stepper :deep(.el-input-number__increase:hover){background:var(--primary-alpha-10);color:var(--primary)}.queue-limit-stepper :deep(.el-input-number__decrease.is-disabled),.queue-limit-stepper :deep(.el-input-number__increase.is-disabled){color:var(--text-tertiary)}
.queue-overview-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.queue-overview-summary span {
  display: grid;
  gap: 5px;
  padding: 14px 16px;
  border: 1px solid var(--border-color-light);
  border-radius: 14px;
  background: var(--surface-elevated);
}

.queue-overview-summary small {
  color: var(--text-tertiary);
  font-size: 11px;
}

.queue-overview-summary strong {
  color: var(--text-primary);
  font-size: 21px;
}

.site-queue-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  max-height: 56vh;
  overflow: auto;
  padding: 2px;
}

.site-queue-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-color-light);
  border-radius: 16px;
  background: var(--surface-card);
}

.site-queue-card header,
.site-queue-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.site-queue-card header > div {
  display: grid;
  gap: 5px;
}

.site-queue-card header strong {
  color: var(--text-primary);
  font-size: 14px;
}

.site-queue-card .eyebrow {
  margin: 0;
  color: var(--text-tertiary);
  font-size: 10px;
}

.site-queue-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: var(--text-secondary);
  font-size: 12px;
}

.site-queue-card footer {
  justify-content: flex-end;
  padding-top: 10px;
  border-top: 1px solid var(--border-color-light);
}

.queue-site-select {
  width: 100%;
}

.queue-settings-content :deep(.el-form-item) {
  display: grid;
  justify-items: start;
}

.queue-settings-content :deep(.el-form-item__content) {
  display: grid;
  justify-items: start;
  gap: 9px;
}

@media (max-width: 720px) {
  .site-queue-list {
    grid-template-columns: 1fr;
  }

  .queue-overview-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .queue-overview-summary span {
    min-width: 0;
  }

  .queue-overview-summary strong {
    font-size: 18px;
  }
}

@media (max-width: 480px) {
  .queue-overview-summary {
    grid-template-columns: 1fr;
  }
}
.queued-order-note{display:flex;align-items:center;gap:12px;margin-bottom:12px;padding:11px 13px;border:1px solid var(--primary-alpha-20);border-radius:13px;background:var(--primary-alpha-10)}.queue-drag-mark{display:grid;flex:0 0 32px;height:32px;place-items:center;border-radius:9px;background:var(--surface-card);color:var(--primary);font-size:20px}.queued-order-note div{display:grid;gap:2px}.queued-order-note strong{font-size:12px}.queued-order-note small{color:var(--text-secondary);font-size:11px}.queue-drag-handle{display:grid;width:34px;height:34px;margin:auto;place-items:center;border:1px solid transparent;border-radius:9px;background:transparent;color:var(--text-tertiary);cursor:grab;font-size:20px;line-height:1;transition:color .18s ease,background .18s ease,border-color .18s ease,opacity .18s ease}.queue-drag-handle:hover,.queue-drag-handle:focus-visible{border-color:var(--primary-alpha-20);outline:none;background:var(--primary-alpha-10);color:var(--primary)}.queue-drag-handle:active,.queue-drag-handle.dragging{cursor:grabbing;opacity:.45}.queue-drag-handle.disabled{cursor:not-allowed;opacity:.3}:deep(.queued-task-row-dragging td.el-table__cell){background:var(--primary-alpha-10)!important}.queue-task-link{display:flex;width:100%;min-height:34px;align-items:center;padding:4px 0;border:0;background:transparent;color:var(--text-primary);font:inherit;text-align:left;cursor:pointer}.queue-task-link strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.queue-task-link:hover strong{color:var(--primary)}.queue-task-link:focus-visible{outline:2px solid var(--primary);outline-offset:2px;border-radius:6px}.queued-task-actions{display:inline-flex;align-items:center;gap:4px;padding:4px;border:1px solid var(--border-color-light);border-radius:13px;background:color-mix(in srgb,var(--surface-elevated) 88%,transparent);box-shadow:0 3px 10px color-mix(in srgb,var(--text-primary) 5%,transparent);white-space:nowrap}.queued-task-actions :deep(.el-button){height:28px;margin-left:0;padding:5px 10px;border-color:transparent;background:transparent;font-size:11px;font-weight:700;transition:background .18s ease,color .18s ease,box-shadow .18s ease}.queued-task-actions :deep(.queued-task-action-details),.queued-task-actions :deep(.queued-task-action-resume),.queued-task-actions :deep(.queued-task-action-prioritize){background:var(--primary-alpha-10);color:var(--primary)}.queued-task-actions :deep(.queued-task-action-details:hover),.queued-task-actions :deep(.queued-task-action-details:focus-visible),.queued-task-actions :deep(.queued-task-action-resume:hover),.queued-task-actions :deep(.queued-task-action-resume:focus-visible),.queued-task-actions :deep(.queued-task-action-prioritize:hover),.queued-task-actions :deep(.queued-task-action-prioritize:focus-visible){background:var(--primary);color:#fff;box-shadow:0 4px 10px color-mix(in srgb,var(--primary) 22%,transparent)}.queued-task-actions :deep(.queued-task-action-pause){color:var(--text-secondary)}.queued-task-actions :deep(.queued-task-action-pause:hover),.queued-task-actions :deep(.queued-task-action-pause:focus-visible){background:var(--surface-card);color:var(--text-primary)}.queued-task-actions :deep(.queued-task-action-cancel){color:var(--danger)}.queued-task-actions :deep(.queued-task-action-cancel:hover),.queued-task-actions :deep(.queued-task-action-cancel:focus-visible){background:color-mix(in srgb,var(--danger) 10%,transparent);color:var(--danger)}.queued-task-pagination{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 2px 0;color:var(--text-tertiary);font-size:12px}.queued-task-pagination :deep(.el-pagination){min-width:0}@media(max-width:720px){.queue-heading-actions{align-items:flex-end;flex-direction:column}.queue-runtime{order:2}.queued-order-note{align-items:flex-start}.queued-task-pagination{align-items:flex-start;flex-direction:column}.queued-task-pagination :deep(.el-pagination){flex-wrap:wrap;justify-content:flex-start}.site-queue-list{grid-template-columns:1fr}.queue-overview-summary{grid-template-columns:1fr}.queue-overview-summary strong{font-size:18px}}@media(max-width:520px){.queue-runtime{width:100%}.queue-runtime span,.queue-runtime button{flex:1;justify-content:center}}
.muted-text{color:var(--text-tertiary);font-size:11px}
.task-filter-toolbar{display:flex;align-items:center;gap:10px;margin-bottom:16px;padding:12px 14px;border:1px solid var(--border-color-light);border-radius:14px;background:var(--surface-elevated)}.task-filter-toolbar :deep(.el-select){width:210px}.task-filter-toolbar .el-button{margin-left:0}@media(max-width:520px){.task-filter-toolbar{align-items:stretch;flex-direction:column}.task-filter-toolbar :deep(.el-select),.task-filter-toolbar .el-button{width:100%}}
.task-batch-bar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:14px;padding:11px 12px;border:1px solid color-mix(in srgb,var(--primary) 28%,var(--border-color-light));border-radius:15px;background:linear-gradient(105deg,var(--primary-alpha-10),color-mix(in srgb,var(--surface-elevated) 94%,var(--primary) 6%));box-shadow:0 8px 24px color-mix(in srgb,var(--primary) 8%,transparent)}.task-batch-summary{display:flex;min-width:0;align-items:center;gap:10px}.task-batch-summary>span{display:grid;width:36px;height:36px;flex:0 0 36px;place-items:center;border-radius:11px;background:var(--primary);color:#fff;font-size:14px;font-weight:800;box-shadow:0 6px 14px color-mix(in srgb,var(--primary) 25%,transparent)}.task-batch-summary>div{display:grid;min-width:0;gap:2px}.task-batch-summary strong{font-size:12px}.task-batch-summary small{overflow:hidden;color:var(--text-secondary);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.task-batch-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:7px}.task-batch-actions .el-button{margin-left:0}.task-batch-rise-enter-active,.task-batch-rise-leave-active{transition:opacity .18s ease,transform .22s cubic-bezier(.2,.8,.2,1)}.task-batch-rise-enter-from,.task-batch-rise-leave-to{opacity:0;transform:translateY(-8px)}@media(max-width:760px){.task-batch-bar{align-items:stretch;flex-direction:column}.task-batch-actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr))}.task-batch-actions :deep(.el-dropdown),.task-batch-actions :deep(.el-button){width:100%}}@media(max-width:440px){.task-batch-actions{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(prefers-reduced-motion:reduce){.task-batch-rise-enter-active,.task-batch-rise-leave-active{transition:none}}
:deep(.task-action-cluster){display:inline-flex;align-items:center;gap:4px;padding:4px;border:1px solid var(--border-color-light);border-radius:13px;background:color-mix(in srgb,var(--surface-elevated) 88%,transparent);box-shadow:0 3px 10px color-mix(in srgb,var(--text-primary) 5%,transparent);white-space:nowrap}:deep(.task-action-cluster .el-button){height:28px;margin-left:0;padding:5px 11px;border-color:transparent;background:transparent;font-size:11px;font-weight:700;transition:background .18s ease,color .18s ease,box-shadow .18s ease}:deep(.task-action-cluster .task-action-details),:deep(.task-action-cluster .task-action-resume){background:var(--primary-alpha-10);color:var(--primary)}:deep(.task-action-cluster .task-action-details:hover),:deep(.task-action-cluster .task-action-details:focus-visible),:deep(.task-action-cluster .task-action-resume:hover),:deep(.task-action-cluster .task-action-resume:focus-visible){background:var(--primary);color:#fff;box-shadow:0 4px 10px color-mix(in srgb,var(--primary) 22%,transparent)}:deep(.task-action-cluster .task-action-pause){color:var(--text-secondary)}:deep(.task-action-cluster .task-action-pause:hover),:deep(.task-action-cluster .task-action-pause:focus-visible){background:var(--surface-card);color:var(--text-primary)}:deep(.task-action-cluster .task-action-cancel){color:var(--danger)}:deep(.task-action-cluster .task-action-cancel:hover),:deep(.task-action-cluster .task-action-cancel:focus-visible){background:color-mix(in srgb,var(--danger) 10%,transparent);color:var(--danger)}:deep(.task-action-cluster .el-dropdown){display:inline-flex}:deep(.task-action-cluster .task-action-more){width:28px;padding:0;color:var(--text-tertiary)}:deep(.task-action-cluster .task-action-more:hover),:deep(.task-action-cluster .task-action-more:focus-visible){background:var(--surface-card);color:var(--text-primary)}
:deep(.crawler-book-action-cluster){display:inline-flex;align-items:center;gap:4px;padding:4px;border:1px solid var(--border-color-light);border-radius:13px;background:color-mix(in srgb,var(--surface-elevated) 88%,transparent);box-shadow:0 3px 10px color-mix(in srgb,var(--text-primary) 5%,transparent);white-space:nowrap}:deep(.crawler-book-action-cluster .el-button){height:28px;margin-left:0;padding:5px 10px;border-color:transparent;background:transparent;font-size:11px;font-weight:700;transition:background .18s ease,color .18s ease,box-shadow .18s ease}:deep(.crawler-book-action-cluster .crawler-book-action-details),:deep(.crawler-book-action-cluster .crawler-book-action-import){background:var(--primary-alpha-10);color:var(--primary)}:deep(.crawler-book-action-cluster .crawler-book-action-details:hover),:deep(.crawler-book-action-cluster .crawler-book-action-details:focus-visible),:deep(.crawler-book-action-cluster .crawler-book-action-import:hover),:deep(.crawler-book-action-cluster .crawler-book-action-import:focus-visible){background:var(--primary);color:#fff;box-shadow:0 4px 10px color-mix(in srgb,var(--primary) 22%,transparent)}:deep(.crawler-book-action-cluster .crawler-book-action-continue){color:var(--text-secondary)}:deep(.crawler-book-action-cluster .crawler-book-action-continue:hover),:deep(.crawler-book-action-cluster .crawler-book-action-continue:focus-visible){background:var(--surface-card);color:var(--text-primary)}:deep(.crawler-book-action-cluster .el-dropdown){display:inline-flex}:deep(.crawler-book-action-cluster .crawler-book-action-more){width:28px;padding:0;color:var(--text-tertiary)}:deep(.crawler-book-action-cluster .crawler-book-action-more:hover),:deep(.crawler-book-action-cluster .crawler-book-action-more:focus-visible){background:var(--surface-card);color:var(--text-primary)}
:deep(.favorite-action){width:28px!important;height:28px!important;padding:0!important;color:var(--text-tertiary)}:deep(.favorite-action:hover),:deep(.favorite-action:focus-visible){background:color-mix(in srgb,#f6c85f 16%,transparent)!important;color:#a66a00!important}:deep(.favorite-action.is-favorite){border-color:#d59b26!important;background:#f6c85f!important;color:#3b2500!important;font-weight:800}:deep(.favorite-action.is-favorite:hover),:deep(.favorite-action.is-favorite:focus-visible){border-color:#c88b12!important;background:#ffd978!important;color:#2d1b00!important}
:deep(.el-table__row .row-hover-action) {
  opacity: 0;
  pointer-events: none;
  transform: translateY(2px);
  transition: opacity .16s ease, transform .16s ease;
}

:deep(.el-table__row .favorite-action.row-hover-action.is-favorite),
:deep(.el-table__row:hover .row-hover-action),
:deep(.el-table__row .row-hover-action:focus-within) {
  opacity: 1;
  pointer-events: auto;
  transform: none;
}

@media (hover: none) {
  :deep(.el-table__row .row-hover-action) {
    opacity: 1;
    pointer-events: auto;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.el-table__row .row-hover-action) {
    transition: none;
  }
}
.book-batch-actions,.book-batch-task-actions,.book-batch-status-actions{display:flex;flex-wrap:wrap;align-items:center;justify-content:flex-end;gap:8px}.book-batch-status-actions{padding-left:8px;border-left:1px solid var(--border-color)}.book-batch-actions :deep(.el-select){width:170px}.book-batch-actions .el-button{margin-left:0}@media(max-width:760px){.book-batch-actions{width:100%;align-items:stretch;flex-direction:column}.book-batch-task-actions,.book-batch-status-actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));justify-content:stretch}.book-batch-status-actions{grid-template-columns:minmax(0,1fr) auto;padding:8px 0 0;border-top:1px solid var(--border-color);border-left:0}.book-batch-actions :deep(.el-select),.book-batch-actions .el-button{width:100%}}@media(max-width:440px){.book-batch-task-actions{grid-template-columns:minmax(0,1fr)}.book-batch-status-actions{grid-template-columns:minmax(0,1fr)}}
:deep(.task-failure-reason){display:flex;align-items:flex-start;gap:7px;width:100%;padding:7px 9px;border:1px solid color-mix(in srgb,var(--danger) 22%,transparent);border-radius:9px;background:color-mix(in srgb,var(--danger) 9%,transparent);color:var(--danger);cursor:help}:deep(.task-failure-reason:focus-visible){outline:2px solid var(--danger);outline-offset:2px}:deep(.task-failure-reason-icon){flex:0 0 auto;width:15px;height:15px;margin-top:2px}:deep(.task-failure-reason span){display:-webkit-box;overflow:hidden;color:var(--text-primary);font-size:12px;line-height:1.45;overflow-wrap:anywhere;-webkit-box-orient:vertical;-webkit-line-clamp:2}
:deep(.task-success-progress){display:flex;align-items:center;gap:10px;min-height:24px;color:var(--success)}:deep(.task-success-progress strong){font-size:13px}:deep(.task-success-progress span){color:var(--text-tertiary);font-size:11px}@keyframes crawler-progress-stripes{from{background-position:0 0}to{background-position:24px 0}}:deep(.crawler-running-progress .el-progress-bar__inner){background-color:var(--success)!important;background-image:linear-gradient(45deg,rgba(255,255,255,.38) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.38) 50%,rgba(255,255,255,.38) 75%,transparent 75%,transparent);background-size:24px 24px;animation:crawler-progress-stripes .7s linear infinite}:deep(.crawler-running-progress .el-progress__text){color:var(--success)}@media(prefers-reduced-motion:reduce){:deep(.crawler-running-progress .el-progress-bar__inner){animation:none}}
.site-protection{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:10px 12px;border:1px solid color-mix(in srgb,var(--warning) 22%,var(--border-color-light));border-radius:12px;background:color-mix(in srgb,var(--warning) 8%,var(--surface-card))}.site-protection>div{display:grid;min-width:0;gap:2px}.site-protection strong{color:var(--warning);font-size:12px}.site-protection span{overflow:hidden;color:var(--text-secondary);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.site-protection small{flex:0 0 auto;color:var(--text-tertiary);font-size:10px}.site-protection.cooling{border-color:color-mix(in srgb,var(--danger) 24%,var(--border-color-light));background:color-mix(in srgb,var(--danger) 7%,var(--surface-card))}.site-protection.cooling strong{color:var(--danger)}
@media(max-width:520px){.site-protection{align-items:stretch;flex-direction:column;gap:5px}.site-protection small{flex:auto}}

:deep(.chapter-reader-dialog .el-dialog__body){padding:0}.chapter-reader{--reader-bg:#eee5d2;--reader-surface:rgba(250,246,236,.94);--reader-solid:#faf6ec;--reader-ink:#30291f;--reader-muted:#766c5e;--reader-line:rgba(79,62,39,.16);--reader-accent:#8b4e2d;display:grid;height:100%;grid-template-rows:auto minmax(0,1fr) auto;overflow:hidden;border:0;border-radius:18px;background:radial-gradient(circle at 50% -30%,rgba(255,255,255,.82),transparent 44%),linear-gradient(135deg,rgba(115,83,44,.045) 25%,transparent 25%) 0 0/22px 22px,var(--reader-bg);box-shadow:0 24px 70px rgba(20,18,15,.26);color:var(--reader-ink)}.chapter-reader--light{--reader-bg:#f4f5f6;--reader-surface:rgba(255,255,255,.95);--reader-solid:#fff;--reader-ink:#202226;--reader-muted:#6c7078;--reader-line:rgba(24,30,40,.12);--reader-accent:#8a4b2b}.chapter-reader--night{--reader-bg:#17191c;--reader-surface:rgba(34,37,41,.96);--reader-solid:#222529;--reader-ink:#d8d2c6;--reader-muted:#918b82;--reader-line:rgba(255,255,255,.1);--reader-accent:#d4986d}.chapter-reader-toolbar{display:flex;min-height:48px;align-items:center;justify-content:space-between;gap:12px;padding:9px 14px;border-bottom:1px solid var(--reader-line);background:var(--reader-surface);backdrop-filter:blur(18px)}.chapter-reader-toolbar>div:first-child{display:grid;min-width:0;gap:2px}.chapter-reader-toolbar>div:first-child>span{color:var(--reader-accent);font-size:9px;font-weight:900;letter-spacing:.14em}.chapter-reader-toolbar p{overflow:hidden;color:var(--reader-muted);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.chapter-reader-actions{display:flex;flex:0 0 auto;align-items:center;gap:7px}.chapter-reader-actions a,.chapter-reader-actions button{padding:7px 10px;border:1px solid var(--reader-line);border-radius:9px;background:transparent;color:var(--reader-muted);font-family:inherit;font-size:11px;text-decoration:none;cursor:pointer}.chapter-reader-actions button{color:var(--reader-ink);font-weight:700}.chapter-reader-actions a:hover,.chapter-reader-actions button:hover,.chapter-reader-actions a:focus-visible,.chapter-reader-actions button:focus-visible{border-color:var(--reader-accent);outline:none;color:var(--reader-accent)}.chapter-reader-stage{position:relative;height:auto;min-height:0;overflow:hidden}.chapter-reader-surface{height:100%;overflow:auto;scrollbar-color:var(--reader-line) transparent}.chapter-reader-surface article{width:min(var(--chapter-reader-content-width),calc(100% - 56px));min-height:100%;box-sizing:border-box;margin:0 auto;padding:42px 0 70px}.chapter-reader-kicker{margin:0 0 10px!important;color:var(--reader-accent)!important;font-size:10px!important;font-weight:800;letter-spacing:.12em;text-align:center;text-indent:0!important}.chapter-reader-surface h2{margin:0;color:var(--reader-ink);font:600 clamp(24px,4vw,34px)/1.35 'Iowan Old Style','Songti SC',serif;text-align:center}.chapter-reader-rule{display:flex;align-items:center;justify-content:center;gap:12px;margin:20px auto 30px;color:var(--reader-accent);font-size:7px}.chapter-reader-rule::before,.chapter-reader-rule::after{width:62px;height:1px;background:linear-gradient(90deg,transparent,var(--reader-line));content:''}.chapter-reader-rule::after{background:linear-gradient(90deg,var(--reader-line),transparent)}.chapter-reader-paragraph{margin:0 0 1.05em;color:var(--reader-ink);font-family:var(--chapter-reader-font-family);font-size:var(--chapter-reader-font-size);line-height:var(--chapter-reader-line-height);letter-spacing:.025em;text-align:justify;text-indent:2em;overflow-wrap:anywhere}.chapter-reader-empty{padding:50px 0;color:var(--reader-muted);text-align:center}.chapter-reader-state{display:grid;height:100%;place-content:center;place-items:center;gap:12px;color:var(--reader-muted)}.chapter-reader-state i{width:28px;height:28px;border:2px solid var(--reader-line);border-top-color:var(--reader-accent);border-radius:50%;animation:chapter-reader-spin .8s linear infinite}.chapter-reader-settings{position:absolute;inset:0 0 0 auto;z-index:2;width:min(310px,88%);overflow:auto;border-left:1px solid var(--reader-line);background:var(--reader-surface);box-shadow:-14px 0 38px rgba(30,24,18,.13);backdrop-filter:blur(22px)}.chapter-reader-settings-heading{display:flex;align-items:center;justify-content:space-between;padding:18px 18px 12px}.chapter-reader-settings-heading small{color:var(--reader-accent);font-size:9px;font-weight:900;letter-spacing:.15em}.chapter-reader-settings-heading h3{margin:3px 0 0;color:var(--reader-ink);font:600 21px 'Iowan Old Style','Songti SC',serif}.chapter-reader-settings-heading>button{border:0;background:transparent;color:var(--reader-muted);font-size:24px;cursor:pointer}.chapter-reader-setting{display:grid;gap:10px;padding:14px 18px;border-top:1px solid var(--reader-line)}.chapter-reader-setting>label{display:flex;justify-content:space-between;color:var(--reader-muted);font-size:12px}.chapter-reader-setting b{color:var(--reader-ink)}.chapter-reader-setting input{width:100%;accent-color:var(--reader-accent)}.chapter-reader-theme{position:relative;display:grid;grid-template-columns:repeat(3,1fr);padding:3px;border:1px solid var(--reader-line);border-radius:11px;background:color-mix(in srgb,var(--reader-bg) 72%,transparent);isolation:isolate}.chapter-reader-theme>span{position:absolute;inset:3px auto 3px 3px;z-index:-1;width:calc((100% - 6px)/3);border-radius:8px;background:var(--reader-solid);box-shadow:0 2px 8px rgba(0,0,0,.08);transition:transform .22s ease}.chapter-reader-theme button{padding:8px 4px;border:0;background:transparent;color:var(--reader-muted);font-size:11px;cursor:pointer}.chapter-reader-theme button.active{color:var(--reader-accent);font-weight:800}.chapter-reader-widths{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.chapter-reader-widths button{padding:8px;border:1px solid var(--reader-line);border-radius:9px;background:transparent;color:var(--reader-muted);cursor:pointer}.chapter-reader-widths button.active{border-color:var(--reader-accent);background:color-mix(in srgb,var(--reader-accent) 9%,transparent);color:var(--reader-accent)}.chapter-reader-settings>p{margin:12px 18px 18px;color:var(--reader-muted);font-size:11px;line-height:1.6}.chapter-reader-navigation{display:grid;grid-template-columns:110px minmax(100px,220px) 110px;align-items:center;justify-content:center;gap:18px;padding:8px 12px;border-top:1px solid var(--reader-line);background:var(--reader-surface);backdrop-filter:blur(18px)}.chapter-reader-navigation button{display:flex;align-items:center;justify-content:center;gap:8px;padding:7px;border:0;background:transparent;color:var(--reader-ink);cursor:pointer}.chapter-reader-navigation button:disabled{opacity:.28;cursor:not-allowed}.chapter-reader-navigation button span{font-size:18px}.chapter-reader-navigation button small,.chapter-reader-navigation>div small{color:var(--reader-muted);font-size:10px}.chapter-reader-navigation>div{display:grid;gap:5px;text-align:center}.chapter-reader-progress{height:3px;overflow:hidden;border-radius:99px;background:var(--reader-line)}.chapter-reader-progress i{display:block;height:100%;border-radius:inherit;background:var(--reader-accent);transition:width .25s ease}.chapter-settings-slide-enter-active,.chapter-settings-slide-leave-active{transition:transform .24s ease,opacity .2s ease}.chapter-settings-slide-enter-from,.chapter-settings-slide-leave-to{transform:translateX(100%);opacity:0}@keyframes chapter-reader-spin{to{transform:rotate(360deg)}}
@media(max-width:620px){:deep(.chapter-reader-dialog){width:96vw!important}.chapter-reader-toolbar{align-items:flex-start}.chapter-reader-actions a{display:none}.chapter-reader-actions button span{display:none}.chapter-reader-stage{height:auto;min-height:0}.chapter-reader-surface article{width:calc(100% - 32px);padding:34px 0 60px}.chapter-reader-navigation{grid-template-columns:76px minmax(80px,1fr) 76px;gap:5px}.chapter-reader-navigation button small{display:none}}
@media(prefers-reduced-motion:reduce){.chapter-reader-theme>span,.chapter-reader-progress i,.chapter-settings-slide-enter-active,.chapter-settings-slide-leave-active{transition:none}.chapter-reader-state i{animation:none}}
:global(.chapter-reader-dialog){--el-dialog-padding-primary:0;height:80vh;height:80dvh;margin:10vh auto 0!important;overflow:hidden;border:0!important;border-radius:18px!important;background:transparent!important;box-shadow:none!important}:global(.chapter-reader-dialog .el-dialog__header){display:none}:global(.chapter-reader-dialog .el-dialog__body){height:100%;padding:0!important}.chapter-reader-heading{display:grid;min-width:0;gap:3px}.chapter-reader-heading strong{overflow:hidden;color:var(--reader-ink);font:600 16px/1.25 'Iowan Old Style','Songti SC',serif;text-overflow:ellipsis;white-space:nowrap}.chapter-reader-heading p span{color:var(--reader-accent);font-size:9px;font-weight:900;letter-spacing:.12em}.chapter-reader-actions .chapter-reader-close{display:grid;width:32px;height:32px;padding:0;place-items:center;font-size:21px;font-weight:400;line-height:1}
:deep(.task-action-prioritize) { color: var(--primary); }
:deep(.task-action-prioritize:hover),
:deep(.task-action-prioritize:focus-visible) {
  background: var(--primary-alpha-10);
  color: var(--primary);
}
</style>
