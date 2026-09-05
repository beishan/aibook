export interface SeriesSummary {
  name: string
  bookCount: number
  finishedCount: number
}

export interface SeriesBook {
  id: number
  title: string
  author?: string
  coverUrl?: string
  format: string
  seriesIndex: number | null
  readingStatus: string
}

export function volumeLabel(index: number | null | undefined): string {
  return index == null ? '卷序待定' : `第 ${index} 卷`
}

/** 仅推测现有最大整数卷号之前的空缺，不猜测全系列总卷数。 */
export function missingVolumeRanges(books: SeriesBook[]): string[] {
  const indices = new Set(books.map(book => book.seriesIndex)
    .filter((index): index is number => index != null && Number.isInteger(index) && index >= 1 && index <= 9999))
  const maximum = Math.max(0, ...indices)
  const ranges: string[] = []
  for (let index = 1; index < maximum; index++) {
    if (indices.has(index)) continue
    const start = index
    while (index + 1 < maximum && !indices.has(index + 1)) index++
    ranges.push(start === index ? String(start) : `${start}–${index}`)
  }
  return ranges
}

/** 同卷的不同记录不推荐为下一卷；卷序未知时不猜测。 */
export function nextSeriesBook(books: SeriesBook[], currentId: number): SeriesBook | undefined {
  const current = books.find(book => book.id === currentId)
  if (current?.seriesIndex == null) return undefined
  return books.filter(book => book.seriesIndex != null && book.seriesIndex > current.seriesIndex!)
    .sort((a, b) => a.seriesIndex! - b.seriesIndex! || a.id - b.id)[0]
}
