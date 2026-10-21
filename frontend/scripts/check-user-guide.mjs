import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'

const require = createRequire(import.meta.url)
const { createSSRApp } = require('vue')
const { renderToString } = require('@vue/server-renderer')
const markdown = fs.readFileSync(new URL('../src/content/user-guide.md', import.meta.url), 'utf8')

function evaluate(source, overrides = {}) {
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const result = { exports: {} }
  new Function('require', 'module', 'exports', compiled)(
    name => Object.hasOwn(overrides, name) ? overrides[name] : require(name),
    result,
    result.exports,
  )
  return result.exports
}

const guideUtils = evaluate(fs.readFileSync(new URL('../src/utils/userGuide.ts', import.meta.url), 'utf8'))
const { parseUserGuide, searchGuideSections } = guideUtils
const guide = parseUserGuide(markdown)
assert.equal(guide.title, '汗牛充栋使用说明')
assert.ok(guide.sections.length >= 30, 'The complete handbook must retain its module coverage')
assert.equal(new Set(guide.sections.map(section => section.id)).size, guide.sections.length)
assert.ok(guide.sections.every(section => section.blocks.length > 0))
assert.equal(searchGuideSections(guide.sections, '   '), guide.sections)
assert.ok(searchGuideSections(guide.sections, '黑名单').some(section => section.title.includes('黑名单')))
assert.ok(searchGuideSections(guide.sections, '自动入库 不回溯').length > 0)
assert.equal(searchGuideSections(guide.sections, '不存在的说明检索词 987654321').length, 0)
assert.deepEqual(
  searchGuideSections(guide.sections, 'ＥＰＵＢ'),
  searchGuideSections(guide.sections, 'epub'),
)
const sample = parseUserGuide('# 标题\r\n简介\r\n## 章节\r\n### 步骤\r\n1. 第一步\r\n2. 第二步\r\n- 提示\r\n普通段落')
assert.deepEqual(sample.sections[0].blocks.map(block => block.type), ['heading', 'list', 'list', 'paragraph'])
assert.equal(sample.sections[0].blocks[1].ordered, true)
assert.deepEqual(sample.sections[0].blocks[1].items, ['第一步', '第二步'])
assert.equal(sample.sections[0].blocks[2].ordered, false)

// Compile and server-render the actual panel, including the canonical handbook.
const componentSource = fs.readFileSync(new URL('../src/components/UserGuidePanel.vue', import.meta.url), 'utf8')
const { descriptor } = parse(componentSource)
const { content } = compileScript(descriptor, { id: 'user-guide-test', inlineTemplate: true })
const panel = evaluate(content, {
  '@/content/user-guide.md?raw': { default: markdown },
  '@/utils/userGuide': guideUtils,
  '../../package.json': { version: 'test-version' },
}).default
const app = createSSRApp(panel)
app.use(require('element-plus').default)
const html = await renderToString(app)
assert.equal((html.match(/class="guide-chapter card glass"/g) || []).length, guide.sections.length)
assert.ok(html.includes('vtest-version'))
assert.ok(html.includes('aria-label="搜索使用说明"'))
assert.ok(html.includes('aria-label="使用说明章节目录"'))
for (const section of guide.sections) {
  assert.ok(html.includes(`id="${section.id}"`))
  assert.ok(html.includes(`href="#${section.id}"`))
}
assert.ok(html.includes('<ol>') && html.includes('<ul>'))
assert.ok(!html.includes('## '), 'Markdown headings must render as headings, not raw markup')
const textOnlyPanel = evaluate(content, {
  '@/content/user-guide.md?raw': { default: '# 安全测试\n## 正文\n<img src=x onerror=alert(1)>\n- <script>alert(1)</script>' },
  '@/utils/userGuide': guideUtils,
  '../../package.json': { version: 'test-version' },
}).default
const safeApp = createSSRApp(textOnlyPanel)
safeApp.use(require('element-plus').default)
const safeHtml = await renderToString(safeApp)
assert.ok(safeHtml.includes('&lt;img') && safeHtml.includes('&lt;script&gt;'))
assert.ok(!safeHtml.includes('<script>') && !safeHtml.includes('<img src=x'))
console.log(`User guide: ${guide.sections.length} chapters parsed and rendered; search and list checks passed`)
