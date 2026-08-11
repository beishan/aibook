import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import api from '@/utils/api'
import type { DockIconName } from '@/components/DockIcon.vue'

const DOCK_ICON_NAMES: DockIconName[] = ['home', 'library', 'shelf', 'repair', 'settings', 'trash']
const SUPPORTED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
const MAX_ICON_SIZE = 5 * 1024 * 1024

interface DockIconStatus {
  icons: DockIconName[]
}

const emptyIconUrls = (): Record<DockIconName, string> => ({
  home: '',
  library: '',
  shelf: '',
  repair: '',
  settings: '',
  trash: '',
})

export const useDockIconStore = defineStore('dockIcons', () => {
  const iconUrls = reactive<Record<DockIconName, string>>(emptyIconUrls())
  const uploading = reactive<Record<DockIconName, boolean>>({
    home: false,
    library: false,
    shelf: false,
    repair: false,
    settings: false,
    trash: false,
  })
  const loading = ref(false)
  let hydrated = false
  let hydrationPromise: Promise<void> | null = null

  const replaceIconUrl = (name: DockIconName, url = '') => {
    if (iconUrls[name].startsWith('blob:')) URL.revokeObjectURL(iconUrls[name])
    iconUrls[name] = url
  }

  async function loadIcon(name: DockIconName) {
    try {
      const response = await api.get(`/api/user/dock-icons/${name}`, {
        responseType: 'blob',
        headers: { 'X-Suppress-Error-Toast': 'true' },
      })
      replaceIconUrl(name, URL.createObjectURL(response.data))
    } catch {
      replaceIconUrl(name)
    }
  }

  async function hydrate(force = false) {
    if (hydrated && !force) return
    if (hydrationPromise && !force) return hydrationPromise
    loading.value = true
    hydrationPromise = api.get<DockIconStatus>('/api/user/dock-icons', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    }).then(async response => {
      const existing = new Set(response.data.icons)
      await Promise.all(DOCK_ICON_NAMES.map(name => {
        if (existing.has(name)) return loadIcon(name)
        replaceIconUrl(name)
        return Promise.resolve()
      }))
      hydrated = true
    }).finally(() => {
      loading.value = false
      hydrationPromise = null
    })
    return hydrationPromise
  }

  async function upload(name: DockIconName, file: File) {
    if (!SUPPORTED_TYPES.has(file.type)) throw new Error('仅支持 JPG、PNG 或 WebP 图片')
    if (file.size > MAX_ICON_SIZE) throw new Error('图标图片不能超过 5MB')
    const formData = new FormData()
    formData.append('file', file)
    uploading[name] = true
    try {
      await api.post(`/api/user/dock-icons/${name}`, formData)
      await loadIcon(name)
    } finally {
      uploading[name] = false
    }
  }

  async function remove(name: DockIconName) {
    uploading[name] = true
    try {
      await api.delete(`/api/user/dock-icons/${name}`)
      replaceIconUrl(name)
    } finally {
      uploading[name] = false
    }
  }

  function reset() {
    DOCK_ICON_NAMES.forEach(name => replaceIconUrl(name))
    hydrated = false
    hydrationPromise = null
  }

  return { iconUrls, uploading, loading, hydrate, upload, remove, reset }
})
