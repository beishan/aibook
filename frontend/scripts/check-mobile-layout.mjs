import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'

// Use an existing Playwright installation; this check adds no application dependency.
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.MOBILE_PLAYWRIGHT_PATH || 'playwright')
const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const output = process.env.MOBILE_SCREENSHOT_DIR
const book = {
  id: 1, title: '手机适配测试：很长的书名与多版本阅读体验', author: '测试作者',
  format: 'epub', status: 'UNREADING', readingStatus: 'UNREADING', fileSize: 20480,
  wordCount: 120000, description: '用于检查窄屏布局的书籍说明。'.repeat(12),
  createdAt: '2026-10-06T10:00:00', updatedAt: '2026-10-06T10:00:00',
  tags: [], versions: [], favorite: false, isWanted: false, progress: 0,
}
const pageResult = (content = []) => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 20 })
const execution = {
  id: 1, taskName: '每日书库备份', contents: 'PostgreSQL 数据库、书籍文件',
  status: 'DELETED', executionStatus: 'SUCCESS', currentStage: '备份完成', progressPercent: 100,
  deletionReason: '自动保留策略清理：超过最近 7 天全量保留范围',
  deletedAt: '2026-10-06T10:00:00', startedAt: '2026-10-01T10:00:00',
  finishedAt: '2026-10-01T10:10:00', fileCount: 12, fileSizeBytes: 102400,
  outputPath: '/backups/aibook-20261001-100000-1', details: '备份成功',
}

function fixture(path) {
  if (path === '/api/site/settings') return { registrationEnabled: true }
  if (path === '/api/user/profile') return { id: 1, username: 'mobile-test', role: 'ADMIN', hasAvatar: false }
  if (path === '/api/user/preferences') return {}
  if (path === '/api/proxy-settings/system' || path === '/api/proxy-settings/crawler') return []
  if (path === '/api/system/runtime') return { startedAt: '2026-10-06T10:00:00', uptimeMillis: 60000 }
  if (path === '/api/system/resources') {
    const metric = { usagePercent: 25, usedBytes: 1024, totalBytes: 4096, status: 'AVAILABLE' }
    return { scope: 'CONTAINER', status: 'AVAILABLE', collectedAt: '2026-10-06T10:00:00', cpu: metric, memory: metric, disk: metric, disks: [] }
  }
  if (path === '/api/user/dock-icons') return { userId: 1, icons: [], versions: {} }
  if (path === '/api/books/trash/count') return 0
  if (path === '/api/books/1') return book
  if (path === '/api/books/1/versions') return [{ id: 1, format: 'epub', versionName: '原版', fileSize: 20480 }]
  if (/^\/api\/books(\/search|\/reading|\/favorites|\/wanted|\/finished|\/trash)?$/.test(path)) return pageResult([book])
  if (path === '/api/reading-statistics') return {
    overview: { totalBooks: 15, finishedBooks: 3, readingBooks: 4 },
    monthlyStats: {}, dailyHeatmap: {}, ratingDistribution: [],
    categoryPreference: [], authorPreference: [], formatPreference: [], topReadingTimeBooks: [],
  }
  if (path === '/api/books/2') return { ...book, id: 2, format: 'html' }
  if (path === '/api/books/2/versions') return [{ id: 2, format: 'html', versionName: '原版' }]
  if (path === '/api/books/2/content-sanitized') return { html: '<h1>第一章 手机阅读</h1>' + '<p>这里是一段用于检查手机阅读排版的正文。</p>'.repeat(30) }
  if (path === '/api/rewrite/projects/1') return {
    id: 1, name: '手机上的长篇书籍重写项目', bookTitle: book.title, versionName: '重写版',
    status: 'ACTIVE', chapterCount: 1, completedCount: 0, totalWordCount: 1200, progress: 0,
  }
  if (path === '/api/rewrite/projects/1/chapters') return [{ id: 1, title: '第一章', status: 'DRAFT', wordCount: 1200 }]
  if (path === '/api/rewrite/projects/1/chapters/1') return {
    id: 1, title: '第一章', status: 'DRAFT', content: '手机编辑测试。\n'.repeat(50), sourceContent: '原文测试。\n'.repeat(50),
  }
  if (path === '/api/shelf') return { groups: [], ungroupedBooks: [book], totalBooks: 1 }
  if (path === '/api/booklists/1') return { id: 1, name: '手机测试书单', description: '长篇书籍阅读计划', bookCount: 1, books: [book] }
  if (path === '/api/rewrite/projects') return pageResult()
  if (path === '/api/system/backups/path') return { path: '/backups', exists: true, writable: true }
  if (path === '/api/system/backups/executions') return [execution]
  if (path === '/api/system/backups/retention') return { enabled: true, recentDays: 7, monthlyMonths: 12 }
  if (path === '/api/crawler/dashboard') return { bookCount: 0, siteCount: 0, runningTaskCount: 0 }
  if (path === '/api/crawler/dashboard/statistics') return {
    days: 7, daily: [], siteContributions: [],
    funnel: { discoveredBooks: 0, taskedBooks: 0, completedBooks: 0, importedBooks: 0 },
  }
  if (path === '/api/crawler/dashboard/chapter-attempts') return { days: 7, daily: [], attempts: pageResult() }
  if (path === '/api/crawler-settings/polling-interval-options') return [5, 10, 30, 60]
  if (path === '/api/crawler/books/1') return { id: 1, bookName: book.title, crawlStatus: 'COMPLETED' }
  if (path === '/api/crawler/books/1/chapters') return pageResult([{ id: 1, chapterIndex: 1, chapterName: '第一章 手机试读', crawlStatus: 'COMPLETED' }])
  if (path === '/api/crawler/books/1/chapters/1') return { content: '手机试读正文。\n'.repeat(60) }
  if (/\/api\/crawler\/(books|tasks)/.test(path)) return pageResult()
  if (path.endsWith('/chapters/current')) return null
  if (path.includes('reading-progress')) return null
  if (/settings|config|status|resources|dashboard/.test(path)) return {}
  return []
}

const browser = await chromium.launch({
  headless: true,
  ...(process.env.MOBILE_BROWSER_CHANNEL ? { channel: process.env.MOBILE_BROWSER_CHANNEL } : {}),
})
const themes = (process.env.MOBILE_THEMES || 'macos26,warm,modern').split(',')
const widths = (process.env.MOBILE_WIDTHS || '320,390,768').split(',').map(Number)
const paths = ['/', '/books', '/books/1', '/shelf', '/rewrite', '/rewrite/1', '/reader/2',
  '/crawler/books/1/trial', '/text-repair', '/format-conversion', '/crawler', '/statistics', '/settings', '/settings?tab=backups']
if (process.env.MOBILE_EXTENDED === 'true') {
  const tabs = ['theme', 'fonts', 'cover-display', 'reader-backgrounds', 'directories', 'covers',
    'categories', 'tags', 'authors', 'trash', 'scheduler', 'metadata-scraping', 'connections',
    'proxies', 'crawler-settings', 'users', 'logs', 'crawler-refresh-options', 'website', 'info']
  paths.push(...tabs.map(tab => `/settings?tab=${tab}`), '/series', '/books/1/repair', '/booklists/1')
}
try {
  if (output) await mkdir(output, { recursive: true })
  const failures = []
  for (const theme of themes) {
    for (const width of widths) {
      const context = await browser.newContext({
        viewport: { width, height: Number(process.env.MOBILE_HEIGHT || 844) },
        isMobile: true,
        hasTouch: true,
      })
      await context.addInitScript(theme => {
        localStorage.setItem('token', 'mobile-layout-fixture')
        localStorage.setItem('ai-book-theme', theme)
      }, theme)
      await context.route('**/api/**', route => route.fulfill({
        json: fixture(new URL(route.request().url()).pathname),
      }))
      const page = await context.newPage()
      const errors = []
      page.on('pageerror', error => errors.push(error.stack))
      for (const path of paths) {
        await page.goto(baseUrl + path)
        const immersive = ['/rewrite/1', '/reader/2', '/crawler/books/1/trial'].includes(path)
        await page.locator(immersive ? '.app-layout--immersive' : '.phone-navigation').waitFor()
        if (immersive) assert.equal(await page.locator('.phone-navigation').count(), 0)
        await page.waitForTimeout(500)
        const overflow = await page.evaluate(() => {
          const width = innerWidth
          return [...document.querySelectorAll('main, .page-header, .filter-card, .settings-content, .panel, .phone-navigation, .view-controls, .workspace-header, .reader-header, .reader-bar')]
            .filter(element => element.getBoundingClientRect().width > 0)
            .filter(element => element.getBoundingClientRect().right > width + 1 || element.getBoundingClientRect().left < -1)
            .map(element => ({ class: element.className, right: Math.round(element.getBoundingClientRect().right) }))
        })
        if (overflow.length) failures.push({ theme, width, path, overflow })
        if (output && theme === 'macos26' && width === 390) {
          await page.screenshot({ path: `${output}/${path.replace(/\W/g, '_') || 'home'}.png`, fullPage: true })
        }
        if (errors.length) failures.push({ theme, width, path, errors: errors.splice(0) })
        if (path === '/books') {
          const group = page.getByRole('tablist', { name: '书库显示方式' })
          await group.getByRole('tab', { name: /卡片/, exact: false }).first().focus()
          await page.keyboard.press('End')
          assert.equal(await group.getByRole('tab', { name: /列表/ }).getAttribute('aria-selected'), 'true')
          await page.getByRole('button', { name: '上传书籍', exact: false }).first().click()
          await page.waitForTimeout(150)
          const dialog = page.locator('.el-dialog:visible, .dialog-overlay:visible > .dialog').first()
          await dialog.waitFor()
          const bounds = await dialog.boundingBox()
          assert(bounds && bounds.x >= 0 && bounds.x + bounds.width <= width + 1, 'Upload dialog fits phone')
          await page.keyboard.press('Escape')
        }
        if (path === '/settings?tab=backups') {
          await page.getByRole('tab', { name: /执行结果/ }).click()
          const row = page.locator('.backup-execution-table tbody tr').first()
          const bounds = await row.boundingBox()
          assert(bounds && bounds.width <= width, 'Backup results fit phone as cards')
          await row.getByRole('button', { name: '详情', exact: true }).click()
          await page.getByRole('dialog', { name: '备份执行详情' }).waitFor()
          await page.getByRole('button', { name: '关闭', exact: true }).last().click()
        }
        if (path === '/crawler' && process.env.MOBILE_EXTENDED === 'true') {
          const tabs = page.getByRole('tablist', { name: '采集中心栏目' })
          for (const label of ['采集统计', '采集网站', '发现书籍', '采集书籍', '采集任务', '失败任务']) {
            await tabs.getByRole('tab', { name: new RegExp(label) }).click()
            await page.waitForTimeout(100)
            const bounds = await page.locator('.crawler-page').boundingBox()
            assert(bounds && bounds.width <= width)
          }
          if (errors.length) failures.push({ theme, width, path: 'crawler tabs', errors: errors.splice(0) })
        }
      }
      await page.getByRole('button', { name: '更多功能', exact: true }).click()
      await page.getByRole('dialog').waitFor()
      assert.equal(await page.locator('.phone-navigation-grid a').count(), 9)
      await page.locator('.phone-navigation-grid a[href="/crawler"]').click()
      await page.waitForURL('**/crawler')
      await page.getByRole('dialog').waitFor({ state: 'hidden' })
      await context.close()
      console.log(`Checked ${theme} at ${width}px`)
    }
  }
  for (const width of widths) {
    const context = await browser.newContext({ viewport: { width, height: 667 }, isMobile: true, hasTouch: true })
    await context.route('**/api/**', route => route.fulfill({ json: fixture(new URL(route.request().url()).pathname) }))
    const page = await context.newPage()
    for (const path of ['/login', '/register']) {
      await page.goto(baseUrl + path)
      const form = page.locator('form')
      await form.waitFor()
      const bounds = await form.boundingBox()
      assert(bounds && bounds.x >= 0 && bounds.x + bounds.width <= width + 1, `${path} fits phone`)
    }
    await context.close()
  }
  for (const theme of themes) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    await context.addInitScript(theme => {
      localStorage.setItem('token', 'mobile-layout-fixture')
      localStorage.setItem('ai-book-theme', theme)
    }, theme)
    await context.route('**/api/**', route => route.fulfill({ json: fixture(new URL(route.request().url()).pathname) }))
    const page = await context.newPage()
    for (const path of ['/books', '/settings', '/crawler']) {
      await page.goto(baseUrl + path)
      await page.locator('.app-layout').waitFor()
      assert.equal(await page.locator('.phone-navigation').isVisible(), false, 'Desktop keeps its original navigation')
      const desktopNav = theme === 'modern' ? '.modern-sidebar' : theme === 'warm' ? '.header-nav' : '.dock-nav'
      assert.equal(await page.locator(desktopNav).isVisible(), true)
    }
    await context.close()
  }
  console.log(JSON.stringify(failures, null, 2))
  assert.equal(failures.length, 0, 'Mobile routes should render without viewport overflow or runtime errors')
  console.log(`Mobile layout checks passed: ${themes.length} layouts × ${widths.length} widths × ${paths.length} routes; dialogs, keyboard selection and navigation checked.`)
} finally {
  await browser.close()
}
