import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/AuthorManagementView.vue', import.meta.url), 'utf8')
const preferences = await readFile(new URL('../src/stores/preferences.ts', import.meta.url), 'utf8')

assert.match(source, /api\.get<AuthorPage>\('\/api\/authors',[\s\S]*?page: currentPage\.value - 1,[\s\S]*?size: pageSize\.value,[\s\S]*?keyword:[\s\S]*?sort: sort\.value/, 'author list must request one server-side page')
assert.match(source, /:total="totalElements"/, 'pagination must use the server total')
assert.doesNotMatch(source, /filteredAuthors|pagedAuthors/, 'author filtering and pagination must not process the full list in the browser')
assert.match(source, /setTimeout\(\(\) => \{ void loadAuthors\(\) \}, 250\)/, 'author search must be debounced')
assert.match(source, /sequence !== loadSequence/, 'stale author responses must not replace newer searches')
assert.match(preferences, /authorPageSize: number \| null/, 'author page size must be an account preference')
assert.match(preferences, /persistRemote\(\{ authorPageSize: value \}\)/, 'author page size must persist to the account')

console.log('Author management uses server pagination, debounced search, and account page-size persistence')
