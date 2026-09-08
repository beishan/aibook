import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'))
const [shared, navigator] = await Promise.all([
  import('@readium/shared'),
  import('@readium/navigator'),
])

assert.equal(typeof shared.Manifest.deserialize, 'function')
assert.equal(typeof shared.Publication, 'function')
assert.equal(typeof shared.HttpFetcher, 'function')
assert.equal(typeof navigator.EpubNavigator, 'function')
assert.equal(typeof navigator.EpubPreferences, 'function')

const manifest = shared.Manifest.deserialize({
  metadata: { title: 'Compatibility test' },
  links: [{ rel: 'self', href: '/api/readium-resources/test/manifest.json' }],
  readingOrder: [{ href: 'resources/OPS/chapter.xhtml', type: 'application/xhtml+xml' }],
})
assert.ok(manifest)
assert.equal(manifest.baseURL, '/api/readium-resources/test/')
assert.equal(manifest.readingOrder.items[0].href, 'resources/OPS/chapter.xhtml')

for (const dependency of ['@readium/navigator', '@readium/shared', '@readium/navigator-html-injectables']) {
  assert.ok(packageJson.dependencies[dependency], `${dependency} must remain a direct dependency`)
  const metadata = JSON.parse(await readFile(
    new URL(`../node_modules/${dependency}/package.json`, import.meta.url),
    'utf8',
  ))
  assert.equal(metadata.license, 'BSD-3-Clause', `${dependency} license changed`)
}

console.log('epub.js and Readium engine dependencies are compatible')
