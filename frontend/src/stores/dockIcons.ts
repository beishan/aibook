import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import api from '@/utils/api'
import type { DockIconName } from '@/components/DockIcon.vue'
import {
  deleteCachedDockIcon,
  readCachedDockIcons,
  writeCachedDockIcon,
} from '@/utils/dockIconCache'

export type CustomDockIconName = DockIconName | 'trash'
const DOCK_ICON_NAMES: CustomDockIconName[] = [
  'home', 'library', 'shelf', 'repair', 'conversion', 'settings', 'trashEmpty', 'trashFull', 'trash',
]
const SUPPORTED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
const MAX_ICON_SIZE = 5 * 1024 * 1024

interface DockIconStatus {
  userId: number
  icons: CustomDockIconName[]
  versions: Partial<Record<CustomDockIconName, number>>
}

const ACTIVE_CACHE_USER_KEY = 'aibook.dockIconCacheUserId'

const emptyIconUrls = (): Record<CustomDockIconName, string> => ({
  home: '',
  library: '',
  shelf: '',
  repair: '',
  conversion: '',
  settings: '',
  trashEmpty: '',
  trashFull: '',
  trash: '',
})

export const useDockIconStore = defineStore('dockIcons', () => {
  const iconUrls = reactive<Record<CustomDockIconName, string>>(emptyIconUrls())
  const uploading = reactive<Record<CustomDockIconName, boolean>>({
    home: false,
    library: false,
    shelf: false,
    repair: false,
    conversion: false,
    settings: false,
    trashEmpty: false,
    trashFull: false,
    trash: false,
  })
  const loading = ref(false)
  const cachedVersions = new Map<CustomDockIconName, number>()
  let cachedUserId: number | null = null
  let hydrated = false
  let hydrationPromise: Promise<void> | null = null

  const replaceIconUrl = (name: CustomDockIconName, url = '') => {
    if (iconUrls[name].startsWith('blob:')) URL.revokeObjectURL(iconUrls[name])
    iconUrls[name] = url
  }

  async function restoreCached(userId?: number) {
    if (!localStorage.getItem('token')) return
    const selectedUserId = userId ?? Number(localStorage.getItem(ACTIVE_CACHE_USER_KEY))
    if (!Number.isSafeInteger(selectedUserId) || selectedUserId <= 0) return
    try {
      const records = await readCachedDockIcons(selectedUserId, DOCK_ICON_NAMES)
      cachedUserId = selectedUserId
      records.forEach(record => {
        cachedVersions.set(record.name, record.version)
        replaceIconUrl(record.name, URL.createObjectURL(record.blob))
      })
    } catch {
      // IndexedDB 不可用时继续使用原有网络加载流程。
    }
  }

  async function loadIcon(name: CustomDockIconName, version: number) {
    try {
      const response = await api.get(`/api/user/dock-icons/${name}`, {
        responseType: 'blob',
        headers: { 'X-Suppress-Error-Toast': 'true' },
      })
      replaceIconUrl(name, URL.createObjectURL(response.data))
      cachedVersions.set(name, version)
      if (cachedUserId !== null) {
        await writeCachedDockIcon(cachedUserId, name, version, response.data).catch(() => undefined)
      }
    } catch {
      if (!iconUrls[name]) replaceIconUrl(name)
    }
  }

  async function hydrate(force = false) {
    if (hydrated && !force) return
    if (hydrationPromise && !force) return hydrationPromise
    loading.value = true
    hydrationPromise = api.get<DockIconStatus>('/api/user/dock-icons', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    }).then(async response => {
      if (cachedUserId !== response.data.userId) {
        DOCK_ICON_NAMES.forEach(name => replaceIconUrl(name))
        cachedVersions.clear()
        cachedUserId = response.data.userId
        await restoreCached(response.data.userId)
      }
      localStorage.setItem(ACTIVE_CACHE_USER_KEY, String(response.data.userId))
      const existing = new Set(response.data.icons)
      await Promise.all(DOCK_ICON_NAMES.map(name => {
        const version = response.data.versions[name]
        if (existing.has(name) && version !== undefined) {
          if (iconUrls[name] && cachedVersions.get(name) === version) return Promise.resolve()
          return loadIcon(name, version)
        }
        replaceIconUrl(name)
        cachedVersions.delete(name)
        return deleteCachedDockIcon(response.data.userId, name).catch(() => undefined)
      }))
      hydrated = true
    }).finally(() => {
      loading.value = false
      hydrationPromise = null
    })
    return hydrationPromise
  }

  async function upload(name: CustomDockIconName, file: File) {
    if (!SUPPORTED_TYPES.has(file.type)) throw new Error('仅支持 JPG、PNG 或 WebP 图片')
    if (file.size > MAX_ICON_SIZE) throw new Error('图标图片不能超过 5MB')
    const formData = new FormData()
    formData.append('file', file)
    uploading[name] = true
    try {
      const { data } = await api.post<DockIconStatus>(`/api/user/dock-icons/${name}`, formData)
      const version = data.versions[name]
      if (version !== undefined) await loadIcon(name, version)
    } finally {
      uploading[name] = false
    }
  }

  async function remove(name: CustomDockIconName) {
    uploading[name] = true
    try {
      await api.delete(`/api/user/dock-icons/${name}`)
      replaceIconUrl(name)
      cachedVersions.delete(name)
      if (cachedUserId !== null) {
        await deleteCachedDockIcon(cachedUserId, name).catch(() => undefined)
      }
    } finally {
      uploading[name] = false
    }
  }

  function reset() {
    DOCK_ICON_NAMES.forEach(name => replaceIconUrl(name))
    cachedVersions.clear()
    cachedUserId = null
    localStorage.removeItem(ACTIVE_CACHE_USER_KEY)
    hydrated = false
    hydrationPromise = null
  }

  return { iconUrls, uploading, loading, restoreCached, hydrate, upload, remove, reset }
})
