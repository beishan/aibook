import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [settingsSource, storeSource, layoutSource, iconSource] = await Promise.all([
  readFile(new URL('../src/components/DockSettingsPanel.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/stores/dockIcons.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/layouts/DockLayout.vue', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/DockIcon.vue', import.meta.url), 'utf8'),
])

assert.match(settingsSource, /\{ icon: 'statistics', label: '阅读统计' \}/)
assert.match(storeSource, /'crawler', 'statistics', 'settings'/)
assert.match(storeSource, /statistics: '',/)
assert.match(storeSource, /statistics: false,/)
assert.match(layoutSource, /\{ path: '\/statistics', icon: 'statistics', title: '阅读统计' \}/)
assert.match(iconSource, /statistics: Histogram,/)

console.log('Reading statistics custom Dock icon is configurable, cached, and rendered')
