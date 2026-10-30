import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
import { ref } from 'vue'
const source = fs.readFileSync(new URL('../src/utils/navigationMenu.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const { defaultNavigationMenuOrder, normalizeNavigationMenuOrder, sortNavigationMenu } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
const defaults = defaultNavigationMenuOrder()
assert.deepEqual(normalizeNavigationMenuOrder(['/settings', '/bad', '/settings']), ['/settings', ...defaults.filter(path => path !== '/settings')])
const reversed = [...defaults].reverse()
const input = [{ path: '/books' }, { path: '/rewrite' }, { path: '/text-repair' }, { path: '/format-conversion' }, { path: '/settings' }]
assert.deepEqual(sortNavigationMenu(input, reversed).map(item => item.path), ['/settings', '/rewrite', '/text-repair', '/format-conversion', '/books'])
assert.equal(input[0].path, '/books', 'sorting must not mutate the source')
const preferences = fs.readFileSync(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8')
const saveSource = preferences.slice(preferences.indexOf('  const saveNavigationMenuOrder ='), preferences.indexOf('  const setBookDisplaySettingsTab ='))
let token = 'account-a'
let fail = false
let release
const requests = []
const navigationMenuOrder = ref(defaults)
const save = new Function('api', 'navigationMenuOrder', 'normalizeNavigationMenuOrder', 'localStorage', ts.transpile(saveSource + '\nreturn saveNavigationMenuOrder;'))({
  put: async (path, data) => {
    requests.push({ path, data })
    if (fail) throw new Error('save failed')
    if (release === true) await new Promise(resolve => { release = resolve })
  },
}, navigationMenuOrder, normalizeNavigationMenuOrder, { getItem: () => token })
await save(reversed)
assert.deepEqual(navigationMenuOrder.value, reversed)
assert.equal(requests[0].path, '/api/user/preferences')
fail = true
await assert.rejects(() => save(defaults))
assert.deepEqual(navigationMenuOrder.value, reversed)
fail = false
release = true
const pending = save(defaults)
token = 'account-b'
release()
await pending
assert.deepEqual(navigationMenuOrder.value, reversed, 'old account save cannot change current navigation')
console.log('Navigation order: normalization, grouped tools, stable source, persistence, failed save and account switch passed')
