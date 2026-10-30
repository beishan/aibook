import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { computed, nextTick, ref, watch } from 'vue'

const source = await readFile(new URL('../src/views/TagManagementView.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('tags.ts', script, ts.ScriptTarget.Latest, true)
const names = new Set([
  'filteredManageTags', 'pagedManageTags', 'allManagePageSelected', 'someManagePageSelected',
  'toggleManagePage', 'resetManageFilter', 'clampManagePage', 'toggleManageTag', 'openManage',
  'deleteSelectedTags',
])
const declarations = ast.statements.filter(node => ts.isVariableStatement(node)
  && node.declarationList.declarations.some(declaration => names.has(declaration.name.getText(ast))))
  .map(node => node.getText(ast)).join('\n')
const original = Array.from({ length: 45 }, (_, index) => ({
  id: index + 1, name: `标签 ${index + 1}`, sourceType: index % 2 ? 'MANUAL' : 'SCRAPER',
}))
const tagStore = { tags: ref([...original]), deleteTags: async ids => {
  tagStore.tags.value = tagStore.tags.value.filter(tag => !ids.includes(tag.id))
  return { deletedCount: ids.length }
} }
// Pinia unwraps its refs when accessed by the component.
const store = { get tags() { return tagStore.tags.value }, deleteTags: ids => tagStore.deleteTags(ids) }
const manageKeyword = ref(''), manageSourceFilter = ref(''), managePage = ref(1)
const selectedManageTagIds = ref([]), deletingManageTags = ref(false), manageDialogVisible = ref(false)
const notices = []
const args = {
  ref, computed, watch, tagStore: store, manageKeyword, manageSourceFilter, managePage,
  managePageSize: 20, selectedManageTagIds, deletingManageTags, manageDialogVisible,
  confirm: async () => true,
  message: { success: value => notices.push(value), error: value => notices.push(value) },
}
const code = ts.transpile(declarations, { target: ts.ScriptTarget.ES2022 })
const handlers = new Function(...Object.keys(args), `${code};
  watch([manageKeyword, manageSourceFilter], resetManageFilter);
  watch(() => filteredManageTags.value.length, clampManagePage);
  return { ${[...names].join(', ')} };`)(...Object.values(args))

handlers.openManage()
assert.equal(handlers.pagedManageTags.value.length, 20)
handlers.toggleManageTag(1, true)
assert.equal(handlers.someManagePageSelected.value, true)
assert.equal(handlers.allManagePageSelected.value, false)
handlers.toggleManagePage(true)
assert.equal(handlers.allManagePageSelected.value, true)
assert.equal(selectedManageTagIds.value.length, 20)
managePage.value = 2
handlers.toggleManagePage(true)
assert.equal(selectedManageTagIds.value.length, 40, 'page selection preserves choices from previous pages')
handlers.toggleManagePage(false)
assert.deepEqual([...selectedManageTagIds.value], original.slice(0, 20).map(tag => tag.id),
  'deselecting current page cannot clear another page')
managePage.value = 3
assert.equal(handlers.pagedManageTags.value.length, 5)
handlers.toggleManagePage(true)
assert.equal(selectedManageTagIds.value.length, 25)

manageSourceFilter.value = 'SCRAPER'
await nextTick()
assert.equal(managePage.value, 1)
assert.equal(selectedManageTagIds.value.length, 0, 'filter changes clear hidden destructive selections')
assert.equal(handlers.filteredManageTags.value.length, 23)
manageKeyword.value = '不存在'
await nextTick()
assert.equal(handlers.pagedManageTags.value.length, 0)
assert.equal(handlers.allManagePageSelected.value, false)
handlers.toggleManagePage(true)
assert.equal(selectedManageTagIds.value.length, 0)

handlers.openManage()
await nextTick()
managePage.value = 3
handlers.toggleManagePage(true)
await handlers.deleteSelectedTags()
await nextTick()
assert.equal(managePage.value, 2, 'deleting last page returns to last valid page')
assert.equal(handlers.pagedManageTags.value.length, 20)
handlers.toggleManagePage(true)
deletingManageTags.value = true
handlers.toggleManagePage(false)
assert.equal(selectedManageTagIds.value.length, 20, 'in-flight deletion freezes selection')
deletingManageTags.value = false
tagStore.deleteTags = async () => { throw new Error('删除失败') }
await handlers.deleteSelectedTags()
assert.equal(selectedManageTagIds.value.length, 20, 'failed deletion preserves choices for retry')
assert.equal(deletingManageTags.value, false)
console.log('标签整理：分页、本页全选/半选、跨页保留、筛选重置、删除回退及失败重试通过')
