import vm from 'node:vm'
import ts from 'typescript'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [preferencesSource, crawlerViewSource, preferenceEntitySource, preferenceDtoSource, userServiceSource] =
  await Promise.all([
    readFile(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8'),
    readFile(new URL('../../backend/src/main/java/com/aibook/model/entity/UserPreference.java', import.meta.url), 'utf8'),
    readFile(new URL('../../backend/src/main/java/com/aibook/dto/UserPreferencesDTO.java', import.meta.url), 'utf8'),
    readFile(new URL('../../backend/src/main/java/com/aibook/service/UserService.java', import.meta.url), 'utf8'),
  ])

assert.match(preferencesSource, /crawlerPollingIntervalSeconds: number \| null/)
assert.match(
  preferencesSource,
  /persistRemote\(\{ crawlerPollingIntervalSeconds: value }\)/,
  'changing the interval must persist it to the active account',
)
assert.match(
  preferencesSource,
  /isCrawlerPollingIntervalSeconds\(data\.crawlerPollingIntervalSeconds\)/,
  'account hydration must restore a valid saved interval',
)
assert.match(
  preferencesSource,
  /missingPreferences\.crawlerPollingIntervalSeconds = crawlerPollingIntervalSeconds\.value/,
  'an existing local interval must migrate when the account has no saved value',
)
assert.match(
  crawlerViewSource,
  /preferencesStore\.setCrawlerPollingIntervalSeconds\(value\)/,
  'CrawlerView must update the shared account preference',
)
assert.doesNotMatch(
  crawlerViewSource,
  /localStorage\.setItem\(POLLING_INTERVAL_KEY/,
  'CrawlerView must not remain the sole owner of polling persistence',
)
assert.match(preferenceEntitySource, /crawlerPollingIntervalSeconds/)
assert.match(preferenceDtoSource, /crawlerPollingIntervalSeconds/)
assert.match(userServiceSource, /DEFAULT_CRAWLER_POLLING_INTERVALS =\s*Set\.of\(1, 3, 5, 10, 30\)/)

console.log('Crawler polling interval is validated and persisted as an account preference')

const timers = new Map()
let nextTimer = 0
let syncs = 0
const context = vm.createContext({
  crawlerPageMounted: true,
  pollingReady: true,
  progressPolling: false,
  timer: undefined,
  document: { visibilityState: 'visible' },
  pollingIntervalSeconds: { value: 3 },
  window: {
    clearInterval: id => timers.delete(id),
    setInterval: (callback, delay) => {
      timers.set(++nextTimer, { callback, delay })
      return nextTimer
    },
  },
  currentCrawlerTasks: { value: [] },
  tasks: { value: [] },
  dashboard: { value: null },
  bookDrawer: { value: true },
  taskDrawer: { value: false },
  syncOpenBookProgress: async () => { syncs++ },
})
const lifecycle = crawlerViewSource.slice(crawlerViewSource.indexOf('function canPollCrawlerProgress()'),
  crawlerViewSource.indexOf('async function loadPollingIntervalOptions()'))
const poll = crawlerViewSource.slice(crawlerViewSource.indexOf('async function pollCrawlerProgress()'),
  crawlerViewSource.indexOf('async function syncOpenBookProgress('))
vm.runInContext(ts.transpile(`${lifecycle}\n${poll}`), context)
context.restartPolling()
assert.equal(timers.size, 1)
assert.equal([...timers.values()][0].delay, 3000)
await context.pollCrawlerProgress()
assert.equal(syncs, 1)
context.document.visibilityState = 'hidden'
context.handlePollingVisibilityChange()
assert.equal(timers.size, 0, 'hidden pages stop the timer')
await context.pollCrawlerProgress()
assert.equal(syncs, 1, 'hidden pages reject queued or direct polling')
context.pollingIntervalSeconds.value = 10
context.restartPolling()
assert.equal(timers.size, 0, 'interval changes cannot resume hidden polling')
context.document.visibilityState = 'visible'
context.handlePollingVisibilityChange()
await Promise.resolve()
assert.equal(syncs, 2, 'returning to the visible page refreshes immediately')
assert.equal([...timers.values()][0].delay, 10000)
context.handlePollingVisibilityChange()
assert.equal(timers.size, 1, 'visibility events never duplicate timers')
context.crawlerPageMounted = false
context.stopPolling()
context.restartPolling()
assert.equal(timers.size, 0, 'late initialization cannot restart an unmounted page')
context.crawlerPageMounted = true
context.pollingReady = false
context.handlePollingVisibilityChange()
assert.equal(timers.size, 0, 'visibility changes wait for initialization')
assert(crawlerViewSource.includes("document.removeEventListener('visibilitychange', handlePollingVisibilityChange)"))
console.log('Crawler polling visibility: pause, resume, interval changes, initialization and unmount passed')
