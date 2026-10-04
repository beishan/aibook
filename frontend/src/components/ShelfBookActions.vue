<template>
  <div class="shelf-common-actions" :class="{ 'is-list': list, 'is-small': small }" @click.stop>
    <button type="button" class="common-action" :class="{ active:book.isFavorite }" :disabled="disabled"
      :title="book.isFavorite ? '取消收藏' : '收藏'" :aria-label="book.isFavorite ? '取消收藏' : '收藏'"
      @click="emit('action', 'favorite', book)">
      <span aria-hidden="true">{{ book.isFavorite ? '⭐' : '☆' }}</span>
      <span v-if="list">{{ book.isFavorite ? '已收藏' : '收藏' }}</span>
    </button>
    <button type="button" class="common-action" :class="{ active:book.isWanted }" :disabled="disabled"
      :title="book.isWanted ? '取消想读' : '想读'" :aria-label="book.isWanted ? '取消想读' : '想读'"
      @click="emit('action', 'wanted', book)">
      <span aria-hidden="true">{{ book.isWanted ? '🔖' : '📑' }}</span>
      <span v-if="list">{{ book.isWanted ? '已想读' : '想读' }}</span>
    </button>
    <button v-if="!hideShelf" type="button" class="common-action" :class="{ active:book.onShelf }"
      :disabled="disabled" :title="book.onShelf ? '移出书架' : '加入书架'"
      :aria-label="book.onShelf ? '移出书架' : '加入书架'" @click="emit('action', 'shelf', book)">
      <span aria-hidden="true">{{ book.onShelf ? '📚' : '➕' }}</span>
      <span v-if="list">{{ book.onShelf ? '已在书架' : '加入书架' }}</span>
    </button>
    <button v-if="list" type="button" class="common-action" :disabled="disabled"
      @click="emit('action', 'quick-read', book)">小窗阅读</button>
    <el-dropdown trigger="click" :disabled="disabled" @command="command => emit('action', String(command), book)">
      <button type="button" class="common-action" :disabled="disabled" title="更多操作" aria-label="更多操作">
        <span>{{ list ? '更多' : '⋯' }}</span>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="quick-read">▣ 小窗阅读</el-dropdown-item>
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
import type { Book } from '@/stores/book'

defineProps<{
  book:Book
  list?:boolean
  small?:boolean
  hideShelf?:boolean
  disabled?:boolean
}>()
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
