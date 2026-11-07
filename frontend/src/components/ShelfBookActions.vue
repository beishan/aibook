<template>
  <div class="shelf-common-actions" :class="{ 'is-list': list, 'is-small': small, 'is-expanded': expanded }" @click.stop>
    <button type="button" :role="expanded ? 'menuitem' : undefined" class="common-action" :class="{ active:book.isFavorite }" :disabled="disabled"
      :title="book.isFavorite ? '取消收藏' : '收藏'" :aria-label="book.isFavorite ? '取消收藏' : '收藏'"
      @click="emit('action', 'favorite', book)">
      <el-icon v-if="expanded"><Star /></el-icon>
      <span v-else aria-hidden="true">{{ book.isFavorite ? '⭐' : '☆' }}</span>
      <span v-if="list || expanded">{{ book.isFavorite ? '已收藏' : '收藏' }}</span>
    </button>
    <button type="button" :role="expanded ? 'menuitem' : undefined" class="common-action" :class="{ active:book.isWanted }" :disabled="disabled"
      :title="book.isWanted ? '取消想读' : '想读'" :aria-label="book.isWanted ? '取消想读' : '想读'"
      @click="emit('action', 'wanted', book)">
      <el-icon v-if="expanded"><CollectionTag /></el-icon>
      <span v-else aria-hidden="true">{{ book.isWanted ? '🔖' : '📑' }}</span>
      <span v-if="list || expanded">{{ book.isWanted ? '已想读' : '想读' }}</span>
    </button>
    <button v-if="!hideShelf" type="button" :role="expanded ? 'menuitem' : undefined" class="common-action" :class="{ active:book.onShelf }"
      :disabled="disabled" :title="book.onShelf ? '移出书架' : '加入书架'"
      :aria-label="book.onShelf ? '移出书架' : '加入书架'" @click="emit('action', 'shelf', book)">
      <el-icon v-if="expanded"><Reading /></el-icon>
      <span v-else aria-hidden="true">{{ book.onShelf ? '📚' : '➕' }}</span>
      <span v-if="list || expanded">{{ book.onShelf ? '已在书架' : '加入书架' }}</span>
    </button>
    <button v-if="list && canQuickRead" type="button" :role="expanded ? 'menuitem' : undefined" class="common-action" :disabled="disabled"
      @click="emit('action', 'quick-read', book)">小窗阅读</button>
    <template v-if="expanded">
      <div class="shelf-menu-divider" role="separator"></div>
      <button
        v-for="action in expandedActions"
        :key="action.value"
        type="button"
        role="menuitem"
        class="common-action"
        :disabled="disabled"
        @click="emit('action', action.value, book)"
      >
        <el-icon><component :is="action.icon" /></el-icon>
        <span>{{ action.label }}</span>
      </button>
    </template>
    <el-dropdown
      v-else
      :persistent="false"
      trigger="click"
      :disabled="disabled"
      @command="command => emit('action', String(command), book)"
    >
      <button type="button" :role="expanded ? 'menuitem' : undefined" class="common-action" :disabled="disabled" title="更多操作" aria-label="更多操作">
        <span>{{ list ? '更多' : '⋯' }}</span>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item v-if="canQuickRead" command="quick-read">▣ 小窗阅读</el-dropdown-item>
          <el-dropdown-item command="scrape">✨ 刮削元数据</el-dropdown-item>
          <el-dropdown-item command="random-cover">🎲 随机封面</el-dropdown-item>
          <el-dropdown-item command="reparse">🔄 重新解析</el-dropdown-item>
          <el-dropdown-item command="repair">🔧 内容修复</el-dropdown-item>
          <el-dropdown-item command="edit">✏️ 编辑书籍</el-dropdown-item>
          <el-dropdown-item command="add-to-list">📚 加入书单</el-dropdown-item>
          <el-dropdown-item divided command="delete">🗑️ 移入回收站</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Star, CollectionTag, Reading, MagicStick, Picture, Refresh, Tools, Edit, Collection } from '@element-plus/icons-vue'
import type { Book } from '@/stores/book'

const props = defineProps<{
  book:Book
  list?:boolean
  small?:boolean
  hideShelf?:boolean
  expanded?:boolean
  disabled?:boolean
}>()
const canQuickRead = computed(() => props.book.format !== 'cbz' && props.book.format !== 'cbr')
const expandedActions = computed(() => [
  { value: 'quick-read', label: '小窗阅读', icon: Reading },
  { value: 'scrape', label: '刮削元数据', icon: MagicStick },
  { value: 'random-cover', label: '随机封面', icon: Picture },
  { value: 'reparse', label: '重新解析', icon: Refresh },
  { value: 'repair', label: '内容修复', icon: Tools },
  { value: 'edit', label: '编辑书籍', icon: Edit },
  { value: 'add-to-list', label: '加入书单', icon: Collection },
].filter(action => action.value !== 'quick-read' || canQuickRead.value))
const emit = defineEmits<{ (event:'action', command:string, book:Book):void }>()
</script>

<style scoped>
.shelf-common-actions {
  display: contents;
}

.common-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 30px;
  gap: 5px;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  cursor: pointer;
}

.common-action:hover,
.common-action.active {
  background: rgba(255, 255, 255, 0.3);
}

.common-action:disabled {
  opacity: 0.5;
  cursor: wait;
}

.common-action:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.is-small .common-action {
  flex-basis: 24px;
  width: 24px;
  height: 24px;
}

.is-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.is-list .common-action {
  width: auto;
  height: auto;
  min-height: 30px;
  flex: 0 0 auto;
  padding: 5px 8px;
  border-color: transparent;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
}

.is-list .common-action:hover,
.is-list .common-action.active {
  background: var(--surface-hover);
  color: var(--primary);
}
</style>
