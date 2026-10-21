/** Keep Unicode characters intact, including supplementary Chinese characters. */
export function maskCrawlerBookTitle(title: string, hidden: boolean): string {
  const characters = Array.from(title)
  if (!hidden || characters.length <= 2) return title
  return characters[0] + '*'.repeat(characters.length - 2) + characters[characters.length - 1]
}
