import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { browser, setup, errors, capture, initialSettings } from './reader-ui-fixture.mjs'

const output = process.env.READER_SCREENSHOT_DIR || '/tmp/reader-spreadsheet-review'
await mkdir(output, { recursive: true })
try {
  for (const appearance of ['spreadsheet', 'spreadsheetWarm']) {
    const test = await setup(1536, { ...initialSettings, appearance, fontSize: 20 })
    const { page } = test
    await page.locator('.sheet-header').waitFor()
    assert.equal(await page.locator('.sheet-column-ruler span').count(), 15)
    const body = page.locator('.reader-body')
    assert.ok(await body.evaluate(el => el.clientHeight > 300))
    assert.equal(await body.evaluate(el => getComputedStyle(el).fontSize), '20px')
    assert.equal(await page.locator('.sheet-titlebar').evaluate(el => getComputedStyle(el).backgroundColor),
      appearance === 'spreadsheetWarm' ? 'rgb(75, 118, 81)' : 'rgb(33, 115, 70)')
    await capture(page, `${output}/${appearance}-desktop.png`)
    await page.getByLabel('正文字号', { exact: true }).fill('22')
    await page.getByLabel('正文字号', { exact: true }).press('Tab')
    await page.waitForFunction(() => getComputedStyle(document.querySelector('.reader-body')).fontSize === '22px')
    await page.waitForResponse(response => response.url().includes('/api/user/preferences')
      && response.request().method() === 'PUT' && response.request().postDataJSON()?.readerSettings?.fontSize === 22)
    await page.reload()
    await page.locator('.sheet-header').waitFor()
    assert.equal(await page.getByLabel('正文字号', { exact: true }).inputValue(), '22')
    await page.getByRole('radiogroup', { name: '工作表章节' }).getByRole('radio').nth(1).click()
    await page.waitForFunction(() => document.querySelector('.sheet-formula-text')?.textContent.includes('第二章'))
    await page.getByRole('radio', { name: '视图', exact: true }).click()
    await page.getByRole('button', { name: '切换全屏', exact: true }).click()
    await page.locator('.fullscreen-mode .sheet-header').waitFor()
    assert.ok(await body.evaluate(el => el.clientHeight > 300))
    await page.getByRole('button', { name: '切换全屏', exact: true }).click()
    await page.getByRole('radio', { name: '批注', exact: true }).click()
    await page.getByRole('radio', { name: '批注', exact: true }).focus()
    await page.keyboard.press('ArrowLeft')
    assert.equal(await page.getByRole('radio', { name: '阅读', exact: true }).getAttribute('aria-checked'), 'true')
    await page.getByRole('radio', { name: '批注', exact: true }).click()
    assert.equal(await page.getByRole('button', { name: '添加批注', exact: true }).isDisabled(), true)
    await page.locator('.reader-text p').first().scrollIntoViewIfNeeded()
    await page.locator('.reader-body').evaluate(element => new Promise(resolve => {
      let previous = element.scrollTop
      let stable = 0
      const check = () => {
        stable = element.scrollTop === previous ? stable + 1 : 0
        previous = element.scrollTop
        if (stable > 8) resolve()
        else requestAnimationFrame(check)
      }
      requestAnimationFrame(check)
    }))
    await page.locator('.reader-text p').first().evaluate(element => {
      const range = document.createRange()
      range.selectNodeContents(element)
      const selection = window.getSelection()
      selection.removeAllRanges()
      selection.addRange(range)
      element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    })
    await page.getByRole('toolbar', { name: '选中文字操作' }).waitFor()
    await page.getByRole('button', { name: '添加批注', exact: true }).click()
    await page.getByRole('textbox', { name: '笔记内容' }).fill('表格阅读批注验证')
    await page.getByRole('button', { name: '保存笔记', exact: true }).click()
    await page.locator('.highlight-editor').waitFor({ state: 'hidden' })
    await page.getByRole('button', { name: '全部批注', exact: true }).click()
    await page.getByText('表格阅读批注验证', { exact: true }).waitFor()
    await capture(page, `${output}/${appearance}-notes.png`)
    await page.goto((process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100') + '/reader/1?quickWindow=1')
    await page.locator('.quick-window-mode .sheet-header').waitFor()
    await page.setViewportSize({ width: 560, height: 680 })
    await page.getByRole('button', { name: '关闭小窗 ×' }).waitFor()
    assert.ok(await body.evaluate(el => el.clientHeight > 150))
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await capture(page, `${output}/${appearance}-quick.png`)
    await page.getByRole('button', { name: '阅读设置', exact: true }).first().click()
    await page.getByRole('region', { name: '小窗章节标题设置' }).waitFor()
    await page.keyboard.press('Escape')
    await page.setViewportSize({ width: 390, height: 640 })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await capture(page, `${output}/${appearance}-mobile.png`)
    await test.context.close()
  }
  for (const format of ['html', 'epub', 'pdf']) {
    const test = await setup(1200, { ...initialSettings, appearance: 'spreadsheetWarm' }, format)
    await test.page.locator('.sheet-header').waitFor()
    assert.ok(await test.page.locator('.reader-body').evaluate(el => el.clientHeight > 300))
    if (format === 'pdf') assert.equal(await test.page.getByLabel('正文字号', { exact: true }).isDisabled(), true)
    await capture(test.page, `${output}/spreadsheet-${format}.png`)
    await test.context.close()
  }
  assert.deepEqual(errors, [])
  console.log('Spreadsheet reader: both themes, account restoration, chapters, selection/notes, quick mode, narrow layout, HTML/EPUB/PDF passed')
} finally {
  await browser.close()
}
