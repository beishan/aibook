import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { browser, setup, initialSettings, errors } from './reader-ui-fixture.mjs'

const base = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const output = '/tmp/quick-reader-toolbar-review'
await mkdir(output, { recursive: true })
try {
  for (const appearance of ['blueGray', 'classic', 'readingRoom', 'trialReader', 'warmPaper', 'spreadsheet', 'spreadsheetWarm']) {
    const test = await setup(1200, { ...initialSettings, appearance })
    const { page } = test
    await page.route('**/__quick-reader-test', route => route.fulfill({
      contentType: 'text/html',
      body: '<!doctype html><html><body><div id="app"></div><script type="module" src="/scripts/fixtures/quick-reader-window-harness.ts"></script></body></html>',
    }))
    await page.goto(base + '/__quick-reader-test')
    await page.getByRole('button', { name: '打开阅读小窗' }).click()
    const frame = page.frameLocator('.book-quick-reader-frame')
    const drag = frame.getByRole('button', { name: '拖动小窗：拖动调整位置，也可使用方向键' })
    await drag.waitFor()
    assert.equal(await page.locator('.book-quick-reader-window-toolbar').count(), 0)
    assert.equal(await frame.locator('.blue-avatar').count(), 0)
    const dialog = page.locator('.book-quick-reader-dialog')
    // Create room for movement using the retained resize handle.
    await page.getByRole('button', { name: '拖动调整阅读窗口大小，也可使用方向键' }).focus()
    await page.keyboard.press('Shift+ArrowLeft')
    const before = await dialog.boundingBox()
    await drag.focus()
    await page.keyboard.press('ArrowRight')
    await page.waitForFunction(left => Math.round(document.querySelector('.book-quick-reader-dialog').getBoundingClientRect().left) > left, before.x)
    const afterKeyboard = await dialog.boundingBox()
    const point = await drag.boundingBox()
    await page.mouse.move(point.x + point.width / 2, point.y + point.height / 2)
    await page.mouse.down()
    await page.mouse.move(point.x + point.width / 2 - 20, point.y + point.height / 2 + 8, { steps: 5 })
    await page.mouse.up()
    assert.ok((await dialog.boundingBox()).x < afterKeyboard.x)
    if (appearance === 'blueGray') {
      await frame.getByRole('button', { name: '全屏阅读', exact: true }).click()
      await frame.getByRole('button', { name: '退出全屏', exact: true }).first().waitFor()
      await drag.waitFor()
      await frame.getByRole('button', { name: '退出全屏', exact: true }).first().click()
    }
    await page.screenshot({ path: `${output}/${appearance}-toolbar.png` })
    const settings = frame.getByRole('button', { name: '阅读设置', exact: true })
    if (['classic', 'readingRoom', 'trialReader'].includes(appearance)) {
      await frame.locator('.reader-tool-rail button[title="阅读设置"]').click()
    } else await settings.first().click()
    const mask = frame.locator('#quick-reader-mask-transparency')
    await mask.waitFor()
    await mask.evaluate(el => {
      el.value = '80'
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
    })
    await page.waitForFunction(() => getComputedStyle(document.querySelector('.book-quick-reader-overlay')).backgroundColor.includes('0.2'))
    await page.waitForFunction(() => document.querySelector('.book-quick-reader-frame').contentDocument.querySelector('output')?.textContent === '80%')
    await page.screenshot({ path: `${output}/${appearance}-settings.png` })
    await frame.locator('.settings-header .dialog-close').click()
    await page.setViewportSize({ width: 560, height: 760 })
    await drag.waitFor()
    assert.ok(await drag.isVisible())
    await page.screenshot({ path: `${output}/${appearance}-narrow-toolbar.png` })
    const close = ['classic', 'readingRoom', 'trialReader'].includes(appearance)
      ? frame.getByRole('button', { name: /关闭小窗/ }).first()
      : frame.getByRole('button', { name: appearance.startsWith('spreadsheet') ? '关闭小窗 ×' : '关闭小窗', exact: true })
    await close.click()
    await dialog.waitFor({ state: 'hidden' })
    assert.equal(test.getPreferences().quickReaderWindow.overlayTransparency, 80)
    await page.getByRole('button', { name: '打开阅读小窗' }).click()
    await drag.waitFor()
    assert.ok(await page.locator('.book-quick-reader-overlay').evaluate(el => getComputedStyle(el).backgroundColor.includes('0.2')))
    await test.context.close()
  }
  assert.deepEqual(errors, [])
  console.log('Quick toolbar: seven skins, real parent/iframe dragging, keyboard movement, resize, mask updates/persistence, close/reopen and no avatar passed')
} finally {
  await browser.close()
}
