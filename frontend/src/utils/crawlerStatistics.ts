export type StatisticsGranularity = 'day' | 'hour' | 'minute'

export const statisticsRanges: Record<StatisticsGranularity, readonly number[]> = {
  day: [7, 30, 90],
  hour: [24, 48, 72],
  minute: [30, 60, 180],
}

export function parseStatisticsWindow(value: unknown): {
  granularity: StatisticsGranularity
  range: number
} | undefined {
  if (typeof value !== 'string') return
  const [unit, count, extra] = value.split(':')
  if (extra !== undefined || !Object.prototype.hasOwnProperty.call(statisticsRanges, unit)) return
  const granularity = unit as StatisticsGranularity
  const range = Number(count)
  if (count !== String(range) || !statisticsRanges[granularity].includes(range)) return
  return { granularity, range }
}

export function statisticsTimeLabel(date: string, granularity: StatisticsGranularity): string {
  if (granularity === 'day') return date.slice(5, 10).replace('-', '/')
  const time = date.slice(11, 16)
  return granularity === 'minute' ? time : `${date.slice(5, 10).replace('-', '/')} ${time}`
}
