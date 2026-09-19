import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/ReaderView.vue', import.meta.url), 'utf8')

const readerStyleBlock = source.match(/const readerStyle = computed\(\(\) => \{([\s\S]*?)\n\}\)/)?.[1] ?? ''
const shellStyleBlock = source.match(/const readerShellStyle = computed\(\(\) => \{([\s\S]*?)\n\}\)/)?.[1] ?? ''
const finalReaderBodyBlock = source.match(/\/\* 参考 read_ui[\s\S]*?\.reader-body \{([\s\S]*?)\n\}/)?.[1] ?? ''
const finalReadingRoomBodyBlock = source.match(/\.reader-view\.reading-room-mode \.reader-body \{([\s\S]*?)\n\}/)?.[1] ?? ''
const epubThemeBlock = source.match(/const applyThemeToContent = \(contents: any\) => \{([\s\S]*?)\n\}/)?.[1] ?? ''
const epubContainerBlock = source.match(/\.epub-container \{([\s\S]*?)\n\}/)?.[1] ?? ''

assert.match(readerStyleBlock, /maxWidth: isDoubleScreen \? '100%' : widthOption\.maxWidth/,
  '正文容器应继续使用阅读设置中的版心宽度')
assert.doesNotMatch(readerStyleBlock, /background(?:Color|Image|Position|Repeat|Size):/,
  '正文容器不应单独绘制阅读主题背景')
assert.match(shellStyleBlock, /backgroundColor: colors\.bg/,
  '阅读主题底色应覆盖整个阅读舞台')
assert.match(shellStyleBlock, /backgroundImage: selectedReaderBackground\.value/,
  '自定义阅读背景应覆盖整个阅读舞台')
assert.match(finalReaderBodyBlock, /border: 0;/,
  '正文容器不应保留书页边框')
assert.match(finalReaderBodyBlock, /background: transparent !important;/,
  '正文容器应透出阅读舞台主题')
assert.match(finalReaderBodyBlock, /box-shadow: none;/,
  '正文容器不应保留书页阴影')
assert.match(finalReadingRoomBodyBlock, /border: 0;/,
  '书房外观不应重新添加正文边框')
assert.match(finalReadingRoomBodyBlock, /box-shadow: none;/,
  '书房外观不应重新添加正文阴影')
assert.match(source, /backgroundColor: 'transparent',\n\s+textColor: colors\.text/,
  'Readium 内部页面应透出阅读舞台背景')
assert.match(epubThemeBlock, /contents\.css\('background-color', 'transparent', true\)/,
  'epub.js 内部页面应使用透明背景')
assert.match(epubThemeBlock, /html,\s+body \{[\s\S]*?background: transparent !important;/,
  'EPUB 文档根节点不应形成独立书页')
assert.match(epubContainerBlock, /background: transparent;/,
  'EPUB 容器本身应保持透明')
assert.match(source, /\.epub-container :deep\(iframe\)[\s\S]*?background: transparent !important;/,
  'EPUB iframe 应清除引擎默认背景')

console.log('Reader seamless surface checks passed.')
