import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [view, api] = await Promise.all([
  readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/utils/crawler.ts', import.meta.url), 'utf8'),
])

for (const text of [
  '章节新增与采集趋势',
  '每日新增书籍',
  '采集成功率',
  '来源网站贡献',
  '采集转化漏斗',
  'statisticsRangeOptions:StatisticsDays[]=[7,30,90]',
  '@keydown="handleStatisticsRangeKey"',
  "await import('echarts')",
  'newChapters',
  'successfulChapters',
  'siteContributions',
  'funnel.discoveredBooks',
]) assert.ok(view.includes(text), `missing crawler dashboard statistics behavior: ${text}`)

for (const text of [
  'CrawlerDashboardStatistics',
  'CrawlerDailyStatistics',
  'CrawlerSiteContribution',
  'CrawlerFunnel',
  '/api/crawler/dashboard/statistics',
]) assert.ok(api.includes(text), `missing crawler dashboard statistics contract: ${text}`)

assert.match(view, /statistics-card statistics-card-wide[\s\S]*?chapterTrendChartRef/, 'chapter comparison chart must span the full statistics row')
assert.match(view, /finishedTasks\?Math\.round\(item\.successfulTasks\/item\.finishedTasks\*100\):null/, 'daily success rate must avoid treating days without finished tasks as zero-percent failures')
assert.match(view, /disposeStatisticsCharts\(\)[\s\S]*?echarts\.init/, 'statistics charts must dispose detached instances before reinitializing')
assert.match(view, /window\.addEventListener\('resize',resizeStatisticsCharts\)/, 'statistics charts must resize with the page')

console.log('crawler dashboard renders five account-scoped statistics cards with shared ranges')
