import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.MOBILE_PLAYWRIGHT_PATH || 'playwright')
const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const books = Array.from({ length: 5 }, (_, index) => ({
  id: index + 1, title: `测试书籍${index + 1}`, author: '作者', format: 'epub',
  coverUrl: '/mock-cover.png', readingStatus: 'UNREADING', tags: [],
  createdAt: '2026-10-23T10:00:00', isFavorite: false, isWanted: false, onShelf: false,
}))
const browser = await chromium.launch({ headless: true, channel: process.env.MOBILE_BROWSER_CHANNEL || 'chrome' })
try {
  for (const theme of (process.env.LIBRARY_SELECTION_THEMES || 'warm,modern,macos26').split(',')) {
    for (const width of (process.env.LIBRARY_SELECTION_WIDTHS || '390,1564').split(',').map(Number)) {
      const context = await browser.newContext({ viewport: { width, height: 1000 } })
      await context.addInitScript(theme => {
        localStorage.setItem('token', 'selection-test')
        localStorage.setItem('ai-book-theme', theme)
      }, theme)
      const errors = []
      await context.route('**/mock-cover*', route => route.fulfill({
        contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII=', 'base64'),
      }))
      await context.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname
        let json = []
        if (path === '/api/user/profile') json = { id: 1, username: 'reader', role: 'ADMIN' }
        else if (path === '/api/site/settings') json = { registrationEnabled: true }
        else if (path === '/api/user/preferences') json = { libraryCardPageSize: 10, libraryListPageSize: 10 }
        else if (path === '/api/user/dock-icons') json = { userId: 1, icons: [], versions: {} }
        else if (path === '/api/books/trash/count') json = 0
        else if (path === '/api/books') json = { content: books, totalElements: 5, totalPages: 1, size: 10, number: 0 }
        else if (path === '/api/categories' || path === '/api/categories/tree') json = [{ id: 7, name: '文学分类', children: [], enabled: true }]
        else if (path === '/api/tags') json = [{ id: 8, name: '测试标签', color: '#64748b' }]
        return route.fulfill({ json })
      })
      const page = await context.newPage()
      page.on('pageerror', error => errors.push(error.message))
      await page.goto(baseUrl + '/books')
      for (const mode of (process.env.LIBRARY_SELECTION_MODES || '卡片,小卡片,列表').split(',')) {
        await page.getByRole('tab').filter({ has: page.getByText(mode, { exact: true }) }).click()
        const items = page.locator(mode === '列表' ? '.book-list-item' : '.books-grid .book-card')
        await items.first().waitFor()
        assert.equal(await items.count(), 5)
        await page.locator('.selection-controls button').filter({ has: page.getByText('多选', { exact: true }) }).click()
        assert.equal(await page.locator('.book-cover-actions, .book-list-actions, .book-cover-privacy-button').count(), 0)
        await items.first().locator(mode === '列表' ? '.book-cover-small' : '.book-cover').click()
        await page.getByText('已选择 1 本', { exact: true }).waitFor()
        assert.equal(new URL(page.url()).pathname, '/books')
        await items.first().click()
        assert.equal(await items.locator('input:checked').count(), 0)
        await items.first().getByRole('checkbox').check()
        await page.getByRole('button', { name: '全选当前页', exact: true }).click()
        assert.equal(await items.locator('input:checked').count(), 5)
        const toolbar = page.locator('.batch-toolbar')
        const layout = await toolbar.evaluate(element => {
          const box = element.getBoundingClientRect()
          const bad = [...element.querySelectorAll('button, .el-select')].filter(child => {
            const rect = child.getBoundingClientRect()
            return rect.width <= 0 || rect.left < box.left - 1 || rect.right > box.right + 1
          }).map(child => child.textContent)
          const wrappedButtons = [...element.querySelectorAll('button.btn')].filter(button =>
            getComputedStyle(button).whiteSpace !== 'nowrap').length
          return { left: box.left, right: box.right, bad, wrappedButtons }
        })
        assert(layout.left >= 0 && layout.right <= width + 1, JSON.stringify(layout))
        assert.deepEqual(layout.bad, [])
        assert.equal(layout.wrappedButtons, 0)
        for (const [selector, option] of [
          ['.batch-category-select', '文学分类'], ['.batch-tag-select', '测试标签'], ['.batch-tag-mode', '替换标签'],
        ]) {
          await toolbar.locator(selector).click()
          await page.getByRole('option', { name: option, exact: true }).click()
          await page.keyboard.press('Escape')
        }
        assert.equal(await toolbar.locator('.batch-tag-mode').textContent().then(text => text.includes('替换标签')), true)
        if (process.env.LIBRARY_SELECTION_SCREENSHOT && theme === 'warm' && width === 1564 && mode === '小卡片') {
          await page.screenshot({ path: process.env.LIBRARY_SELECTION_SCREENSHOT, fullPage: true })
        }
        await page.getByRole('button', { name: '取消全选', exact: true }).click()
        assert.equal(await items.locator('input:checked').count(), 0)
        await page.locator('.selection-controls button').filter({ has: page.getByText('退出多选', { exact: true }) }).click()
        assert.equal(await page.locator(mode === '列表' ? '.book-list-actions' : '.book-cover-actions').count(), 5)
        assert.equal(await page.locator('.book-cover-privacy-button').count(), 5)
        console.log(`Library selection passed: ${theme}, ${width}px, ${mode}`)
      }
      assert.deepEqual(errors, [])
      await context.close()
    }
  }
} finally {
  await browser.close()
}
