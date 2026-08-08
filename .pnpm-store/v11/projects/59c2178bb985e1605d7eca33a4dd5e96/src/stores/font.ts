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

export interface FontDirectoryNode {
  name: string
  path: string
  leaf: boolean
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
  let systemFontApplyVersion = 0

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

  const browseDirectories = async (path?: string) => {
    const { data } = await api.get<FontDirectoryNode[]>(
      '/api/font-scan-directories/tree',
      { params: path ? { path } : undefined }
    )
    return data
  }

  const getFont = (id: number | null | undefined) =>
    id == null ? undefined : fonts.value.find(font => font.id === id)

  const cssFamily = (id: number) => `"${managedFamily(id)}"`

  const markFontUnavailable = async (font: FontAsset) => {
    font.available = false
    const loaded = loadedFonts.get(font.id)
    if (loaded) {
      document.fonts.forEach(face => {
        if (face.family === loaded.family) document.fonts.delete(face)
      })
      URL.revokeObjectURL(loaded.objectUrl)
      loadedFonts.delete(font.id)
    }
    try {
      await api.post(`/api/fonts/${font.id}/unavailable`, null, {
        headers: { 'X-Suppress-Error-Toast': 'true' },
      })
    } catch (error) {
      // 本地状态已立即生效；服务端暂时不可达时也不要干扰当前页面。
      console.warn('Failed to persist unavailable font state:', error)
    }
  }

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
      if (!(response.data instanceof ArrayBuffer) || response.data.byteLength === 0) {
        await markFontUnavailable(font)
        throw new Error('字体文件内容为空或响应格式不正确')
      }
      const contentType = String(response.headers['content-type'] || 'font/ttf')
      const objectUrl = URL.createObjectURL(
        new Blob([response.data], { type: contentType })
      )
      const family = managedFamily(id)
      const descriptors: FontFaceDescriptors = {
        style: font.fontStyle || 'normal',
        weight: String(font.fontWeight || 'normal'),
      }

      try {
        // 部分经过修改或压缩的中文字体通过 Blob URL 加载会被浏览器拒绝，
        // 直接传入二进制数据兼容性更好；URL 仍保留给 EPUB iframe 使用。
        let face: FontFace
        try {
          face = new FontFace(family, response.data.slice(0), descriptors)
          await face.load()
        } catch {
          face = new FontFace(family, `url("${objectUrl}")`)
          await face.load()
        }
        document.fonts.add(face)
      } catch (error) {
        URL.revokeObjectURL(objectUrl)
        await markFontUnavailable(font)
        const detail = error instanceof Error && error.message
          ? `：${error.message}`
          : ''
        throw new Error(`浏览器无法解析该字体文件${detail}`)
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
    const applyVersion = ++systemFontApplyVersion
    if (id == null) {
      document.documentElement.style.setProperty('--font-family', DEFAULT_FONT_STACK)
      document.documentElement.style.setProperty('--el-font-family', DEFAULT_FONT_STACK)
      return
    }

    try {
      await loadFont(id)
      if (applyVersion !== systemFontApplyVersion) return
      const stack = `${cssFamily(id)}, ${DEFAULT_FONT_STACK}`
      document.documentElement.style.setProperty('--font-family', stack)
      document.documentElement.style.setProperty('--el-font-family', stack)
    } catch (error) {
      if (applyVersion !== systemFontApplyVersion) return
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
    browseDirectories,
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
