import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import ts from 'typescript'
import * as vue from 'vue'

const filename = new URL('../src/components/ScraperProxyDialog.vue', import.meta.url)
const { descriptor } = parse(fs.readFileSync(filename, 'utf8'))
assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: filename.pathname, id: 'proxy' }).errors, [])
const compiled = compileScript(descriptor, { id: 'proxy' }).content
const code = ts.transpileModule(compiled, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText
const direct = {
  mode: 'NONE', systemProxyId: null, nodeGroupId: null, switchingMode: 'MANUAL', manualNode: null,
  failover: false, failureThreshold: 2, cooldownSeconds: 300, rotationSeconds: 300,
  rotationRequests: 20, rotationTasks: 1, randomOrder: false,
}
const requests = []
const groupResolvers = new Map()
const api = {
  async get(url) {
    requests.push({ url })
    return { data: { settings: { ...direct }, currentNode: null, requests: 0, tasks: 0, cooldowns: {}, lastEvent: '' } }
  },
  async put(url, payload) {
    const copy = JSON.parse(JSON.stringify(payload))
    requests.push({ url, payload: copy })
    return { data: { settings: copy, requests: 0, tasks: 0, cooldowns: {} } }
  },
}
const context = {
  exports: {},
  require(name) {
    if (name === 'vue') return vue
    if (name === '@/utils/api') return { default: api }
    if (name === '@/utils/message') return { message: { success() {} } }
    if (name === '@/utils/proxySettings') return { proxySettingsApi: {
      systemList: async () => [
        { id: 1, name: '固定', enabled: true, proxyType: 'SIMPLE' },
        { id: 2, name: 'Mihomo 1', enabled: true, proxyType: 'MIHOMO' },
        { id: 3, name: 'Mihomo 2', enabled: true, proxyType: 'MIHOMO' },
        { id: 4, name: '停用', enabled: false, proxyType: 'SIMPLE' },
      ],
      mihomoGroups: id => new Promise(resolve => groupResolvers.set(id, resolve)),
    } }
    throw new Error(name)
  },
}
vm.runInNewContext(code, context)
const props = vue.reactive({ modelValue: false, source: { configKey: 'douban', name: '豆瓣' } })
const state = context.exports.default.setup(props, { expose() {}, emit() {} })
props.modelValue = true
await vue.nextTick()
for (let index = 0; index < 8; index++) await Promise.resolve()
assert.equal(state.loading.value, false)
assert.equal(state.form.value.mode, 'NONE')
state.changeMode('FIXED')
assert.equal(state.availableProxies.value.length, 1)
state.form.value.systemProxyId = 1
await state.save()
assert.equal(requests.at(-1).payload.mode, 'FIXED')
assert.equal(requests.at(-1).payload.systemProxyId, 1)
assert(!Object.hasOwn(requests.at(-1).payload, 'ownerId'))
state.changeMode('MIHOMO')
assert.equal(state.availableProxies.value.length, 2)
state.form.value.systemProxyId = 2
state.changeSystemProxy()
state.form.value.systemProxyId = 3
state.changeSystemProxy()
groupResolvers.get(3)([{ id: 30, systemProxyId: 3, name: '新组', nodes: ['A', 'B'] }])
for (let index = 0; index < 8; index++) await Promise.resolve()
groupResolvers.get(2)([{ id: 20, systemProxyId: 2, name: '旧组', nodes: ['OLD'] }])
for (let index = 0; index < 8; index++) await Promise.resolve()
assert.equal(state.groups.value[0].id, 30, 'stale group responses must not replace current proxy groups')
state.form.value.nodeGroupId = 30
await state.save()
assert.match(state.error.value, /手动模式/)
state.form.value.manualNode = 'B'
state.form.value.switchingMode = 'TASK'
state.form.value.rotationTasks = 2
await state.save()
assert.equal(requests.at(-1).payload.nodeGroupId, 30)
assert.equal(requests.at(-1).payload.rotationTasks, 2)
assert.equal(requests.at(-1).url, '/api/scraper/proxy-settings/douban')
await state.switchModeByKeyboard({key: 'Home', preventDefault() {}, currentTarget: {querySelector: () => ({focus() {}})}})
assert.equal(state.form.value.mode, 'NONE')
assert.equal(state.form.value.systemProxyId, null)
assert.equal(state.form.value.nodeGroupId, null)
console.log('Scraper proxy configuration: load, direct/fixed/Mihomo selection, group races, validation, persistence and keyboard switching passed')
