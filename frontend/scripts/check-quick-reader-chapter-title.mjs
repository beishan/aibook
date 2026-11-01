import assert from 'node:assert/strict'
import { browser, setup, errors, capture, initialSettings, screenshotDir } from './reader-ui-fixture.mjs'

const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'

async function openQuick(test) {
  await test.page.goto(baseUrl + '/reader/1?quickWindow=1')
  await test.page.locator('.reader-loading-overlay').waitFor({ state: 'hidden' })
}

try {
  const test = await setup(1024, { ...initialSettings, quickChapterTitleStyle: 'original', quickChapterTitleFontSize: null })
  const { page } = test
  await openQuick(test)
  await page.locator('.chapter-title').first().waitFor()
  await page.getByRole('button', { name: '阅读设置', exact: true }).click()
  const section = page.getByRole('region', { name: '小窗章节标题设置' })
  await section.getByRole('radio', { name: '横线居中' }).click()
  await capture(page, `${screenshotDir}/quick-title-settings.png`)
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.chapter-title')).fontSize === '16px')
  assert.equal(await page.locator('.chapter-title').first().evaluate(el => getComputedStyle(el).display), 'flex')
  assert.equal(await page.locator('.chapter-title').first().evaluate(el => getComputedStyle(el, '::before').display), 'block')
  assert.equal(await page.locator('.chapter-title').first().evaluate(el => getComputedStyle(el, '::before').borderTopWidth), '1px')
  await section.locator('input[type=range]').evaluate(el => {
    el.value = '14'
    el.dispatchEvent(new Event('input', { bubbles: true }))
  })
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.chapter-title')).fontSize === '14px')
  await section.getByRole('radio', { name: '正文同款' }).click()
  assert.equal(await page.locator('.chapter-title').first().evaluate(el => getComputedStyle(el).fontSize), '14px')
  await section.getByRole('button', { name: '跟随样式' }).click()
  const bodySize = await page.locator('.reader-body').evaluate(el => getComputedStyle(el).fontSize)
  await page.waitForFunction(size => getComputedStyle(document.querySelector('.chapter-title')).fontSize === size, bodySize)
  await section.getByRole('radio', { name: '简洁小标题' }).click()
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.chapter-title')).fontSize === '18px')
  await section.getByRole('radio', { name: '横线居中' }).click()
  await page.waitForResponse(response => response.url().endsWith('/api/user/preferences') && response.request().method() === 'PUT')
  assert.equal(test.getPreferences().readerSettings.quickChapterTitleStyle, 'divider')
  await page.locator('.settings-header .dialog-close').click()
  await capture(page, `${screenshotDir}/quick-title-divider.png`)
  await page.setViewportSize({ width: 390, height: 844 })
  const closeSidebar = page.getByRole('button', { name: '收起侧栏', exact: true })
  if (await closeSidebar.isVisible()) await closeSidebar.click()
  await page.locator('.side-panel').waitFor({ state: 'hidden' })
  await page.locator('.chapter-title').first().evaluate(el => { el.textContent = '第一章 ' + '这是一个很长的章节标题'.repeat(8) })
  assert.ok(await page.locator('.chapter-title').first().evaluate(el => el.scrollWidth <= el.clientWidth + 2), 'long divider titles must wrap at narrow widths')
  await capture(page, `${screenshotDir}/quick-title-mobile-long.png`)
  await page.reload()
  await page.locator('.chapter-title').first().waitFor()
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.chapter-title')).fontSize === '16px')
  await page.goto(baseUrl + '/reader/1')
  await page.locator('.chapter-title').first().waitFor()
  assert.equal(await page.locator('[data-quick-chapter-title]').count(), 0, 'full reader must retain its own headings')
  await test.context.close()

  for (const appearance of ['classic', 'readingRoom', 'trialReader', 'warmPaper']) {
    const sample = await setup(1024, { ...initialSettings, appearance, quickChapterTitleStyle: 'compact', quickChapterTitleFontSize: 15 })
    await openQuick(sample)
    await sample.page.locator('.chapter-title').first().waitFor()
    await sample.page.waitForFunction(() => getComputedStyle(document.querySelector('.chapter-title')).fontSize === '15px')
    await sample.context.close()
  }
  for (const format of ['html', 'epub']) {
    const sample = await setup(1024, { ...initialSettings, quickChapterTitleStyle: 'divider', quickChapterTitleFontSize: 17 }, format)
    await openQuick(sample)
    if (format === 'epub') {
      const heading = sample.page.frameLocator('.epub-container iframe').first().locator('h1, h2').first()
      await heading.waitFor()
      assert.equal(await heading.evaluate(el => getComputedStyle(el).fontSize), '17px')
      assert.equal(await heading.evaluate(el => getComputedStyle(el, '::after').borderTopWidth), '1px')
    } else {
      await sample.page.locator('.reader-html h1').first().waitFor()
      await sample.page.waitForFunction(() => getComputedStyle(document.querySelector('.reader-html h1')).fontSize === '17px')
    }
    await capture(sample.page, `${screenshotDir}/quick-title-${format}.png`)
    await sample.context.close()
  }
  assert.deepEqual(errors, [])
  console.log('Quick chapter titles: four modes, custom/automatic sizes, account restore, all five skins, long mobile titles, HTML and epub.js passed')
} finally {
  await browser.close()
}
