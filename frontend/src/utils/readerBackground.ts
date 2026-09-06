import { getCoverUrl } from '@/utils/cover'
import warmPaperUrl from '@/assets/reader-backgrounds/warm-paper.svg'
import ricePaperUrl from '@/assets/reader-backgrounds/rice-paper.svg'
import sageLinenUrl from '@/assets/reader-backgrounds/sage-linen.svg'
import mistBlueUrl from '@/assets/reader-backgrounds/mist-blue.svg'

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
  { id: 'warm-paper', name: '暖阳纸张', imageUrl: warmPaperUrl, builtIn: true },
  { id: 'rice-paper', name: '宣纸纤维', imageUrl: ricePaperUrl, builtIn: true },
  { id: 'sage-linen', name: '鼠尾草亚麻', imageUrl: sageLinenUrl, builtIn: true },
  { id: 'mist-blue', name: '远山薄雾', imageUrl: mistBlueUrl, builtIn: true },
]

export const toReaderBackgroundOption = (background: ReaderBackgroundDto): ReaderBackgroundOption => ({
  id: `custom-${background.id}`,
  customId: background.id,
  name: background.name,
  imageUrl: getCoverUrl(background.imageUrl),
  builtIn: false,
  fileSize: background.fileSize,
})
