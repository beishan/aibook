const CHINA_TIME_ZONE = 'Asia/Shanghai'
const OFFSET_PATTERN = /(?:Z|[+-]\d{2}:?\d{2})$/i

/**
 * 后端历史接口使用不带偏移量的 LocalDateTime。此类值按中国时区解释，
 * 带 Z 或偏移量的标准时间则保留其原始瞬时时间。
 */
export const parseServerDateTime = (value: string) => {
  const normalized = value.trim().replace(' ', 'T')
  return new Date(OFFSET_PATTERN.test(normalized) ? normalized : `${normalized}+08:00`)
}

export const formatChinaDateTime = (
  value?: string,
  options: Intl.DateTimeFormatOptions = {}
) => {
  if (!value) return ''
  const date = parseServerDateTime(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    timeZone: CHINA_TIME_ZONE,
    ...options,
  })
}

export const formatChinaDate = (value?: string) =>
  formatChinaDateTime(value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
