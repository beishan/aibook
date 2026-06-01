import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'

interface Tag {
  id: number
  name: string
  color?: string
  createdAt: string
}

export const useTagStore = defineStore('tag', () => {
  const tags = ref<Tag[]>([])
  const loading = ref(false)

  // 获取所有标签
  async function fetchTags() {
    loading.value = true
    try {
      const response = await api.get('/api/tags')
      tags.value = response.data
      return response.data
    } finally {
      loading.value = false
    }
  }

  // 创建标签
  async function createTag(name: string, color?: string) {
    const response = await api.post('/api/tags', { name, color })
    tags.value.push(response.data)
    return response.data
  }

  // 更新标签
  async function updateTag(id: number, name: string, color?: string) {
    const response = await api.put(`/api/tags/${id}`, { name, color })
    const index = tags.value.findIndex((t) => t.id === id)
    if (index !== -1) {
      tags.value[index] = response.data
    }
    return response.data
  }

  // 删除标签
  async function deleteTag(id: number) {
    await api.delete(`/api/tags/${id}`)
    tags.value = tags.value.filter((t) => t.id !== id)
  }

  return {
    tags,
    loading,
    fetchTags,
    createTag,
    updateTag,
    deleteTag,
  }
})
