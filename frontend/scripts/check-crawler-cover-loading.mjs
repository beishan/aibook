import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const observers = []
const requests = []
const revoked = []
const timers = new Map()
let timerId = 0
let blobId = 0
const context = {
  exports: {}, AbortController,
  setTimeout(callback) {
    timers.set(++timerId, callback)
    return timerId
  },
  clearTimeout(id) { timers.delete(id) },
  URL: {
    createObjectURL: () => `blob:cover-${++blobId}`,
    revokeObjectURL: url => revoked.push(url),
  },
  IntersectionObserver: class {
    constructor(callback, options) {
      this.callback = callback
      this.options = options
      observers.push(this)
    }
    observe(image) { this.image = image }
    disconnect() { this.disconnected = true }
  },
  fetch(url, { signal }) {
    return new Promise((resolve, reject) => {
      requests.push({ url, signal, resolve, reject })
      signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true })
    })
  },
}
const source = fs.readFileSync(new URL('../src/utils/queuedCover.ts', import.meta.url), 'utf8')
vm.runInNewContext(ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText, context)
const directive = context.exports.vQueuedCover
const flush = async () => {
  for (let i = 0; i < 15; i++) await Promise.resolve()
}
function mount(url) {
  const image = {
    style: { removeProperty(key) { delete this[key] } },
    removeAttribute(key) { delete this[key] },
  }
  directive.mounted(image, { value: url })
  return { image, observer: observers.at(-1), url }
}
const enter = item => item.observer.callback([{ isIntersecting: true }])
const leave = item => item.observer.callback([{ isIntersecting: false }])
const unmount = item => directive.beforeUnmount(item.image)
const finish = async request => {
  request.resolve({ ok: true, blob: async () => ({}) })
  await flush()
}

const oldPage = Array.from({ length: 30 }, (_, index) => mount(`/covers/old-${index}`))
assert.equal(requests.length, 0, 'offscreen covers must not request images')
oldPage.forEach(enter)
assert.equal(requests.length, 3, 'only three covers may occupy connections')
assert.equal(oldPage[0].observer.options.rootMargin, '160px 0px')
oldPage.forEach(unmount)
await flush()
assert(requests.every(request => request.signal.aborted), 'pagination aborts active old-page requests')
assert.equal(requests.length, 3, 'queued old-page covers must never start')
assert(oldPage.every(item => item.observer.disconnected))

const current = mount('/covers/current')
enter(current)
assert.equal(requests.at(-1).url, '/covers/current', 'new page does not wait for old covers')
await finish(requests.at(-1))
assert.equal(current.image.src, 'blob:cover-1')
directive.updated(current.image, { value: '/covers/current', oldValue: '/covers/current' })
assert.equal(current.image.src, 'blob:cover-1', 'polling must not restart unchanged covers')
directive.updated(current.image, { value: '/covers/resized', oldValue: '/covers/current' })
assert(revoked.includes('blob:cover-1'), 'size/source changes revoke old blobs')
assert.equal(current.image.src, undefined)
current.observer = observers.at(-1)
enter(current)
const resized = requests.at(-1)
leave(current)
await flush()
assert(resized.signal.aborted, 'scrolling away cancels in-flight cover downloads')
enter(current)
await finish(requests.at(-1))
unmount(current)
assert(revoked.includes('blob:cover-2'), 'unmount releases decoded cover URLs')

const slow = Array.from({ length: 4 }, (_, index) => mount(`/covers/slow-${index}`))
slow.forEach(enter)
const beforeTimeout = requests.length
for (const callback of [...timers.values()]) callback()
await flush()
assert.equal(requests.length, beforeTimeout + 1, 'timeouts allow queued covers to advance')
slow.forEach(unmount)
await flush()
assert.equal(timers.size, 0)

const broken = mount('/covers/broken')
enter(broken)
requests.at(-1).resolve({ ok: false })
await flush()
assert.equal(broken.image.src, undefined, 'failed covers leave the text placeholder')
unmount(broken)

const stale = mount('/covers/stale')
enter(stale)
requests.at(-1).resolve({ ok: true, blob: async () => ({}) })
unmount(stale)
await flush()
assert.equal(stale.image.src, undefined, 'late old-page results must not recreate object URLs')

delete context.IntersectionObserver
const fallback = mount('/covers/fallback')
assert.equal(requests.at(-1).url, '/covers/fallback', 'unsupported browsers still load covers')
await finish(requests.at(-1))
unmount(fallback)

const view = fs.readFileSync(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
assert.equal((view.match(/v-queued-cover="getCoverThumbnailUrl/g) || []).length, 2)
assert.equal((view.match(/v-if="book.coverUrl && shouldLoadBookCover\(\)"/g) || []).length, 2)
console.log('Crawler covers: viewport loading, concurrency, page cancellation, scrolling, source updates, timeout, cleanup, fallback and privacy passed')
