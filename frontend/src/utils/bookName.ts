export type BookNameMaskMode = 'HEAD_TAIL' | 'PREFIX'
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
): string {
  const text = title || ''
  if (!hidden) return text
  const characters = Array.from(text)
  if (mode === 'PREFIX') {
    const count = normalizeBookNamePrefixLength(prefixLength)
    return characters.slice(0, count).join('') + '*'.repeat(Math.max(0, characters.length - count))
  }
  if (characters.length <= 2) return text
  return characters[0] + '*'.repeat(characters.length - 2) + characters[characters.length - 1]
}
