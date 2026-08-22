import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'
import type { Tag } from '@/stores/tag'

export interface Book {
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
  sourceType?: 'UPLOAD' | 'DIRECTORY_SCAN'
  sourcePath?: string
  fileSize?: number
  language?: string
  rating?: number
  readingStatus: string
  categoryId?: number
  categoryName?: string
  categoryPath?: string
  tags: Tag[]
  tagNames: string[]
  isFavorite: boolean
  isWanted: boolean
  onShelf: boolean
  shelfGroupId?: number
  shelfAddedAt?: string
  shelfSortOrder?: number
  notes?: string
  chapterInfo?: string
  chapterCount?: number
  deletedAt?: string
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

export interface BookVersionRebuildTask {
  taskId: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED'
  message: string
  totalBooks: number
  processedBooks: number
  matchedGroups: number
  completedGroups: number
  mergedBooks: number
  aggregatedVersions: number
  skippedBooks: number
  failedBooks: number
  currentBookTitle?: string
  startedAt: number
  finishedAt: number
  elapsedMs: number
  errors: string[]
}

export const useBookStore = defineStore('book', () => {
  const books = ref<Book[]>([])
  const currentBook = ref<Book | null>(null)
  const loading = ref(false)
  const totalElements = ref(0)
  const currentPage = ref(0)
  const pageSize = ref(10)
  const trashCount = ref(0)

  // 获取书籍列表
  async function fetchBooks(
    page = 0,
    size = 10,
    sortBy = 'createdAt',
    sortDir = 'desc',
    filters: {
      format?: string
      status?: string
      categoryId?: number
      includeChildren?: boolean
      tagId?: number
    } = {},
  ) {
    loading.value = true
    try {
      const response = await api.get('/api/books', {
        params: { page, size, sortBy, sortDir, ...filters },
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
    const response = await api.get(`/api/books/${id}`)
    currentBook.value = response.data
    return response.data
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

  async function updateReadingStatus(id: number, status: 'UNREADING' | 'READING' | 'FINISHED') {
    const response = await api.put(`/api/books/${id}/status`, { status })
    const updatedBook: Book = response.data
    const index = books.value.findIndex((book) => book.id === id)
    if (index !== -1) {
      books.value[index] = updatedBook
    }
    if (currentBook.value?.id === id) {
      currentBook.value = updatedBook
    }
    return updatedBook
  }

  async function addToShelf(id: number, groupId?: number) {
    const response = await api.post(`/api/shelf/books/${id}`, { groupId: groupId || null })
    const updatedBook: Book = response.data
    updateLocalBook(updatedBook)
    return updatedBook
  }

  async function removeFromShelf(id: number) {
    const response = await api.delete(`/api/shelf/books/${id}`)
    const updatedBook: Book = response.data
    updateLocalBook(updatedBook)
    return updatedBook
  }

  // 移入系统回收站（NAS 原始文件不作改动）
  async function deleteBook(id: number) {
    await api.delete(`/api/books/${id}`)
    books.value = books.value.filter((b) => b.id !== id)
    totalElements.value = Math.max(0, totalElements.value - 1)
    trashCount.value++
  }

  async function fetchTrash(page = 0, size = 20, keyword = '') {
    const response = await api.get('/api/books/trash', {
      params: { page, size, keyword },
    })
    return response.data as BookPage
  }

  async function fetchTrashCount() {
    const response = await api.get('/api/books/trash/count')
    trashCount.value = Number(response.data.count || 0)
    return trashCount.value
  }

  async function moveBooksToTrash(bookIds: number[]) {
    await api.post('/api/books/trash/move', { bookIds })
    const removed = new Set(bookIds)
    books.value = books.value.filter(book => !removed.has(book.id))
    totalElements.value = Math.max(0, totalElements.value - removed.size)
    trashCount.value += removed.size
  }

  async function restoreTrashBooks(bookIds: number[]) {
    const response = await api.post('/api/books/trash/restore', { bookIds })
    trashCount.value = Math.max(0, trashCount.value - bookIds.length)
    return response.data as Book[]
  }

  async function permanentlyRemoveTrashBooks(bookIds: number[]) {
    await api.post('/api/books/trash/permanent', { bookIds })
    trashCount.value = Math.max(0, trashCount.value - bookIds.length)
  }

  async function emptyTrash() {
    await api.delete('/api/books/trash')
    trashCount.value = 0
  }

  // 更新书籍元数据
  async function updateBookMetadata(id: number, metadata: Partial<Book>) {
    const response = await api.put(`/api/books/${id}/metadata`, metadata)
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

  async function updateBookCategory(id: number, categoryId?: number) {
    const response = await api.put(`/api/books/${id}/category`, {
      categoryId: categoryId || null,
    })
    const updatedBook = response.data
    const index = books.value.findIndex((book) => book.id === id)
    if (index !== -1) {
      books.value[index] = updatedBook
    }
    if (currentBook.value?.id === id) {
      currentBook.value = updatedBook
    }
    return updatedBook
  }

  async function updateBookCategories(bookIds: number[], categoryId?: number) {
    const response = await api.put('/api/books/batch/category', {
      bookIds,
      categoryId: categoryId || null,
    })
    const updatedBooks: Book[] = response.data
    const updatedById = new Map(updatedBooks.map((book) => [book.id, book]))
    books.value = books.value.map((book) => updatedById.get(book.id) || book)
    return updatedBooks
  }

  async function updateBookTags(id: number, tagIds: number[]) {
    const response = await api.put(`/api/books/${id}/tags`, { tagIds })
    const updatedBook: Book = response.data
    const index = books.value.findIndex((book) => book.id === id)
    if (index !== -1) {
      books.value[index] = updatedBook
    }
    if (currentBook.value?.id === id) {
      currentBook.value = updatedBook
    }
    return updatedBook
  }

  async function updateBookTagsBatch(
    bookIds: number[],
    tagIds: number[],
    mode: 'ADD' | 'REMOVE' | 'REPLACE' = 'ADD',
  ) {
    const response = await api.put('/api/books/batch/tags', { bookIds, tagIds, mode })
    const updatedBooks: Book[] = response.data
    const updatedById = new Map(updatedBooks.map((book) => [book.id, book]))
    books.value = books.value.map((book) => updatedById.get(book.id) || book)
    return updatedBooks
  }

  async function reparseBook(id: number) {
    const response = await api.post(`/api/books/${id}/reparse`)
    const updatedBook: Book = response.data.book
    updateLocalBook(updatedBook)
    return response.data
  }

  async function startBookVersionRebuild() {
    const response = await api.post('/api/books/versions/rebuild')
    return response.data as BookVersionRebuildTask
  }

  async function getBookVersionRebuildTask(taskId: string) {
    const response = await api.get(`/api/books/versions/rebuild/${taskId}`)
    return response.data as BookVersionRebuildTask
  }

  async function uploadBookCover(id: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post(`/api/books/${id}/cover-upload`, formData)
    const updatedBook: Book = response.data
    updateLocalBook(updatedBook)
    return updatedBook
  }

  async function randomizeBookCover(id: number) {
    const response = await api.post(`/api/books/${id}/random-cover`, undefined, {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    const updatedBook: Book = response.data
    updateLocalBook(updatedBook)
    return updatedBook
  }

  function updateLocalBook(updatedBook: Book) {
    const index = books.value.findIndex(book => book.id === updatedBook.id)
    if (index !== -1) {
      books.value[index] = updatedBook
    }
    if (currentBook.value?.id === updatedBook.id) {
      currentBook.value = updatedBook
    }
  }

  return {
    books,
    currentBook,
    loading,
    totalElements,
    currentPage,
    pageSize,
    trashCount,
    fetchBooks,
    searchBooks,
    fetchBookById,
    toggleFavorite,
    toggleWanted,
    updateReadingStatus,
    addToShelf,
    removeFromShelf,
    deleteBook,
    fetchTrash,
    fetchTrashCount,
    moveBooksToTrash,
    restoreTrashBooks,
    permanentlyRemoveTrashBooks,
    emptyTrash,
    updateBookMetadata,
    updateBookCategory,
    updateBookCategories,
    updateBookTags,
    updateBookTagsBatch,
    reparseBook,
    startBookVersionRebuild,
    getBookVersionRebuildTask,
    uploadBookCover,
    randomizeBookCover,
  }
})
