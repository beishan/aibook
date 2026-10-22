import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'

const source = fs.readFileSync(new URL('../src/components/BackupSettingsPanel.vue', import.meta.url), 'utf8')
const extracted = source.slice(source.indexOf('const isExecutionActive ='), source.indexOf('const hasContent ='))
const code = ts.transpile(extracted, { target: ts.ScriptTarget.ES2020 })
const busy = { value: null }, selected = { value: 1 }, visible = { value: true }
let accept = false, calls = 0, loads = 0, polls = 0, errors = [], success = 0
let failure = false, refreshFailure = false, pendingConfirm
const handler = new Function('deletingExecutionId', 'selectedExecutionId', 'executionDetailsVisible',
  'confirm', 'backupApi', 'loadExecutionPage', 'syncExecutionPolling', 'message', 'formatTime',
  `${code}; return removeExecution`)(busy, selected, visible,
  async text => {
    assert(text.includes('永久删除') && text.includes('全部备份文件') && text.includes('/backup/demo'))
    if (pendingConfirm) return pendingConfirm
    return accept
  }, { deleteExecution: async () => { calls++; if (failure) throw new Error('network') } },
  async () => { loads++; if (refreshFailure) throw new Error('refresh') }, () => polls++,
  { success: () => success++, error: text => errors.push(text) }, () => '2026/10/21')
const row = { id: 1, taskName: '测试备份', startedAt: '2026-10-21', outputPath: '/backup/demo', executionStatus: 'SUCCESS' }
await handler(row)
assert.equal(calls, 0, 'Cancel must not send a delete request')
assert.equal(busy.value, null)
accept = true
await handler({ ...row, executionStatus: 'RUNNING' })
await handler({ ...row, executionStatus: 'QUEUED' })
assert.equal(calls, 0, 'Active backups cannot be deleted')
await handler({ ...row, status: 'DELETED', executionStatus: 'SUCCESS' })
await handler({ ...row, status: 'SUCCESS', deletedAt: '2026-10-22' })
assert.equal(calls, 0, 'Deleted backups cannot trigger another delete request')
assert.match(source, /v-if="!isExecutionDeleted\(execution\)"/,
  'Deleted rows must hide their delete button')
let resolveConfirm
pendingConfirm = new Promise(resolve => { resolveConfirm = resolve })
const first = handler(row)
assert.equal(busy.value, 1)
await handler(row)
resolveConfirm(true)
await first
pendingConfirm = null
assert.equal(calls, 1, 'Repeated clicks must not send duplicate requests')
assert.equal(visible.value, false)
assert.equal(loads, 1)
assert.equal(polls, 1)
assert.equal(success, 1)
failure = true
await handler(row)
assert.equal(errors.length, 1)
assert.equal(loads, 1, 'Failure keeps current records')
assert.equal(busy.value, null)
failure = false
refreshFailure = true
await handler(row)
assert(errors.at(-1).includes('删除已完成'))
assert.equal(busy.value, null)
console.log('Backup deletion: cancel, confirm, active guard, repeat clicks, refresh, failure and retry passed.')
