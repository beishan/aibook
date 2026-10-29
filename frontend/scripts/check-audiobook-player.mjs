import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import ts from 'typescript'
const require = createRequire(import.meta.url)
const { createPinia, setActivePinia } = require('pinia')
setActivePinia(createPinia())
const storage = new Map([['token', 'test-account']])
global.localStorage = { getItem: key => storage.get(key) || null }
global.window = new EventTarget()
global.document = Object.assign(new EventTarget(), { hidden: false })
Object.defineProperty(global, 'navigator', { value: {}, configurable: true })
const elements = []
class TestAudio extends EventTarget {
  paused = true
  currentTime = 0
  src = ''
  constructor() { super(); elements.push(this) }
  load() { if (this.src) queueMicrotask(() => this.dispatchEvent(new Event('loadedmetadata'))) }
  async play() { this.paused = false; this.dispatchEvent(new Event('playing')) }
  pause() { const changed = !this.paused; this.paused = true; if (changed) this.dispatchEvent(new Event('pause')) }
  removeAttribute() { this.src = '' }
}
global.Audio = TestAudio
const detail = {
  id: 1, bookId: 2, title: '测试有声书', durationMs: 20000, contentRevision: 3,
  tracks: [{ id: 11, durationMs: 10000 }, { id: 12, durationMs: 10000 }],
  chapters: [{ id: 101, title: '第一章', segments: [{ trackId: 11, startMs: 0, endMs: 10000 }] },
    { id: 102, title: '第二章', segments: [{ trackId: 12, startMs: 0, endMs: 10000 }] }],
  bookmarks: [], progress: { revision: 0, positionMs: 0, status: 'NEW' },
}
let serverRevision = 1
const writes = []
const sequences = new Map()
let sessionNumber = 0
const api = {
  get: async () => ({ data: { speed: 1, backSeconds: 15, forwardSeconds: 30, sleepMinutes: 30 } }),
  post: async () => ({ data: { sessionId: `test-session-${++sessionNumber}`, key: 'scoped', contentRevision: 3,
    progress: { revision: serverRevision, trackId: 11, positionMs: 2000, status: 'LISTENING' } } }),
  put: async (url, body) => {
    if (url.endsWith('/settings')) return {}
    assert.equal(body.revision, serverRevision, 'writes must serialize against the latest server revision')
    if (sequences.has(url)) assert.ok(body.sequence > sequences.get(url))
    sequences.set(url,body.sequence)
    writes.push(body)
    return { data: { ...body, revision: ++serverRevision, status: 'LISTENING' } }
  },
  delete: async () => ({}),
}
const source = readFileSync(new URL('../src/stores/audioPlayer.ts', import.meta.url), 'utf8')
const javascript = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const module = { exports: {} }
new Function('require', 'module', 'exports', javascript)(name => {
  if (name === '@/utils/api') return { default: api }
  if (name === '@/utils/audiobook') return {
    audioApi: { detail: async () => structuredClone(detail) },
    contentPosition: (book, id, position) => (id === 12 ? 10000 : 0) + position,
  }
  if (name === '@/utils/authSession') return { AUTH_EXPIRED_EVENT: 'expired' }
  return require(name)
}, module, module.exports)
const player = module.exports.useAudioPlayerStore()
try {
  await player.open(1)
  assert.equal(elements.length, 1)
  assert.equal(player.positionMs, 2000)
  player.seek(5000)
  player.seek(1000)
  await player.sync()
  assert.equal(writes.at(-1).positionMs, 1000, 'intentional rewinds must remain valid')
  const sameStore = module.exports.useAudioPlayerStore()
  assert.equal(sameStore, player, 'route components must share the application player')
  player.sleep('chapter')
  elements[0].currentTime = 10
  elements[0].dispatchEvent(new Event('timeupdate'))
  elements[0].dispatchEvent(new Event('ended'))
  await player.sync()
  assert.equal(player.trackId, 11, 'chapter sleep must prevent automatic next-track playback')
  assert.equal(player.playing, false)
  player.sleep(null)
  await player.selectTrack(12, 0, true)
  assert.equal(elements.length, 1, 'changing tracks must reuse one audio element')
  const oldElement = elements[0]
  await player.close()
  await player.open(1)
  assert.equal(elements.length, 2)
  await player.sync()
  oldElement.dispatchEvent(new Event('error'))
  assert.equal(player.error, '', 'released media elements cannot fail a newer session')
  storage.set('token', 'other-account')
  window.dispatchEvent(new Event('expired'))
  await player.close()
  assert.equal(player.book, null)
  assert.equal(player.playing, false)
  console.log('Audiobook player: serialized revisions, rewind, shared lifetime, chapter sleep, stale media events and account teardown passed')
} finally {
  await player.close()
}
