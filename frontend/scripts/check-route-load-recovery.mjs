import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/routeLoadRecovery.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext },
}).outputText
const { isRouteAssetLoadError, recoverRouteLoad, installRouteLoadRecovery } = await import(
  `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`
)

const loadError = new TypeError('Failed to fetch dynamically imported module: /assets/Books-old.js')
for (const text of [
  loadError.message,
  'error loading dynamically imported module',
  'Importing a module script failed.',
  'Unable to preload CSS for /assets/Books-old.css',
]) assert.equal(isRouteAssetLoadError(new Error(text)), true)
assert.equal(isRouteAssetLoadError(new Error('Cannot access book before initialization')), false)

const values = new Map()
const destinations = []
const environment = {
  online: true,
  storage: {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  },
  origin: 'https://books.example',
  now: 1000000,
  replace: url => destinations.push(url),
}
assert.equal(recoverRouteLoad(loadError, '/books?search=test#list', environment), true)
assert.deepEqual(destinations, ['https://books.example/books?search=test#list'])
// 同标签页刷新后，任意其他目标仍受保护，不会陷入重载循环。
assert.equal(recoverRouteLoad(loadError, '/shelf', { ...environment, now: 1000100 }), false)
assert.equal(recoverRouteLoad(loadError, '/shelf', { ...environment, now: 1300001 }), true)
assert.equal(recoverRouteLoad(loadError, '/books', { ...environment, online: false }), false)
assert.equal(recoverRouteLoad(new Error('runtime error'), '/books', environment), false)
assert.equal(recoverRouteLoad(loadError, 'https://elsewhere.example/books', environment), false)
assert.equal(recoverRouteLoad(loadError, '/books', {
  ...environment,
  storage: { getItem: () => { throw new Error('storage blocked') }, setItem: () => {} },
}), false)

// 离线或运行错误仍向用户提供反馈，不吞掉路由异常。
let onError
const notifications = []
const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window')
const originalConsoleError = console.error
try {
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: false } })
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { sessionStorage: environment.storage, location: { origin: environment.origin } },
  })
  console.error = () => {}
  installRouteLoadRecovery({ onError: handler => { onError = handler } }, text => notifications.push(text))
  onError(loadError, { fullPath: '/books' })
  onError(new Error('runtime error'), { fullPath: '/shelf' })
  assert.equal(notifications.length, 2)
  assert.match(notifications[0], /页面文件加载失败/)
  assert.match(notifications[1], /页面打开失败/)
} finally {
  console.error = originalConsoleError
  if (originalNavigator) Object.defineProperty(globalThis, 'navigator', originalNavigator)
  else delete globalThis.navigator
  if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow)
  else delete globalThis.window
}

console.log('Route load recovery checks passed')
