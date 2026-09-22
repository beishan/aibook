import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [view, api] = await Promise.all([
  readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/utils/crawler.ts', import.meta.url), 'utf8'),
])

for (const text of [
  'discoveryFavoriteOnly',
  'bookFavoriteOnly',
  'taskFavoriteOnly',
  '@toggle-favorite="toggleTaskFavorite"',
  'openCrawlerBookLists',
  'saveCrawlerBookLists',
  "crawlerApi.setFavorite",
  "crawlerApi.setBookLists",
]) assert.ok(view.includes(text), `missing crawler favorite/book-list flow: ${text}`)

for (const text of [
  'favorite:boolean',
  'bookListIds:number[]',
  'favoriteOnly?:boolean',
  '/favorite`, {favorite}',
  '/book-lists`, {bookListIds}',
]) assert.ok(api.includes(text), `missing crawler API contract: ${text}`)

console.log('crawler favorite and preselected book-list checks passed')
