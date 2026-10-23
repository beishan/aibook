import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.MOBILE_PLAYWRIGHT_PATH || 'playwright')
const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const name = '𠮷野家的秘密书房'
const characters = Array.from(name)
const headTail = characters[0] + '*'.repeat(characters.length - 2) + characters.at(-1)
const prefix = characters.slice(0, 3).join('') + '*'.repeat(characters.length - 3)
const book = {
  id: 1, title: name, author: '作者', format: 'epub', onShelf: true,
  readingStatus: 'READING', coverUrl: null, tags: [], versions: [],
  fileSize: 2048, createdAt: '2026-10-23T10:00:00', updatedAt: '2026-10-23T10:00:00',
  totalProgress: 25, progress: 25, isFavorite: true, isWanted: true, wordCount: 10000,
}
const pageResult = content => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 10 })
const browser = await chromium.launch({ headless: true, channel: process.env.MOBILE_BROWSER_CHANNEL || 'chrome' })
try {
  for (const [theme, width] of [['warm', 1564], ['modern', 390], ['macos26', 1280]]) {
    const context = await browser.newContext({ viewport: { width, height: 1000 }, reducedMotion: 'reduce' })
    let preferences = { hideBookNames: false, bookNameMaskMode: 'HEAD_TAIL', bookNamePrefixLength: 2 }
    const changes = [], errors = []
    await context.addInitScript(theme => {
      localStorage.setItem('token', 'book-name-display-test')
      localStorage.setItem('ai-book-theme', theme)
    }, theme)
    await context.route('**/api/**', route => {
      const request = route.request(), path = new URL(request.url()).pathname
      let json = []
      if (path === '/api/site/settings') json = { registrationEnabled: true }
      else if (path === '/api/user/profile') json = { id: 1, username: 'reader', role: 'ADMIN' }
      else if (path === '/api/user/preferences') {
        if (request.method() === 'PUT') {
          const patch = request.postDataJSON()
          preferences = { ...preferences, ...patch }
          changes.push(patch)
        }
        json = preferences
      } else if (path === '/api/user/dock-icons') json = { userId: 1, icons: [], versions: {} }
      else if (path === '/api/books/trash/count') json = 0
      else if (path === '/api/books/1') json = book
      else if (path === '/api/books/1/versions') json = []
      else if (/^\/api\/books(\/reading|\/favorites|\/wanted|\/finished)?$/.test(path)) json = pageResult([book])
      else if (path === '/api/shelf') json = { groups: [], ungroupedBooks: [book], totalBooks: 1 }
      else if (path === '/api/booklists/1') json = { id: 1, name: '测试书单', bookCount: 1, books: [book] }
      else if (path === '/api/home') json = {
        overview: { totalBooks: 1, readingBooks: 1, finishedBooks: 0, favoriteBooks: 1, wantedBooks: 1, fileSizeBytes: 2048 },
        categories: [], continuing: [book], recentlyRead: [book], recentlyAdded: [book], recentlyUpdated: [book], wanted: [book],
      }
      else if (path === '/api/recommendations/today') json = { date: '2026-10-23', groups: [{ generatedAt: '2026-10-23T10:00:00', rule: { id: 1, name: '今日推荐', enabled: true, filter: { count: 1, avoidDays: 7, strictAvoid: false, categoryIds: [], tagIds: [], excludedTagIds: [], authorIds: [], shelfGroupIds: [], statuses: [], formats: [], sources: [], favorite: null, wanted: null, onShelf: null } }, books: [book] }] }
      else if (path === '/api/reading-statistics') json = {
        overview: { totalBooks: 1, finishedBooks: 0, ratedBooks: 0, totalReadingSeconds: 60, averageRating: 0 },
        monthly: [], activity: [], topBooks: [{ ...book, readingSeconds: 60 }], authors: [], formats: [], categories: [], ratings: [],
      }
      else if (path === '/api/operation-logs') json = pageResult([])
      else if (path === '/api/operation-logs/retention') json = { rules: [] }
      else if (path === '/api/cover-privacy/books') json = { initialized: true, allHidden: true, overrides: {} }
      else if (/settings|config|status|resources|dashboard/.test(path)) json = {}
      return route.fulfill({ json })
    })
    const page = await context.newPage()
    page.on('pageerror', error => errors.push(error.message))
    async function saveThrough(action, key, value) {
      const response = page.waitForResponse(result => result.url().endsWith('/api/user/preferences')
        && result.request().method() === 'PUT' && result.request().postDataJSON()[key] === value)
      await action()
      await response
    }
    await page.goto(baseUrl + '/settings?tab=cover-display')
    await page.getByRole('heading', { name: '书籍名称显示', exact: true }).waitFor()
    const panel = page.locator('.book-name-settings')
    await panel.getByRole('radio', { name: '显示', exact: true }).focus()
    await saveThrough(() => page.keyboard.press('End'), 'hideBookNames', true)
    await page.waitForFunction(() => document.querySelector('.book-name-settings [role="radio"][aria-checked="true"]')?.textContent.trim() === '隐藏')
    assert.equal(preferences.hideBookNames, true)
    const routes = [
      ['/books', '.books-grid .book-title'], ['/shelf', '.shelf-view .book-title'],
      ['/booklists/1', '.booklist-view .book-title'], ['/recommendations', '.recommendations-view .book-title'],
      ['/', '.home-view h3'], ['/books/1', '.book-detail-view h1.book-title'],
    ]
    for (const [path, selector] of routes) {
      await page.goto(baseUrl + path)
      await page.locator(selector).filter({ hasText: headTail }).first().waitFor()
      console.log('Masked page', theme, path)
      assert(!(await page.locator('body').innerText()).includes(name), `raw name leaked in ${path}`)
      const leakedHints = await page.locator('[title], [aria-label], [alt]').evaluateAll((elements, name) => elements
        .filter(element => ['title', 'aria-label', 'alt'].some(key => element.getAttribute(key)?.includes(name)))
        .map(element => element.outerHTML), name)
      assert.deepEqual(leakedHints, [], `raw hint leaked in ${path}`)
    }
    await page.goto(baseUrl + '/settings?tab=cover-display')
    await panel.getByRole('radio', { name: '首尾显示', exact: true }).focus()
    await saveThrough(() => page.keyboard.press('End'), 'bookNameMaskMode', 'PREFIX')
    const input = page.locator('#book-name-prefix-length')
    await saveThrough(async () => {
      await input.fill('3')
      await input.blur()
    }, 'bookNamePrefixLength', 3)
    await page.getByRole('heading', { name: '书籍名称显示', exact: true }).click()
    assert.equal(preferences.bookNameMaskMode, 'PREFIX')
    assert.equal(preferences.bookNamePrefixLength, 3)
    for (const [path, selector] of routes.slice(0, 4)) {
      await page.goto(baseUrl + path)
      await page.locator(selector).filter({ hasText: prefix }).first().waitFor()
      assert(!(await page.locator('body').innerText()).includes(name))
    }
    await page.goto(baseUrl + '/settings?tab=cover-display')
    const bounds = await panel.boundingBox()
    assert(bounds.x >= 0 && bounds.x + bounds.width <= width + 1)
    if (process.env.BOOK_NAME_SETTINGS_SCREENSHOT && theme === 'warm') {
      await panel.screenshot({ path: process.env.BOOK_NAME_SETTINGS_SCREENSHOT })
    }
    await saveThrough(() => panel.getByRole('radio', { name: '显示', exact: true }).click(), 'hideBookNames', false)
    await page.goto(baseUrl + '/books')
    await page.locator('.book-title').filter({ hasText: name }).first().waitFor()
    assert.equal(book.title, name, 'display changes must not mutate source data')
    assert(changes.some(patch => patch.hideBookNames === true))
    assert(changes.some(patch => patch.bookNameMaskMode === 'PREFIX'))
    assert(changes.some(patch => patch.bookNamePrefixLength === 3))
    assert.equal(preferences.hideBookNames, false)
    assert.deepEqual(errors, [])
    console.log(`Book name UI: settings, keyboard, persistence, both modes, six pages, hints and restore passed: ${theme}/${width}px`)
    await context.close()
  }
} finally {
  await browser.close()
}
