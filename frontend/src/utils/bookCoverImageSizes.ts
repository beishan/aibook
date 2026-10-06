import { reactive } from 'vue'
import api from '@/utils/api'

export type BookCoverView = 'card' | 'list' | 'detail'
export type BookCoverImageSize = '96' | '160' | '320' | '640' | 'original'

export interface BookCoverImageSizeSettings {
  card: BookCoverImageSize
  list: BookCoverImageSize
  detail: BookCoverImageSize
}

export const bookCoverImageSizeOptions: Array<{
  value: BookCoverImageSize
  label: string
  description: string
}> = [
  { value: '96', label: '小 · 96 px', description: '适合紧凑列表' },
  { value: '160', label: '标准 · 160 px', description: '适合常规卡片' },
  { value: '320', label: '清晰 · 320 px', description: '适合较大卡片与详情' },
  { value: '640', label: '高清 · 640 px', description: '适合高分辨率大图' },
  { value: 'original', label: '原图', description: '不生成缩略图' },
]

export const defaultBookCoverImageSizes: BookCoverImageSizeSettings = {
  card: '320',
  list: '96',
  detail: '320',
}

export const bookCoverImageSizes = reactive<BookCoverImageSizeSettings>({
  ...defaultBookCoverImageSizes,
})

const isBookCoverImageSize = (value: unknown): value is BookCoverImageSize =>
  typeof value === 'string'
  && bookCoverImageSizeOptions.some(option => option.value === value)

const normalizeSettings = (value: Partial<BookCoverImageSizeSettings> | null | undefined) => ({
  card: isBookCoverImageSize(value?.card) ? value.card : defaultBookCoverImageSizes.card,
  list: isBookCoverImageSize(value?.list) ? value.list : defaultBookCoverImageSizes.list,
  detail: isBookCoverImageSize(value?.detail) ? value.detail : defaultBookCoverImageSizes.detail,
})

export const hydrateBookCoverImageSizes = async () => {
  Object.assign(bookCoverImageSizes, defaultBookCoverImageSizes)
  if (!localStorage.getItem('token')) return
  try {
    const { data } = await api.get<BookCoverImageSizeSettings>(
      '/api/cover-privacy/book-image-sizes',
      { headers: { 'X-Suppress-Error-Toast': 'true' } },
    )
    Object.assign(bookCoverImageSizes, normalizeSettings(data))
  } catch (error) {
    console.error('Failed to load book cover image sizes:', error)
  }
}

export const saveBookCoverImageSizes = async (
  settings: BookCoverImageSizeSettings,
) => {
  const { data } = await api.put<BookCoverImageSizeSettings>(
    '/api/cover-privacy/book-image-sizes',
    normalizeSettings(settings),
  )
  Object.assign(bookCoverImageSizes, normalizeSettings(data))
  return { ...bookCoverImageSizes }
}

export const resetBookCoverImageSizes = () => {
  Object.assign(bookCoverImageSizes, defaultBookCoverImageSizes)
}
