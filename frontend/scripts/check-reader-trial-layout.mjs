import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const readerSource = await readFile(
  new URL('../src/views/ReaderView.vue', import.meta.url),
  'utf8',
)

assert.match(
  readerSource,
  /'trial-reader-mode': settings\.appearance === 'trialReader'/,
  'ReaderView must activate the migrated trial-reader layout class',
)
assert.match(
  readerSource,
  /type ReaderAppearance = 'classic' \| 'readingRoom' \| 'trialReader'/,
)
assert.match(
  readerSource,
  /value: 'trialReader', label: '临时阅读', description: '轻量试读布局'/,
)
assert.match(readerSource, /grid-template-columns: repeat\(3, minmax\(0, 1fr\)\)/)
assert.match(readerSource, /\.appearance-segmented--trialReader\s*{\s*--appearance-index: 2;/)
assert.match(
  readerSource,
  /appearanceOptions\.findIndex\(option => option\.value === settings\.value\.appearance\)/,
  'keyboard navigation must support every layout option',
)

for (const selector of [
  '.trial-reader-mode .reader-header',
  '.trial-reader-mode .reader-body',
  '.trial-reader-mode .side-panel',
  '.trial-reader-mode .reader-tool-rail',
  '.trial-reader-mode .reader-progress-dock',
  '.trial-reader-mode .settings-panel',
]) {
  assert.ok(readerSource.includes(selector), `missing migrated layout rule: ${selector}`)
}

assert.doesNotMatch(
  readerSource,
  /settings\.appearance === 'trialReader'[\s\S]{0,300}CrawlerTrialReaderView/,
  'the formal reader layout must not fork to the temporary reader component',
)

console.log('ReaderView includes the migrated temporary-reading layout theme')
