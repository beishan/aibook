import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'

const require = createRequire(import.meta.url)
const { createSSRApp, h } = require('vue')
const { renderToString } = require('@vue/server-renderer')
const source = fs.readFileSync(new URL('../src/components/ShelfBooksTable.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const compiled = compileScript(descriptor, { id: 'shelf-table-check', inlineTemplate: true })
const code = ts.transpileModule(compiled.content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const overrides = {
  '@/components/BookCoverPlaceholderText.vue': { default: { props: ['title'], render() { return h('span', this.title) } } },
  '@/components/BookCoverPrivacyButton.vue': { default: { render() { return h('button', '隐藏封面') } } },
  '@/composables/useBookNameDisplay': { useBookNameDisplay: () => ({ displayBookName: title => `显示：${title}` }) },
  '@/utils/cover': { getCoverThumbnailUrl: url => `${url}?list` },
  '@/utils/imagePrivacy': { shouldLoadBookCover: id => id !== 2 },
}
const module = { exports: {} }
new Function('require', 'module', 'exports', code)(
  name => Object.hasOwn(overrides, name) ? overrides[name] : require(name), module, module.exports,
)
const Table = module.exports.default
const books = [
  { id: 1, title: '甲', author: '作者甲', format: 'epub', readingStatus: 'READING', coverUrl: '/cover1', shelfAddedAt: '2026-11-13', publisher: '出版社甲' },
  { id: 2, title: '乙', format: 'structured', readingStatus: 'FINISHED', coverUrl: '/cover2' },
  { id: 3, title: '丙', format: 'pdf', readingStatus: 'NEW' },
]
async function render(formatAddedAt) {
  const app = createSSRApp({
    render: () => h(Table, { books, formatAddedAt }, {
      actions: ({ book, index }) => h('button', `操作-${book.id}-${index}`),
    }),
  })
  app.component('router-link', {
    props: ['to'],
    render() { return h('a', { href: this.to }, this.$slots.default()) },
  })
  return renderToString(app)
}
const html = await render(value => value || '—')
assert.equal((html.match(/scope="col"/g) || []).length, 7)
assert.equal((html.match(/scope="row"/g) || []).length, 3)
for (const value of ['书名', '作者', '格式', '阅读状态', '加入时间', '操作', '正在阅读', '已读完', '未读', '在线章节', '未知作者', '2026-11-13']) {
  assert.ok(html.includes(value), value)
}
assert.ok(html.includes('href="/books/1"'))
assert.ok(html.includes('显示：甲'))
assert.ok(html.includes('/cover1?list'))
assert.ok(!html.includes('/cover2'), 'hidden covers must not load')
assert.ok(html.includes('操作-1-0') && html.includes('操作-3-2'), 'actions receive the book and page-local index')
const otherTab = await render()
assert.ok(otherTab.includes('出版社甲') && otherTab.includes('出版社'))
const parent = fs.readFileSync(new URL('../src/views/ShelfView.vue', import.meta.url), 'utf8')
for (const collection of ['Shelf', 'Favorite', 'Reading', 'Finished', 'Wanted']) {
  assert.ok(parent.includes(`:books="paged${collection}Books"`), collection)
}
console.log('Shelf table: semantic columns, rows, links, status, cover privacy, action slots and five tab data sources passed')
