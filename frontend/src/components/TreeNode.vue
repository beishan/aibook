<template>
  <div class="tree-node-wrapper">
    <div
      class="tree-node"
      :class="{ 'is-selected': isSelected, 'is-disabled': !node.accessible }"
      @click="handleClick"
    >
      <!-- 展开/收起图标 -->
      <span
        v-if="node.isDirectory"
        class="expand-icon"
        :class="{ 'is-expanded': node.expanded }"
        @click.stop="handleToggle"
      >
        ›
      </span>
      <span v-else class="expand-icon-placeholder"></span>

      <!-- 文件夹图标 -->
      <span class="node-icon">
        {{ node.expanded ? '📂' : '📁' }}
      </span>

      <!-- 节点名称 -->
      <span class="node-label" :class="{ 'disabled': !node.accessible }">
        {{ node.name }}
      </span>

      <!-- 操作按钮 -->
      <span v-if="node.accessible" class="node-action">
        <button class="select-btn" @click.stop="handleSelect">选择</button>
      </span>
      <span v-else class="node-action">
        <span class="access-tag">禁止访问</span>
      </span>
    </div>

    <!-- 子节点 -->
    <div v-if="node.expanded && node.children" class="tree-children">
      <tree-node
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :selected-path="selectedPath"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface TreeNodeData {
  name: string
  path: string
  isDirectory: boolean
  accessible: boolean
  expanded?: boolean
  children?: TreeNodeData[]
  loaded?: boolean
}

const props = defineProps<{
  node: TreeNodeData
  selectedPath: string
}>()

const emit = defineEmits<{
  (e: 'select', node: TreeNodeData): void
  (e: 'toggle', node: TreeNodeData): void
}>()

const isSelected = computed(() => props.selectedPath === props.node.path)

const handleClick = () => {
  if (props.node.accessible) {
    emit('select', props.node)
  }
}

const handleSelect = () => {
  emit('select', props.node)
}

const handleToggle = () => {
  emit('toggle', props.node)
}
</script>

<style scoped>
.tree-node-wrapper {
  user-select: none;
}

.tree-node {
  display: flex;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-md);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
}

.tree-node:hover {
  background: rgba(0, 122, 255, 0.1);
}

.tree-node.is-selected {
  background: rgba(0, 122, 255, 0.15);
}

.tree-node.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.expand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-right: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  transition: transform var(--transition-fast);
}

.expand-icon.is-expanded {
  transform: rotate(90deg);
}

.expand-icon-placeholder {
  width: 20px;
  margin-right: var(--spacing-xs);
}

.node-icon {
  margin-right: var(--spacing-sm);
  font-size: 18px;
}

.node-label {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-label.disabled {
  color: var(--text-tertiary);
}

.node-action {
  margin-left: var(--spacing-sm);
}

.select-btn {
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--primary);
  background: transparent;
  border: 1px solid var(--primary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  opacity: 0;
  transition: all var(--transition-fast);
}

.tree-node:hover .select-btn {
  opacity: 1;
}

.select-btn:hover {
  background: var(--primary);
  color: white;
}

.access-tag {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  background: var(--bg-secondary);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-sm);
}

.tree-children {
  padding-left: var(--spacing-lg);
}
</style>
