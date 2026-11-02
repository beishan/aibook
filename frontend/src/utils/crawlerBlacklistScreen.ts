export type BlacklistScreenScope = 'MANAGED' | 'DISCOVERED'

export interface BlacklistScreenJob {
  scope: BlacklistScreenScope
  id: string
  siteId: number | null
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  total: number
  processed: number
  matched: number
  errorMessage: string | null
  createdAt: string
}

export interface BlacklistMatchDetail {
  type: 'KEYWORD' | 'TAG'
  field: string
  value: string
  evidence: string
}

export interface BlacklistScreenItem {
  matchDetails?: BlacklistMatchDetail[]
  bookId: number
  bookName: string
  author: string
  siteName: string
  reason: string
  blacklisted: boolean
  available: boolean
}
