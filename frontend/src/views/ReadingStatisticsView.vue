<template>
  <div class="reading-statistics-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">阅读统计</h1>
        <p class="page-subtitle">您的年度阅读报告</p>
      </div>
    </div>

    <div v-if="loading" class="loading glass">
      <div class="loading-spinner"></div>
      <p>正在计算阅读数据...</p>
    </div>

    <div v-else-if="loadError" class="empty error-state glass" role="alert">
      <div class="empty-icon">⚠️</div>
      <p>{{ loadError }}</p>
      <button type="button" class="retry-button" @click="loadStatistics">重新加载</button>
    </div>

    <div v-else-if="!stats" class="empty glass">
      <div class="empty-icon">📊</div>
      <p>暂无统计数据</p>
    </div>

    <template v-else>
      <!-- 概览卡片 -->
      <section class="stats-cards">
        <div class="stat-card glass" style="--accent-color: var(--primary)">
          <div class="stat-icon-wrapper">
            <span class="stat-icon">📚</span>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.overview.totalBooks }}</div>
            <div class="stat-label">书库藏书</div>
          </div>
        </div>
        <div class="stat-card glass" style="--accent-color: var(--success, #10b981)">
          <div class="stat-icon-wrapper">
            <span class="stat-icon">✅</span>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.overview.finishedBooks }}</div>
            <div class="stat-label">已读完</div>
          </div>
        </div>
        <div class="stat-card glass" style="--accent-color: var(--warning, #f59e0b)">
          <div class="stat-icon-wrapper">
            <span class="stat-icon">📖</span>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.overview.readingBooks }}</div>
            <div class="stat-label">正在阅读</div>
          </div>
        </div>
        <div class="stat-card glass" style="--accent-color: var(--info, #6366f1)">
          <div class="stat-icon-wrapper">
            <span class="stat-icon">📝</span>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.overview.wantedBooks }}</div>
            <div class="stat-label">想读清单</div>
          </div>
        </div>
        <div class="stat-card glass" style="--accent-color: #8b5cf6">
          <div class="stat-icon-wrapper">
            <span class="stat-icon">🎯</span>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.overview.completionRate }}%</div>
            <div class="stat-label">完成率</div>
          </div>
        </div>
        <div class="stat-card glass" style="--accent-color: #ec4899">
          <div class="stat-icon-wrapper">
            <span class="stat-icon">⏱</span>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ formatHours(stats.overview.totalReadingTimeSeconds) }}</div>
            <div class="stat-label">累计阅读</div>
          </div>
        </div>
        <div class="stat-card glass" style="--accent-color: #f97316">
          <div class="stat-icon-wrapper">
            <span class="stat-icon">⭐</span>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.overview.averageRating || '-' }}</div>
            <div class="stat-label">平均评分 ({{ stats.overview.ratedBooks }}本)</div>
          </div>
        </div>
      </section>

      <!-- 月度趋势 -->
      <section class="chart-card glass">
        <div class="chart-header">
          <h3>年度阅读趋势</h3>
          <p class="chart-subtitle">每月阅读活跃本数与阅读时长 · {{ currentYear }}年</p>
        </div>
        <div ref="monthlyTrendRef" class="chart-container"></div>
      </section>

      <!-- 热力图 -->
      <section class="chart-card glass">
        <div class="chart-header">
          <h3>阅读热力图</h3>
          <p class="chart-subtitle">最近 365 天每日阅读活跃的书籍数</p>
        </div>
        <div ref="heatmapRef" class="chart-container heatmap-container"></div>
      </section>

      <!-- 评分 + 格式 -->
      <section class="charts-row">
        <div class="chart-card glass chart-half">
          <div class="chart-header">
            <h3>评分分布</h3>
            <p class="chart-subtitle">{{ stats.overview.ratedBooks }} 本书已评分</p>
          </div>
          <div ref="ratingRef" class="chart-container"></div>
        </div>
        <div class="chart-card glass chart-half">
          <div class="chart-header">
            <h3>格式偏好</h3>
            <p class="chart-subtitle">不同格式的藏书数量</p>
          </div>
          <div ref="formatRef" class="chart-container"></div>
        </div>
      </section>

      <!-- 分类 + 作者 -->
      <section class="charts-row">
        <div class="chart-card glass chart-half">
          <div class="chart-header">
            <h3>分类偏好</h3>
            <p class="chart-subtitle">各类别书籍数量</p>
          </div>
          <div ref="categoryRef" class="chart-container"></div>
        </div>
        <div class="chart-card glass chart-half">
          <div class="chart-header">
            <h3>作者偏好</h3>
            <p class="chart-subtitle">藏书最多的 10 位作者</p>
          </div>
          <div ref="authorRef" class="chart-container"></div>
        </div>
      </section>

      <!-- 阅读时长 TOP10 -->
      <section v-if="stats.topReadingTimeBooks.length > 0" class="chart-card glass">
        <div class="chart-header">
          <h3>阅读时长排行</h3>
          <p class="chart-subtitle">累计阅读时间最多的书籍</p>
        </div>
        <div class="top-books-list">
          <div
            v-for="(book, index) in stats.topReadingTimeBooks"
            :key="book.bookId"
            class="top-book-item"
            @click="$router.push(`/books/${book.bookId}`)"
          >
            <div class="rank-badge" :class="{ top3: index < 3 }">{{ index + 1 }}</div>
            <div class="top-book-cover">
              <img v-if="book.coverUrl" :src="getCoverUrl(book.coverUrl)" alt="" />
              <span v-else>{{ book.title.charAt(0) }}</span>
            </div>
            <div class="top-book-meta">
              <div class="top-book-title">{{ book.title }}</div>
              <div class="top-book-author">{{ book.author || '未知作者' }}</div>
            </div>
            <div class="top-book-time">{{ formatHours(book.readingTimeSeconds) }}</div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import api from '@/utils/api'
import { getCoverUrl } from '@/utils/cover'

interface Overview {
  totalBooks: number
  finishedBooks: number
  readingBooks: number
  unreadBooks: number
  wantedBooks: number
  completionRate: number
  totalReadingTimeSeconds: number
  averageReadingTimeSeconds: number
  averageRating: number
  ratedBooks: number
}

interface MonthReadingStat {
  month: number
  activeCount: number
  readingTimeSeconds: number
}

interface RatingStat {
  rating: number
  count: number
}

interface CategoryStat {
  categoryId: number | null
  categoryName: string
  count: number
}

interface AuthorStat {
  author: string
  count: number
}

interface FormatStat {
  format: string
  displayName: string
  count: number
}

interface BookReadingTimeStat {
  bookId: number
  title: string
  author: string
  readingTimeSeconds: number
  coverUrl: string | null
}

interface ReadingStatistics {
  overview: Overview
  monthlyStats: Record<number, MonthReadingStat>
  dailyHeatmap: Record<string, number>
  ratingDistribution: RatingStat[]
  categoryPreference: CategoryStat[]
  authorPreference: AuthorStat[]
  formatPreference: FormatStat[]
  topReadingTimeBooks: BookReadingTimeStat[]
}

const loading = ref(true)
const stats = ref<ReadingStatistics | null>(null)
const loadError = ref('')

const monthlyTrendRef = ref<HTMLElement>()
const heatmapRef = ref<HTMLElement>()
const ratingRef = ref<HTMLElement>()
const formatRef = ref<HTMLElement>()
const categoryRef = ref<HTMLElement>()
const authorRef = ref<HTMLElement>()

const currentYear = computed(() => new Date().getFullYear())

let monthlyChart: echarts.ECharts | null = null
let heatmapChart: echarts.ECharts | null = null
let ratingChart: echarts.ECharts | null = null
let formatChart: echarts.ECharts | null = null
let categoryChart: echarts.ECharts | null = null
let authorChart: echarts.ECharts | null = null

const loadStatistics = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get<ReadingStatistics>('/api/reading-statistics')
    stats.value = normalizeStatistics(data)
  } catch (error) {
    console.error('Failed to load reading statistics:', error)
    stats.value = null
    loadError.value = '阅读统计加载失败，请稍后重试'
  } finally {
    loading.value = false
    // loading 分支结束后图表容器才会挂载，必须在此后初始化 ECharts。
    await nextTick()
    if (stats.value) renderCharts()
  }
}

const emptyOverview = (): Overview => ({
  totalBooks: 0,
  finishedBooks: 0,
  readingBooks: 0,
  unreadBooks: 0,
  wantedBooks: 0,
  completionRate: 0,
  totalReadingTimeSeconds: 0,
  averageReadingTimeSeconds: 0,
  averageRating: 0,
  ratedBooks: 0,
})

const normalizeStatistics = (value: Partial<ReadingStatistics> | null | undefined): ReadingStatistics => ({
  overview: { ...emptyOverview(), ...(value?.overview || {}) },
  monthlyStats: value?.monthlyStats || {},
  dailyHeatmap: value?.dailyHeatmap || {},
  ratingDistribution: Array.isArray(value?.ratingDistribution) ? value.ratingDistribution : [],
  categoryPreference: Array.isArray(value?.categoryPreference) ? value.categoryPreference : [],
  authorPreference: Array.isArray(value?.authorPreference) ? value.authorPreference : [],
  formatPreference: Array.isArray(value?.formatPreference) ? value.formatPreference : [],
  topReadingTimeBooks: Array.isArray(value?.topReadingTimeBooks) ? value.topReadingTimeBooks : [],
})

const formatHours = (seconds: number): string => {
  if (!seconds || seconds <= 0) return '0 小时'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours === 0) return `${minutes} 分钟`
  return minutes > 0 ? `${hours} 小时${minutes} 分钟` : `${hours} 小时`
}

const disposeCharts = () => {
  monthlyChart?.dispose()
  heatmapChart?.dispose()
  ratingChart?.dispose()
  formatChart?.dispose()
  categoryChart?.dispose()
  authorChart?.dispose()
  monthlyChart = null
  heatmapChart = null
  ratingChart = null
  formatChart = null
  categoryChart = null
  authorChart = null
}

const renderCharts = () => {
  disposeCharts()
  if (!stats.value) return
  renderMonthlyTrend()
  renderHeatmap()
  renderRatingDistribution()
  renderFormatPreference()
  renderCategoryCharts()
  renderAuthorCharts()
}

// 月度趋势
const renderMonthlyTrend = () => {
  if (!monthlyTrendRef.value) return
  monthlyChart = echarts.init(monthlyTrendRef.value, null, { renderer: 'canvas' })
  const monthlyData = stats.value!.monthlyStats
  const months = Array.from({ length: 12 }, (_, i) => i + 1)
  const bookCounts = months.map(m => monthlyData[m]?.activeCount || 0)
  const timeValues = months.map(m => Math.round((monthlyData[m]?.readingTimeSeconds || 0) / 60)) // 分钟

  monthlyChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e5e7eb',
      textStyle: { color: '#374151' },
    },
    legend: {
      data: ['阅读本数', '阅读分钟'],
      top: 0,
      textStyle: { color: '#6b7280' },
    },
    grid: { left: 50, right: 60, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: months.map(m => `${m}月`),
      axisLabel: { color: '#6b7280' },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
    },
    yAxis: [
      {
        type: 'value',
        name: '本',
        position: 'left',
        axisLabel: { color: '#6b7280' },
        splitLine: { lineStyle: { color: '#f3f4f6' } },
      },
      {
        type: 'value',
        name: '分钟',
        position: 'right',
        axisLabel: { color: '#9ca3af' },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '读完本数',
        type: 'bar',
        data: bookCounts,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#3b82f6' },
            { offset: 1, color: '#8b5cf6' },
          ]),
          borderRadius: [4, 4, 0, 0],
        },
      },
      {
        name: '阅读分钟',
        type: 'line',
        yAxisIndex: 1,
        data: timeValues,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#ec4899', width: 2 },
        itemStyle: { color: '#ec4899' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(236,72,153,0.2)' },
            { offset: 1, color: 'rgba(236,72,153,0)' },
          ]),
        },
      },
    ],
  })
}

// 阅读热力图
const renderHeatmap = () => {
  if (!heatmapRef.value) return
  heatmapChart = echarts.init(heatmapRef.value, null, { renderer: 'canvas' })
  const rawHeatmap = stats.value!.dailyHeatmap || {}

  // 生成过去 365 天的日期序列
  const today = new Date()
  const data: [string, number][] = []
  for (let i = 364; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const key = d.toISOString().slice(0, 10)
    data.push([key, rawHeatmap[key] || 0])
  }

  const maxVal = Math.max(...data.map(d => d[1]), 1)

  heatmapChart.setOption({
    tooltip: {
      formatter: (params: any) => {
        const [date, count] = params.value as [string, number]
        return `${date}<br/>阅读活跃书籍：${count} 本`
      },
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e5e7eb',
      textStyle: { color: '#374151', fontSize: 12 },
    },
    visualMap: {
      min: 0,
      max: maxVal,
      type: 'piecewise',
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      pieces: [
        { min: 0, max: 0, label: '0', color: '#ebedf0' },
        { min: 1, max: 2, label: '1-2', color: '#9be9a8' },
        { min: 3, max: 5, label: '3-5', color: '#40c463' },
        { min: 6, max: 10, label: '6-10', color: '#30a14e' },
        { min: 11, label: '11+', color: '#216e39' },
      ],
      textStyle: { color: '#6b7280', fontSize: 11 },
    },
    calendar: {
      top: 20,
      left: 40,
      right: 20,
      bottom: 40,
      cellSize: [14, 14],
      range: [
        new Date(today.getFullYear() - 1, today.getMonth(), today.getDate() + 1)
          .toISOString().slice(0, 10),
        today.toISOString().slice(0, 10),
      ],
      itemStyle: { borderWidth: 2, borderColor: '#fff', color: '#ebedf0' },
      splitLine: { show: false },
      dayLabel: { color: '#9ca3af', fontSize: 10, nameMap: 'cn' },
      monthLabel: { color: '#6b7280', fontSize: 11, nameMap: 'cn' },
      yearLabel: { show: false },
    },
    series: [
      {
        type: 'heatmap',
        coordinateSystem: 'calendar',
        data,
      },
    ],
  })
}

// 评分分布
const renderRatingDistribution = () => {
  if (!ratingRef.value) return
  ratingChart = echarts.init(ratingRef.value, null, { renderer: 'canvas' })
  const ratingData = stats.value!.ratingDistribution
  const colors = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#3b82f6']

  ratingChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => `${params.name}<br/>${params.value} 本 (${params.percent}%)`,
    },
    grid: { left: 40, right: 20, top: 20, bottom: 20 },
    xAxis: {
      type: 'category',
      data: ratingData.map(r => `${r.rating} 星`),
      axisLabel: { color: '#6b7280' },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#9ca3af' },
      splitLine: { lineStyle: { color: '#f3f4f6' } },
    },
    series: [
      {
        type: 'bar',
        data: ratingData.map((r, i) => ({
          value: r.count,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: colors[i] },
              { offset: 1, color: colors[i] + '99' },
            ]),
            borderRadius: [4, 4, 0, 0],
          },
        })),
        barWidth: '50%',
        label: {
          show: true,
          position: 'top',
          color: '#6b7280',
          fontSize: 11,
        },
      },
    ],
  })
}

// 格式偏好
const renderFormatPreference = () => {
  if (!formatRef.value) return
  formatChart = echarts.init(formatRef.value, null, { renderer: 'canvas' })
  const formatData = [...stats.value!.formatPreference].slice(0, 8).reverse()
  const total = formatData.reduce((sum, f) => sum + f.count, 0)
  const palette = ['#818cf8', '#a78bfa', '#c084fc', '#e879f9', '#f472b6', '#fb7185', '#f97316', '#fbbf24']

  formatChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const p = params[0]
        return `${p.name}<br/>${p.value} 本`
      },
    },
    grid: { left: 80, right: 30, top: 10, bottom: 10 },
    xAxis: {
      type: 'value',
      axisLabel: { show: false },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'category',
      data: formatData.map(f => f.displayName),
      axisLabel: { color: '#374151', fontSize: 12 },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [
      {
        type: 'bar',
        data: formatData.map((f, i) => ({
          value: f.count,
          itemStyle: {
            color: palette[i % palette.length],
            borderRadius: [0, 6, 6, 0],
          },
        })),
        barWidth: 18,
        label: {
          show: true,
          position: 'right',
          color: '#6b7280',
          fontSize: 11,
          formatter: (params: any) => {
            if (total === 0) return ''
            const pct = ((params.value / total) * 100).toFixed(0)
            return `${params.value} (${pct}%)`
          },
        },
      },
    ],
  })
}

// 分类 + 作者
const renderCategoryCharts = () => {
  if (!categoryRef.value) return
  categoryChart = echarts.init(categoryRef.value, null, { renderer: 'canvas' })
  const categoryData = stats.value!.categoryPreference.slice(0, 10)
  const palette = [
    '#3b82f6', '#8b5cf6', '#ec4899', '#f97316', '#eab308',
    '#22c55e', '#14b8a6', '#06b6d4', '#6366f1', '#a855f7',
  ]

  categoryChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => `${params.name}<br/>${params.value} 本 (${params.percent}%)`,
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'center',
      textStyle: { color: '#6b7280', fontSize: 11 },
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 13,
            fontWeight: 'bold',
            color: '#374151',
          },
        },
        data: categoryData.map((c, i) => ({
          name: c.categoryName,
          value: c.count,
          itemStyle: { color: palette[i % palette.length] },
        })),
      },
    ],
  })
}

const renderAuthorCharts = () => {
  if (!authorRef.value) return
  authorChart = echarts.init(authorRef.value, null, { renderer: 'canvas' })
  const authorData = [...stats.value!.authorPreference].slice(0, 10).reverse()
  const total = authorData.reduce((sum, a) => sum + a.count, 0)

  authorChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const p = params[0]
        return `${p.name}<br/>${p.value} 本`
      },
    },
    grid: { left: 100, right: 30, top: 10, bottom: 10 },
    xAxis: {
      type: 'value',
      axisLabel: { show: false },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'category',
      data: authorData.map(a => a.author),
      axisLabel: { color: '#374151', fontSize: 12 },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [
      {
        type: 'bar',
        data: authorData.map(a => ({
          value: a.count,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#3b82f680' },
              { offset: 1, color: '#3b82f6' },
            ]),
            borderRadius: [0, 6, 6, 0],
          },
        })),
        barWidth: 18,
        label: {
          show: true,
          position: 'right',
          color: '#6b7280',
          fontSize: 11,
          formatter: (params: any) => {
            if (total === 0) return ''
            return `${params.value} 本 (${((params.value / total) * 100).toFixed(0)}%)`
          },
        },
      },
    ],
  })
}

// 窗口大小变化时自适应
const handleResize = () => {
  monthlyChart?.resize()
  heatmapChart?.resize()
  ratingChart?.resize()
  formatChart?.resize()
  categoryChart?.resize()
  authorChart?.resize()
}

onMounted(() => {
  loadStatistics()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<style scoped>
.reading-statistics-view {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.page-subtitle {
  color: var(--text-secondary);
  font-size: 14px;
  margin-top: 4px;
}

.loading, .empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 16px;
  padding: 40px;
  border-radius: 16px;
}

.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-icon {
  font-size: 48px;
}

.error-state p {
  color: var(--text-secondary);
  margin: 0;
}

.retry-button {
  border: 1px solid var(--primary);
  border-radius: 8px;
  padding: 8px 18px;
  color: var(--primary);
  background: transparent;
  cursor: pointer;
}

.retry-button:hover,
.retry-button:focus-visible {
  color: #fff;
  background: var(--primary);
  outline: none;
}

/* 概览卡片 */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  border-radius: 16px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.stat-icon-wrapper {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--accent-color, var(--primary)) 10%, transparent);
  border-radius: 12px;
  flex-shrink: 0;
}

.stat-icon {
  font-size: 24px;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

/* 图表卡片 */
.chart-card {
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
}

.chart-header {
  margin-bottom: 16px;
}

.chart-header h3 {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.chart-subtitle {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 4px 0 0;
}

.chart-container {
  width: 100%;
  height: 280px;
}

.heatmap-container {
  height: 220px;
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 24px;
}

.chart-half {
  margin-bottom: 0;
}

/* 排行榜 */
.top-books-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.top-book-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}

.top-book-item:hover {
  background: var(--surface-hover);
}

.rank-badge {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-secondary);
  color: var(--text-tertiary);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.rank-badge.top3 {
  background: var(--primary);
  color: #fff;
}

.top-book-cover {
  width: 36px;
  height: 48px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--bg-secondary);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
}

.top-book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.top-book-meta {
  flex: 1;
  min-width: 0;
}

.top-book-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.top-book-author {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.top-book-time {
  font-size: 13px;
  font-weight: 600;
  color: var(--primary);
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .charts-row {
    grid-template-columns: 1fr;
  }

  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-container {
    height: 240px;
  }

  .heatmap-container {
    height: 260px;
  }
}
</style>
