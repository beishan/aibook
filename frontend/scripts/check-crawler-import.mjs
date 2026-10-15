import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/crawlerImport.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
}).outputText
const { runCrawlerImports } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const books = Array.from({ length: 100 }, (_, index) => ({ id: index + 1, bookName: `书籍 ${index + 1}` }))
const completed = (targets) => ({
  id: 'batch', status: 'COMPLETED', total: targets.length, completed: targets.length,
  results: targets.map(book => ({ crawlerBookId: book.id, bookId: book.id + 1000, success: true })),
})
const noWait = async () => {}

test('100 本书一次提交，查询进度后仅重试失败项', async () => {
  const submitted = []
  const progress = []
  const final = completed(books)
  final.results = final.results.map(item => item.crawlerBookId % 5 === 0
    ? { ...item, success: false, bookId: null, reason: '没有可用正文' } : item)
  const result = await runCrawlerImports(books, {
    submit: async ids => {
      submitted.push(ids)
      return { id: 'batch', status: 'RUNNING', total: 100, completed: 0, results: [] }
    },
    progress: async id => { assert.equal(id, 'batch'); return final },
    onProgress: (done, total) => progress.push([done, total]),
    waitForPoll: noWait,
  })
  assert.deepEqual(submitted, [books.map(book => book.id)])
  assert.equal(result.succeeded.length, 80)
  assert.equal(result.failures.length, 20)
  assert.equal(result.failures[0].reason, '没有可用正文')
  assert.deepEqual(progress.at(-1), [100, 100])
  const retryBooks = result.failures.map(item => item.book)
  await runCrawlerImports(retryBooks, {
    submit: async ids => { submitted.push(ids); return completed(retryBooks) },
    progress: async () => { throw Error('已完成任务无需再查询') },
    onProgress: () => {},
  })
  assert.deepEqual(submitted[1], books.filter(book => book.id % 5 === 0).map(book => book.id))
})

test('相同书籍 ID 去重后一次提交', async () => {
  const result = await runCrawlerImports([books[0], books[0]], {
    submit: async ids => { assert.deepEqual(ids, [1]); return completed([books[0]]) },
    progress: async () => { throw Error('不应查询') },
    onProgress: () => {},
  })
  assert.equal(result.succeeded.length, 1)
})

test('查询中断后继续同一任务，不重复提交且保留已确认结果', async () => {
  const targets = books.slice(0, 2)
  const result = await runCrawlerImports(targets, {
    submit: async () => ({ ...completed(targets), status: 'RUNNING', completed: 1,
      results: completed(targets).results.slice(0, 1) }),
    progress: async () => { throw { code: 'ECONNABORTED' } },
    onProgress: () => {}, waitForPoll: noWait,
  })
  assert.equal(result.pendingJobId, 'batch')
  assert.equal(result.succeeded.length, 1)
  assert.match(result.failures[0].reason, /尚未确认/)
  const resumed = await runCrawlerImports(targets, {
    resumeJobId: result.pendingJobId,
    submit: async () => { throw Error('不得重复提交') },
    progress: async id => { assert.equal(id, 'batch'); return completed(targets) },
    onProgress: () => {},
  })
  assert.equal(resumed.succeeded.length, 2)
  assert.equal(resumed.failures.length, 0)
  assert.equal(resumed.pendingJobId, undefined)
})

test('提交失败时保留全部书籍，结果不冒充成功', async () => {
  const result = await runCrawlerImports(books, {
    submit: async () => { throw { response: { status: 401 } } },
    progress: async () => { throw Error('尚未取得任务，不应查询') },
    onProgress: () => {},
  })
  assert.equal(result.succeeded.length, 0)
  assert.equal(result.failures.length, 100)
  assert.match(result.failures[0].reason, /登录已过期/)
})
