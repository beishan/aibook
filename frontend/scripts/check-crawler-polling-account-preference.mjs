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
assert.match(userServiceSource, /CRAWLER_POLLING_INTERVAL_SECONDS = Set\.of\(1, 3, 5, 10, 30\)/)

console.log('Crawler polling interval is validated and persisted as an account preference')
