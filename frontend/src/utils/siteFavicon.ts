export interface SiteFaviconStatus {
  hasCustom: boolean
  url?: string
  version: number
}

export const DEFAULT_FAVICON_URL = '/favicon.svg'

export const faviconUrl = (status: SiteFaviconStatus) =>
  status.hasCustom && status.url
    ? `${status.url}?v=${status.version}`
    : DEFAULT_FAVICON_URL

export const applySiteFavicon = (url: string) => {
  let link = document.querySelector<HTMLLinkElement>('link[rel~="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.removeAttribute('type')
  link.href = url
}

export const loadSiteFavicon = async () => {
  try {
    const response = await fetch('/api/site/favicon/status', { cache: 'no-store' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const status = await response.json() as SiteFaviconStatus
    applySiteFavicon(faviconUrl(status))
  } catch {
    applySiteFavicon(DEFAULT_FAVICON_URL)
  }
}
