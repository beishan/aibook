import { usePreferencesStore } from '@/stores/preferences'
import { maskBookName } from '@/utils/bookName'

export function useBookNameDisplay() {
  const preferences = usePreferencesStore()
  const displayBookName = (title: string | null | undefined): string => {
    // Do not flash a full title before the current account's preferences arrive.
    if (!preferences.hydrated && typeof localStorage !== 'undefined' && localStorage.getItem('token')) {
      return '*'.repeat(Array.from(title || '').length)
    }
    return maskBookName(
      title,
      preferences.hideBookNames,
      preferences.bookNameMaskMode,
      preferences.bookNamePrefixLength,
    )
  }
  return { displayBookName }
}
