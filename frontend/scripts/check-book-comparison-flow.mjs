import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.MOBILE_PLAYWRIGHT_PATH || 'playwright')
const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const browser = await chromium.launch({ headless: true, channel: process.env.MOBILE_BROWSER_CHANNEL || 'chrome' })
const book = id => ({
  id, title: `对比书籍${id}`, author: '作者', format: 'txt',
  sourceType: id % 2 ? 'DIRECTORY_SCAN' : 'CRAWLER', sourceSiteName: '测试书站',
  sourcePath: `/书库/对比书籍${id}.txt`, chapterCount: id % 2 ? 2 : 3, fileSize: 2048,
  filePath: `/books/${id}.txt`, description: `书籍${id}的简介`, isbn: '123456', publisher: '测试出版社',
  readingStatus: 'UNREADING', tags: [], tagNames: [], isFavorite: false, isWanted: false,
  onShelf: false, createdAt: '2026-10-14T10:00:00', updatedAt: '2026-10-14T10:00:00',
})
const group = (left, right) => ({ id: `similar-${left}`, similarity: 100, books: [book(left), book(right)] })
const initialGroups = [group(1, 2), ...Array.from({ length: 19 }, (_, index) => group(5 + index * 2, 6 + index * 2)), group(3, 4)]
const chapters = [{ index: 0, title: '第一章 出发' }, { index: 1, title: '第二章 到达' }]

try {
  for (const width of [390, 1440]) {
    const context = await browser.newContext({ viewport: { width, height: 1000 } })
    await context.addInitScript(() => localStorage.setItem('token', 'comparison-fixture'))
    const errors = []
    const imports = []
    const chapterRequests = []
    let scans = 0
    let delayOldChapter = false
    await context.route('**/api/**', async route => {
      const request = route.request()
      const url = new URL(request.url())
      const path = url.pathname
      let json = []
      if (path === '/api/site/settings') json = { registrationEnabled: true }
      else if (path === '/api/user/profile') json = { id: 1, username: 'reader', role: 'ADMIN' }
      else if (path === '/api/user/preferences') json = {}
      else if (path === '/api/user/dock-icons') json = { userId: 1, icons: [], versions: {} }
      else if (path === '/api/books/trash/count') json = 0
      else if (path === '/api/books') json = { content: [book(1)], totalElements: 42, totalPages: 3, number: 0, size: 20 }
      else if (path === '/api/books/similarity-scans' || path === '/api/books/similarity-scans/test') {
        if (request.method() === 'POST') scans++
        const groups = scans > 1 ? [group(3, 4)] : initialGroups
        const size = Number(url.searchParams.get('size') || 20)
        const page = Number(url.searchParams.get('page') || 0)
        json = { taskId: 'test', status: 'COMPLETED', message: '扫描完成',
          totalBooks: 42, processedBooks: 42, matchedBooks: groups.length * 2,
          totalGroups: groups.length, groups: groups.slice(page * size, (page + 1) * size) }
      } else if (/\/comparison\/chapters\/\d+$/.test(path)) {
        const id = Number(path.split('/')[3])
        const chapter = Number(path.split('/').at(-1))
        const versionId = Number(url.searchParams.get('versionId'))
        chapterRequests.push({ id, chapter, versionId })
        const delayed = delayOldChapter && id === 1 && chapter === 1
        if (delayed) {
          delayOldChapter = false
          await new Promise(resolve => setTimeout(resolve, 800))
        }
        const content = delayed ? '迟到的旧章节内容'
          : versionId === 11 ? '校对版正文，存在修改。' : chapter === 0 ? '共同正文。\n第二段。' : `书籍${id}的第二章正文。`
        json = { index: chapter, title: chapters[chapter].title, content, totalCharacters: content.length, truncated: false }
      } else if (path.endsWith('/comparison')) {
        const id = Number(path.split('/')[3])
        json = { book: book(id), versionId: Number(url.searchParams.get('versionId') || id * 10), format: 'txt',
          fileSize: 2048, contentSupported: true, message: '', chapters }
      } else if (path.endsWith('/versions/import')) {
        const id = Number(path.split('/')[3])
        imports.push({ id, body: request.postDataJSON() })
        if (id === 4) {
          await route.fulfill({ status: 409, json: { message: '源书籍存在重写项目' } })
          return
        }
        json = []
      } else if (path.endsWith('/versions')) {
        const id = Number(path.split('/')[3])
        json = [{ id: id * 10, displayName: '原版', format: 'txt', primaryVersion: true },
          { id: id * 10 + 1, displayName: '校对版', format: 'txt', primaryVersion: false }]
      } else if (/^\/api\/books\/\d+$/.test(path)) json = book(Number(path.split('/').at(-1)))
      await route.fulfill({ json })
    })
    const page = await context.newPage()
    page.on('pageerror', error => errors.push(error.message))
    await page.goto(`${baseUrl}/books`)
    await page.getByRole('button', { name: '扫描相似书名', exact: true }).click()
    const scan = page.locator('.book-similarity-dialog:visible')
    await scan.getByText('网站采集 · 测试书站 · 3 章 · 2.00 KiB', { exact: true }).first().waitFor()
    await scan.getByRole('button', { name: '对比书籍', exact: true }).first().click()
    const comparison = page.locator('.book-comparison-dialog:visible')
    await comparison.getByText('所选章节文本一致', { exact: true }).waitFor()
    assert.equal(imports.length, 0)
    const left = comparison.locator('.comparison-pane').nth(0)
    const right = comparison.locator('.comparison-pane').nth(1)
    await left.getByText('2.00 KiB', { exact: true }).waitFor()
    await left.getByText('目录扫描', { exact: true }).waitFor()
    await right.getByText('网站采集 · 测试书站', { exact: true }).waitFor()
    delayOldChapter = true
    await left.getByRole('button', { name: '2. 第二章 到达', exact: true }).click()
    await left.getByRole('button', { name: '1. 第一章 出发', exact: true }).click()
    await page.waitForTimeout(950)
    await comparison.getByText('所选章节文本一致', { exact: true }).waitFor()
    assert.equal(await comparison.getByText('迟到的旧章节内容', { exact: true }).count(), 0)
    await left.locator('header .el-select').nth(1).click()
    await page.getByRole('option', { name: '校对版 · TXT', exact: true }).click()
    await left.getByText('校对版正文，存在修改。', { exact: true }).waitFor()
    assert(chapterRequests.some(request => request.id === 1 && request.versionId === 11))
    await comparison.getByText('所选章节文本不同，请对照下方正文', { exact: true }).waitFor()
    const overflow = await comparison.evaluate(element => [...element.querySelectorAll('.comparison-pane')]
      .some(pane => pane.getBoundingClientRect().right > innerWidth + 1 || pane.getBoundingClientRect().left < -1))
    assert.equal(overflow, false)
    if (process.env.SIMILARITY_SCREENSHOT_DIR) {
      await mkdir(process.env.SIMILARITY_SCREENSHOT_DIR, { recursive: true })
      await page.waitForTimeout(250)
      await page.screenshot({ path: `${process.env.SIMILARITY_SCREENSHOT_DIR}/comparison-${width}.png`, fullPage: true })
    }
    await comparison.getByRole('button', { name: '关闭对比', exact: true }).click()
    assert.equal(imports.length, 0, 'comparison must not merge books')

    await scan.getByRole('button', { name: '选择全部结果', exact: true }).click()
    await scan.getByText('已选 21 组（可跨页选择）', { exact: true }).waitFor()
    await scan.getByRole('button', { name: '清空分组', exact: true }).click()
    await scan.locator('.group-header .el-checkbox').first().click()
    await scan.locator('.scan-pagination').getByRole('button', { name: '下一页', exact: true }).click()
    await scan.getByText('2 / 2', { exact: true }).waitFor()
    await scan.locator('.group-header .el-checkbox').first().click()
    await scan.getByText('已选 2 组（可跨页选择）', { exact: true }).waitFor()
    await scan.locator('.scan-pagination').getByRole('button', { name: '上一页', exact: true }).click()
    await scan.getByText('1 / 2', { exact: true }).waitFor()
    assert(await scan.locator('.group-header').first().getByRole('checkbox').isChecked())
    await scan.getByRole('button', { name: '批量合并 2 组', exact: true }).click()
    const batch = page.locator('.book-batch-merge-dialog:visible')
    await batch.locator('.batch-plan .el-select').first().click()
    await page.getByRole('option', { name: '对比书籍1 · 2 章 · #1', exact: true }).click()
    await batch.getByRole('button', { name: '确认批量合并 2 组', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '取消', exact: true }).click()
    assert.equal(imports.length, 0)
    await batch.getByRole('button', { name: '确认批量合并 2 组', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await batch.getByText('源书籍存在重写项目', { exact: true }).waitFor()
    assert.equal(imports.length, 2)
    assert.deepEqual(imports[0], { id: 1, body: { sourceHandling: 'MERGE', sources: [{ bookId: 2, versionIds: [20, 21] }] } })
    assert.deepEqual(imports[1], { id: 4, body: { sourceHandling: 'MERGE', sources: [{ bookId: 3, versionIds: [30, 31] }] } })
    await batch.getByRole('button', { name: '关闭', exact: true }).click()
    await scan.getByText('已检查 42 / 42 本 · 1 组 · 2 本候选', { exact: false }).waitFor()
    assert.deepEqual(errors, [])
    await context.close()
    console.log(`Comparison details, source/count/size, versions, stale response protection, all/cross-page selection and partial batch merge passed at ${width}px`)
  }
} finally {
  await browser.close()
}
