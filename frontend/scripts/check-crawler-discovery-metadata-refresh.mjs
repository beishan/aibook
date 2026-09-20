import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const api = await readFile(new URL('../src/utils/crawler.ts', import.meta.url), 'utf8')

assert.match(source, /activeTab === 'discovered'[\s\S]*?@click="batchRefreshDiscoveryMetadata">刷新分类标签<\/el-button>/)
assert.match(source, /@click="refreshBookMetadata\(row\)">刷新分类标签<\/el-button>/)
assert.match(source, /command="metadata"[^>]*>刷新分类标签<\/el-dropdown-item>/)
assert.match(source, /function handleDiscoveryCardMore[\s\S]*?command==='metadata'[\s\S]*?refreshBookMetadata\(book\)/)
assert.match(source, /async function batchRefreshDiscoveryMetadata\(\)[\s\S]*?crawlerApi\.batchRefreshMetadata/)
assert.match(source, /label="分类 \/ 标签"/)
assert.match(source, /v-for="tag in book\.tags"/)
assert.match(api, /batchRefreshMetadata: \(bookIds:number\[\]\) => api\.post<CrawlerTask\[]>\('\/api\/crawler\/books\/batch\/refresh-metadata'/)

console.log('Discovery books expose single and batch metadata refresh actions and display refreshed tags')
