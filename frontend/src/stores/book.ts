import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'

interface Book {
  id: number
  title: string
  author?: string
  isbn?: string
  publisher?: string
  publishDate?: string
  description?: string
  coverUrl?: string
  format: string
  filePath: string
  fileSize?: number
  language?: string
  rating?: number
  readingStatus: string
  categoryName?: string
  tagNames: string[]
  isFavorite: boolean
  isWanted: boolean
  notes?: string
  chapterInfo?: string
  createdAt: string
  updatedAt: string
}

interface BookPage {
  content: Book[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const useBookStore = defineStore('book', () => {
  const books = ref<Book[]>([])
  const currentBook = ref<Book | null>(null)
  const loading = ref(false)
  const totalElements = ref(0)
  const currentPage = ref(0)
  const pageSize = ref(10)

  // 获取书籍列表
  async function fetchBooks(page = 0, size = 10, sortBy = 'createdAt', sortDir = 'desc') {
    loading.value = true
    try {
      const response = await api.get('/api/books', {
        params: { page, size, sortBy, sortDir },
      })
      const data: BookPage = response.data
      books.value = data.content
      totalElements.value = data.totalElements
      currentPage.value = data.number
      pageSize.value = data.size
      return data
    } finally {
      loading.value = false
    }
  }

  // 搜索书籍
  async function searchBooks(keyword: string, page = 0, size = 10) {
    loading.value = true
    try {
      const response = await api.get('/api/books/search', {
        params: { keyword, page, size },
      })
      const data: BookPage = response.data
      books.value = data.content
      totalElements.value = data.totalElements
      currentPage.value = data.number
      pageSize.value = data.size
      return data
    } finally {
      loading.value = false
    }
  }

  // 获取书籍详情
  async function fetchBookById(id: number) {
    loading.value = true
    try {
      const response = await api.get(`/api/books/${id}`)
      currentBook.value = response.data
      return response.data
    } finally {
      loading.value = false
    }
  }

  // 切换收藏状态
  async function toggleFavorite(id: number) {
    const response = await api.put(`/api/books/${id}/favorite`)
    const updatedBook = response.data
    const index = books.value.findIndex((b) => b.id === id)
    if (index !== -1) {
      books.value[index] = updatedBook
    }
    if (currentBook.value?.id === id) {
      currentBook.value = updatedBook
    }
    return updatedBook
  }

  // 切换想读状态
  async function toggleWanted(id: number) {
    const response = await api.put(`/api/books/${id}/wanted`)
    const updatedBook = response.data
    const index = books.value.findIndex((b) => b.id === id)
    if (index !== -1) {
      books.value[index] = updatedBook
    }
    if (currentBook.value?.id === id) {
      currentBook.value = updatedBook
    }
    return updatedBook
  }

  // 删除书籍
  async function deleteBook(id: number) {
    await api.delete(`/api/books/${id}`)
    books.value = books.value.filter((b) => b.id !== id)
    totalElements.value--
  }

  // 更新书籍元数据
  async function updateBookMetadata(id: number, metadata: Partial<Book>) {
    const response = await api.put(`/api/books/${id}`, metadata)
    const updatedBook = response.data
    const index = books.value.findIndex((b) => b.id === id)
    if (index !== -1) {
      books.value[index] = updatedBook
    }
    if (currentBook.value?.id === id) {
      currentBook.value = updatedBook
    }
    return updatedBook
  }

  return {
    books,
    currentBook,
    loading,
    totalElements,
    currentPage,
    pageSize,
    fetchBooks,
    searchBooks,
    fetchBookById,
    toggleFavorite,
    toggleWanted,
    deleteBook,
    updateBookMetadata,
  }
})
