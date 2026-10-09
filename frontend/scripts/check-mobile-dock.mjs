import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

// Reuse an existing browser runtime without adding an application dependency.
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.MOBILE_PLAYWRIGHT_PATH || 'playwright')
const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const navigation = [
  { key: 'home', title: '我的首页', icon: 'library', enabled: true, order: 2 },
  { key: 'library', title: '藏书', icon: 'home', enabled: true, order: 0 },
  { key: 'rewrite', title: '重写', icon: 'rewrite', enabled: false, order: 1 },
  { key: 'shelf', title: '书架', icon: 'shelf', enabled: true, order: 3 },
  { key: 'repair', title: '内容修复', icon: 'repair', enabled: true, order: 4 },
  { key: 'conversion', title: '格式转换', icon: 'conversion', enabled: true, order: 5 },
  { key: 'crawler', title: '采集', icon: 'crawler', enabled: true, order: 6 },
  { key: 'statistics', title: '统计', icon: 'statistics', enabled: true, order: 7 },
  { key: 'settings', title: '设置', icon: 'settings', enabled: true, order: 8 },
]
const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64')
const browser = await chromium.launch({
  headless: true,
  ...(process.env.MOBILE_BROWSER_CHANNEL ? { channel: process.env.MOBILE_BROWSER_CHANNEL } : {}),
})
try {
  for (const theme of ['macos26', 'warm', 'modern']) {
    for (const viewport of [{ width: 320, height: 844 }, { width: 390, height: 844 },
      { width: 768, height: 1024 }, { width: 900, height: 420 }]) {
      const context = await browser.newContext({ viewport, isMobile: true, hasTouch: true })
      await context.addInitScript(theme => {
        localStorage.setItem('token', 'mobile-dock-test')
        localStorage.setItem('ai-book-theme', theme)
      }, theme)
      await context.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname
        if (path === '/api/user/dock-icons/home' || path === '/api/user/dock-icons/library') {
          return route.fulfill({ body: image, contentType: 'image/png' })
        }
        let data = []
        if (path === '/api/site/settings') data = { registrationEnabled: true }
        if (path === '/api/user/profile') data = { id: 1, username: 'dock-test', role: 'ADMIN', hasAvatar: false }
        if (path === '/api/user/preferences') data = {
          theme, dockIconStyle: 'custom', dockNavigationItems: navigation,
          dockSize: 76, dockOpacity: 80, dockBlur: 20,
        }
        if (path === '/api/user/dock-icons') data = { userId: 1, icons: ['home', 'library'], versions: { home: 1, library: 1 } }
        if (path === '/api/books/trash/count') data = 0
        if (path === '/api/books' || path === '/api/books/reading') {
          data = { content: [], totalElements: 0, totalPages: 0, number: 0 }
        }
        await route.fulfill({ json: data })
      })
      const page = await context.newPage()
      await page.goto(`${baseUrl}/`)
      const dock = page.locator('.phone-navigation')
      await dock.waitFor({ state: 'visible' })
      const links = dock.locator('.phone-dock-items > a')
      assert.deepEqual(await links.allTextContents().then(items => items.map(text => text.trim())),
        ['藏书', '我的首页', '书架', '内容修复', '格式转换', '采集', '统计', '设置'])
      assert.equal(await links.nth(0).getAttribute('href'), '/books')
      assert.equal(await links.nth(1).getAttribute('aria-current'), 'page')
      await page.waitForFunction(() => {
        const images = [...document.querySelectorAll('.phone-navigation .dock-glyph-custom-image')]
        return images.length === 2 && images.every(img => img.complete && img.naturalWidth > 0)
      })
      assert.equal(await links.nth(2).locator('.dock-glyph-simple').count(), 1, 'Missing custom icon falls back')
      const geometry = await dock.evaluate(element => {
        const rect = element.getBoundingClientRect()
        const scroller = element.querySelector('.phone-dock-items')
        return {
          left: rect.left, right: rect.right, bottom: rect.bottom,
          radius: getComputedStyle(element).borderRadius,
          scrollable: scroller.scrollWidth > scroller.clientWidth,
        }
      })
      assert(geometry.left > 0 && geometry.right < viewport.width, 'Floating Dock stays within viewport')
      assert(geometry.bottom < viewport.height && geometry.radius === '24px')
      if (viewport.width < 600) assert(geometry.scrollable, 'Long menu scrolls inside Dock')
      await dock.getByRole('button', { name: '更多功能' }).click()
      await page.locator('.phone-navigation-grid').waitFor({ state: 'visible' })
      assert.equal(await page.locator('.phone-navigation-grid .dock-glyph-custom-image').count(), 2)
      await page.keyboard.press('Escape')
      await links.first().click()
      await page.waitForURL('**/books')
      assert.equal(await links.first().getAttribute('aria-current'), 'page')
      await page.goto(`${baseUrl}/rewrite/1`)
      await page.locator('.app-layout--immersive').waitFor()
      assert.equal(await page.locator('.phone-navigation').count(), 0, 'Editor keeps immersive navigation')
      await context.close()
    }
  }
  console.log('Mobile Dock: 3 themes × 4 viewports; account order/title/icon mapping, custom images, fallback, overflow, more menu, routing and immersive editor passed.')
} finally {
  await browser.close()
}
