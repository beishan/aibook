import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const crawlerViewSource = await readFile(
  new URL('../src/views/CrawlerView.vue', import.meta.url),
  'utf8',
)
const loaderSource = crawlerViewSource.match(
  /async function loadTaskQueueSettings\(\)\{([\s\S]*?)\n\}/,
)?.[1]

assert.ok(loaderSource, 'task queue settings loader must exist')
assert.match(
  loaderSource,
  /taskQueueSettings\.value=latest/,
  'automatic polling must continue refreshing the displayed runtime settings',
)
assert.match(
  loaderSource,
  /if\(!queueSettingsDialog\.value&&!savingQueueSettings\.value\)queueLimit\.value=latest\.maxConcurrentTasks/,
  'automatic polling must not overwrite the queue limit draft while the dialog is open or saving',
)
assert.match(
  crawlerViewSource,
  /function openQueueSettings\(\)\{if\(taskQueueSettings\.value\)queueLimit\.value=taskQueueSettings\.value\.maxConcurrentTasks;queueSettingsDialog\.value=true\}/,
  'opening the dialog must initialize a fresh draft from the latest server value',
)
assert.match(
  crawlerViewSource,
  /<el-input-number class="queue-limit-stepper"[^>]*:min="1"[^>]*:max="16"[^>]*step-strictly/,
  'the concurrency editor must use the Element Plus input-number stepper with its supported range',
)
assert.doesNotMatch(
  crawlerViewSource,
  /class="queue-limit-stepper"[^>]*controls-position="right"/,
  'the stepper must keep Element Plus controls on opposite sides',
)
assert.match(crawlerViewSource, /\.queue-limit-stepper\{width:min\(300px,100%\);height:64px\}/)

console.log('Crawler queue settings use the protected Element Plus side-control stepper')
