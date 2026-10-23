import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.MOBILE_PLAYWRIGHT_PATH || 'playwright')
const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const books = Array.from({ length: 100 }, (_, index) => ({
  id: index + 1, title: `测试书籍${index + 1}`, author: '作者', format: 'epub',
  coverUrl: `/api/covers/${index + 1}.jpg`, readingStatus: 'UNREADING', tags: [],
  createdAt: '2026-10-23T10:00:00', isFavorite: false, isWanted: false, onShelf: false,
}))
const browser = await chromium.launch({ headless: true, channel: process.env.MOBILE_BROWSER_CHANNEL || 'chrome' })
try {
  for (const theme of (process.env.LIBRARY_PERFORMANCE_THEMES || 'warm,modern,macos26').split(',')) {
    for (const width of (process.env.LIBRARY_PERFORMANCE_WIDTHS || '390,1564').split(',').map(Number)) {
      const context = await browser.newContext({ viewport: { width, height: 1000 } })
      await context.addInitScript(theme => {
        localStorage.setItem('token', 'selection-test')
        localStorage.setItem('ai-book-theme', theme)
        localStorage.setItem('aibook-cover-images-hidden', 'true')
      }, theme)
      const errors = []
      await context.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname
        let json = []
        if (path === '/api/user/profile') json = { id: 1, username: 'reader', role: 'ADMIN' }
        else if (path === '/api/site/settings') json = { registrationEnabled: true }
        else if (path === '/api/user/preferences') json = { libraryCardPageSize: 100, libraryListPageSize: 100, libraryViewMode: 'card' }
        else if (path === '/api/user/dock-icons') json = { userId: 1, icons: [], versions: {} }
        else if (path === '/api/cover-privacy/books') json = { initialized: true, allHidden: true, overrides: {}, bookOverrides: {} }
        else if (path === '/api/books/trash/count') json = 0
        else if (path === '/api/books') json = { content: books, totalElements: 100, totalPages: 1, size: 100, number: 0 }
        else if (path === '/api/categories' || path === '/api/categories/tree') json = [{ id: 7, name: '文学分类', children: [], enabled: true }]
        else if (path === '/api/tags') json = [{ id: 8, name: '测试标签', color: '#64748b' }]
        return route.fulfill({ json })
      })
      const page = await context.newPage()
      page.on('pageerror', error => errors.push(error.message))
      await page.goto(baseUrl + '/books')

      for (const mode of ['卡片', '小卡片', '列表']) {
        await page.getByRole('tab').filter({ has: page.getByText(mode, { exact: true }) }).click()
        const items = page.locator(mode === '列表' ? '.book-list-item' : '.books-grid .book-card')
        await page.waitForFunction(selector => document.querySelectorAll(selector).length === 100,
          mode === '列表' ? '.book-list-item' : '.books-grid .book-card')
        await page.waitForFunction(() => document.querySelectorAll('.library-book-menu .el-dropdown-menu').length === 0)
        assert.equal(await items.locator('img').count(), 0)
        assert.equal(await page.locator('.library-book-menu .el-dropdown-menu__item').count(), 0,
          'Unopened book menus must not create menu items')
        const initialNodes = await page.locator('*').count()
        const trigger = item => item.locator(mode === '列表' ? '.more-list-button' : '.more-trigger')
        await items.first().hover()
        await trigger(items.first()).click()
        await page.getByRole('menuitem', { name: /编辑书籍/ }).waitFor()
        assert.equal(await page.locator('.library-book-menu .el-dropdown-menu').count(), 1)
        assert.equal(await page.locator('.library-book-menu .el-dropdown-menu__item').count(), mode === '列表' ? 7 : 8)
        await page.locator('.search-box .input').click()
        await page.waitForFunction(() => document.querySelectorAll('.library-book-menu .el-dropdown-menu').length === 0)
        // Re-create on another book using the keyboard and verify command context.
        await trigger(items.nth(1)).focus()
        await page.keyboard.press('Enter')
        await page.getByRole('menuitem', { name: /编辑书籍/ }).click()
        const dialog = page.getByRole('dialog', { name: '编辑书籍', exact: true })
        await dialog.waitFor()
        assert.equal(await dialog.locator('.form-grid input').first().inputValue(), '测试书籍2')
        await dialog.getByRole('button', { name: '取消', exact: true }).click()
        await dialog.waitFor({ state: 'hidden' })
        await page.waitForFunction(() => document.querySelectorAll('.library-book-menu .el-dropdown-menu').length === 0)
        assert.equal(new URL(page.url()).pathname, '/books')
        console.log(`Library 100-book lazy menus passed: ${theme}/${width}/${mode}; idle DOM ${initialNodes}`)
      }
      assert.deepEqual(errors, [])
      await context.close()
    }
  }
} finally {
  await browser.close()
}
