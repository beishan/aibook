import assert from 'node:assert/strict'
import { browser, setup, initialSettings, errors, capture, screenshotDir } from './reader-ui-fixture.mjs'

function desktopBridge() {
  window.desktopCalls = []
  window.desktopRecover = null
  window.desktopReader = {
    settings: async value => { window.desktopCalls.push(value) },
    action: async action => { window.desktopCalls.push(action) },
    onRecover: callback => {
      window.desktopRecover = callback
      return () => { window.desktopRecover = null }
    },
  }
}

try {
  for (const appearance of ['classic', 'blueGray', 'warmPaper', 'spreadsheet', 'spreadsheetWarm', 'readingRoom', 'trialReader']) {
    const test = await setup(1000, { ...initialSettings, appearance }, 'txt', desktopBridge)
    const { page, context } = test
    await page.getByRole('button', { name: '隐身阅读', exact: true }).click()
    await page.locator('html.desktop-reading').waitFor()
    const surfaces = await page.evaluate(() => {
      const selectors = ['html', 'body', '#app', '.app-layout', '.reader-content', '.modern-main', '.content-shell', '.reader-body-wrapper', '.reader-body']
      return selectors.map(selector => {
        const element = document.querySelector(selector)
        const style = getComputedStyle(element)
        return [selector, style.backgroundColor, style.backgroundImage]
      })
    })
    for (const [selector, color, image] of surfaces) {
      assert.equal(color, 'rgba(0, 0, 0, 0)', `${appearance}: ${selector} background`)
      assert.equal(image, 'none', `${appearance}: ${selector} image`)
    }
    assert.equal(await page.locator('.reader-header:visible, .blue-topbar:visible, .warm-reader-header:visible, .sheet-header:visible, .warm-tool-rail:visible, .modern-sidebar:visible, .modern-header:visible, .blue-reader-footer:visible, .sheet-footer:visible').count(), 0)
    await page.getByRole('button', { name: '窗口设置', exact: true }).click()
    await page.getByRole('slider', { name: '整体不透明度', exact: true }).fill('35')
    await page.getByRole('slider', { name: '文字不透明度', exact: true }).fill('60')
    await page.waitForFunction(() => window.desktopCalls.some(value => value?.opacity === 35 && value.immersive))
    await page.waitForTimeout(800)
    assert.equal(test.getPreferences().readerSettings.desktop.opacity, 35)
    assert.equal(test.getPreferences().readerSettings.desktop.textOpacity, 60)
    // A ReaderView draft save must not overwrite the independently changed desktop settings.
    await page.evaluate(async () => {
      const { usePreferencesStore } = await import('/src/stores/preferences.ts')
      const store = usePreferencesStore()
      store.setReaderSettings({ ...store.readerSettings, desktop: { opacity: 100, backgroundOpacity: 100, textOpacity: 100, alwaysOnTop: false } })
    })
    await page.waitForTimeout(700)
    assert.equal(test.getPreferences().readerSettings.desktop.opacity, 35)
    await page.getByRole('button', { name: '鼠标穿透', exact: true }).click()
    assert.equal(await page.evaluate(() => window.desktopCalls.includes('passthrough')), true)
    if (appearance === 'warmPaper') await capture(page, `${screenshotDir}/desktop-reader-settings.png`)
    await page.evaluate(() => window.desktopRecover())
    await page.locator('html:not(.desktop-reading)').waitFor()
    await page.waitForFunction(() => window.desktopCalls.at(-1)?.immersive === false)
    await context.close()
  }
  const ordinary = await setup()
  assert.equal(await ordinary.page.locator('.desktop-controls').count(), 0)
  await ordinary.context.close()
  assert.deepEqual(errors, [])
  console.log('Desktop reader: seven skins, transparent surfaces, account settings, stale drafts, recovery and browser isolation passed')
} finally {
  await browser.close()
}
