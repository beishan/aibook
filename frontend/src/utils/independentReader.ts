import type { Router } from 'vue-router'
import type { Book } from '@/stores/book'

/** 在用户点击事件中同步打开标签页，避免被浏览器当作弹窗拦截。 */
export function openIndependentReader(router: Router, book: Book) {
  if (['cbz', 'cbr'].includes(book.format.toLowerCase())) return
  const href = router.resolve({
    name: 'Reader',
    params: { id: book.id },
    query: { quickWindow: '1', independentWindow: '1' },
  }).href
  window.open(href, '_blank', 'noopener,noreferrer')
}
