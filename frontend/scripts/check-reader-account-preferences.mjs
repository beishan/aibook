import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [preferencesSource, readerSource, entitySource, dtoSource, serviceSource] = await Promise.all([
  readFile(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/views/ReaderView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/model/entity/UserPreference.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/dto/UserPreferencesDTO.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/com/aibook/service/UserService.java', import.meta.url), 'utf8'),
])

for (const field of [
  'appearance',
  'epubEngine',
  'fontFamily',
  'fontSize',
  'lineHeight',
  'paragraphSpacing',
  'contentWidth',
  'backgroundColor',
  'backgroundImageId',
  'screenMode',
  'textIndent',
  'showProgress',
]) {
  assert.match(preferencesSource, new RegExp(`${field}:`), `missing reader preference ${field}`)
}

assert.match(preferencesSource, /READER_SETTINGS_STORAGE_KEY = 'ai-book-reader-settings'/)
assert.match(preferencesSource, /READER_SETTINGS_SAVE_DELAY_MS = 500/)
assert.match(preferencesSource, /if \(!accountToken\) \{[\s\S]*localStorage\.setItem\(READER_SETTINGS_STORAGE_KEY/)
assert.match(preferencesSource, /if \(localStorage\.getItem\('token'\) !== expectedToken\) return/)
assert.match(preferencesSource, /if \(data\.readerSettings\)[\s\S]*setReaderSettings\(data\.readerSettings, false\)/)
assert.match(preferencesSource, /else \{[\s\S]*setReaderSettings\(accountDefaults, false\)[\s\S]*missingPreferences\.readerSettings = accountDefaults/)
assert.match(preferencesSource, /catch \(error\) \{[\s\S]*Failed to load user preferences/)
assert.match(preferencesSource, /setTimeout\(persist, READER_SETTINGS_SAVE_DELAY_MS\)/)

assert.match(readerSource, /Object\.assign\(settings\.value, preferencesStore\.readerSettings\)/)
assert.match(readerSource, /preferencesStore\.setReaderSettings\(settings\.value\)/)
assert.doesNotMatch(readerSource, /localStorage\.setItem\(SETTINGS_STORAGE_KEY/)

assert.match(entitySource, /@Column\(name = "reader_settings", columnDefinition = "TEXT"\)/)
assert.match(dtoSource, /private ReaderSettingsDTO readerSettings/)
assert.match(serviceSource, /normalizeReaderSettings\(request\.getReaderSettings\(\)\)/)
assert.match(serviceSource, /readerSettings\(readReaderSettings\(user\.getReaderSettings\(\)\)\)/)

console.log('Reader settings use account persistence and keep logged-in settings isolated from browser cache')
