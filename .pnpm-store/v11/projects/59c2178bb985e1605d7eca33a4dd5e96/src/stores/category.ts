import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import api from '@/utils/api'

export interface Category {
  id: number
  name: string
  description?: string
  parentId?: number
  sortOrder: number
  builtIn: boolean
  enabled: boolean
  directBookCount: number
  bookCount: number
  children: Category[]
  createdAt: string
  updatedAt?: string
}

export interface CategoryPayload {
  name: string
  description?: string
  parentId?: number
  sortOrder?: number
  enabled?: boolean
}

export const useCategoryStore = defineStore('category', () => {
  const categories = ref<Category[]>([])
  const categoryTree = ref<Category[]>([])
  const loading = ref(false)

  const flatTree = computed(() => {
    const result: Array<Category & { depth: number; path: string }> = []
    const visit = (nodes: Category[], depth: number, parentPath: string) => {
      nodes.forEach((node) => {
        const path = parentPath ? `${parentPath} / ${node.name}` : node.name
        result.push({ ...node, depth, path })
        visit(node.children || [], depth + 1, path)
      })
    }
    visit(categoryTree.value, 0, '')
    return result
  })

  async function fetchCategories() {
    const response = await api.get('/api/categories')
    categories.value = response.data
    return response.data as Category[]
  }

  async function fetchTree() {
    loading.value = true
    try {
      const response = await api.get('/api/categories/tree')
      categoryTree.value = response.data
      return response.data as Category[]
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    // 先让树接口完成首次预置初始化，避免两个并发请求重复初始化空分类库。
    await fetchTree()
    await fetchCategories()
  }

  async function initializePresets() {
    loading.value = true
    try {
      const response = await api.post('/api/categories/presets/initialize')
      categoryTree.value = response.data
      await fetchCategories()
      return response.data as Category[]
    } finally {
      loading.value = false
    }
  }

  async function createCategory(payload: CategoryPayload) {
    const response = await api.post('/api/categories', payload)
    await refresh()
    return response.data as Category
  }

  async function updateCategory(id: number, payload: CategoryPayload) {
    const response = await api.put(`/api/categories/${id}`, payload)
    await refresh()
    return response.data as Category
  }

  async function moveCategory(id: number, parentId?: number, sortOrder?: number) {
    const response = await api.patch(`/api/categories/${id}/move`, {
      parentId,
      sortOrder,
    })
    await refresh()
    return response.data as Category
  }

  async function deleteCategory(id: number, targetCategoryId?: number) {
    await api.delete(`/api/categories/${id}`, {
      params: targetCategoryId ? { targetCategoryId } : {},
    })
    await refresh()
  }

  async function mergeCategory(id: number, targetCategoryId: number) {
    const response = await api.post(`/api/categories/${id}/merge`, { targetCategoryId })
    await refresh()
    return response.data as Category
  }

  return {
    categories,
    categoryTree,
    flatTree,
    loading,
    fetchCategories,
    fetchTree,
    refresh,
    initializePresets,
    createCategory,
    updateCategory,
    moveCategory,
    deleteCategory,
    mergeCategory,
  }
})
