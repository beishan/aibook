import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/BookDetailView.vue', import.meta.url), 'utf8')

assert.match(source, /title="添加书籍版本"[\s\S]*?append-to-body/, 'add-version dialog must render above the detail surface')
assert.match(source, />本地上传<\/button>[\s\S]*?>从已有书籍选择<\/button>/, 'dialog must expose both add-version modes')
assert.match(source, /candidateKeyword\.value = book\.value\?\.title \|\| ''/, 'same-title books must be the default candidate search')
assert.match(source, /\/versions\/candidates/, 'candidate books must use server-side search and pagination')
assert.match(source, /selectedCandidateBooks/, 'candidate books must support multi-selection')
assert.match(source, /Promise\.all\(selectedCandidateBooks\.value\.map/, 'all selected books must load their available versions')
assert.match(source, /selectedVersionIds/, 'individual source versions must be selectable')
assert.match(source, />保留原书籍<\/button>[\s\S]*?>合并并移除<\/button>/, 'source handling must offer keep and merge')
assert.match(source, /allImportVersionsSelected[\s\S]*?合并并移除原书籍时，需要勾选/, 'merge must prevent unselected versions from disappearing')
assert.match(source, /\/versions\/import/, 'selected versions must be submitted to the import endpoint')
assert.match(source, /确定将[\s\S]*?合并后原书籍会从书库中消失/, 'destructive merge must require explicit confirmation')

console.log('Book detail supports local upload and guarded multi-book version import')
