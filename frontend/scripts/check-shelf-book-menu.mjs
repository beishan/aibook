import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { browser, setup, initialSettings } from './reader-ui-fixture.mjs'

const output = '/tmp/shelf-book-menu-review'
await mkdir(output, { recursive: true })
try {
  const test = await setup(1280, initialSettings)
  const { page, context } = test
  const books = [1, 2, 3].map(id => ({ id, title: `测试图书${id}：长书名也能清楚识别`, author: '测试作者', format: 'txt',
    isFavorite: false, isWanted: false, onShelf: true, readingStatus: 'UNREAD', coverUrl: null, shelfGroupId: null, tags: [] }))
  const requests = []
  await context.route('**/api/**', async route => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname
    let json
    if (path === '/api/shelf') json = { ungroupedBooks: books, groups: [{ id: 9, name: '文学', icon: '📚', books: [] }], totalBooks: books.length }
    else if (path === '/api/shelf/books/1/group') {
      requests.push({ path, data: req.postDataJSON() })
      json = books[0]
    } else if (/\/api\/books\/(favorites|wanted)$/.test(path) || path === '/api/books') json = { content: books, totalPages: 1, totalElements: 3 }
    else if (path === '/api/books/1/favorite') {
      requests.push({ path })
      books[0].isFavorite = !books[0].isFavorite
      json = books[0]
    } else if (path === '/api/reading-progress/recent') json = books
    else return route.fallback()
    await route.fulfill({ json })
  })
  await page.goto((process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100') + '/shelf')
  const trigger = page.getByRole('button', { name: books[0].title + '的操作菜单', exact: true })
  await trigger.click()
  const menu = page.getByRole('menu', { name: books[0].title + '的书籍操作', exact: true })
  await menu.waitFor()
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.shelf-book-menu-popper')).opacity === '1')
  for (const label of ['收藏', '想读', '小窗阅读', '编辑书籍', '下载到本地', '移出书架', '移入回收站']) {
    assert.equal(await menu.getByRole('menuitem', { name: label, exact: true }).count(), 1, label)
  }
  assert.equal(await menu.getByRole('button', { name: '更多操作' }).count(), 0)
  assert.ok(await menu.getByRole('menuitem', { name: '下载到本地', exact: true }).evaluate(el => el.clientWidth > 200))
  assert.ok(page.url().endsWith('/shelf'))
  await page.screenshot({ path: `${output}/desktop.png` })
  await page.getByRole('heading', { name: '我的书架', exact: true }).click()
  await menu.waitFor({ state: 'hidden' })
  await trigger.click()
  await page.keyboard.press('End')
  assert.equal(await menu.getByRole('menuitem', { name: '移入回收站', exact: true }).evaluate(el => el === document.activeElement), true)
  await page.keyboard.press('Escape')
  await menu.waitFor({ state: 'hidden' })
  assert.equal(await trigger.evaluate(el => el === document.activeElement), true)
  await trigger.click()
  await menu.getByRole('menuitem', { name: '收藏', exact: true }).click()
  await menu.waitFor({ state: 'hidden' })
  assert.ok(requests.some(req => req.path === '/api/books/1/favorite'))
  await trigger.click()
  await menu.getByRole('button', { name: '移动到分组', exact: true }).click()
  await page.getByRole('menuitem', { name: '📚 文学' }).click()
  await menu.waitFor({ state: 'hidden' })
  assert.ok(requests.some(req => req.path === '/api/shelf/books/1/group' && req.data.groupId === 9))
  for (const close of await page.locator('.el-notification__closeBtn').all()) await close.click()
  await page.setViewportSize({ width: 390, height: 780 })
  await trigger.click()
  await menu.waitFor()
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.shelf-book-menu-popper')).opacity === '1')
  await page.screenshot({ path: `${output}/mobile.png` })
  const box = await page.locator('.shelf-book-menu-popper').boundingBox()
  assert.ok(box.x >= 0 && box.x + box.width <= 391)
  assert.ok(box.y >= 0 && box.y + box.height <= 781)
  assert.ok(await menu.evaluate(el => el.scrollWidth <= el.clientWidth))
  await page.keyboard.press('Escape')
  await menu.waitFor({ state: 'hidden' })
  await page.setViewportSize({ width: 1280, height: 1024 })
  for (const label of ['收藏', '已读完', '想读']) {
    await page.getByRole('tab', { name: new RegExp(label) }).click()
    await trigger.click()
    await menu.waitFor()
    assert.equal(await menu.getByRole('menuitem', { name: '下载到本地', exact: true }).count(), 1)
    await page.keyboard.press('Escape')
    await menu.waitFor({ state: 'hidden' })
  }
  await test.context.close()
  console.log('Shelf menu: labelled actions, no nested More, keyboard/Escape, favorite and group actions, action close, mobile bounds passed')
} finally {
  await browser.close()
}
