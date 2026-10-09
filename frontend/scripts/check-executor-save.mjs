import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'

const source = fs.readFileSync(new URL('../src/components/CrawlerExecutorsSettingsPanel.vue', import.meta.url), 'utf8')
const functions = [
  source.slice(source.indexOf('function applyPolicy('), source.indexOf('\nasync function edit(')),
  source.slice(source.indexOf('async function save()'), source.indexOf('\nasync function unbind()')),
].join('\n')
const js = ts.transpile(functions, { target: ts.ScriptTarget.ES2020 })
function fixture({ mode = 'MIHOMO', existing = true } = {}) {
  const calls = []
  const policy = { value: null }
  const reference = { value: {} }
  const savedReference = { value: null }
  const saving = { value: false }
  const dialog = { value: true }
  const editingId = { value: existing ? 4 : undefined }
  const form = { value: { name: '执行器', enabled: true, proxyMode: mode, proxies: [] } }
  const value = {
    systemProxyId: 7, nodeGroupId: 8, switchingMode: 'TIME', manualNode: 'A',
    failover: true, failureThreshold: 2, cooldownSeconds: 300, rotationSeconds: 300,
    rotationChapters: 50, rotationTasks: 1, randomOrder: false,
  }
  const api = {
    async createQueueExecutor(queue, payload) { calls.push(['create', payload]); return { id: 4 } },
    async saveMihomoSelection(queue, id, payload) { calls.push(['selection', { ...payload }]); return { ...payload } },
    async updateQueueExecutor(queue, id, payload) { calls.push(['update', payload]) },
  }
  const factory = new Function('policy', 'reference', 'savedReference', 'saving', 'dialog',
    'editingId', 'form', 'queueId', 'crawlerApi', 'message', 'error', 'loadExecutors',
    `${js}; return { save, applyPolicy }`)
  const handlers = factory(policy, reference, savedReference, saving, dialog, editingId,
    form, { value: 2 }, api, { error: text => calls.push(['validation', text]), success: () => calls.push(['success']) },
    error => calls.push(['error', error.message]), async () => calls.push(['reload']))
  handlers.applyPolicy(value)
  if (!existing) savedReference.value = null
  return { ...handlers, reference, savedReference, form, api, calls, dialog, saving }
}
{
  const f = fixture()
  f.form.value.name = '只改名称'
  await f.save()
  assert.deepEqual(f.calls.map(call => call[0]), ['update', 'success', 'reload'])
  assert.equal(f.dialog.value, false)
}
{
  const f = fixture()
  f.reference.value.rotationSeconds = 600
  await f.save()
  assert.deepEqual(f.calls.map(call => call[0]), ['selection', 'update', 'success', 'reload'])
}
{
  const f = fixture({ existing: false })
  await f.save()
  assert.deepEqual(f.calls.map(call => call[0]), ['create', 'selection', 'update', 'success', 'reload'])
  assert.equal(f.calls[0][1].enabled, false)
  assert.equal(f.calls[2][1].enabled, true)
}
{
  const f = fixture()
  f.reference.value.cooldownSeconds = 600
  f.api.saveMihomoSelection = async () => { throw new Error('busy') }
  await f.save()
  assert.deepEqual(f.calls.map(call => call[0]), ['error', 'reload'])
  assert.equal(f.dialog.value, true)
  assert.equal(f.reference.value.cooldownSeconds, 600)
  assert.equal(f.saving.value, false)
}
{
  const f = fixture()
  f.reference.value.cooldownSeconds = 600
  f.api.updateQueueExecutor = async () => { f.calls.push(['update']); throw new Error('update failed') }
  await f.save()
  f.api.updateQueueExecutor = async () => f.calls.push(['update'])
  await f.save()
  assert.equal(f.calls.filter(call => call[0] === 'selection').length, 1, 'Retry skips already persisted policy')
}
{
  const f = fixture({ mode: 'DEFAULT' })
  f.saving.value = true
  await f.save()
  assert.equal(f.calls.length, 0, 'Duplicate submission is ignored')
  f.saving.value = false
  await f.save()
  assert.deepEqual(f.calls.map(call => call[0]), ['update', 'success', 'reload'])
}
console.log('Executor save: unchanged policy, changed policy, create ordering, busy draft retention, partial retry and duplicate submission passed.')
