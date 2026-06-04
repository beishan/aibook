<template>
  <div class="directory-browser">
    <!-- 当前选中路径显示 -->
    <div class="selected-path">
      <span class="path-icon">📁</span>
      <span class="path-text">{{ selectedPath || '请选择目录' }}</span>
    </div>

    <!-- 目录树 -->
    <div class="tree-container">
      <div v-if="loading" class="loading">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>
      <div v-else-if="treeData.length === 0" class="empty">
        <div class="empty-icon">📂</div>
        <p>暂无数据</p>
      </div>
      <div v-else class="tree-content">
        <tree-node
          v-for="node in treeData"
          :key="node.path"
          :node="node"
          :selected-path="selectedPath"
          @select="handleNodeSelect"
          @toggle="handleNodeToggle"
        />
      </div>
    </div>

    <!-- 手动输入 -->
    <div class="manual-input">
      <div class="divider">
        <span class="divider-text">或手动输入路径</span>
      </div>
      <div class="input-group">
        <input
          v-model="manualPath"
          type="text"
          class="input"
          placeholder="例如: /app/uploads"
          @keyup.enter="handleManualInput"
        />
        <button class="btn btn-primary" @click="handleManualInput">确定</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from '@/utils/message'
import api from '@/utils/api'
import TreeNode from './TreeNode.vue'

interface DirectoryItem {
  name: string
  path: string
  isDirectory: boolean
  size: number
  type: string
  accessible: boolean
}

interface TreeNodeData {
  name: string
  path: string
  isDirectory: boolean
  accessible: boolean
  expanded?: boolean
  children?: TreeNodeData[]
  loaded?: boolean
}

const emit = defineEmits<{
  (e: 'select', path: string): void
}>()

const loading = ref(false)
const selectedPath = ref('')
const manualPath = ref('')
const treeData = ref<TreeNodeData[]>([])

// 加载目录内容
const loadDirectory = async (path: string): Promise<TreeNodeData[]> => {
  try {
    const res = await api.get('/api/files/browse', { params: { path } })
    // 后端直接返回数组格式
    const items: DirectoryItem[] = Array.isArray(res.data) ? res.data : (res.data.directories || [])
    return items
      .filter((item: DirectoryItem) => item.isDirectory)
      .map((item: DirectoryItem) => ({
        name: item.name,
        path: item.path,
        isDirectory: item.isDirectory,
        accessible: item.accessible !== false,
        expanded: false,
        children: [],
        loaded: false,
      }))
  } catch (error) {
    console.error('Failed to load directory:', error)
    return []
  }
}

// 加载根目录
const loadRoot = async () => {
  loading.value = true
  try {
    treeData.value = await loadDirectory('/')
  } finally {
    loading.value = false
  }
}

// 处理节点选择
const handleNodeSelect = (node: TreeNodeData) => {
  if (node.accessible) {
    selectedPath.value = node.path
    manualPath.value = node.path
    emit('select', node.path)
  }
}

// 处理节点展开/收起
const handleNodeToggle = async (node: TreeNodeData) => {
  if (!node.loaded) {
    const children = await loadDirectory(node.path)
    node.children = children
    node.loaded = true
  }
  node.expanded = !node.expanded
}

// 手动输入路径
const handleManualInput = () => {
  const path = manualPath.value.trim()
  if (!path) {
    message.warning('请输入目录路径')
    return
  }

  if (!path.startsWith('/')) {
    message.warning('请输入绝对路径（以/开头）')
    return
  }

  selectedPath.value = path
  emit('select', path)
}

onMounted(() => {
  loadRoot()
})
</script>

<style scoped>
.directory-browser {
  width: 100%;
}

.selected-path {
  margin-bottom: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}

.path-icon {
  margin-right: var(--spacing-sm);
  font-size: 18px;
}

.path-text {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--primary);
}

.tree-container {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  max-height: 400px;
  overflow-y: auto;
  padding: var(--spacing-sm);
}

.loading,
.empty {
  text-align: center;
  color: var(--text-secondary);
  padding: var(--spacing-lg);
}

.loading-spinner {
  display: inline-block;
  width: 24px;
  height: 24px;
  border: 2px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: var(--spacing-sm);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-icon {
  font-size: 32px;
  margin-bottom: var(--spacing-sm);
  opacity: 0.5;
}

.manual-input {
  margin-top: var(--spacing-lg);
}

.input-group {
  display: flex;
  gap: var(--spacing-sm);
}
</style>
