import assert from 'node:assert/strict'
import { browser, setup, initialSettings } from './reader-ui-fixture.mjs'

const base = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const remote = { readerSettings: initialSettings, shelfViewMode: null, shelfCardSize: null }
const other = { readerSettings: initialSettings }
const books = [1, 2].map(id => ({ id, title: `书架图书${id}`, format: 'txt', author: '作者', onShelf: true, tags: [] }))
async function wire(context, target) {
  await context.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let json
    if (path === '/api/user/preferences') {
      if (route.request().method() === 'PUT') Object.assign(target, route.request().postDataJSON())
      json = target
    } else if (path === '/api/shelf') json = { ungroupedBooks: books, groups: [], totalBooks: 2 }
    else if (path === '/api/books' || /\/api\/books\/(favorites|wanted)$/.test(path)) json = { content: books, totalPages: 1 }
    else return route.fallback()
    await route.fulfill({ json })
  })
}
try {
  const test = await setup(1200, initialSettings)
  const { page, context } = test
  await wire(context, remote)
  await page.evaluate(() => localStorage.setItem('shelf-settings', JSON.stringify({ viewMode: 'grid', cardSize: 'small' })))
  await page.goto(base + '/shelf')
  await page.locator('.books-grid.card-small').waitFor()
  await page.waitForFunction(() => localStorage.getItem('shelf-settings') === null)
  assert.equal(remote.shelfCardSize, 'small', 'legacy small cards migrate to account')
  assert.equal(remote.shelfViewMode, 'grid')
  const savedLarge = page.waitForResponse(res => res.url().endsWith('/api/user/preferences')
    && res.request().method() === 'PUT' && res.request().postDataJSON()?.shelfCardSize === 'large')
  await page.getByRole('radio', { name: '大卡片', exact: true }).click()
  await savedLarge
  await page.reload()
  await page.locator('.books-grid.card-large').waitFor()
  const savedSmall = page.waitForResponse(res => res.url().endsWith('/api/user/preferences')
    && res.request().method() === 'PUT' && res.request().postDataJSON()?.shelfCardSize === 'small')
  await page.getByRole('radio', { name: '大卡片', exact: true }).focus()
  await page.keyboard.press('Home')
  await savedSmall
  const savedList = page.waitForResponse(res => res.url().endsWith('/api/user/preferences')
    && res.request().method() === 'PUT' && res.request().postDataJSON()?.shelfViewMode === 'list')
  await page.getByRole('button', { name: '表格视图', exact: true }).click()
  await page.locator('.shelf-books-table').waitFor()
  await savedList
  await page.reload()
  await page.locator('.shelf-books-table').waitFor()
  const savedGrid = page.waitForResponse(res => res.url().endsWith('/api/user/preferences')
    && res.request().method() === 'PUT' && res.request().postDataJSON()?.shelfViewMode === 'grid')
  await page.getByRole('button', { name: '卡片视图', exact: true }).click()
  await page.locator('.books-grid.card-small').waitFor()
  await savedGrid
  // A separate browser has no legacy cache and must still recover the account's selection.
  const fresh = await setup(1200, initialSettings)
  await wire(fresh.context, remote)
  await fresh.page.goto(base + '/shelf')
  await fresh.page.locator('.books-grid.card-small').waitFor()
  // Server settings win over a conflicting browser migration value.
  await fresh.page.evaluate(() => localStorage.setItem('shelf-settings', JSON.stringify({ viewMode: 'list', cardSize: 'large' })))
  await fresh.page.reload()
  await fresh.page.locator('.books-grid.card-small').waitFor()
  assert.equal(remote.shelfCardSize, 'small')
  await wire(fresh.context, other)
  await fresh.context.addInitScript(() => localStorage.setItem('token', 'other-account'))
  await fresh.page.goto(base + '/shelf')
  await fresh.page.locator('.books-grid.card-medium').waitFor()
  assert.equal(await fresh.page.getByRole('radio', { name: '中卡片', exact: true }).getAttribute('aria-checked'), 'true')
  await fresh.context.close()
  const delayed = await setup(1200, initialSettings)
  await wire(delayed.context, remote)
  let release
  const gate = new Promise(resolve => { release = resolve })
  await delayed.context.route('**/api/user/preferences', async route => {
    if (route.request().method() !== 'GET') return route.fallback()
    const snapshot = { ...remote, shelfCardSize: 'medium' }
    await gate
    await route.fulfill({ json: snapshot })
  })
  await delayed.page.goto(base + '/shelf', { waitUntil: 'domcontentloaded' })
  const latest = delayed.page.waitForResponse(res => res.url().endsWith('/api/user/preferences')
    && res.request().method() === 'PUT' && res.request().postDataJSON()?.shelfCardSize === 'small')
  await delayed.page.getByRole('radio', { name: '小卡片', exact: true }).click()
  await latest
  const hydrated = delayed.page.waitForResponse(res => res.url().endsWith('/api/user/preferences') && res.request().method() === 'GET')
  release()
  await hydrated
  await delayed.page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  assert.equal(await delayed.page.getByRole('radio', { name: '小卡片', exact: true }).getAttribute('aria-checked'), 'true',
    'late hydration must not overwrite an explicit newer card-size selection')
  await delayed.context.close()
  await context.close()
  console.log('Shelf display: legacy migration, immediate save, small/large and keyboard, list/grid restoration, fresh browser, server precedence and account isolation passed')
} finally {
  await browser.close()
}
