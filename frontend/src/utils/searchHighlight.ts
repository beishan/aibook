import { match } from 'pinyin-pro'

/** 搜索高亮分段：hit 为 true 的片段需要强调展示。 */
export interface HighlightSegment {
  text: string
  hit: boolean
}

/**
 * 计算关键词在文本中的高亮区间。
 *
 * 匹配策略与后端检索保持一致：
 * 1. 直接子串匹配（大小写不敏感，命中全部出现位置）；
 * 2. 无直接命中且关键词为拉丁字符时，按拼音反查（支持全拼、首字母、
 *    混合缩写，如输入 sgyy 高亮“三国演义”），基于 pinyin-pro 的 match。
 */
export function findHighlightRanges(text: string, keyword: string): Array<[number, number]> {
  const kw = keyword.trim()
  if (!kw || !text) {
    return []
  }

  // 1) 直接子串匹配
  const lowerText = text.toLocaleLowerCase()
  const lowerKw = kw.toLocaleLowerCase()
  const ranges: Array<[number, number]> = []
  if (lowerKw) {
    let from = 0
    let idx = lowerText.indexOf(lowerKw, from)
    while (idx >= 0) {
      ranges.push([idx, idx + lowerKw.length])
      from = idx + lowerKw.length
      idx = lowerText.indexOf(lowerKw, from)
    }
  }
  if (ranges.length) {
    return ranges
  }

  // 2) 拼音反查（仅拉丁关键词，去空格后与后端拼音索引归一方式一致）
  const pinyinKw = kw.replace(/\s+/g, '')
  if (!pinyinKw || !/^[\x20-\x7e]+$/.test(kw)) {
    return []
  }
  const hits = match(text, pinyinKw)
  if (!hits || !hits.length) {
    return []
  }

  // 将命中的字符下标合并为连续区间
  const merged: Array<[number, number]> = []
  let start = -1
  for (let i = 0; i <= hits.length; i += 1) {
    const cur = i < hits.length ? hits[i] : -2
    const prev = i > 0 ? hits[i - 1] : -2
    if (cur !== prev + 1) {
      if (start >= 0) {
        merged.push([start, prev + 1])
      }
      start = cur
    }
  }
  return merged
}

/** 将文本按高亮区间切分为分段列表；无命中时返回整段普通文本。 */
export function highlightSegments(text: string, keyword: string): HighlightSegment[] {
  const ranges = findHighlightRanges(text, keyword)
  if (!ranges.length) {
    return [{ text, hit: false }]
  }
  const segments: HighlightSegment[] = []
  let cursor = 0
  for (const [start, end] of ranges) {
    if (start > cursor) {
      segments.push({ text: text.slice(cursor, start), hit: false })
    }
    segments.push({ text: text.slice(start, end), hit: true })
    cursor = end
  }
  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor), hit: false })
  }
  return segments
}
