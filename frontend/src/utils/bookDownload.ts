import api from '@/utils/api'

interface BookDownloadOptions {
  bookId: number
  title: string
  format: string
  versionId?: number | null
}

const safeDownloadName = (title: string, format: string) => {
  const normalizedTitle = title.trim() || 'book'
  const normalizedFormat = format.trim().toLowerCase() || 'bin'
  return normalizedTitle.toLowerCase().endsWith(`.${normalizedFormat}`)
    ? normalizedTitle
    : `${normalizedTitle}.${normalizedFormat}`
}

export const downloadBookToLocal = async ({
  bookId,
  title,
  format,
  versionId,
}: BookDownloadOptions) => {
  const response = await api.get(`/api/books/${bookId}/content`, {
    params: versionId ? { versionId } : undefined,
    responseType: 'blob',
  })
  const downloadUrl = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = downloadUrl
  anchor.download = safeDownloadName(title, format)
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000)
}
