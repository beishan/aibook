import { getCoverUrl } from '@/utils/cover'

export interface ReaderBackgroundDto {
  id: number
  name: string
  imageUrl: string
  contentType: string
  fileSize: number
  createdAt: string
}

export interface ReaderBackgroundOption {
  id: string
  name: string
  imageUrl: string
  builtIn: boolean
  fileSize?: number
  customId?: number
}

export const BUILT_IN_READER_BACKGROUNDS: ReaderBackgroundOption[] = [
  { id: 'warm-paper', name: '暖阳纸张', imageUrl: '/reader-backgrounds/warm-paper.svg', builtIn: true },
  { id: 'rice-paper', name: '宣纸纤维', imageUrl: '/reader-backgrounds/rice-paper.svg', builtIn: true },
  { id: 'sage-linen', name: '鼠尾草亚麻', imageUrl: '/reader-backgrounds/sage-linen.svg', builtIn: true },
  { id: 'mist-blue', name: '远山薄雾', imageUrl: '/reader-backgrounds/mist-blue.svg', builtIn: true },
]

export const toReaderBackgroundOption = (background: ReaderBackgroundDto): ReaderBackgroundOption => ({
  id: `custom-${background.id}`,
  customId: background.id,
  name: background.name,
  imageUrl: getCoverUrl(background.imageUrl),
  builtIn: false,
  fileSize: background.fileSize,
})
