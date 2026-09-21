import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const dialog = await readFile(new URL('../src/components/BookEditDialog.vue', import.meta.url), 'utf8')
const seriesPanel = await readFile(new URL('../src/components/BookSeriesPanel.vue', import.meta.url), 'utf8')
const detailView = await readFile(new URL('../src/views/BookDetailView.vue', import.meta.url), 'utf8')

assert.match(dialog, /<el-dialog[\s\S]*?append-to-body[\s\S]*?>/, 'book editor must teleport its overlay outside filtered page containers')
assert.match(seriesPanel, /<BookEditDialog[\s\S]*?:visible="editing"/, 'series editor must keep using the shared book editor')
assert.match(detailView, /class="book-content glass"/, 'regression fixture must retain the filtered glass detail container')

console.log('Book edit dialog overlay is teleported outside the rounded glass detail container')
