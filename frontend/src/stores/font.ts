import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import api from '@/utils/api'

export interface FontAsset {
  id: number
  displayName: string
  fontFamily: string
  fontWeight?: number | string
  fontStyle?: string
  format: string
  sourceType: 'SCANNED' | 'UPLOADED'
  filePath?: string
  fileSize?: number
  enabled: boolean
  available: boolean
  createdAt?: string
  updatedAt?: string
}

export interface FontScanDirectory {
  id: number
  path: string
  enabled?: boolean
  lastScanAt?: string
  lastError?: string
}

interface LoadedFont {
  family: string
  objectUrl: string
}

const DEFAULT_FONT_STACK =
  "'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif"

const managedFamily = (id: number) => `AiBookManagedFont-${id}`

export const useFontStore = defineStore('fonts', () => {
  const fonts = ref<FontAsset[]>([])
  const directories = ref<FontScanDirectory[]>([])
  const loading = ref(false)
  const scanning = ref(false)
  const loadedFonts = new Map<number, LoadedFont>()
  const loadingFonts = new Map<number, Promise<LoadedFont>>()

  const availableFonts = computed(() =>
    fonts.value.filter(font => font.enabled && font.available)
  )

  const fetchFonts = async () => {
    if (!localStorage.getItem('token')) return []
    loading.value = true
    try {
      const { data } = await api.get<FontAsset[]>('/api/fonts')
      fonts.value = data
      return data
    } finally {
      loading.value = false
    }
  }

  const fetchDirectories = async () => {
    const { data } = await api.get<FontScanDirectory[]>('/api/font-scan-directories')
    directories.value = data
    return data
  }

  const getFont = (id: number | null | undefined) =>
    id == null ? undefined : fonts.value.find(font => font.id === id)

  const cssFamily = (id: number) => `"${managedFamily(id)}"`

  const loadFont = async (fontOrId: FontAsset | number): Promise<LoadedFont> => {
    const id = typeof fontOrId === 'number' ? fontOrId : fontOrId.id
    const cached = loadedFonts.get(id)
    if (cached) return cached

    const pending = loadingFonts.get(id)
    if (pending) return pending

    const task = (async () => {
      let font = typeof fontOrId === 'number' ? getFont(id) : fontOrId
      if (!font) {
        await fetchFonts()
        font = getFont(id)
      }
      if (!font || !font.enabled || !font.available) {
        throw new Error('字体不存在或当前不可用')
      }

      const response = await api.get<ArrayBuffer>(`/api/fonts/${id}/content`, {
        responseType: 'arraybuffer',
      })
      const contentType = String(response.headers['content-type'] || 'font/ttf')
      const objectUrl = URL.createObjectURL(
        new Blob([response.data], { type: contentType })
      )
      const family = managedFamily(id)
      const face = new FontFace(family, `url("${objectUrl}")`, {
        style: font.fontStyle || 'normal',
        weight: String(font.fontWeight || 'normal'),
      })

      try {
        await face.load()
        document.fonts.add(face)
      } catch (error) {
        URL.revokeObjectURL(objectUrl)
        throw error
      }

      const loaded = { family, objectUrl }
      loadedFonts.set(id, loaded)
      return loaded
    })().finally(() => {
      loadingFonts.delete(id)
    })

    loadingFonts.set(id, task)
    return task
  }

  const applySystemFont = async (id: number | null) => {
    if (id == null) {
      document.documentElement.style.setProperty('--font-family', DEFAULT_FONT_STACK)
      document.documentElement.style.setProperty('--el-font-family', DEFAULT_FONT_STACK)
      return
    }

    try {
      await loadFont(id)
      const stack = `${cssFamily(id)}, ${DEFAULT_FONT_STACK}`
      document.documentElement.style.setProperty('--font-family', stack)
      document.documentElement.style.setProperty('--el-font-family', stack)
    } catch (error) {
      document.documentElement.style.setProperty('--font-family', DEFAULT_FONT_STACK)
      document.documentElement.style.setProperty('--el-font-family', DEFAULT_FONT_STACK)
      console.error('Failed to apply system font:', error)
    }
  }

  const getLoadedFont = (id: number) => loadedFonts.get(id)

  const uploadFont = async (file: File) => {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post<FontAsset[]>('/api/fonts/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    await fetchFonts()
    return data[0]
  }

  const scanFonts = async () => {
    scanning.value = true
    try {
      const { data } = await api.post('/api/fonts/scan')
      await Promise.all([fetchFonts(), fetchDirectories()])
      return data
    } finally {
      scanning.value = false
    }
  }

  const addDirectory = async (path: string) => {
    const { data } = await api.post<FontScanDirectory>(
      '/api/font-scan-directories',
      { path }
    )
    await fetchDirectories()
    return data
  }

  const removeDirectory = async (id: number) => {
    await api.delete(`/api/font-scan-directories/${id}`)
    await Promise.all([fetchDirectories(), fetchFonts()])
  }

  const updateFont = async (
    id: number,
    payload: { displayName?: string; enabled?: boolean }
  ) => {
    const { data } = await api.put<FontAsset>(`/api/fonts/${id}`, payload)
    const index = fonts.value.findIndex(font => font.id === id)
    if (index >= 0) fonts.value[index] = data
    return data
  }

  const removeFont = async (id: number) => {
    await api.delete(`/api/fonts/${id}`)
    fonts.value = fonts.value.filter(font => font.id !== id)
  }

  return {
    fonts,
    directories,
    loading,
    scanning,
    availableFonts,
    fetchFonts,
    fetchDirectories,
    getFont,
    cssFamily,
    loadFont,
    applySystemFont,
    getLoadedFont,
    uploadFont,
    scanFonts,
    addDirectory,
    removeDirectory,
    updateFont,
    removeFont,
  }
})
