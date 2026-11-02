export interface BlacklistScreenJob {
  id: string
  siteId: number | null
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  total: number
  processed: number
  matched: number
  errorMessage: string | null
  createdAt: string
}

export interface BlacklistScreenItem {
  bookId: number
  bookName: string
  author: string
  siteName: string
  reason: string
  blacklisted: boolean
  available: boolean
}
