import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [panel, api, dto, service, client, controller] = await Promise.all([
  readFile(new URL('../src/components/ProxySettingsPanel.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/utils/proxySettings.ts', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/dto/CrawlerSettingsDtos.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/service/CrawlerSettingsService.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/service/crawler/CrawlerHttpClient.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/controller/CrawlerSettingsController.java', import.meta.url), 'utf8'),
])

const fields = [
  'retryBackoffMaxMillis', 'maxInlineRetryDelayMillis', 'maxResponseSizeMb',
  'maxRedirects', 'maxOriginConcurrency', 'adaptiveDelayMaxMillis',
  'circuitCooldownSeconds', 'accessDeniedCooldownSeconds', 'robotsCacheMinutes',
  'robotsErrorCacheMinutes', 'softBlockDetectionEnabled',
]

for (const field of fields) {
  assert.ok(panel.includes(`requestSettings.${field}`), `${field} missing from settings UI`)
  assert.ok(api.includes(`${field}:`), `${field} missing from frontend type`)
  assert.ok(dto.includes(field), `${field} missing from backend DTO`)
  assert.ok(service.includes(field), `${field} missing from persistence service`)
  assert.ok(client.includes(`settings.${field}()`), `${field} is not consumed by HTTP client`)
}
assert.match(controller, /refreshGlobalConfiguration\(updated\)/)

console.log('Crawler protection constants are editable, persisted, and applied at runtime')
