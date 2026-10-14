import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFile } from 'node:fs/promises'

const require = createRequire(import.meta.url)
const ts = require('typescript')
const source = await readFile(new URL('../src/utils/bookComparison.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
})
const { executeBatchMerge, suggestedTarget, validateBatchPlans, sourceLabel, chapterCountLabel, sizeLabel } =
  await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)

const book = (id, chapterCount = 10) => ({ id, title: `书籍${id}`, format: 'txt', chapterCount })
const plan = (id, left, right) => ({ groupId: id, title: id, books: [book(left), book(right)], targetBookId: left })
const plans = [plan('a', 1, 2), plan('b', 3, 4), plan('c', 5, 6)]
const events = []
const progress = []
const results = await executeBatchMerge(plans, async id => {
  events.push(`versions:${id}`)
  if (id === 4) throw new Error('书籍已删除')
  return [{ id: id * 10 }, { id: id * 10 + 1 }]
}, async (targetId, sources) => {
  events.push(`merge:${targetId}`)
  assert.deepEqual(sources, [{ bookId: targetId + 1, versionIds: [(targetId + 1) * 10, (targetId + 1) * 10 + 1] }])
}, (result, completed) => progress.push([result.status, completed]))
assert.deepEqual(events, ['versions:2', 'merge:1', 'versions:4', 'versions:6', 'merge:5'])
assert.deepEqual(progress, [['completed', 1], ['failed', 2], ['completed', 3]])
assert.equal(results[1].message, '书籍已删除')
assert.throws(() => validateBatchPlans([plan('a', 1, 2), plan('b', 2, 3)]), /多个合并计划/)
assert.throws(() => validateBatchPlans([{ ...plans[0], targetBookId: 99 }]), /组内/)
assert.throws(() => validateBatchPlans([]), /请选择/)
let calls = 0
await assert.rejects(() => executeBatchMerge([plan('a', 1, 2), plan('b', 2, 3)],
  async () => { calls++; return [] }, async () => { calls++ }, () => {}), /多个合并计划/)
assert.equal(calls, 0, 'invalid overlapping groups must be rejected before any writes or requests')
assert.equal(suggestedTarget([book(1, 2), book(2, 20)]), 2)
assert.equal(sourceLabel({ sourceType: 'CRAWLER', sourceSiteName: '测试站' }), '网站采集 · 测试站')
assert.equal(chapterCountLabel(null), '章节数待解析')
assert.equal(chapterCountLabel(0), '0 章')
assert.equal(sizeLabel(0), '0 B')
let stop = false
const stopped = await executeBatchMerge(plans, async () => [{ id: 100 }], async () => {},
  () => { stop = true }, () => stop)
assert.equal(stopped.length, 1, 'stopping must leave later groups unexecuted')
let authRequests = 0
const authFailure = await executeBatchMerge(plans, async () => {
  authRequests++
  throw { response: { status: 401, data: { message: '登录已失效' } } }
}, async () => assert.fail('must not write after an auth failure'), () => {})
assert.equal(authFailure.length, 1)
assert.equal(authRequests, 1, 'auth failure must stop all later requests')
console.log('Batch merge serial execution, full-version selection, partial failure, overlapping plan rejection and metadata formatting passed')
