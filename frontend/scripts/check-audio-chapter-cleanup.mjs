import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/audioChapterCleanup.ts', import.meta.url), 'utf8')
const compiled = ts.transpile(source, { module: ts.ModuleKind.ESNext })
const { previewChapterCleanup } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

const ad = '【持续更新关注闲鱼：大猫券码】'
const titles = [`001.西游记 ${ad}.m4a`, `第二章 ${ad}${ad}`, '无广告的章节', ad]
const preview = previewChapterCleanup(titles, ad)
assert.deepEqual(preview.map((item) => item.index), [0, 1, 3])
assert.equal(preview[0].after, '001.西游记 .m4a')
assert.equal(preview[1].after, '第二章')
assert.equal(preview[2].after, '')
assert.deepEqual(previewChapterCleanup(titles, ''), [])
assert.deepEqual(previewChapterCleanup(titles, '不存在的广告'), [])
assert.equal(titles[0], `001.西游记 ${ad}.m4a`)
console.log('Audio chapter cleanup preview: literal matching, repeated text and empty titles passed')
