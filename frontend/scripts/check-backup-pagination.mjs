import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'

const source = fs.readFileSync(new URL('../src/components/BackupSettingsPanel.vue', import.meta.url), 'utf8')
const extracted = source.slice(source.indexOf('const loadExecutionPage ='), source.indexOf('const refreshAll ='))
const code = ts.transpile(extracted, { target: ts.ScriptTarget.ES2020 })
const pending = []
const ref = value => ({ value })
const page = ref(1), rows = ref([]), total = ref(0), pages = ref(0), active = ref(false), loading = ref(false)
const details = ref(true)
const api = { executionPage: number => new Promise((resolve, reject) => pending.push({ number, resolve, reject })) }
let polling = 0, errors = 0
const handlers = new Function('backupApi', 'executionPage', 'executions', 'executionTotal',
  'executionPages', 'executionActive', 'executionPageLoading', 'executionDetailsVisible',
  'syncExecutionPolling', 'message', `let executionRequestSequence = 0; const componentUnmounted = false; ${code}; return { loadExecutionPage, changeExecutionPage }`)(
  api, page, rows, total, pages, active, loading, details, () => polling++, { error: () => errors++ })
const result = (number, id, running = false) => ({ content: [{ id }], number, totalElements: 123, totalPages: 7, size: 20, active: running })
const old = handlers.loadExecutionPage()
const next = handlers.changeExecutionPage(2)
assert.deepEqual(pending.map(request => request.number), [0, 1])
pending[1].resolve(result(1, 21, true))
await next
pending[0].resolve(result(0, 1))
await old
assert.equal(page.value, 2)
assert.equal(rows.value[0].id, 21, 'Old request cannot overwrite a new page')
assert.equal(total.value, 123)
assert.equal(active.value, true, 'Activity tracks tasks outside current page')
assert.equal(details.value, false)
const failed = handlers.changeExecutionPage(3)
pending[2].reject(new Error('network'))
await failed
assert.equal(page.value, 2)
assert.equal(rows.value[0].id, 21)
assert.equal(loading.value, false)
assert.equal(errors, 1)
const clamped = handlers.changeExecutionPage(7)
pending[3].resolve({ ...result(2, 41), totalElements: 41, totalPages: 3 })
await clamped
assert.equal(page.value, 3, 'Server can return the last available page')
assert(polling >= 3)
console.log('Backup pagination: page requests, totals, stale response protection, global activity, failed page recovery and bounds passed.')
