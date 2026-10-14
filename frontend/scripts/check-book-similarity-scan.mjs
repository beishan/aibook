import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.MOBILE_PLAYWRIGHT_PATH || 'playwright')
const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const browser = await chromium.launch({
  headless: true,
  ...(process.env.MOBILE_BROWSER_CHANNEL ? { channel: process.env.MOBILE_BROWSER_CHANNEL } : {}),
})

const book = id => ({
  id, title: `深入理解 Java 虚拟机${id === 2 ? '（精校版）' : id === 3 ? '补充版' : ''}`,
  author: id === 3 ? '另一位作者' : '周志明', format: 'epub', filePath: `/books/${id}.epub`,
  readingStatus: 'UNREADING', tags: [], tagNames: [], isFavorite: false,
  isWanted: false, onShelf: false, createdAt: '2026-10-14T10:00:00', updatedAt: '2026-10-14T10:00:00',
})
const group = { id: 'similar-1', similarity: 80, books: [book(1), book(2), book(3)] }
const scanTask = (status, page = 0) => ({
  taskId: 'scan-test', status, message: status === 'RUNNING' ? '正在匹配相似书名' : '扫描完成',
  totalBooks: 100, processedBooks: status === 'RUNNING' ? 25 : 100,
  matchedBooks: status === 'COMPLETED' ? 42 : 0,
  totalGroups: status === 'COMPLETED' ? 21 : 0,
  groups: status === 'COMPLETED'
    ? [page === 0 ? group : { id: 'similar-4', similarity: 100, books: [book(4), book(5)] }]
    : [],
})

try {
  for (const width of [390, 1440]) {
    const context = await browser.newContext({ viewport: { width, height: 1000 } })
    await context.addInitScript(() => localStorage.setItem('token', 'similarity-fixture'))
    let starts = 0
    let polls = 0
    let mergeCalls = 0
    let mergePayload
    let failNextPoll = false
    const page = await context.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
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
      else if (path === '/api/books' || path === '/api/books/search') {
        json = { content: [book(1)], totalElements: 100, totalPages: 5, number: 0, size: 20 }
      } else if (path === '/api/books/similarity-scans') {
        assert.equal(request.method(), 'POST')
        assert.equal(url.search, '', 'scan must not inherit the current page or filters')
        starts++
        json = starts === 1 ? scanTask('RUNNING') : {
          ...scanTask('COMPLETED'), taskId: 'scan-after-merge', matchedBooks: 0, totalGroups: 0, groups: [],
        }
      } else if (path === '/api/books/similarity-scans/scan-test') {
        polls++
        if (failNextPoll) {
          failNextPoll = false
          await route.fulfill({ status: 503, json: { message: '进度暂时不可用' } })
          return
        }
        json = scanTask('COMPLETED', Number(url.searchParams.get('page')))
      } else if (/^\/api\/books\/\d+$/.test(path)) {
        json = book(Number(path.split('/').at(-1)))
      } else if (/^\/api\/books\/\d+\/versions$/.test(path)) {
        const id = Number(path.split('/')[3])
        json = [{ id: id * 10, displayName: '原版', format: 'epub', primaryVersion: true }]
      } else if (/^\/api\/books\/\d+\/versions\/import$/.test(path)) {
        mergeCalls++
        mergePayload = request.postDataJSON()
        json = {}
      }
      await route.fulfill({ json })
    })

    await page.goto(`${baseUrl}/books`)
    await page.getByPlaceholder('搜索书名、作者、ISBN...').fill('当前筛选条件')
    await page.getByPlaceholder('搜索书名、作者、ISBN...').press('Enter')
    await page.getByRole('button', { name: '扫描相似书名', exact: true }).click()
    const scan = page.locator('.book-similarity-dialog:visible')
    await scan.getByRole('button', { name: '后台运行', exact: true }).click()
    await scan.waitFor({ state: 'hidden' })
    const pollsWhenClosed = polls
    await page.waitForTimeout(1100)
    assert.equal(polls, pollsWhenClosed, 'closing the dialog must stop browser polling')
    failNextPoll = true
    await page.getByRole('button', { name: '扫描相似书名', exact: true }).click()
    await scan.getByText('进度暂时不可用', { exact: true }).waitFor()
    await scan.getByRole('button', { name: '重试获取进度', exact: true }).click()
    await scan.getByText('作者不同，请确认是否为同一本书。').waitFor()
    assert.equal(starts, 1, 'reopening must resume the same scan')

    await scan.getByRole('button', { name: '下一页', exact: true }).click()
    await scan.getByText('2 / 2', { exact: true }).waitFor()
    await scan.getByRole('button', { name: '上一页', exact: true }).click()
    await scan.getByText('1 / 2', { exact: true }).waitFor()

    const candidateOverflow = await scan.evaluate(element => [...element.querySelectorAll('.group-header, .candidate-row')]
      .some(row => row.getBoundingClientRect().right > innerWidth + 1 || row.getBoundingClientRect().left < -1))
    assert.equal(candidateOverflow, false, `candidate rows must fit ${width}px viewport`)
    if (process.env.SIMILARITY_SCREENSHOT_DIR) {
      await mkdir(process.env.SIMILARITY_SCREENSHOT_DIR, { recursive: true })
      await page.screenshot({ path: `${process.env.SIMILARITY_SCREENSHOT_DIR}/scan-${width}.png`, fullPage: true })
    }
    const checkboxes = scan.locator('.candidate-list').getByRole('checkbox')
    await checkboxes.nth(2).focus()
    await page.keyboard.press('Space')
    await scan.getByRole('button', { name: '合并所选 2 本', exact: true }).click()
    const merge = page.locator('.book-merge-dialog:visible')
    await merge.getByRole('button', { name: '确认合并', exact: true }).waitFor()
    assert.equal(mergeCalls, 0, 'preview must not merge automatically')
    await merge.getByRole('button', { name: '确认合并', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '取消', exact: true }).click()
    assert.equal(mergeCalls, 0, 'cancelling confirmation must not change books')
    await merge.getByRole('button', { name: '确认合并', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await scan.getByText('未发现相似书名', { exact: true }).waitFor()
    assert.equal(mergeCalls, 1)
    assert.equal(mergePayload.sourceHandling, 'MERGE')
    assert.deepEqual(mergePayload.sources, [{ bookId: 2, versionIds: [20] }])
    assert.equal(starts, 2, 'merge completion must rescan to remove stale candidates')
    const overflow = await scan.evaluate(element => element.getBoundingClientRect().right > innerWidth + 1
      || element.getBoundingClientRect().left < -1)
    assert.equal(overflow, false, `scan dialog must fit ${width}px viewport`)
    assert.deepEqual(errors, [], 'flow must not produce runtime errors')
    await context.close()
    console.log(`Similarity scan, resume/retry, pagination, keyboard selection and confirmed merge passed at ${width}px`)
  }
} finally {
  await browser.close()
}
