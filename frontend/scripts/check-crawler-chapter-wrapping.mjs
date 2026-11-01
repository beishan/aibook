import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { runInNewContext } from 'node:vm'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const start = source.indexOf('const chapterReaderParagraphs = computed(')
const end = source.indexOf('\nconst chapterReaderTheme=', start)
assert.ok(start >= 0 && end > start)
const paragraphCode = source.slice(start, end)
const paragraphs = content => Array.from(runInNewContext(
  `${paragraphCode}\nchapterReaderParagraphs`,
  { computed: fn => fn(), chapterDetail: { value: { content } } },
))

assert.deepEqual(paragraphs('第一段\n第二段\n\n第三段\n第四段'), ['第一段', '第二段', '第三段', '第四段'])
assert.deepEqual(paragraphs('甲\r\n乙\r丙\n丁\u0085戊\u2028己\u2029庚'), ['甲', '乙', '丙', '丁', '戊', '己', '庚'])
assert.deepEqual(paragraphs(' \n\r\n　'), [])
assert.deepEqual(paragraphs(undefined), [])
assert.deepEqual(paragraphs('　带缩进正文　\n <script>文本</script> '), ['带缩进正文', '<script>文本</script>'])
const longText = '正文'.repeat(2000) + 'https://example.com/' + 'a'.repeat(500)
assert.deepEqual(paragraphs(longText), [longText])
assert.match(source, /\.chapter-reader-paragraph\s*\{[^}]*white-space:\s*normal;[^}]*overflow-wrap:\s*anywhere;/)
assert.match(source, /class="chapter-reader-paragraph">\{\{ paragraph \}\}/)
console.log('Chapter preview preserves mixed line breaks and enables wrapping without changing stored content')
