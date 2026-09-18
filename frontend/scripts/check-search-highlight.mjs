import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

// 1) 高亮工具：直接子串匹配 + 拼音反查（pinyin-pro match）
const highlightSource = await readFile(
  new URL('../src/utils/searchHighlight.ts', import.meta.url),
  'utf8',
)
assert.match(highlightSource, /from 'pinyin-pro'/, 'searchHighlight 必须基于 pinyin-pro')
assert.match(highlightSource, /indexOf\(lowerKw/, '必须支持直接子串匹配全部出现位置')
assert.match(highlightSource, /match\(text,\s*pinyinKw\)/, '必须支持拼音反查定位汉字区间')

// 2) 高亮组件：以 mark.search-hit 渲染命中片段
const componentSource = await readFile(
  new URL('../src/components/HighlightText.vue', import.meta.url),
  'utf8',
)
assert.match(componentSource, /<mark[^>]*class="search-hit"/, '命中片段必须用 mark.search-hit 渲染')
assert.match(componentSource, /keyword\?: string/, '组件必须声明 keyword 属性')
assert.match(componentSource, /props\.keyword/, '组件必须消费 keyword 属性')
assert.match(componentSource, /keyword[^}]*\)\.trim\(\)/, '空关键词时必须原样渲染')

// 3) 书库页接入：卡片视图与列表视图的书名/作者都要走高亮组件
const viewSource = await readFile(
  new URL('../src/views/BooksView.vue', import.meta.url),
  'utf8',
)
assert.match(viewSource, /import HighlightText from '@\/components\/HighlightText\.vue'/)
assert.match(
  viewSource,
  /<HighlightText :text="book\.title" :keyword="searchKeyword" \/>/,
  '卡片视图书名必须高亮',
)
assert.match(
  viewSource,
  /<HighlightText :text="row\.title" :keyword="searchKeyword" \/>/,
  '列表视图书名必须高亮',
)
assert.match(
  viewSource,
  /<HighlightText v-if="row\.author" :text="row\.author" :keyword="searchKeyword" \/>/,
  '列表视图作者必须高亮且保留未知作者占位',
)
assert.match(viewSource, /支持拼音/, '搜索框占位文案应提示拼音搜索')

// 4) 运行时行为：pinyin-pro 拼音反查（与后端拼音索引能力对齐）
const { match } = await import('pinyin-pro')
assert.deepEqual(match('三国演义', 'sgyy'), [0, 1, 2, 3], '首字母 sgyy 应命中全部四字')
assert.deepEqual(match('三国演义', 'sanguo'), [0, 1], '全拼 sanguo 应命中前两字')
assert.equal(match('三国演义', 'xyz'), null, '无关拼音不应命中')

// 5) 依赖声明
const packageJson = JSON.parse(
  await readFile(new URL('../package.json', import.meta.url), 'utf8'),
)
assert.ok(
  packageJson.dependencies && packageJson.dependencies['pinyin-pro'],
  'package.json 必须声明 pinyin-pro 依赖',
)

console.log('Search highlighting with pinyin fallback works for card and list views')
