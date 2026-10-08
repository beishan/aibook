import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/crawlerChapterProgress.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: {
  target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
} }).outputText
const exports = {}
vm.runInNewContext(compiled, { exports })
const { CHAPTER_PROGRESS_STAGES: stages, chapterStageProgress: progress, chapterStageRowStyle: style } = exports
assert.equal(stages.length, 7)
assert.equal(new Set(stages.map(stage => stage.color)).size, 7)

const steps = ['准备采集章节', '固定等待中', '随机等待中', '自适应等待中', '请求章节内容中', '解析章节内容中', '保存章节内容中']
for (const [index, step] of steps.entries()) {
  const row = { crawlStatus: 'CRAWLING', currentSubStep: step }
  assert.equal(progress(row).activeIndex, index)
  assert.equal(style(row)['--chapter-stage-color'], stages[index].color)
  const gradient = style(row)['--chapter-stage-track']
  for (let passed = 0; passed < index; passed++) assert.ok(gradient.includes(stages[passed].color))
  for (let future = index + 1; future < stages.length; future++) assert.ok(!gradient.includes(stages[future].color))
  assert.ok(gradient.includes('var(--chapter-stage-pending'), '未到达的阶段为灰色')
}
for (const step of ['访问时段等待中', '其他等待中', '异常重试中（1 / 3 次失败）']) {
  assert.equal(progress({ crawlStatus: 'CRAWLING', currentSubStep: step }).activeIndex, 3)
}
assert.equal(progress({ crawlStatus: 'CRAWLING', currentSubStep: '请求间隔等待中' }).index, 1)
assert.equal(progress({ crawlStatus: 'WAITING' }).visible, false)
assert.equal(progress({ crawlStatus: 'COMPLETED' }).visible, false)
assert.equal(progress({ crawlStatus: 'CRAWLING' }).activeIndex, -1, '缺少阶段信息不伪造闪烁阶段')
assert.equal(progress({ crawlStatus: 'CRAWLING', currentSubStep: '未知阶段' }).activeIndex, -1)
assert.equal(progress({ crawlStatus: 'COMPLETED', currentSubStep: '请求章节内容中' }).activeIndex, 4,
  '已有正文的更新采集也显示真实请求阶段')
const failed = { crawlStatus: 'FAILED', currentSubStep: '请求章节内容中' }
assert.equal(progress(failed).activeIndex, -1)
assert.ok(style(failed)['--chapter-stage-track'].includes('#ef4444'))
assert.equal(progress({ crawlStatus: 'FAILED', currentSubStep: '本章采集异常，继续处理后续章节' }).index, -1,
  '无法确定失败位置时不伪造出错阶段')
const finished = { crawlStatus: 'COMPLETED', currentSubStep: '保存章节内容中', crawlFinishedAt: '2026-10-08T10:00:00' }
assert.equal(progress(finished).activeIndex, -1)
assert.equal(progress(finished).completed, true)
for (const stage of stages) assert.ok(style(finished)['--chapter-stage-track'].includes(stage.color))
console.log('章节阶段进度检查通过：7 段映射、未到达灰色、重试、未知阶段、更新采集及终态停止闪烁。')
