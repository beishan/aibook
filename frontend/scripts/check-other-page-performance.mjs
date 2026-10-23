import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.MOBILE_PLAYWRIGHT_PATH || 'playwright')
const books = Array.from({ length: 100 }, (_, i) => ({ id: i + 1, title: `测试书籍${i + 1}`, author: '作者', format: 'epub', readingStatus: 'READING', tags: [], isFavorite: true, isWanted: true, onShelf: true, createdAt: '2026-10-23T10:00:00' }))
const crawlerBooks = books.map(book => ({ ...book, bookName: book.title, siteId: 1, siteName: '测试网站', crawlStatus: 'COMPLETED', importStatus: 'PENDING', crawledChapterCount: 10, totalChapterCount: 10, discoveredAt: book.createdAt }))
const tasks = books.map(book => ({ id: String(book.id), bookId: book.id, bookName: book.title, type: 'BOOK_CRAWL', status: 'FAILED', priority: 'NORMAL', successCount: 0, totalCount: 10, failedCount: 1, errorMessage: '测试失败', createdAt: book.createdAt }))
const pageResult = content => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 100 })
function fixture(path) {
 if (path === '/api/user/profile') return { id: 1, username: 'tester', role: 'ADMIN' }
 if (path === '/api/site/settings') return { registrationEnabled: true }
 if (path === '/api/user/preferences') return { shelfPageSize: 96, crawlerPollingIntervalSeconds: 60 }
 if (path === '/api/user/dock-icons') return { userId: 1, icons: [], versions: {} }
 if (path === '/api/cover-privacy/books') return { initialized: true, allHidden: true, overrides: {}, bookOverrides: {} }
 if (path === '/api/shelf') return { groups: [{ id: 1, name: '测试分组', icon: '📁', books: [] }], ungroupedBooks: books, totalBooks: 100 }
 if (/^\/api\/books(\/favorites|\/wanted)?$/.test(path)) return pageResult(books)
 if (path === '/api/books/1') return books[0]
 if (path === '/api/books/1/versions') return [{ id: 1, format: 'epub', versionName: '原版' }]
 if (path === '/api/rewrite/projects/1') return { id: 1, name: '测试重写项目', bookTitle: '测试书籍1', versionName: '重写版', status: 'ACTIVE', chapterCount: 0, totalWordCount: 0, progress: 0 }
 if (path === '/api/booklists') return [{ id: 1, name: '测试书单', bookCount: 100 }]
 if (path === '/api/crawler/books' || path === '/api/crawler/books/discovered') return pageResult(crawlerBooks)
 if (path === '/api/crawler/tasks') return pageResult(tasks)
 if (path === '/api/crawler/dashboard') return { bookCount: 100, siteCount: 0, runningTaskCount: 0 }
 if (path === '/api/crawler/dashboard/statistics') return { days: 7, daily: [], siteContributions: [], funnel: { discoveredBooks: 100, taskedBooks: 100, completedBooks: 0, importedBooks: 0 } }
 if (path === '/api/crawler/dashboard/chapter-attempts') return { days: 7, daily: [], attempts: pageResult([]) }
 if (path === '/api/crawler-settings/polling-interval-options') return [5, 10, 30, 60]
 if (/settings|config/.test(path)) return {}
 return []
}

const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const browser = await chromium.launch({ headless: true, channel: process.env.MOBILE_BROWSER_CHANNEL || 'chrome' })
const menuSelector = '.el-dropdown-menu'
async function noMenus(page) {
  await page.waitForFunction(selector => document.querySelectorAll(selector).length === 0, menuSelector)
}
async function openMenu(page, trigger, item) {
  await trigger.focus()
  await trigger.click()
  await page.getByRole('menuitem', { name: item, exact: true }).waitFor()
  assert.equal(await page.locator(menuSelector).count(), 1)
}
async function closeMenu(page) {
  await page.locator('h1').first().click()
  await noMenus(page)
}
try {
  for (const theme of (process.env.LAZY_MENU_THEMES || 'warm,modern,macos26').split(',')) {
    for (const width of (process.env.LAZY_MENU_WIDTHS || '390,1564').split(',').map(Number)) {
      const context = await browser.newContext({ viewport: { width, height: 1000 } })
      await context.addInitScript(theme => {
        localStorage.setItem('token', 'lazy-menu-test')
        localStorage.setItem('ai-book-theme', theme)
      }, theme)
      const writes = []
      await context.route('**/api/**', route => {
        if (route.request().method() === 'PUT') {
          writes.push({ path: new URL(route.request().url()).pathname, data: route.request().postDataJSON() })
        }
        return route.fulfill({ json: fixture(new URL(route.request().url()).pathname) })
      })
      const page = await context.newPage()
      const errors = []
      page.on('pageerror', error => errors.push(error.message))
      await page.goto(baseUrl + '/shelf')
      const panel = page.locator('#shelf-panel-shelf')
      await panel.locator('.book-card').first().waitFor()
      assert.equal(await panel.locator('.book-card').count(), 96)
      assert.equal(await page.locator('.shelf-view [role="tabpanel"]').count(), 1)
      await noMenus(page)
      await openMenu(page, panel.getByRole('button', { name: '更多操作', exact: true }).first(), '✏️ 编辑书籍')
      await closeMenu(page)
      await panel.getByRole('button', { name: '更多操作', exact: true }).nth(1).focus()
      await page.keyboard.press('Enter')
      await page.getByRole('menuitem', { name: '✏️ 编辑书籍', exact: true }).click()
      const edit = page.getByRole('dialog', { name: '编辑书籍', exact: true })
      await edit.waitFor()
      assert.equal(await edit.locator('.form-grid input').first().inputValue(), '测试书籍2')
      await edit.getByRole('button', { name: '取消', exact: true }).click()
      await edit.waitFor({ state: 'hidden' })
      await noMenus(page)
      await openMenu(page, panel.getByTitle('移动到分组', { exact: true }).first(), '📁 测试分组')
      await page.getByRole('menuitem', { name: '📁 测试分组', exact: true }).click()
      await page.waitForFunction(() => !document.querySelector('#shelf-panel-shelf .loading'))
      assert(writes.some(write => write.path === '/api/shelf/books/1/group' && write.data.groupId === 1))
      await noMenus(page)
      await page.locator('.shelf-pagination .number').filter({ hasText: /^2$/ }).click()
      await page.waitForFunction(() => document.querySelectorAll('#shelf-panel-shelf .book-card').length === 4)
      for (const key of ['favorite', 'reading', 'finished', 'wanted', 'lists']) {
        await page.locator(`#shelf-tab-${key}`).click()
        const active = page.locator(`#shelf-panel-${key}`)
        await active.waitFor()
        if (key !== 'lists') {
          await active.locator('.book-card').first().waitFor()
          assert.equal(await active.locator('.book-card').count(), 96)
        } else {
          await active.getByText('测试书单', { exact: true }).waitFor()
        }
        assert.equal(await page.locator('.shelf-view [role="tabpanel"]').count(), 1)
        await noMenus(page)
      }
      await page.locator('#shelf-tab-shelf').click()
      await panel.locator('.book-card').first().waitFor()
      assert.equal(await panel.locator('.book-card').count(), 4)
      assert.equal(await panel.locator('.book-title').first().textContent(), '测试书籍97')
      await page.getByRole('button', { name: '列表视图', exact: true }).click()
      await panel.locator('.book-list-item').first().waitFor()
      assert.equal(await panel.locator('.book-list-item').count(), 4)
      await openMenu(page, panel.getByRole('button', { name: '更多操作', exact: true }).first(), '✏️ 编辑书籍')
      await closeMenu(page)
      await openMenu(page, panel.getByRole('button', { name: '移动分组', exact: true }).first(), '📁 测试分组')
      await closeMenu(page)
      console.log(`Shelf active panels, page retention and lazy menus passed: ${theme}/${width}`)

      await page.goto(baseUrl + '/crawler')
      for (const tab of ['发现书籍', '采集书籍']) {
        await page.getByRole('tab', { name: new RegExp(tab) }).click()
        const control = page.locator(tab === '发现书籍' ? '.discovery-view-switch' : '.crawler-book-view-switch')
        for (const mode of ['表格', '卡片']) {
          await control.getByRole('tab', { name: new RegExp(mode) }).click()
          const trigger = mode === '表格'
            ? page.locator('.crawler-book-action-more').first()
            : page.getByRole('button', { name: '更多操作', exact: true }).first()
          await trigger.waitFor({ state: 'attached' })
          await noMenus(page)
          await openMenu(page, trigger, '加入书单')
          await closeMenu(page)
          await trigger.focus()
          await page.keyboard.press('Enter')
          await page.getByRole('menuitem', { name: '加入书单', exact: true }).click()
          const list = page.getByRole('dialog', { name: '预选书单 · 测试书籍1', exact: true })
          await list.waitFor()
          await list.getByRole('button', { name: '取消', exact: true }).click()
          await list.waitFor({ state: 'hidden' })
          await noMenus(page)
        }
      }
      for (const tab of ['采集任务', '失败任务']) {
        await page.getByRole('tab', { name: new RegExp(tab) }).click()
        const trigger = page.locator('.task-action-more').first()
        await trigger.waitFor({ state: 'attached' })
        await noMenus(page)
        await openMenu(page, trigger, '修改')
        await page.getByRole('menuitem', { name: '修改', exact: true }).click()
        const edit = page.getByRole('dialog', { name: '修改采集任务', exact: true })
        await edit.waitFor()
        assert((await edit.textContent()).includes('测试书籍1'))
        await edit.getByRole('button', { name: '取消', exact: true }).click()
        await edit.waitFor({ state: 'hidden' })
        await noMenus(page)
      }
      console.log(`Crawler discoveries, books and task lazy menus passed: ${theme}/${width}`)
      await page.goto(baseUrl + '/books/1')
      const detailTrigger = page.locator('.more-actions-button')
      await detailTrigger.waitFor()
      await noMenus(page)
      await openMenu(page, detailTrigger, '🔍 刮削元数据')
      assert.equal(await detailTrigger.getAttribute('aria-expanded'), 'true')
      await closeMenu(page)
      assert.equal(await detailTrigger.getAttribute('aria-expanded'), 'false')
      const userMenu = page.locator('.modern-layout .user-button')
      if (await userMenu.isVisible()) {
        await openMenu(page, userMenu, '退出登录')
        await closeMenu(page)
      }
      await page.goto(baseUrl + '/rewrite/1')
      const projectTrigger = page.getByRole('button', { name: /项目操作/ })
      await projectTrigger.waitFor()
      await noMenus(page)
      await projectTrigger.hover()
      await page.getByRole('menuitem', { name: '修改项目名称', exact: true }).waitFor()
      assert.equal(await page.locator(menuSelector).count(), 1)
      let oldName
      page.once('dialog', async dialog => {
        oldName = dialog.defaultValue()
        await dialog.dismiss()
      })
      await page.getByRole('menuitem', { name: '修改项目名称', exact: true }).click()
      assert.equal(oldName, '测试重写项目')
      await page.locator('h1').hover()
      await noMenus(page)
      assert.deepEqual(errors, [])
      console.log(`Detail, project and available user menus passed: ${theme}/${width}`)
      await context.close()
    }
  }
} finally {
  await browser.close()
}
