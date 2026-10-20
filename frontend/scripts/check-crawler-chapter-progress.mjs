import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { compile, createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'

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

const view = readFileSync(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const viewScript = view.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('CrawlerView.ts', viewScript, ts.ScriptTarget.Latest, true)
const formattingFunctions = ast.statements.filter(node => ts.isFunctionDeclaration(node)
  && ['chapterDurationDetails', 'formatAttemptDuration', 'formatChapterSeconds'].includes(node.name?.text))
  .map(node => node.getText(ast)).join('\n')
const context = vm.createContext({})
vm.runInContext(ts.transpile(formattingFunctions, { target: ts.ScriptTarget.ES2022 }), context)
const cell = view.match(/<span\s+v-if="row\.crawlStatus==='COMPLETED' && row\.totalElapsedMillis!=null"[\s\S]*?<\/span>/)
assert.ok(cell, '耗时文字必须直接承载原生悬停提示')
const render = compile(cell[0])
const renderDuration = row => renderToString(createSSRApp({
  data: () => ({ row }),
  methods: {
    chapterDurationDetails: context.chapterDurationDetails,
    formatChapterSeconds: context.formatChapterSeconds,
  },
  render,
}))
const html = await renderDuration({
  crawlStatus: 'COMPLETED', totalElapsedMillis: 2750,
  collectionMillis: 1000, fixedWaitMillis: 1500, randomWaitMillis: 200, otherWaitMillis: 50,
})
const expectedDetails = '总耗时：2.75 秒\n采集耗时：1.00 秒\n固定等待：1.50 秒\n随机等待：200 毫秒\n其他等待：50 毫秒'
assert.equal(html.match(/title="([\s\S]*?)"/)[1], expectedDetails)
assert.equal(html.match(/aria-label="([\s\S]*?)"/)[1], expectedDetails)
assert.match(html, />2\.75 秒<\/span>/, '列表只显示总耗时')
const zero = await renderDuration({ crawlStatus: 'COMPLETED', totalElapsedMillis: 0 })
assert.match(zero, />0\.00 秒<\/span>/)
assert.ok(zero.includes('其他等待：0 毫秒'), '缺失明细不会生成空提示')
const missing = await renderDuration({ crawlStatus: 'COMPLETED', totalElapsedMillis: null })
assert.ok(!missing.includes('title='), '缺失耗时记录不会生成虚构的提示')
console.log('章节耗时提示检查通过：实际单元格渲染包含完整多行 title/读屏明细、仅总耗时展示、零值及缺失值。')
