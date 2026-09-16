import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [view, api, entity, dtos, management, httpClient, configuration] = await Promise.all([
  readFile(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/utils/crawler.ts', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/model/entity/CrawlerSite.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/dto/crawler/CrawlerDtos.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/service/crawler/CrawlerManagementService.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/service/crawler/CrawlerHttpClient.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/service/crawler/CrawlerSiteConfigurationService.java', import.meta.url), 'utf8'),
])

assert.match(view, /v-model="siteForm\.respectRobotsTxt"/)
assert.match(view, /active-text="遵守 robots\.txt" inactive-text="忽略 robots\.txt"/)
assert.match(view, /respectRobotsTxt:true/)
assert.match(api, /respectRobotsTxt:boolean/)
assert.match(entity, /Boolean respectRobotsTxt = true/)
assert.match(dtos, /Boolean respectRobotsTxt/)
assert.match(management, /setRespectRobotsTxt\(bool\(p\.respectRobotsTxt\(\), true\)\)/)
assert.match(httpClient, /Boolean\.FALSE\.equals\(site\.getRespectRobotsTxt\(\)\)/)
assert.match(configuration, /view\.respectRobotsTxt\(\)/)

console.log('Crawler sites can independently enable or disable robots.txt enforcement')
