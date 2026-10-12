import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import ts from 'typescript'

const helperSource = await readFile(new URL('../src/utils/crawlerStatistics.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(helperSource, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const context = vm.createContext({ exports: {} })
vm.runInContext(compiled, context)
const { parseStatisticsWindow, statisticsTimeLabel } = context.exports

for (const [unit, ranges] of Object.entries({ day: [7, 30, 90], hour: [24, 48, 72], minute: [30, 60, 180] })) {
  for (const range of ranges) {
    const result = parseStatisticsWindow(`${unit}:${range}`)
    assert.equal(result.granularity, unit)
    assert.equal(result.range, range)
  }
}
for (const invalid of [null, '', 'hour:90', 'minute:7', 'day:24', 'second:60', 'day:30:extra', 'day:030', '__proto__:30']) {
  assert.equal(parseStatisticsWindow(invalid), undefined, `must reject ${invalid}`)
}
assert.equal(statisticsTimeLabel('2026-10-01', 'day'), '10/01')
assert.equal(statisticsTimeLabel('2026-10-01T00:01', 'hour'), '10/01 00:01')
assert.equal(statisticsTimeLabel('2026-10-01T00:01', 'minute'), '00:01')

const view = await readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const preferences = await readFile(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8')
assert.ok(view.includes('granularity, dashboardStats.end'), 'detail and chart queries must share the same end')
assert.ok(view.includes('statistics.value?.end'), 'pagination must keep the current snapshot end')
assert.ok(view.includes('requestSequence !== statisticsRequestSequence'), 'stale requests must be discarded')
assert.ok(preferences.includes('persistRemote({ crawlerStatisticsWindow: value })'), 'selection must persist to the account')
assert.ok(preferences.includes("crawlerStatisticsWindow.value = 'day:30'"), 'account change must reset the selection')
console.log('crawler statistics windows: ranges, labels, shared boundaries, request protection and account persistence passed')
