import { usePreferencesStore } from '@/stores/preferences'
import { maskCrawlerBookTitle } from '@/utils/crawlerBookTitle'

/** Crawler titles are controlled exclusively by the crawler page preference. */
export function useCrawlerBookNameDisplay() {
  const preferences = usePreferencesStore()
  const displayCrawlerBookName = (title: string | null | undefined): string =>
    maskCrawlerBookTitle(title || '', preferences.crawlerHideBookNames)
  return { displayCrawlerBookName }
}
