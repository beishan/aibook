import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'

export interface Tag {
  id: number
  name: string
  color: string
  sourceType?: string
  sourceDetail?: string
  bookCount?: number
  createdAt?: string
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
  async function createTag(name: string, color = '#64748B') {
    const response = await api.post('/api/tags', { name, color })
    tags.value.push(response.data)
    tags.value.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
    return response.data
  }

  // 更新标签
  async function updateTag(id: number, name: string, color = '#64748B') {
    const response = await api.put(`/api/tags/${id}`, { name, color })
    const index = tags.value.findIndex((t) => t.id === id)
    if (index !== -1) {
      tags.value[index] = response.data
    }
    tags.value.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
    return response.data
  }

  // 删除标签
  async function deleteTag(id: number) {
    await api.delete(`/api/tags/${id}`)
    tags.value = tags.value.filter((t) => t.id !== id)
  }

  async function scanLibraryPage(page: number, size = 100) {
    const response = await api.get('/api/tags/library-scan', { params: { page, size } })
    return response.data as {
      tags: string[]
      scannedBooks: number
      totalBooks: number
      complete: boolean
    }
  }

  async function addScannedTags(names: string[]) {
    const response = await api.post('/api/tags/library-scan/import', { names })
    await fetchTags()
    return response.data as Tag[]
  }

  async function deleteTags(ids: number[]) {
    const response = await api.post('/api/tags/batch-delete', { ids })
    const deletedIds = new Set(ids)
    tags.value = tags.value.filter(tag => !deletedIds.has(tag.id))
    return response.data as { deletedCount: number }
  }

  async function fetchPendingTags() {
    const response = await api.get('/api/tags/pending')
    return response.data as Tag[]
  }

  async function managePendingTags(ids: number[], action: 'approve' | 'delete') {
    await api.post('/api/tags/pending/' + action, { ids })
    await fetchTags()
  }

  return {
    fetchPendingTags,
    managePendingTags,
    tags,
    loading,
    fetchTags,
    createTag,
    updateTag,
    deleteTag,
    scanLibraryPage,
    addScannedTags,
    deleteTags,
  }
})
