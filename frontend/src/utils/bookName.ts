export type BookNameMaskMode = 'HEAD_TAIL' | 'PREFIX'
export const DEFAULT_BOOK_NAME_MASK_CHARACTER = '*'
const maskCharacterSegmenter = new Intl.Segmenter('zh-CN', { granularity: 'grapheme' })

export function isBookNameMaskCharacter(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= 64
    && !/[\s\p{Cc}\p{Cs}]/u.test(value)
    && !/^[\p{M}\p{Cf}]+$/u.test(value)
    && Array.from(maskCharacterSegmenter.segment(value)).length === 1
}

export function normalizeBookNameMaskCharacter(value: unknown): string {
  return isBookNameMaskCharacter(value) ? value : DEFAULT_BOOK_NAME_MASK_CHARACTER
}

export const DEFAULT_BOOK_NAME_PREFIX_LENGTH = 2
export const MAX_BOOK_NAME_PREFIX_LENGTH = 100

export function normalizeBookNamePrefixLength(value: unknown): number {
  return typeof value === 'number' && Number.isInteger(value)
    && value >= 1 && value <= MAX_BOOK_NAME_PREFIX_LENGTH
    ? value : DEFAULT_BOOK_NAME_PREFIX_LENGTH
}

/** Operate on Unicode code points so supplementary Chinese characters stay intact. */
export function maskBookName(
  title: string | null | undefined,
  hidden: boolean,
  mode: BookNameMaskMode = 'HEAD_TAIL',
  prefixLength = DEFAULT_BOOK_NAME_PREFIX_LENGTH,
  maskCharacter = DEFAULT_BOOK_NAME_MASK_CHARACTER,
): string {
  const text = title || ''
  if (!hidden) return text
  const characters = Array.from(text)
  const replacement = normalizeBookNameMaskCharacter(maskCharacter)
  if (mode === 'PREFIX') {
    const count = normalizeBookNamePrefixLength(prefixLength)
    return characters.slice(0, count).join('') + replacement.repeat(Math.max(0, characters.length - count))
  }
  if (characters.length <= 2) return text
  return characters[0] + replacement.repeat(characters.length - 2) + characters[characters.length - 1]
}
