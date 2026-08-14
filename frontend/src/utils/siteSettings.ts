import { reactive } from 'vue'
import type { ThemeId } from '@/types/theme'

export type LoginPageStyle = 'glass' | 'split' | 'minimal'

export interface WebsiteSettings {
  siteName: string
  browserTitle: string
  loginDescription: string
  registrationEnabled: boolean
  loginStyles: Record<ThemeId, LoginPageStyle>
  hasLoginIcon: boolean
  loginIconUrl?: string
  loginIconVersion: number
}

const defaultLoginStyles = (): Record<ThemeId, LoginPageStyle> => ({
  modern: 'glass',
  warm: 'glass',
  natural: 'glass',
  macos26: 'glass',
})

export const websiteSettings = reactive<WebsiteSettings>({
  siteName: '汗牛充栋',
  browserTitle: '汗牛充栋 - 私人书库',
  loginDescription: '您的私人书库管理系统',
  registrationEnabled: true,
  loginStyles: defaultLoginStyles(),
  hasLoginIcon: false,
  loginIconVersion: 0,
})

let loaded = false
let loadPromise: Promise<WebsiteSettings> | null = null

const isLoginStyle = (value: unknown): value is LoginPageStyle =>
  value === 'glass' || value === 'split' || value === 'minimal'

export const applyWebsiteSettings = (value: Partial<WebsiteSettings>) => {
  websiteSettings.siteName = value.siteName?.trim() || '汗牛充栋'
  websiteSettings.browserTitle = value.browserTitle?.trim() || websiteSettings.siteName
  websiteSettings.loginDescription = value.loginDescription?.trim() || ''
  websiteSettings.registrationEnabled = value.registrationEnabled !== false
  websiteSettings.hasLoginIcon = value.hasLoginIcon === true
  websiteSettings.loginIconUrl = value.loginIconUrl
  websiteSettings.loginIconVersion = Number(value.loginIconVersion) || 0
  const styles = value.loginStyles || defaultLoginStyles()
  ;(['modern', 'warm', 'natural', 'macos26'] as ThemeId[]).forEach(theme => {
    websiteSettings.loginStyles[theme] = isLoginStyle(styles[theme]) ? styles[theme] : 'glass'
  })
  document.title = websiteSettings.browserTitle
  return websiteSettings
}

export const loginIconSrc = () => websiteSettings.hasLoginIcon && websiteSettings.loginIconUrl
  ? `${websiteSettings.loginIconUrl}?v=${websiteSettings.loginIconVersion}`
  : ''

export const loadWebsiteSettings = async (force = false) => {
  if (loaded && !force) return websiteSettings
  if (loadPromise && !force) return loadPromise
  loadPromise = fetch('/api/site/settings', { cache: 'no-store' })
    .then(response => {
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      return response.json() as Promise<WebsiteSettings>
    })
    .then(value => {
      loaded = true
      return applyWebsiteSettings(value)
    })
    .catch(() => {
      document.title = websiteSettings.browserTitle
      return websiteSettings
    })
    .finally(() => {
      loadPromise = null
    })
  return loadPromise
}
