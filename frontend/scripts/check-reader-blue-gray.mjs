import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.MOBILE_PLAYWRIGHT_PATH || 'playwright')
const baseUrl = process.env.MOBILE_BASE_URL || 'http://127.0.0.1:3100'
const screenshotDir = process.env.READER_SCREENSHOT_DIR || '/tmp/reader-blue-gray-review'
await mkdir(screenshotDir, { recursive: true })
const chapterTitles = ['第一章 词之境界', '第二章 词之言情', '第三章 词之格律']
const paragraphs = [
  '词以境界为最上。有境界则自成高格，自有名句。五代、北宋之词所以独绝者在此。然境界非独谓景物也。喜怒哀乐，亦人心中之一境界。能写真景物、真感情者，谓之有境界。',
  '有我之境，以我观物，故物皆著我之色彩；无我之境，以物观物，故不知何者为我，何者为物。然二者非截然对立，实互为因果，相资为成。',
  '词之境界，贵在含蓄，不贵直露。含蓄则余味无穷，直露则味同嚼蜡。故能以极少之言，写无限之意，此词之所以为词也。若仅言情而无景，或仅写景而无情，皆不能入词之正格。',
  '古今之成大事业、大学问者，必经过三种之境界。衣带渐宽终不悔，为伊消得人憔悴。众里寻他千百度，蓦然回首，那人却在灯火阑珊处。',
]
const text = chapterTitles.map(title => [title, ...paragraphs, ...paragraphs].join('\n\n')).join('\n\n')
const initialSettings = {
  appearance: 'blueGray', epubEngine: 'epubjs', fontFamily: 'default', fontSize: 16,
  lineHeight: 1.8, paragraphSpacing: 16, contentWidth: 'medium', backgroundColor: 'auto',
  backgroundImageId: 'none', paginationMode: false, screenMode: 'single', textIndent: true,
  showProgress: true,
}
async function makeEpub() {
  const JSZip = require('jszip')
  const zip = new JSZip()
  zip.file('mimetype', 'application/epub+zip', { compression: 'STORE' })
  zip.file('META-INF/container.xml', '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
  const manifest = chapterTitles.map((_, i) => `<item id="c${i}" href="c${i}.xhtml" media-type="application/xhtml+xml"/>`).join('')
  const spine = chapterTitles.map((_, i) => `<itemref idref="c${i}"/>`).join('')
  zip.file('OEBPS/content.opf', `<?xml version="1.0"?><package version="2.0" unique-identifier="id" xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">reader-test</dc:identifier><dc:title>人间词话</dc:title><dc:language>zh</dc:language></metadata><manifest>${manifest}<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest><spine toc="ncx">${spine}</spine></package>`)
  zip.file('OEBPS/toc.ncx', `<?xml version="1.0"?><ncx version="2005-1" xmlns="http://www.daisy.org/z3986/2005/ncx/"><head><meta name="dtb:uid" content="reader-test"/></head><docTitle><text>人间词话</text></docTitle><navMap>${chapterTitles.map((title, i) => `<navPoint id="n${i}" playOrder="${i + 1}"><navLabel><text>${title}</text></navLabel><content src="c${i}.xhtml"/></navPoint>`).join('')}</navMap></ncx>`)
  chapterTitles.forEach((title, i) => zip.file(`OEBPS/c${i}.xhtml`, `<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${title}</title></head><body><h1>${title}</h1>${[...paragraphs, ...paragraphs].map(p => `<p>${p}</p>`).join('')}</body></html>`))
  return zip.generateAsync({ type: 'nodebuffer' })
}

function makePdf() {
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>',
    ...[6, 7, 8].map(id => `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 9 0 R >> >> /Contents ${id} 0 R >>`),
    ...[1, 2, 3].map(i => {
      const stream = `BT /F1 24 Tf 60 760 Td (Reader fixture page ${i}) Tj ET`
      return `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`
    }),
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ]
  let pdf = '%PDF-1.4\n'
  const offsets = [0]
  objects.forEach((object, i) => {
    offsets.push(Buffer.byteLength(pdf))
    pdf += `${i + 1} 0 obj\n${object}\nendobj\n`
  })
  const xref = Buffer.byteLength(pdf)
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
  pdf += offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`
  return Buffer.from(pdf)
}

const browser = await chromium.launch({ headless: true, channel: process.env.MOBILE_BROWSER_CHANNEL || 'chrome' })
const errors = []

async function capture(page, filename) {
  if (await page.locator('.blue-selection-toolbar').count() === 0) {
    for (const button of await page.locator('.el-notification__closeBtn').all()) {
      await button.click().catch(() => {})
    }
  }
  await page.evaluate(async () => {
    await document.fonts.ready
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  })
  await page.screenshot({ path: filename, animations: 'disabled' })
}

async function setup(width = 1536, settings = initialSettings, format = 'txt') {
  const context = await browser.newContext({ viewport: { width, height: 1024 }, reducedMotion: 'reduce' })
  let preferences = { readerSettings: structuredClone(settings) }
  const updates = []
  const binary = format === 'epub' ? await makeEpub() : format === 'pdf' ? makePdf() : null
  let bookmarks = [{ id: 1, title: chapterTitles[0], chapter: chapterTitles[0], excerpt: paragraphs[1], scrollPosition: 0, createdAt: '2026-10-27T10:00:00' }]
  let highlights = [
    { id: 1, text: paragraphs[1], note: '有我与无我，两种观物的方式。', cfiRange: 'textv2:2:0:2:60', color: '#9ed0ff', chapter: chapterTitles[0], createdAt: '2026-10-27T10:00:00' },
    { id: 2, text: paragraphs[2], note: '', cfiRange: 'textv2:3:0:3:35', color: '#ffe066', chapter: chapterTitles[0], createdAt: '2026-10-27T10:00:00' },
  ]
  const book = { id: 1, title: '人间词话', author: '王国维', format, onShelf: true, coverUrl: null, tags: [], versions: [] }
  await context.addInitScript(() => {
    localStorage.setItem('token', 'reader-theme-test')
    localStorage.setItem('ai-book-theme', 'modern')
  })
  await context.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    let json = []
    if (path === '/api/site/settings') json = { registrationEnabled: true }
    else if (path === '/api/user/profile') json = { id: 1, username: 'reader', role: 'ADMIN' }
    else if (path === '/api/user/preferences') {
      if (method === 'PUT') {
        const patch = request.postDataJSON()
        updates.push(patch)
        preferences = { ...preferences, ...patch }
      }
      json = preferences
    } else if (path === '/api/books/1') json = book
    else if (path === '/api/books/1/versions') json = [{ id: 11, format, displayName: '原版', primaryVersion: true, fileSize: text.length * 3 }]
    else if (path === '/api/books/1/content-processed') json = { text }
    else if (path === '/api/books/1/content-sanitized') json = { html: `<h1>${chapterTitles[0]}</h1>` + paragraphs.map(p => `<p>${p}</p>`).join('') }
    else if (path === '/api/books/1/content' && binary) {
      return route.fulfill({ contentType: format === 'pdf' ? 'application/pdf' : 'application/epub+zip', body: binary })
    } else if (path === '/api/books/1/content' && format === 'html') {
      return route.fulfill({ contentType: 'text/html', body: `<h1>${chapterTitles[0]}</h1>` + paragraphs.map(p => `<p>${p}</p>`).join('') })
    } else if (path === '/api/reading-progress/book/1') json = method === 'GET' ? { totalProgress: 0 } : request.postDataJSON()
    else if (path === '/api/books/1/bookmarks') {
      if (method === 'POST') bookmarks.push({ ...request.postDataJSON(), id: bookmarks.length + 1, createdAt: '2026-10-27T11:00:00' })
      json = method === 'POST' ? bookmarks.at(-1) : bookmarks
    } else if (path === '/api/books/1/highlights') {
      if (method === 'POST') highlights.push({ ...request.postDataJSON(), id: highlights.length + 1, createdAt: '2026-10-27T11:00:00' })
      json = method === 'POST' ? highlights.at(-1) : highlights
    } else if (/\/highlights\/\d+$/.test(path) && method === 'PUT') {
      const id = Number(path.split('/').at(-1))
      highlights = highlights.map(item => item.id === id ? { ...item, ...request.postDataJSON() } : item)
      json = highlights.find(item => item.id === id)
    } else if (path === '/api/user/dock-icons') json = { userId: 1, icons: [], versions: {} }
    else if (path === '/api/cover-privacy/books') json = { initialized: true, allHidden: false, overrides: {} }
    else if (/settings|config/.test(path)) json = {}
    await route.fulfill({ json })
  })
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  if (process.env.READER_FORMAT_ONLY) page.on('console', msg => { if (msg.type() === 'error') console.log(msg.text()) })
  await page.goto(baseUrl + '/reader/1')
  await page.locator('.reader-loading-overlay').waitFor({ state: 'hidden' })
  if (format === 'epub') {
    try { await page.locator('.epub-container iframe').first().waitFor() }
    catch (error) { await capture(page, `${screenshotDir}/epub-failure.png`); console.log(await page.locator('body').innerText()); throw error }
  }
  else if (format === 'pdf') await page.locator('.pdf-state').waitFor({ state: 'hidden' })
  else await page.locator('.reader-text, .reader-html').waitFor()
  return { context, page, updates, getPreferences: () => preferences }
}

try {
  if (process.env.READER_FORMAT_ONLY) {
    const test = await setup(1536, initialSettings, process.env.READER_FORMAT_ONLY)
    await capture(test.page, `${screenshotDir}/format-only.png`)
    console.log('Format rendered', process.env.READER_FORMAT_ONLY, errors)
    await test.context.close()
    await browser.close()
    process.exit(0)
  }
  if (process.env.READER_LEGACY_ONLY) {
    for (const skin of ['classic', 'readingRoom', 'trialReader']) {
      const test = await setup(1536, { ...initialSettings, appearance: skin })
      await test.page.evaluate(() => document.fonts.ready)
      await capture(test.page, `${screenshotDir}/legacy-${skin}.png`)
      await test.context.close()
    }
    console.log('Legacy reader screenshots:', screenshotDir)
    await browser.close()
    process.exit(0)
  }
  const desktop = await setup()
  const { page } = desktop
  await page.locator('.blue-book-identity').waitFor()
  assert.equal(await page.locator('.reader-text p').first().evaluate(el => getComputedStyle(el).fontSize), '22px')
  assert.equal(await page.locator('.reader-tool-rail').isVisible(), false)
  await capture(page, `${screenshotDir}/01-reading.png`)
  const sideTabs = page.getByRole('radiogroup', { name: '阅读侧栏' })
  await sideTabs.getByRole('radio', { name: '目录', exact: true }).focus()
  await page.keyboard.press('End')
  assert.equal(await sideTabs.getByRole('radio', { name: '划线', exact: true }).getAttribute('aria-checked'), 'true')
  await page.keyboard.press('ArrowLeft')
  assert.equal(await sideTabs.getByRole('radio', { name: '笔记', exact: true }).getAttribute('aria-checked'), 'true')
  await page.keyboard.press('Home')
  await page.locator('.blue-chapter-button').last().click()
  await page.waitForFunction(() => document.querySelector('.toc-item.active')?.textContent.includes('第二章'))
  await page.locator('.blue-chapter-button').first().click()
  await page.waitForFunction(() => document.querySelector('.toc-item.active')?.textContent.includes('第一章'))
  await page.getByRole('button', { name: '书内搜索', exact: true }).click()
  await page.locator('#reader-search-input').fill('境界')
  await page.locator('.search-result-item').first().waitFor()
  await page.locator('.search-result-item').first().click()
  assert.ok(await page.locator('.search-result-excerpt mark').count() > 0)
  await capture(page, `${screenshotDir}/02-search.png`)
  await page.getByRole('radio', { name: '书签', exact: true }).click()
  await page.locator('.bookmark-add-btn').click()
  await page.waitForFunction(() => document.querySelectorAll('.bookmark-item').length === 2)
  await capture(page, `${screenshotDir}/03-bookmarks.png`)
  await page.getByRole('radio', { name: '笔记', exact: true }).click()
  assert.equal(await page.locator('.highlight-item').count(), 1)
  await page.locator('.blue-highlight-quote').click()
  await page.getByRole('textbox', { name: '笔记内容' }).fill('更新后的阅读笔记')
  await capture(page, `${screenshotDir}/04-notes.png`)
  await page.getByRole('button', { name: '保存笔记', exact: true }).click()
  await page.locator('.highlight-editor').waitFor({ state: 'hidden' })
  await page.locator('.highlight-note').filter({ hasText: '更新后的阅读笔记' }).waitFor()
  await page.getByRole('radio', { name: '划线', exact: true }).click()
  assert.equal(await page.locator('.highlight-item').count(), 2)
  await page.getByRole('button', { name: '筛选颜色 #ffe066', exact: true }).click()
  assert.equal(await page.locator('.highlight-item').count(), 1)
  await page.getByRole('button', { name: '全部颜色', exact: true }).click()
  await page.locator('.reader-text p').first().evaluate(el => {
    const range = document.createRange()
    range.selectNodeContents(el)
    const selection = window.getSelection()
    selection.removeAllRanges()
    selection.addRange(range)
    el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
  })
  await page.getByRole('toolbar', { name: '选中文字操作' }).waitFor()
  await capture(page, `${screenshotDir}/05-highlights.png`)
  await page.getByRole('button', { name: '划线颜色 #a9e8b3', exact: true }).click()
  await page.locator('.highlight-editor').waitFor({ state: 'hidden' })
  await page.waitForFunction(() => document.querySelectorAll('.highlight-item').length === 3)
  await page.locator('.reader-text p').first().evaluate(el => {
    const range = document.createRange()
    range.selectNodeContents(el)
    const selection = window.getSelection()
    selection.removeAllRanges()
    selection.addRange(range)
    el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
  })
  await page.getByRole('toolbar', { name: '选中文字操作' }).getByRole('button', { name: '写笔记', exact: true }).click()
  await page.getByRole('textbox', { name: '笔记内容' }).fill('从选文工具条创建的笔记')
  await page.getByRole('button', { name: '保存笔记', exact: true }).click()
  await page.waitForFunction(() => document.querySelectorAll('.highlight-item').length === 4)
  await page.getByRole('button', { name: '阅读设置', exact: true }).click()
  await capture(page, `${screenshotDir}/06-settings.png`)
  await page.getByRole('button', { name: '增大字号' }).click()
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.reader-text p')).fontSize === '23px')
  await page.waitForResponse(response => response.url().includes('/api/user/preferences') && response.request().method() === 'PUT' && response.request().postDataJSON()?.readerSettings?.blueGray?.fontSize === 23)
  assert.equal(desktop.getPreferences().readerSettings.fontSize, 16)
  await page.getByRole('button', { name: '夜间', exact: true }).click()
  assert.equal(await page.locator('.reader-text p').first().evaluate(el => getComputedStyle(el).color), 'rgb(238, 238, 238)')
  assert.equal(await page.locator('.blue-topbar').evaluate(el => getComputedStyle(el).color), 'rgb(23, 34, 53)')
  await capture(page, `${screenshotDir}/09-night.png`)
  await page.getByRole('button', { name: '日间', exact: true }).click()
  await page.getByRole('radiogroup', { name: '阅读模式', exact: true }).getByRole('radio', { name: '翻页', exact: true }).click()
  await page.keyboard.press('Escape')
  await sideTabs.getByRole('radio', { name: '目录', exact: true }).click()
  await page.locator('.blue-chapter-button').last().click()
  await page.waitForFunction(() => document.querySelector('.toc-item.active')?.textContent.includes('第二章'))
  await page.getByRole('button', { name: '阅读设置', exact: true }).click()
  await page.getByRole('radiogroup', { name: '阅读模式', exact: true }).getByRole('radio', { name: '滚动', exact: true }).click()
  const appearance = page.getByRole('radiogroup', { name: '正式阅读器界面样式' })
  await appearance.getByRole('radio', { name: '经典', exact: true }).click()
  await page.locator('.reader-view:not(.blue-gray-mode)').waitFor()
  assert.equal(await page.locator('.reader-tool-rail').isVisible(), true)
  assert.equal(await page.locator('.reader-body').evaluate(el => getComputedStyle(el).fontSize), '16px')
  await capture(page, `${screenshotDir}/08-classic-preserved.png`)
  await page.getByRole('radio', { name: '浅蓝灰 阅读工作台' }).click()
  await page.getByRole('button', { name: '增大字号' }).waitFor()
  assert.equal(await page.locator('.reader-text p').first().evaluate(el => getComputedStyle(el).fontSize), '23px')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '全屏阅读', exact: true }).click()
  await page.locator('.fullscreen-mode').waitFor()
  assert.equal(await page.locator('.blue-topbar').count(), 0)
  await capture(page, `${screenshotDir}/07-fullscreen.png`)
  await page.getByRole('button', { name: '退出全屏', exact: true }).first().click()
  await page.locator('.blue-topbar').waitFor()
  if (desktop.getPreferences().readerSettings.appearance !== 'blueGray') {
    await page.waitForResponse(response => response.url().includes('/api/user/preferences') && response.request().method() === 'PUT' && response.request().postDataJSON()?.readerSettings?.appearance === 'blueGray')
  }
  await page.reload()
  await page.locator('.reader-loading-overlay').waitFor({ state: 'hidden' })
  assert.equal(await page.locator('.reader-text p').first().evaluate(el => getComputedStyle(el).fontSize), '23px')
  await desktop.context.close()

  for (const skin of ['classic', 'readingRoom', 'trialReader']) {
    const test = await setup(1280, { ...initialSettings, appearance: skin })
    assert.equal(await test.page.locator('.blue-topbar').count(), 0)
    assert.equal(await test.page.locator('.reader-tool-rail').isVisible(), true)
    await test.context.close()
  }
  for (const width of [320, 390, 768]) {
    const test = await setup(width)
    assert.equal(await test.page.locator('.side-panel').count(), 0)
    await test.page.getByRole('button', { name: '切换阅读侧栏' }).click()
    await test.page.getByRole('radiogroup', { name: '阅读侧栏' }).waitFor()
    await test.page.getByRole('button', { name: '收起侧栏' }).click()
    await test.page.getByRole('button', { name: '阅读设置', exact: true }).click()
    await capture(test.page, `${screenshotDir}/mobile-${width}.png`)
    assert.equal(await test.page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
    await test.context.close()
  }
  for (const format of ['md', 'structured']) {
    const test = await setup(1280, initialSettings, format)
    await test.page.locator('.reader-text p').first().waitFor()
    await test.context.close()
  }
  const epub = await setup(1536, initialSettings, 'epub')
  await epub.page.locator('.blue-chapter-button').last().click()
  await epub.page.waitForFunction(() => document.querySelector('.toc-item.active')?.textContent.includes('第二章'))
  await epub.page.getByRole('button', { name: '书内搜索', exact: true }).click()
  await epub.page.locator('#reader-search-input').fill('境界')
  await epub.page.locator('.search-result-item').first().waitFor()
  await epub.page.locator('.search-result-item').first().click()
  await capture(epub.page, `${screenshotDir}/epub-reading.png`)
  await epub.context.close()
  const pdf = await setup(1280, initialSettings, 'pdf')
  await pdf.page.locator('.blue-chapter-button').last().click()
  await pdf.page.waitForFunction(() => document.querySelector('.pdf-page-control input')?.value === '2')
  await pdf.page.locator('[aria-label="跳转全书阅读进度"]').fill('100')
  await pdf.page.locator('[aria-label="跳转全书阅读进度"]').dispatchEvent('change')
  await pdf.page.waitForFunction(() => document.querySelector('.pdf-page-control input')?.value === '3')
  await capture(pdf.page, `${screenshotDir}/pdf-reading.png`)
  await pdf.context.close()
  const html = await setup(1280, initialSettings, 'html')
  await html.page.getByRole('button', { name: '书内搜索', exact: true }).click()
  await html.page.locator('#reader-search-input').fill('境界')
  await html.page.locator('.search-result-item').first().waitFor()
  await html.context.close()
  assert.deepEqual(errors, [], 'reader must not throw runtime errors')
  console.log('Blue-gray reader: desktop panels, selection, notes, bookmarks, account persistence, theme isolation, fullscreen, mobile, TXT/Markdown/structured/EPUB/PDF/HTML passed')
  console.log('Screenshots:', screenshotDir)
} finally {
  await browser.close()
}
