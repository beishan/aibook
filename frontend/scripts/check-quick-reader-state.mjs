import assert from 'node:assert/strict'
import { browser, setup, initialSettings, errors } from './reader-ui-fixture.mjs'

const base = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
try {
  for (const appearance of ['blueGray', 'classic', 'warmPaper', 'spreadsheetWarm']) {
    const test = await setup(1200, { ...initialSettings, appearance, preferScrollMode: false })
    const { page, context } = test
    // A large version with small fixture content reproduces the automatic paging threshold cheaply.
    await context.route('**/api/books/1/versions', route => route.fulfill({ json: [
      { id: 11, format: 'txt', primaryVersion: true, fileSize: 4 * 1024 * 1024, displayName: '长文本' },
    ] }))
    await page.route('**/__quick-reader-test', route => route.fulfill({
      contentType: 'text/html',
      body: '<!doctype html><html><body><div id="app"></div><script type="module" src="/scripts/fixtures/quick-reader-window-harness.ts"></script></body></html>',
    }))
    await page.goto(base + '/__quick-reader-test')
    const open = page.getByRole('button', { name: '打开阅读小窗' })
    const frame = page.frameLocator('.book-quick-reader-frame')
    const close = frame.getByRole('button', { name: appearance === 'spreadsheetWarm' ? '关闭小窗 ×' : '关闭小窗', exact: true }).first()
    const openSettings = async () => {
      if (appearance === 'classic') await frame.locator('.reader-tool-rail button[title="阅读设置"]').click()
      else await frame.getByRole('button', { name: '阅读设置', exact: true }).first().click()
    }
    const toggleToc = async () => {
      if (appearance === 'blueGray') await frame.getByRole('button', { name: '切换阅读侧栏' }).click()
      else if (appearance === 'classic') await frame.locator('.reader-tool-rail button[title="目录"]').click()
      else if (appearance === 'warmPaper') await frame.locator('.warm-tool-rail').getByRole('button', { name: '目录', exact: true }).click()
      else await frame.getByRole('button', { name: '打开完整目录' }).click()
    }
    await open.click()
    await frame.locator('.reader-body.pagination-mode').waitFor()
    if (appearance === 'blueGray' && await frame.locator('.side-panel').count()) {
      await toggleToc()
    } else {
      await toggleToc()
      await frame.locator('.side-panel').waitFor()
      await toggleToc()
    }
    await openSettings()
    const modes = frame.getByRole('radiogroup', { name: '阅读模式', exact: true })
    await modes.getByRole('radio', { name: /滚动/ }).click()
    await frame.locator('.reader-body:not(.pagination-mode)').waitFor()
    await frame.locator('.settings-header .dialog-close').click()
    await close.click()
    await page.locator('.book-quick-reader-dialog').waitFor({ state: 'hidden' })
    assert.equal(test.getPreferences().readerSettings.paginationMode, false)
    assert.equal(test.getPreferences().readerSettings.preferScrollMode, true)
    assert.equal(test.getPreferences().readerSettings.quickReaderTocOpen, false)
    await open.click()
    await frame.locator('.reader-body:not(.pagination-mode)').waitFor()
    assert.equal(await frame.locator('.side-panel').count(), 0)
    await toggleToc()
    await frame.locator('.side-panel').waitFor()
    await close.click()
    await page.locator('.book-quick-reader-dialog').waitFor({ state: 'hidden' })
    assert.equal(test.getPreferences().readerSettings.quickReaderTocOpen, true)
    await open.click()
    await frame.locator('.side-panel').waitFor()
    await frame.locator('.reader-body:not(.pagination-mode)').waitFor()
    // Reversing the mode must also survive closing, even with an open contents panel.
    await openSettings()
    await modes.getByRole('radio', { name: /翻页/ }).click()
    await frame.locator('.settings-header .dialog-close').click()
    await close.click()
    await page.locator('.book-quick-reader-dialog').waitFor({ state: 'hidden' })
    assert.equal(test.getPreferences().readerSettings.preferScrollMode, false)
    assert.equal(test.getPreferences().readerSettings.paginationMode, true)
    await open.click()
    await frame.locator('.reader-body.pagination-mode').waitFor()
    await test.context.close()
  }
  const fresh = await setup(1200, { ...initialSettings, preferScrollMode: false })
  await fresh.page.getByRole('button', { name: '阅读设置', exact: true }).click()
  const savedScroll = fresh.page.waitForResponse(response => response.url().endsWith('/api/user/preferences')
    && response.request().method() === 'PUT' && response.request().postDataJSON()?.readerSettings?.preferScrollMode === true)
  await fresh.page.getByRole('radiogroup', { name: '阅读模式', exact: true }).getByRole('radio', { name: '滚动', exact: true }).click()
  await savedScroll
  assert.equal(fresh.getPreferences().readerSettings.preferScrollMode, true, 'clicking an already active Scroll still records explicit choice')
  await fresh.context.close()
  const legacy = await setup(1200, { ...initialSettings, quickReaderTocOpen: false })
  await legacy.context.route('**/api/books/1/versions', route => route.fulfill({ json: [
    { id: 11, format: 'txt', primaryVersion: true, fileSize: 4 * 1024 * 1024, displayName: '旧配置长文本' },
  ] }))
  await legacy.page.reload()
  await legacy.page.locator('.reader-body:not(.pagination-mode)').waitFor()
  await legacy.page.locator('.reader-loading-overlay').waitFor({ state: 'hidden' })
  assert.equal(await legacy.page.locator('.side-panel').count(), 1, 'main reader ignores quick-window TOC preference')
  await legacy.context.close()
  assert.deepEqual(errors, [])
  console.log('Quick reader state: large-text paging reproduction, explicit scroll/page restore, TOC closed/open restore, immediate close and four skins passed')
} finally {
  await browser.close()
}
