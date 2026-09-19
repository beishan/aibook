import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/ReaderView.vue', import.meta.url), 'utf8')
const template = source.split('<script setup')[0] ?? ''

const loadingMessages = template.match(/正在打开书籍…/g) ?? []
const loadingSpinners = template.match(/class="loading-spinner"/g) ?? []

assert.equal(loadingMessages.length, 1,
  '打开书籍流程应只渲染一份加载提示')
assert.equal(loadingSpinners.length, 1,
  '打开书籍流程应只渲染一个进度圆圈')
assert.match(template, /v-if="loading"[\s\S]*?class="reader-loading-overlay loading glass"/,
  '统一加载遮罩应贯穿书籍信息与正文加载阶段')
assert.match(template, /v-if="!loading && !book" class="empty glass"/,
  '加载期间不应同时渲染空状态')

console.log('Reader single loading indicator checks passed.')
