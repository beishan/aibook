import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'

interface Category {
  id: number
  name: string
  description?: string
  parentId?: number
  sortOrder: number
  createdAt: string
}

export const useCategoryStore = defineStore('category', () => {
  const categories = ref<Category[]>([])
  const loading = ref(false)

  // 获取所有分类
  async function fetchCategories() {
    loading.value = true
    try {
      const response = await api.get('/api/categories')
      categories.value = response.data
      return response.data
    } finally {
      loading.value = false
    }
  }

  // 获取子分类
  async function fetchSubCategories(parentId: number) {
    loading.value = true
    try {
      const response = await api.get(`/api/categories/${parentId}/subcategories`)
      return response.data
    } finally {
      loading.value = false
    }
  }

  // 创建分类
  async function createCategory(name: string, description?: string, parentId?: number) {
    const response = await api.post('/api/categories', {
      name,
      description,
      parentId,
    })
    categories.value.push(response.data)
    return response.data
  }

  // 更新分类
  async function updateCategory(id: number, name: string, description?: string) {
    const response = await api.put(`/api/categories/${id}`, { name, description })
    const index = categories.value.findIndex((c) => c.id === id)
    if (index !== -1) {
      categories.value[index] = response.data
    }
    return response.data
  }

  // 删除分类
  async function deleteCategory(id: number) {
    await api.delete(`/api/categories/${id}`)
    categories.value = categories.value.filter((c) => c.id !== id)
  }

  return {
    categories,
    loading,
    fetchCategories,
    fetchSubCategories,
    createCategory,
    updateCategory,
    deleteCategory,
  }
})
