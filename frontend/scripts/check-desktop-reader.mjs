import assert from 'node:assert/strict'
import { browser, setup, initialSettings, errors, capture, screenshotDir } from './reader-ui-fixture.mjs'

function desktopBridge() {
  window.desktopCalls = []
  window.desktopRecover = null
  window.desktopReader = {
    icon: async value => {
      window.desktopCalls.push({ icon: value })
      return value.startsWith('data:') ? value : ''
    },
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
    await page.getByRole('combobox', { name: '应用图标', exact: true }).selectOption('note')
    await page.waitForFunction(() => window.desktopCalls.some(value => value?.icon === 'note'))
    await page.getByRole('slider', { name: '整体不透明度', exact: true }).fill('35')
    await page.getByRole('slider', { name: '文字不透明度', exact: true }).fill('60')
    await page.waitForFunction(() => window.desktopCalls.some(value => value?.opacity === 35 && value.immersive))
    await page.waitForTimeout(800)
    assert.equal(test.getPreferences().readerSettings.desktop.opacity, 35)
    assert.equal(test.getPreferences().readerSettings.desktop.textOpacity, 60)
    assert.equal(test.getPreferences().readerSettings.desktop.icon, 'note')
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
  const custom = await setup(1000, initialSettings, 'txt', desktopBridge)
  await custom.page.getByRole('button', { name: '窗口设置', exact: true }).click()
  const upload = custom.page.getByLabel('上传 PNG 图标', { exact: true })
  const image = await custom.page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 512
    canvas.height = 128
    const context = canvas.getContext('2d')
    context.fillStyle = '#485b46'
    context.fillRect(0, 0, 512, 128)
    return canvas.toDataURL('image/png')
  })
  await upload.setInputFiles({ name: 'icon.png', mimeType: 'image/png', buffer: Buffer.from(image.split(',')[1], 'base64') })
  await custom.page.waitForFunction(() => document.querySelector('select[aria-label="应用图标"]').value === 'custom')
  await custom.page.waitForTimeout(800)
  const savedIcon = custom.getPreferences().readerSettings.desktop.icon
  assert.ok(savedIcon.startsWith('data:image/png;base64,'))
  assert.ok(savedIcon.length <= 43714)
  const pixels = Buffer.from(savedIcon.split(',')[1], 'base64')
  assert.equal(pixels.readUInt32BE(16), 256)
  assert.equal(pixels.readUInt32BE(20), 256)
  const alpha = await custom.page.evaluate(async data => {
    const image = new Image()
    image.src = data
    await image.decode()
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 256
    const context = canvas.getContext('2d')
    context.drawImage(image, 0, 0)
    return [context.getImageData(0, 0, 1, 1).data[3], context.getImageData(128, 128, 1, 1).data[3]]
  }, savedIcon)
  assert.deepEqual(alpha, [0, 255])
  await custom.page.reload()
  await custom.page.locator('.reader-text').waitFor()
  await custom.page.getByRole('button', { name: '窗口设置', exact: true }).click()
  assert.equal(await custom.page.getByRole('combobox', { name: '应用图标', exact: true }).inputValue(), 'custom')
  assert.equal(await custom.page.getByAltText('当前应用图标').getAttribute('src'), savedIcon)
  await capture(custom.page, `${screenshotDir}/desktop-custom-icon.png`)
  await upload.setInputFiles({ name: 'invalid.png', mimeType: 'image/png', buffer: Buffer.from('<svg/>') })
  await custom.page.getByRole('alert').filter({ hasText: '请选择有效的 PNG 图片' }).waitFor()
  assert.equal(custom.getPreferences().readerSettings.desktop.icon, savedIcon)
  await custom.page.getByRole('combobox', { name: '应用图标', exact: true }).selectOption('book')
  await custom.page.waitForTimeout(800)
  assert.equal(custom.getPreferences().readerSettings.desktop.icon, 'book')
  await custom.context.close()
  const legacy = await setup(1000, initialSettings, 'txt', () => {
    window.desktopReader = {
      settings: async () => {},
      action: async () => {},
      onRecover: () => () => {},
    }
  })
  await legacy.page.getByRole('button', { name: '窗口设置', exact: true }).click()
  assert.equal(await legacy.page.getByRole('combobox', { name: '应用图标', exact: true }).count(), 0)
  await legacy.context.close()
  const ordinary = await setup()
  assert.equal(await ordinary.page.locator('.desktop-controls').count(), 0)
  await ordinary.context.close()
  assert.deepEqual(errors, [])
  console.log('Desktop reader: seven skins, transparent surfaces, account settings, stale drafts, recovery and browser isolation passed')
} finally {
  await browser.close()
}
