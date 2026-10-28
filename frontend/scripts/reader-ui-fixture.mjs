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

export { browser, setup, errors, capture, initialSettings, screenshotDir }
