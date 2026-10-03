import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/crawlerImport.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
}).outputText
const { runCrawlerImports } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

test('100 本均提交给服务端校验，失败保留并仅重试失败项', async () => {
  const targets = Array.from({ length: 100 }, (_, index) => ({
    id: index + 1, bookName: `书籍 ${index + 1}`, crawledChapterCount: 0, importStatus: 'READY',
  }))
  const submitted = []
  const progress = []
  const result = await runCrawlerImports(targets, async book => {
    submitted.push(book.id)
    if (book.id % 5 === 0) throw { response: { status: 409, data: { message: '没有可用正文' } } }
  }, (completed, total) => progress.push([completed, total]))
  assert.equal(submitted.length, 100)
  assert.equal(result.succeeded.length, 80)
  assert.equal(result.failures.length, 20)
  assert.equal(result.failures[0].reason, '没有可用正文')
  assert.deepEqual(progress.at(-1), [100, 100])

  const retried = []
  const retry = await runCrawlerImports(result.failures.map(failure => failure.book), async book => {
    retried.push(book.id)
  }, () => {})
  assert.deepEqual(retried, targets.filter(book => book.id % 5 === 0).map(book => book.id))
  assert.equal(retry.succeeded.length, 20)
  assert.equal(retry.failures.length, 0)
})

test('相同采集书籍 ID 只提交一次', async () => {
  let calls = 0
  const book = { id: 1, bookName: '重复选择' }
  const result = await runCrawlerImports([book, book], async () => { calls++ }, () => {})
  assert.equal(calls, 1)
  assert.equal(result.succeeded.length, 1)
})

test('登录过期后停止后续提交，保留尚未处理的书籍', async () => {
  const books = [1, 2, 3].map(id => ({ id, bookName: `${id}` }))
  const submitted = []
  const result = await runCrawlerImports(books, async book => {
    submitted.push(book.id)
    throw { response: { status: 401 } }
  }, () => {})
  assert.deepEqual(submitted, [1])
  assert.equal(result.failures.length, 3)
  assert.match(result.failures[1].reason, /尚未提交/)
})

test('超时保留为未确认结果，其他书籍继续处理', async () => {
  const books = [1, 2].map(id => ({ id, bookName: `${id}` }))
  const result = await runCrawlerImports(books, async book => {
    if (book.id === 1) throw { code: 'ECONNABORTED' }
  }, () => {})
  assert.equal(result.succeeded[0].id, 2)
  assert.match(result.failures[0].reason, /尚未确认/)
})
