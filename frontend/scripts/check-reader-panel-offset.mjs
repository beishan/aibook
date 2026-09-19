import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/ReaderView.vue', import.meta.url), 'utf8')

assert.match(source, /\.reader-view \{[\s\S]*?--reader-header-height: 76px;/,
  '桌面阅读器应声明标题栏高度')
assert.match(source, /\.side-panel \{[\s\S]*?inset: var\(--reader-header-height\) auto 0 0;/,
  '左侧目录应从标题栏下方展开')
assert.match(source, /\.side-panel--right \{[\s\S]*?inset: var\(--reader-header-height\) 0 0 auto;/,
  '右侧阅读辅助面板应从标题栏下方展开')
assert.match(source, /\.reader-view\.fullscreen-mode \{[\s\S]*?--reader-header-height: 0px;/,
  '全屏隐藏标题栏后侧栏应恢复为全高')
assert.match(source, /@media \(max-width: 768px\)[\s\S]*?--reader-header-height: 66px;/,
  '移动端侧栏起点应匹配移动标题栏')
assert.match(source, /\.trial-reader-mode \{[\s\S]*?--reader-header-height: 64px;/,
  '临时阅读侧栏起点应匹配其标题栏')

console.log('Reader panel/header offset checks passed.')
