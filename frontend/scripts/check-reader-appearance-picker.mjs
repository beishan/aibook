import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { browser, setup, initialSettings } from './reader-ui-fixture.mjs'

const output = '/tmp/reader-appearance-picker-review'
await mkdir(output, { recursive: true })
try {
  for (const [appearance, width, quick] of [['blueGray', 1100, false], ['classic', 560, true], ['spreadsheetWarm', 390, true]]) {
    const test = await setup(width, { ...initialSettings, appearance })
    const { page } = test
    if (quick) {
      await page.goto((process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100') + '/reader/1?quickWindow=1')
      await page.locator('.reader-loading-overlay').waitFor({ state: 'hidden' })
    }
    if (appearance === 'classic') {
      await page.locator('.reader-tool-rail button[title="阅读设置"]').click()
    } else {
      await page.getByRole('button', { name: '阅读设置', exact: true }).first().click()
    }
    const picker = page.getByRole('radiogroup', { name: '正式阅读器界面样式' })
    await picker.scrollIntoViewIfNeeded()
    assert.equal(await picker.getByRole('radio').count(), 7)
    assert.ok(await picker.evaluate(el => el.scrollWidth <= el.clientWidth))
    await page.screenshot({ path: `${output}/${appearance}.png` })
    await picker.getByRole('radio', { name: '暖纸表格', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('[data-reader-appearance="spreadsheetWarm"]'))
    await picker.getByRole('radio', { name: '暖纸表格', exact: true }).focus()
    await page.keyboard.press('Home')
    await page.locator('[data-reader-appearance="classic"]').waitFor()
    await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === '经典')
    await page.keyboard.press('End')
    await page.locator('[data-reader-appearance="spreadsheetWarm"]').waitFor()
    await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === '暖纸表格')
    await page.keyboard.press('ArrowUp')
    await page.locator('[data-reader-appearance="warmPaper"]').waitFor()
    await page.keyboard.press('ArrowDown')
    await page.locator('[data-reader-appearance="spreadsheetWarm"]').waitFor()
    await picker.scrollIntoViewIfNeeded()
    await page.waitForFunction(() => {
      const root = document.querySelector('.reader-appearance-picker')
      const selected = root.querySelector('[aria-checked="true"]').getBoundingClientRect()
      const indicator = root.querySelector('.appearance-picker-indicator').getBoundingClientRect()
      return Math.abs(selected.x - indicator.x) < 2 && Math.abs(selected.y - indicator.y) < 2
        && Math.abs(selected.width - indicator.width) < 2
    })
    await test.context.close()
  }
  console.log('Appearance picker: seven visible options, no horizontal overflow, theme changes, keyboard, moving indicator, main and quick modes passed')
} finally {
  await browser.close()
}
