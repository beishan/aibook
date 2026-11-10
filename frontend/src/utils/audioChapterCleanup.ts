export interface ChapterCleanupPreview {
  index: number
  before: string
  after: string
}

export function previewChapterCleanup(
  titles: string[],
  text: string,
): ChapterCleanupPreview[] {
  if (!text) return []
  return titles.flatMap((title, index) => {
    if (!title.includes(text)) return []
    return [{
      index,
      before: title,
      after: title.split(text).join('').trim(),
    }]
  })
}
