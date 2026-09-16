import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [view, api, entity, dtos, controller, client] = await Promise.all([
  readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/utils/crawler.ts', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/model/entity/CrawlerSite.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/dto/crawler/CrawlerDtos.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/controller/CrawlerController.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/service/crawler/CrawlerHttpClient.java', import.meta.url), 'utf8'),
])

assert.match(view, /site\.protection\.coolingDown/)
assert.match(view, /站点保护冷却中/)
assert.match(view, /resetSiteProtection\(site\)/)
assert.match(api, /CrawlerProtectionState/)
assert.match(api, /sites\/\$\{id\}\/protection\/reset/)
assert.match(entity, /Instant crawlerBlockedUntil/)
assert.match(entity, /String crawlerBlockReason/)
assert.match(dtos, /CrawlerProtectionView protection/)
assert.match(controller, /@PostMapping\("\/sites\/\{id\}\/protection\/reset"\)/)
assert.match(client, /persistProtection\(site, state\.blockedUntil\(\), state\.reason\(\)\)/)
assert.match(client, /site\.getCrawlerBlockedUntil\(\)/)

console.log('Crawler protection state is persisted, restored, displayed, and manually resettable')
