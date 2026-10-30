export const navigationMenuOptions = [
  { path: '/', title: '首页' },
  { path: '/books', title: '书库' },
  { path: '/tools', title: '工具（内容修复、重写、格式转换）' },
  { path: '/shelf', title: '书架' },
  { path: '/libraries', title: '图书馆' },
  { path: '/recommendations', title: '推荐阅读' },
  { path: '/crawler', title: '书籍爬虫' },
  { path: '/statistics', title: '阅读统计' },
  { path: '/settings', title: '设置' },
]
export const defaultNavigationMenuOrder = () => navigationMenuOptions.map(item => item.path)
export function normalizeNavigationMenuOrder(value: unknown): string[] {
  const defaults = defaultNavigationMenuOrder()
  const known = Array.isArray(value) ? value.filter(path => defaults.includes(path)) : []
  return [...new Set([...known, ...defaults])]
}
export function sortNavigationMenu<T extends { path: string }>(items: T[], order: string[]): T[] {
  const normalized = normalizeNavigationMenuOrder(order)
  const rank = (path: string) => normalized.indexOf(
    ['/rewrite', '/text-repair', '/format-conversion'].includes(path) ? '/tools' : path,
  )
  return [...items].sort((left, right) => rank(left.path) - rank(right.path))
}
