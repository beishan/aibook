<template>
  <div class="home-view">
    <header class="home-welcome">
      <div class="welcome-copy">
        <el-icon>
          <House />
        </el-icon>
        <span>
          欢迎回来，{{ displayName }}
        </span>
        <span class="welcome-divider">
          /
        </span>
        <span class="welcome-quote">
          在书海中，遇见更大的自己
          <span aria-hidden="true">
            ✦
          </span>
        </span>
      </div>
      <div class="home-status">
        <span class="status-dot" :class="{ 'is-error': homeError }" />
        <span>
          {{ homeError ? '书库暂不可用' : loading ? '正在同步书库' : '私人书库 · 已同步' }}
        </span>
        <button type="button" class="icon-action" :disabled="loading" aria-label="刷新主页" @click="refreshHome">
          <el-icon :class="{ spinning: loading }">
            <Refresh />
          </el-icon>
        </button>
      </div>
    </header>
    <div v-if="homeError" class="home-error" role="alert">
      <span>
        {{ homeError }}
      </span>
      <button type="button" class="text-action" @click="loadDashboard">
        重试
      </button>
    </div>
    <div class="home-columns">
      <aside class="library-rail surface" aria-label="书库分类与快捷工具">
        <nav class="category-list" aria-label="按分类浏览书库">
          <router-link to="/books" class="category-link category-all">
            <el-icon>
              <Collection />
            </el-icon>
            <span>
              全部书籍
            </span>
            <small>
              {{ number(dashboard?.overview.totalBooks) }}
            </small>
          </router-link>
          <router-link
          v-for="(category, index) in dashboard?.categories || []"
          :key="category.id"
          :to="{ path: '/books', query: { categoryId: category.id, includeChildren: 'false' } }"
          class="category-link"
          :title="`${category.name}：${category.count} 本直属书籍`"
          >
            <el-icon>
              <component :is="categoryIcons[index % categoryIcons.length]" />
            </el-icon>
            <span>
              {{ category.name }}
            </span>
            <small>
              {{ number(category.count) }}
            </small>
          </router-link>
          <p v-if="!loading && !dashboard?.categories.length" class="rail-empty">
            分类整理后，会在这里展示。
          </p>
          <router-link to="/settings?tab=categories" class="category-manage">
            <el-icon>
              <Setting />
            </el-icon>
            管理分类
            <span>
              ›
            </span>
          </router-link>
        </nav>
        <section class="library-volume" aria-label="书库概览">
          <div class="volume-heading">
            <el-icon>
              <Files />
            </el-icon>
            <div>
              <p>
                书库文件
              </p>
              <strong>
                {{ dashboard ? homeFileSize(dashboard.overview.fileSizeBytes) : '—' }}
              </strong>
              <small>
                主版本文件合计
              </small>
            </div>
          </div>
          <div class="volume-progress-label">
            <span>
              阅读完成率
            </span>
            <span>
              {{ completionRate }}%
            </span>
          </div>
          <div class="progress-track" role="progressbar" aria-label="阅读完成率" :aria-valuenow="completionRate" aria-valuemin="0" aria-valuemax="100">
            <span :style="{ width: `${completionRate}%` }" />
          </div>
          <p class="volume-footnote">
            一本一本，读成自己的世界。
          </p>
        </section>
        <nav class="quick-tools" aria-label="书库快捷工具">
          <router-link v-for="tool in quickTools" :key="tool.path" :to="tool.path">
            <span>
              <el-icon>
                <component :is="tool.icon" />
              </el-icon>
            </span>
            <small>
              {{ tool.label }}
            </small>
          </router-link>
        </nav>
      </aside>
      <div class="home-main">
        <section class="reading-hero" aria-label="阅读精选">
          <div class="hero-shade" />
          <router-link v-if="heroBook" :to="`/books/${heroBook.id}`" class="hero-cover" :aria-label="`查看《${displayBookName(heroBook.title)}》详情`">
            <HomeBookCover :book="heroBook" eager />
          </router-link>
          <div class="hero-copy" :class="{ 'without-cover': !heroBook }">
            <p class="hero-eyebrow">
              私 人 藏 书 · 日 有 所 读
            </p>
            <h1>
              沉浸书海，随心而读
            </h1>
            <p class="hero-subtitle">
              让每一本珍藏，都有自己的位置
            </p>
            <p v-if="heroBook" class="hero-book-name">
              {{ displayBookName(heroBook.title) }}
              <span>
                ／ {{ heroBook.author || '佚名' }}
              </span>
            </p>
            <div class="hero-actions">
              <router-link :to="heroBook ? `/reader/${heroBook.id}` : '/books'" class="hero-primary">
                {{ heroBook ? heroBook.lastReadAt ? '继续阅读' : '开始阅读' : '开启阅读' }}
                <el-icon>
                  <ArrowRight />
                </el-icon>
              </router-link>
              <router-link to="/books" class="hero-secondary">
                <el-icon>
                  <Reading />
                </el-icon>
                浏览书库
              </router-link>
            </div>
          </div>
          <div v-if="heroBooks.length > 1" class="hero-controls" aria-label="切换阅读精选">
            <button type="button" aria-label="上一本精选" @click="moveHero(-1)">
              <el-icon>
                <ArrowLeft />
              </el-icon>
            </button>
            <span>
              {{ heroIndex + 1 }}
              <i>
                /
              </i>
              {{ heroBooks.length }}
            </span>
            <button type="button" aria-label="下一本精选" @click="moveHero(1)">
              <el-icon>
                <ArrowRight />
              </el-icon>
            </button>
          </div>
        </section>
        <section class="continue-section" aria-labelledby="continue-heading">
          <header class="section-heading">
            <h2 id="continue-heading">
              <el-icon>
                <Reading />
              </el-icon>
              继续阅读
            </h2>
            <router-link to="/shelf">
              我的书架
              <span>
                ›
              </span>
            </router-link>
          </header>
          <div v-if="loading && !dashboard" class="continue-grid" aria-label="正在加载阅读进度">
            <div v-for="index in 3" :key="index" class="continue-card skeleton" />
          </div>
          <div v-else-if="dashboard?.continuing.length" class="continue-grid">
            <router-link v-for="book in dashboard.continuing" :key="book.id" :to="`/reader/${book.id}`" class="continue-card surface">
              <HomeBookCover :book="book" compact />
              <div class="continue-info">
                <div class="continue-title">
                  <h3 :title="displayBookName(book.title)">
                    {{ displayBookName(book.title) }}
                  </h3>
                  <span>
                    继续阅读
                  </span>
                </div>
                <p class="book-author">
                  {{ book.author || '未知作者' }}
                </p>
                <p class="chapter-title">
                  {{ book.currentChapterTitle || '从上次的位置继续' }}
                </p>
                <div class="continue-progress">
                  <div class="progress-track" role="progressbar" :aria-label="`${displayBookName(book.title)}阅读进度`" :aria-valuenow="readingPercent(book.totalProgress)" aria-valuemin="0" aria-valuemax="100">
                    <span :style="{ width: `${readingPercent(book.totalProgress)}%` }" />
                  </div>
                  <small>
                    {{ book.totalProgress == null ? '—' : `${readingPercent(book.totalProgress)}%` }}
                  </small>
                </div>
              </div>
            </router-link>
          </div>
          <div v-else class="section-empty surface">
            {{ homeError ? '阅读进度暂未加载' : '翻开一本书，这里会为你留住阅读的位置。' }}
            <router-link to="/books">
              去找一本书 ›
            </router-link>
          </div>
        </section>
        <section class="recent-added-section surface" aria-labelledby="added-heading">
          <header class="section-heading">
            <h2 id="added-heading">
              <el-icon>
                <MagicStick />
              </el-icon>
              最近入库
            </h2>
            <router-link to="/books">
              查看全部
              <span>
                ›
              </span>
            </router-link>
          </header>
          <div v-if="loading && !dashboard" class="home-book-grid" aria-label="正在加载最近入库书籍">
            <div v-for="index in 6" :key="index" class="book-skeleton skeleton" />
          </div>
          <div v-else-if="dashboard?.recentlyAdded.length" class="home-book-grid">
            <router-link v-for="book in dashboard.recentlyAdded" :key="book.id" :to="`/books/${book.id}`" class="shelf-book">
              <HomeBookCover :book="book" />
              <h3 :title="displayBookName(book.title)">
                {{ displayBookName(book.title) }}
              </h3>
              <p class="book-author" :title="book.author">
                {{ book.author || '未知作者' }}
              </p>
              <span class="format-label">
                {{ book.format?.toUpperCase() }}
              </span>
              <p class="book-rating" :aria-label="book.rating ? `评分 ${book.rating} 分，满分 5 分` : '未评分'">
                <template v-if="book.rating">
                  <span aria-hidden="true">
                    {{ '★'.repeat(Math.max(0, Math.min(5, Math.round(book.rating)))) }}
                  </span>
                  <small>
                    {{ book.rating.toFixed(1) }}
                  </small>
                </template>
                <small v-else>
                  未评分
                </small>
              </p>
            </router-link>
          </div>
          <div v-else class="section-empty">
            {{ homeError ? '书籍暂未加载' : '书库还在等待第一本珍藏。' }}
            <router-link to="/books">
              前往导入 ›
            </router-link>
          </div>
        </section>
        <section class="recommendation-section surface" aria-labelledby="recommendation-heading">
          <header class="section-heading">
            <h2 id="recommendation-heading">
              <el-icon>
                <StarFilled />
              </el-icon>
              为你推荐
            </h2>
            <div class="section-actions">
              <button v-if="recommendationGroup" type="button" class="text-action" :disabled="recommendationsLoading" @click="rerollRecommendation">
                <el-icon>
                  <Refresh />
                </el-icon>
                换一批
              </button>
              <router-link to="/recommendations">
                全部推荐
                <span>
                  ›
                </span>
              </router-link>
            </div>
          </header>
          <p v-if="recommendationGroup" class="recommendation-caption">
            {{ recommendationGroup.rule.name }} · {{ recommendations?.date }} · {{ recommendationGroup.books.length }} 本今日推荐
          </p>
          <div v-if="recommendationsLoading && !recommendations" class="section-empty" role="status">
            正在准备今日推荐…
          </div>
          <div v-else-if="recommendationError" class="section-empty" role="alert">
            {{ recommendationError }}
            <button type="button" class="text-action" @click="loadRecommendations">
              重试
            </button>
          </div>
          <template v-else-if="recommendationGroup">
            <p v-if="recommendationGroup.repeatedCount || recommendationGroup.invalidCount" class="recommendation-caption">
              {{ recommendationGroup.invalidCount ? '部分书籍已不再可见，可换一批补充。' : '候选不足，已优先补入最久未推荐的书籍。' }}
            </p>
            <div v-if="recommendationGroup.books.length" class="home-book-grid">
              <router-link v-for="book in recommendationGroup.books.slice(0, 6)" :key="book.id" :to="`/books/${book.id}`" class="shelf-book">
                <HomeBookCover :book="book" />
                <h3 :title="displayBookName(book.title)">
                  {{ displayBookName(book.title) }}
                </h3>
                <p class="book-author">
                  {{ book.author || '未知作者' }}
                </p>
                <span class="format-label">
                  {{ book.format?.toUpperCase() }}
                </span>
              </router-link>
            </div>
            <div v-else class="section-empty">
              当前规则没有可推荐的书籍。
              <router-link to="/recommendations">
                调整推荐规则 ›
              </router-link>
            </div>
          </template>
          <div v-else class="recommendation-intro">
            <el-icon>
              <StarFilled />
            </el-icon>
            <div>
              <h3>
                给阅读留一点偶然
              </h3>
              <p>
                按分类、标签和阅读偏好，每天遇见几本书。
              </p>
            </div>
            <router-link to="/recommendations" class="small-primary">
              设置推荐规则
              <span>
                ›
              </span>
            </router-link>
          </div>
        </section>
      </div>
      <aside class="home-right" aria-label="阅读概览与书库动态">
        <section class="surface reading-overview">
          <header class="section-heading">
            <h2>
              <el-icon>
                <DataAnalysis />
              </el-icon>
              阅读概览
            </h2>
            <router-link to="/statistics">
              更多 ›
            </router-link>
          </header>
          <div class="overview-numbers">
            <router-link to="/books">
              <strong>
                {{ number(dashboard?.overview.totalBooks) }}
              </strong>
              <span>
                册藏书
              </span>
            </router-link>
            <router-link to="/statistics">
              <strong>
                {{ number(dashboard?.overview.finishedBooks) }}
              </strong>
              <span>
                本已读
              </span>
            </router-link>
            <router-link to="/shelf">
              <strong>
                {{ number(dashboard?.overview.readingBooks) }}
              </strong>
              <span>
                本在读
              </span>
            </router-link>
          </div>
          <header class="section-heading activity-heading">
            <h2>
              <el-icon>
                <Clock />
              </el-icon>
              最近阅读
            </h2>
            <router-link to="/statistics">
              阅读统计 ›
            </router-link>
          </header>
          <div v-if="!dashboard?.recentlyRead.length" class="aside-empty">
            {{ loading ? '正在加载…' : homeError ? '阅读记录暂未加载' : '还没有阅读记录' }}
          </div>
          <router-link v-for="book in dashboard?.recentlyRead || []" :key="book.id" :to="`/reader/${book.id}`" class="activity-item">
            <HomeBookCover :book="book" compact />
            <div>
              <p class="activity-title">
                {{ displayBookName(book.title) }}
              </p>
              <p class="activity-detail">
                {{ book.readingStatus === 'FINISHED' ? '已读完' : book.currentChapterTitle || '继续上次阅读' }}
              </p>
              <time>
                {{ formatDate(book.lastReadAt) }}
              </time>
            </div>
          </router-link>
        </section>
        <section class="surface updates-panel">
          <header class="section-heading">
            <h2>
              <el-icon>
                <Refresh />
              </el-icon>
              最近更新
            </h2>
            <router-link :to="{ path: '/books', query: { sortBy: 'updatedAt' } }">
              更多 ›
            </router-link>
          </header>
          <p class="aside-caption">
            书库条目最近修改
          </p>
          <div v-if="!dashboard?.recentlyUpdated.length" class="aside-empty">
            {{ loading ? '正在加载…' : homeError ? '更新记录暂未加载' : '暂无书籍更新' }}
          </div>
          <router-link v-for="book in dashboard?.recentlyUpdated || []" :key="book.id" :to="`/books/${book.id}`" class="activity-item update-item">
            <HomeBookCover :book="book" compact />
            <div>
              <p class="activity-title">
                {{ displayBookName(book.title) }}
              </p>
              <p class="activity-detail">
                {{ book.author || '未知作者' }}
              </p>
              <time>
                {{ formatDate(book.updatedAt) }}
              </time>
            </div>
          </router-link>
        </section>
        <section class="surface wanted-panel">
          <header class="section-heading">
            <h2>
              <el-icon>
                <Star />
              </el-icon>
              想读清单
            </h2>
            <router-link to="/shelf">
              我的书架 ›
            </router-link>
          </header>
          <div v-if="!dashboard?.wanted.length" class="aside-empty">
            {{ loading ? '正在加载…' : homeError ? '清单暂未加载' : '在书籍详情标记“想读”，留待下一次翻开。' }}
          </div>
          <router-link v-for="book in dashboard?.wanted || []" :key="book.id" :to="`/books/${book.id}`" class="wanted-row">
            <span>
              {{ displayBookName(book.title) }}
            </span>
            <small>
              {{ book.author || '未知作者' }}
            </small>
            <span>
              ›
            </span>
          </router-link>
        </section>
        <p class="home-note">
          书在身边，世界就在眼前。
          <router-link to="/settings?tab=guide">
            使用说明 ›
          </router-link>
        </p>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  ArrowLeft, ArrowRight, Clock, Collection, DataAnalysis, Files, Folder, House,
  MagicStick, Reading, Refresh, Setting, Star, StarFilled, Switch, Tools, Upload, EditPen,
} from '@element-plus/icons-vue'
import HomeBookCover from '@/components/HomeBookCover.vue'
import { homeApi, homeFileSize, readingPercent, type HomeDashboard } from '@/utils/home'
import { recommendationApi, recommendationDate, type TodayRecommendations } from '@/utils/recommendations'
import { formatChinaDateTime } from '@/utils/dateTime'
import { useUserStore } from '@/stores/user'

const { displayBookName } = useBookNameDisplay()

const userStore = useUserStore()
const dashboard = ref<HomeDashboard | null>(null)
const recommendations = ref<TodayRecommendations | null>(null)
const loading = ref(false)
const recommendationsLoading = ref(false)
const homeError = ref('')
const recommendationError = ref('')
const heroIndex = ref(0)
const displayName = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || '书友')
const recommendationGroup = computed(() => recommendations.value?.groups[0])
const categoryIcons = [Collection, MagicStick, Reading, Folder, Star, Files, DataAnalysis, Clock]
const quickTools = [
  { path: '/books', label: '书籍导入', icon: Upload },
  { path: '/format-conversion', label: '格式转换', icon: Switch },
  { path: '/text-repair', label: '文本修复', icon: Tools },
  { path: '/rewrite', label: '书籍重写', icon: EditPen },
]
const heroBooks = computed(() => {
  const books = [...(dashboard.value?.continuing || []), ...(dashboard.value?.recentlyAdded || [])]
  const unique = new Map<number, (typeof books)[number]>()
  for (const book of books) {
    if (!unique.has(book.id)) unique.set(book.id, book)
  }
  return [...unique.values()].slice(0, 4)
})
const heroBook = computed(() => heroBooks.value[heroIndex.value % Math.max(1, heroBooks.value.length)])
const completionRate = computed(() => {
  const overview = dashboard.value?.overview
  return overview?.totalBooks ? readingPercent(overview.finishedBooks / overview.totalBooks * 100) : 0
})
let mounted = false
let dateTimer: number | undefined
const number = (value?: number) => value == null ? '—' : new Intl.NumberFormat('zh-CN').format(value)
const errorText = (error: any, fallback: string) => error.response?.data?.message || fallback

async function loadDashboard() {
  if (loading.value) return
  loading.value = true
  homeError.value = ''
  try {
    const data = await homeApi.dashboard()
    if (mounted) {
      dashboard.value = data
      heroIndex.value = 0
    }
  } catch (error) {
    if (mounted) homeError.value = errorText(error, '主页数据加载失败，请重试')
  } finally {
    loading.value = false
  }
}
async function loadRecommendations() {
  if (recommendationsLoading.value) return
  recommendationsLoading.value = true
  recommendationError.value = ''
  try {
    const data = await recommendationApi.today()
    if (mounted) recommendations.value = data
  } catch (error) {
    if (mounted) recommendationError.value = errorText(error, '今日推荐暂未加载，请重试')
  } finally {
    recommendationsLoading.value = false
  }
}
async function refreshHome() {
  await Promise.allSettled([loadDashboard(), loadRecommendations()])
}
async function rerollRecommendation() {
  const id = recommendationGroup.value?.rule.id
  if (!id || recommendationsLoading.value) return
  recommendationsLoading.value = true
  recommendationError.value = ''
  try {
    const group = await recommendationApi.reroll(id)
    if (mounted && recommendations.value) {
      recommendations.value.groups = recommendations.value.groups.map(item => item.rule.id === id ? group : item)
      recommendations.value.date = group.date
    }
  } catch (error) {
    if (mounted) recommendationError.value = errorText(error, '换一批失败，请重试')
  } finally {
    recommendationsLoading.value = false
  }
}
function moveHero(step: number) {
  const length = heroBooks.value.length
  if (length) heroIndex.value = (heroIndex.value + step + length) % length
}
function formatDate(value?: string) {
  return value ? formatChinaDateTime(value, {
    month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }) : '时间未记录'
}

function checkRecommendationDate() {
  if (document.visibilityState === 'visible' && recommendations.value
      && recommendations.value.date !== recommendationDate()) void loadRecommendations()
}
function syncDateTimer() {
  if (dateTimer !== undefined) window.clearInterval(dateTimer)
  dateTimer = undefined
  if (document.visibilityState === 'visible') {
    checkRecommendationDate()
    dateTimer = window.setInterval(checkRecommendationDate, 60000)
  }
}
onMounted(() => {
  mounted = true
  void refreshHome()
  document.addEventListener('visibilitychange', syncDateTimer)
  syncDateTimer()
})
onUnmounted(() => {
  mounted = false
  if (dateTimer !== undefined) window.clearInterval(dateTimer)
  document.removeEventListener('visibilitychange', syncDateTimer)
})
</script>

<style scoped>
.home-view {
  --home-gap: 16px;
  width: 100%;
  max-width: 1760px;
  margin: 0 auto;
  color: var(--text-primary);
}
.home-view a {
  text-decoration: none;
  color: inherit;
}
.home-view button {
  font: inherit;
}
.home-view a:focus-visible,
.home-view button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 4px;
}
.home-welcome,
.welcome-copy,
.home-status {
  display: flex;
  align-items: center;
}
.home-welcome {
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 0 8px 18px;
  font-size: 12px;
  color: var(--text-on-page-bg-secondary);
}
.welcome-copy {
  flex-wrap: wrap;
  gap: 12px;
}
.welcome-copy > .el-icon {
  font-size: 16px;
}
.welcome-divider {
  opacity: .4;
}
.welcome-quote > span {
  padding-left: 5px;
  color: #c89047;
}
.home-status {
  gap: 8px;
  font-size: 11px;
}
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--success);
}
.status-dot.is-error {
  background: var(--danger);
}
.icon-action,
.text-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 4px;
  color: var(--text-secondary);
  background: transparent;
  border: 0;
  cursor: pointer;
}
.icon-action:disabled,
.text-action:disabled {
  cursor: wait;
  opacity: .5;
}
.home-error {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  margin-bottom: 16px;
  border: 1px solid var(--danger);
  border-radius: 8px;
  color: var(--danger);
  background: var(--surface-card);
}
.home-columns {
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr) 280px;
  align-items: start;
  gap: var(--home-gap);
}
.surface {
  border: 1px solid var(--border-color-light);
  border-radius: 8px;
  background: var(--surface-card);
  box-shadow: 0 3px 12px rgb(0 0 0 / 2%);
}
.library-rail {
  position: sticky;
  top: 80px;
  overflow: hidden;
  padding: 12px 8px 16px;
}
.category-link {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 10px 12px;
  border-radius: 5px;
  font-size: 13px;
  transition: background .18s ease;
}
.category-link > .el-icon {
  flex-shrink: 0;
  font-size: 19px;
}
.category-link > span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.category-link small {
  color: var(--text-secondary);
  font-size: 11px;
}
.home-view .category-all {
  color: var(--primary);
  background: var(--primary-alpha-10);
  font-weight: 600;
}
.category-link:hover {
  background: var(--primary-alpha-10);
}
.category-manage {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 12px 10px;
  color: var(--text-secondary) !important;
  font-size: 11px;
}
.category-manage > span {
  margin-left: auto;
}
.rail-empty {
  padding: 16px 12px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.library-volume {
  margin: 16px 10px 0;
  padding: 22px 0 8px;
  border-top: 1px solid var(--border-color-light);
}
.volume-heading {
  display: flex;
  gap: 12px;
  align-items: center;
}
.volume-heading > .el-icon {
  font-size: 25px;
}
.volume-heading p {
  margin: 0 0 5px;
  color: var(--text-secondary);
  font-size: 12px;
}
.volume-heading strong {
  color: var(--primary);
  font-size: 16px;
}
.volume-heading small {
  display: block;
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 10px;
}
.volume-progress-label {
  display: flex;
  justify-content: space-between;
  margin: 20px 0 8px;
  color: var(--text-secondary);
  font-size: 10px;
}
.progress-track {
  height: 4px;
  overflow: hidden;
  border-radius: 8px;
  background: var(--border-color-light);
}
.progress-track > span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--primary);
  transition: width .25s ease;
}
.volume-footnote {
  margin: 12px 0;
  color: var(--text-tertiary);
  font-size: 10px;
  line-height: 1.7;
}
.quick-tools {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 6px;
  padding: 22px 4px 4px;
  margin-top: 14px;
  border-top: 1px solid var(--border-color-light);
}
.quick-tools a {
  display: grid;
  justify-items: center;
  gap: 7px;
}
.quick-tools a > span {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 12px;
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-size: 17px;
}
.quick-tools small {
  color: var(--text-secondary);
  font-size: 10px;
}
.home-main,
.home-right {
  min-width: 0;
}
.reading-hero {
  position: relative;
  display: flex;
  align-items: center;
  min-height: clamp(240px, 18vw, 290px);
  padding: 32px 5%;
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 7px;
  background: #343c37 url('../assets/home-reading-landscape.webp') center 55% / cover;
  color: #fff8ef;
  isolation: isolate;
}
.hero-shade {
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(90deg, rgb(12 23 20 / 12%) 0%, rgb(12 23 20 / 18%) 25%, rgb(12 23 20 / 67%) 58%, rgb(12 23 20 / 52%) 100%);
}
.hero-cover {
  display: block;
  flex: 0 0 19%;
  max-width: 152px;
  margin: 0 8% 0 4%;
  transform: perspective(700px) rotateY(13deg) rotateZ(-3deg);
  filter: drop-shadow(7px 9px 9px rgb(0 0 0 / 40%));
}
.hero-copy {
  flex: 0 1 56%;
  min-width: 0;
  margin-left: auto;
}
.hero-copy.without-cover {
  padding: 16px 4%;
}
.hero-eyebrow {
  margin: 0 0 16px;
  font-size: 10px;
  letter-spacing: .2em;
  opacity: .8;
}
.hero-copy h1 {
  margin: 0;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: clamp(26px, 2.5vw, 43px);
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: .05em;
  text-wrap: balance;
  text-shadow: 0 2px 12px rgb(0 0 0 / 24%);
}
.hero-subtitle {
  margin: 10px 0 0;
  font-family: "Songti SC", serif;
  font-size: clamp(12px, 1.1vw, 16px);
  letter-spacing: .16em;
  opacity: .87;
}
.hero-book-name {
  margin: 16px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  opacity: .8;
}
.hero-book-name span {
  opacity: .8;
}
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}
.hero-actions a {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 10px 18px;
  border: 1px solid rgb(255 255 255 / 64%);
  border-radius: 5px;
  color: #fff;
  font-size: 12px;
}
.hero-primary {
  background: var(--primary);
}
.hero-secondary {
  background: rgb(0 0 0 / 16%);
}
.hero-actions a:hover {
  filter: brightness(1.12);
}
.hero-controls {
  position: absolute;
  right: 18px;
  bottom: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 10px;
}
.hero-controls > span > i {
  padding: 0 3px;
  opacity: .4;
  font-style: normal;
}
.hero-controls .el-icon {
  font-size: 12px;
}
.hero-controls button {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 1px solid rgb(255 255 255 / 30%);
  border-radius: 50%;
  color: inherit;
  background: rgb(0 0 0 / 15%);
  cursor: pointer;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding: 17px 14px 12px;
}
.section-heading h2 {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0;
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: 17px;
  font-weight: 600;
  white-space: nowrap;
}
.section-heading h2 > .el-icon {
  color: var(--primary);
  font-size: 19px;
}
.section-heading > a,
.section-actions a,
.section-actions button {
  color: var(--text-secondary);
  font-size: 10px;
  white-space: nowrap;
}
.section-heading > a > span,
.section-actions a > span {
  padding-left: 6px;
}
.continue-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.continue-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  min-height: 103px;
  padding: 12px;
  transition: border-color .18s ease;
}
.continue-card:hover {
  border-color: var(--primary);
}
.continue-card > :deep(.home-book-cover) {
  flex: 0 0 48px;
  width: 48px;
}
.continue-info {
  flex: 1;
  min-width: 0;
}
.continue-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.continue-title h3 {
  overflow: hidden;
  margin: 0;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
}
.continue-title > span {
  flex-shrink: 0;
  padding: 4px 5px;
  border-radius: 4px;
  color: var(--primary);
  background: var(--primary-alpha-10);
  font-size: 9px;
}
.book-author {
  overflow: hidden;
  margin: 6px 0;
  color: var(--text-secondary);
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 11px;
}
.chapter-title {
  overflow: hidden;
  margin: 10px 0 7px;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--text-secondary);
  font-size: 10px;
}
.continue-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}
.continue-progress .progress-track {
  flex: 1;
}
.continue-progress small {
  min-width: 25px;
  color: var(--text-secondary);
  text-align: right;
  font-size: 10px;
}
.recent-added-section,
.recommendation-section {
  margin-top: 18px;
}
.home-book-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: clamp(12px, 1.5vw, 26px);
  padding: 6px 14px 16px;
}
.shelf-book {
  display: block;
  min-width: 0;
}
.shelf-book > :deep(.home-book-cover) {
  transition: transform .2s ease;
}
.shelf-book:hover > :deep(.home-book-cover) {
  transform: translateY(-3px);
}
.shelf-book h3 {
  overflow: hidden;
  margin: 10px 0 0;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
  font-weight: 600;
}
.format-label {
  display: inline-block;
  padding: 1px 5px;
  border: 1px solid var(--border-color-light);
  border-radius: 4px;
  color: var(--text-secondary);
  background: var(--primary-alpha-10);
  font-size: 9px;
}
.book-rating {
  display: flex;
  align-items: center;
  gap: 5px;
  min-height: 14px;
  margin: 7px 0 0;
  font-size: 11px;
}
.book-rating > span {
  color: #d69741;
  letter-spacing: .03em;
}
.book-rating small {
  color: var(--text-secondary);
  font-size: 10px;
}
.section-empty {
  padding: 26px 18px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.8;
}
.section-empty a {
  margin-left: 6px;
  color: var(--primary);
}
.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.recommendation-caption {
  margin: 0 14px 12px;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.7;
}
.recommendation-intro {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  padding: 18px 20px 26px;
}
.recommendation-intro > .el-icon {
  font-size: 28px;
  color: var(--primary);
}
.recommendation-intro h3 {
  margin: 0;
  font-size: 14px;
}
.recommendation-intro p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
}
.small-primary {
  display: inline-flex;
  justify-content: space-between;
  gap: 18px;
  padding: 10px 12px;
  margin-left: auto;
  border-radius: 5px;
  color: var(--primary) !important;
  background: var(--primary-alpha-10);
  font-size: 11px;
}
.home-right {
  display: grid;
  gap: 14px;
}
.home-right .section-heading {
  padding: 14px 14px 10px;
}
.home-right .section-heading h2 {
  font-size: 14px;
}
.home-right .section-heading h2 > .el-icon {
  font-size: 17px;
}
.overview-numbers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 10px 8px 20px;
  margin: 0 14px;
  border-bottom: 1px solid var(--border-color-light);
}
.overview-numbers a {
  display: grid;
  justify-items: center;
  gap: 8px;
  min-width: 0;
  border-right: 1px solid var(--border-color-light);
}
.overview-numbers a:last-child {
  border-right: 0;
}
.overview-numbers strong {
  font-family: Georgia, "Songti SC", serif;
  font-size: 24px;
  font-weight: 500;
}
.overview-numbers a:first-child strong,
.overview-numbers a:last-child strong {
  color: var(--primary);
}
.overview-numbers span {
  color: var(--text-secondary);
  font-size: 11px;
}
.activity-item {
  display: flex;
  gap: 11px;
  padding: 7px 14px;
}
.activity-item:last-child {
  margin-bottom: 10px;
}
.activity-item > :deep(.home-book-cover) {
  flex: 0 0 32px;
  width: 32px;
  align-self: flex-start;
}
.activity-item > div {
  flex: 1;
  min-width: 0;
}
.activity-title {
  overflow: hidden;
  margin: 0 0 5px;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 1.4;
}
.activity-detail {
  overflow: hidden;
  margin: 0 0 5px;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--text-secondary);
  font-size: 10px;
}
.activity-item time {
  display: block;
  color: var(--text-tertiary);
  font-size: 10px;
}
.update-item > :deep(.home-book-cover) {
  flex-basis: 39px;
  width: 39px;
}
.aside-empty,
.aside-caption {
  padding: 2px 14px 14px;
  margin: 0;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.7;
}
.aside-caption {
  padding-bottom: 4px;
  font-size: 10px;
}
.wanted-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 14px;
  border-top: 1px solid var(--border-color-light);
  font-size: 11px;
}
.wanted-row > span:first-child {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.wanted-row small {
  max-width: 70px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--text-secondary);
  font-size: 10px;
}
.home-note {
  display: grid;
  gap: 7px;
  margin: 3px 4px;
  color: var(--text-tertiary);
  text-align: center;
  font-family: "Songti SC", serif;
  font-size: 11px;
  line-height: 1.7;
}
.home-note a {
  color: var(--text-secondary);
  font-family: inherit;
  font-size: 10px;
}
.skeleton {
  min-height: 103px;
  border-radius: 7px;
  background: linear-gradient(100deg, var(--surface-card) 20%, var(--primary-alpha-10) 50%, var(--surface-card) 80%);
  background-size: 200% 100%;
  animation: home-shimmer 1.5s linear infinite;
}
.book-skeleton {
  aspect-ratio: 2 / 3;
}
.spinning {
  animation: home-spin 1s linear infinite;
}
@keyframes home-shimmer {
  to { background-position-x: -200%; }
}
@keyframes home-spin {
  to { transform: rotate(360deg); }
}
@media (min-width: 1800px) {
  .home-columns {
    grid-template-columns: 230px minmax(0, 1fr) 300px;
  }
}
@media (max-width: 1360px) {
  .home-columns {
    grid-template-columns: 180px minmax(0, 1fr) 246px;
    gap: 12px;
  }
  .continue-card {
    min-height: 103px;
    gap: 9px;
    padding: 10px;
  }
  .continue-card > :deep(.home-book-cover) {
    flex-basis: 40px;
    width: 40px;
  }
  .continue-info .chapter-title {
    margin-top: 6px;
  }
  .hero-cover {
    margin-left: 0;
    margin-right: 5%;
  }
  .hero-copy h1 {
    font-size: 27px;
  }
  .hero-actions a {
    padding: 9px 12px;
  }
}
@media (max-width: 1100px) {
  .home-columns {
    grid-template-columns: 170px minmax(0, 1fr);
  }
  .home-right {
    grid-column: 2;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .home-note {
    grid-column: 1 / -1;
  }
  .hero-copy h1 {
    font-size: 30px;
  }
  .hero-cover {
    flex-basis: 21%;
  }
}
@media (min-width: 761px) and (max-width: 950px) {
  .continue-grid {
    grid-template-columns: 1fr;
  }
  .home-book-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .home-welcome {
    padding: 0 2px 14px;
    font-size: 11px;
  }
  .welcome-quote,
  .welcome-divider {
    display: none;
  }
  .home-columns {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .library-rail {
    position: static;
    width: 100%;
    padding: 6px;
  }
  .category-list {
    display: flex;
    gap: 5px;
    overflow-x: auto;
  }
  .category-link {
    flex: 0 0 auto;
    gap: 7px;
    min-height: 36px;
    padding: 8px 10px;
    font-size: 11px;
  }
  .category-link > .el-icon {
    font-size: 15px;
  }
  .category-link > span {
    max-width: 100px;
  }
  .category-manage {
    flex-shrink: 0;
    padding: 8px;
    white-space: nowrap;
  }
  .rail-empty {
    flex-shrink: 0;
    padding: 0 10px;
    font-size: 10px;
  }
  .library-volume,
  .quick-tools {
    display: none;
  }
  .home-main,
  .home-right {
    width: 100%;
  }
  .reading-hero {
    min-height: 250px;
    padding: 25px 20px 36px;
  }
  .hero-cover {
    flex-basis: 23%;
    margin-right: 7%;
  }
  .hero-copy {
    flex: 1;
  }
  .hero-copy h1 {
    font-size: clamp(22px, 5.5vw, 31px);
    letter-spacing: 0;
  }
  .hero-eyebrow {
    margin-bottom: 10px;
    font-size: 8px;
    letter-spacing: .1em;
  }
  .hero-subtitle {
    font-size: 11px;
    letter-spacing: .03em;
    line-height: 1.8;
  }
  .hero-book-name {
    margin-top: 8px;
    font-size: 10px;
  }
  .hero-book-name span {
    display: none;
  }
  .hero-actions {
    gap: 8px;
    margin-top: 14px;
  }
  .hero-actions a {
    padding: 8px 10px;
    font-size: 10px;
  }
  .hero-actions .hero-secondary {
    display: none;
  }
  .continue-grid {
    grid-template-columns: 1fr;
  }
  .continue-card > :deep(.home-book-cover) {
    flex-basis: 44px;
    width: 44px;
  }
  .home-book-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 17px 12px;
    padding: 6px 12px 16px;
  }
  .shelf-book h3 {
    font-size: 11px;
  }
  .home-right {
    grid-template-columns: 1fr;
  }
  .section-heading h2 {
    font-size: 16px;
  }
  .section-actions {
    gap: 8px;
  }
  .recommendation-intro {
    padding: 14px;
  }
  .small-primary {
    margin-left: 0;
  }
}
@media (max-width: 360px) {
  .home-book-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .hero-cover {
    display: none;
  }
  .hero-copy {
    text-align: center;
  }
  .hero-actions {
    justify-content: center;
  }
}
@media (prefers-reduced-motion: reduce) {
  .home-view *,
  .home-view *::before,
  .home-view *::after {
    animation: none !important;
    transition: none !important;
  }
}
</style>
