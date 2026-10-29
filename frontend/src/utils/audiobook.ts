import api from '@/utils/api'

export interface AudioTrack {
  id: number
  title: string
  durationMs: number
  sortOrder: number
  mimeType: string
  available?: boolean
}
export interface AudioProgress {
  revision: number
  trackId?: number
  positionMs: number
  status: 'NEW' | 'LISTENING' | 'FINISHED'
}
export interface AudioChapter {
  id: number
  title: string
  segments: Array<{ trackId: number; startMs: number; endMs: number }>
}
export interface AudioBookmark {
  id: number
  trackId: number
  positionMs: number
  title: string
}
export interface Audiobook {
  id: number
  bookId: number
  title: string
  author: string
  name: string
  narrator: string
  language: string
  description: string
  coverUrl?: string
  coverHidden?: boolean
  ebookFormat?: string
  durationMs: number
  contentRevision: number
  tracks: AudioTrack[]
  chapters: AudioChapter[]
  bookmarks: AudioBookmark[]
  progress: AudioProgress
  editions: Array<{ id: number; name: string; narrator: string }>
}
export interface AudioSettings {
  speed: number
  backSeconds: number
  forwardSeconds: number
  sleepMinutes: number
}
export const audioApi = {
  async detail(id: number): Promise<Audiobook> {
    return (await api.get(`/api/audiobooks/${id}`)).data
  },
  async list(params: Record<string, unknown> = {}) {
    return (await api.get('/api/audiobooks', { params })).data
  },
  async bookmark(id: number, bookmark: Omit<AudioBookmark, 'id'>) {
    await api.post(`/api/audiobooks/${id}/bookmarks`, bookmark)
  },
}
export function audioTime(ms: number): string {
  const seconds = Math.floor(Math.max(0, ms || 0) / 1000)
  const minutes = Math.floor(seconds / 60)
  return minutes >= 60
    ? `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
    : `${minutes}:${String(seconds % 60).padStart(2, '0')}`
}
export function contentPosition(book: Audiobook, trackId: number, positionMs: number) {
  let offset = 0
  for (const track of book.tracks) {
    if (track.id === trackId) return offset + Math.min(track.durationMs, Math.max(0, positionMs))
    offset += track.durationMs
  }
  return 0
}
