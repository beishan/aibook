import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'
import { usePreferencesStore } from '@/stores/preferences'
import { useDockIconStore } from '@/stores/dockIcons'
import { AUTH_EXPIRED_EVENT } from '@/utils/authSession'
import { hydrateBookCoverPrivacy, resetBookCoverPrivacy } from '@/utils/imagePrivacy'
import {
  hydrateRandomCoverPrivacy,
  resetRandomCoverPrivacy,
} from '@/utils/randomCoverPrivacy'
import {
  deleteCachedUserAvatar,
  readCachedUserAvatar,
  writeCachedUserAvatar,
} from '@/utils/userAvatarCache'

interface UserInfo {
  id: number
  username: string
  email: string
  nickname?: string
  avatarUrl?: string
  hasAvatar?: boolean
  avatarVersion?: string | null
  mood?: string
  notes?: string
  birthDate?: string
  bookPreferences?: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const ACTIVE_AVATAR_USER_KEY = 'aibook.avatarCacheUserId'
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const avatarObjectUrl = ref('')
  const loading = ref(false)
  let cachedAvatarUserId: number | null = null
  let cachedAvatarVersion: string | null = null
  let hydrationPromise: Promise<UserInfo | null> | null = null

  const replaceAvatarObjectUrl = (url = '') => {
    if (avatarObjectUrl.value.startsWith('blob:')) {
      URL.revokeObjectURL(avatarObjectUrl.value)
    }
    avatarObjectUrl.value = url
  }

  async function restoreCachedAvatar(userId?: number) {
    if (!localStorage.getItem('token')) return
    const selectedUserId = userId ?? Number(localStorage.getItem(ACTIVE_AVATAR_USER_KEY))
    if (!Number.isSafeInteger(selectedUserId) || selectedUserId <= 0) return
    try {
      const cached = await readCachedUserAvatar(selectedUserId)
      cachedAvatarUserId = selectedUserId
      if (cached?.blob) {
        cachedAvatarVersion = cached.version
        replaceAvatarObjectUrl(URL.createObjectURL(cached.blob))
      }
    } catch {
      // IndexedDB 不可用时继续使用网络头像加载流程。
    }
  }

  async function clearCachedAvatar(userId: number) {
    cachedAvatarVersion = null
    replaceAvatarObjectUrl()
    await deleteCachedUserAvatar(userId).catch(() => undefined)
  }

  async function loadAvatar() {
    if (!userInfo.value?.hasAvatar) {
      if (cachedAvatarUserId !== null) await clearCachedAvatar(cachedAvatarUserId)
      return ''
    }
    const version = userInfo.value.avatarVersion
    if (avatarObjectUrl.value && version != null && cachedAvatarVersion === version) {
      return avatarObjectUrl.value
    }
    try {
      const response = await api.get('/api/user/profile/avatar', {
        responseType: 'blob',
        headers: { 'X-Suppress-Error-Toast': 'true' },
      })
      const url = URL.createObjectURL(response.data)
      replaceAvatarObjectUrl(url)
      if (cachedAvatarUserId !== null && version != null) {
        cachedAvatarVersion = version
        await writeCachedUserAvatar(
          cachedAvatarUserId,
          version,
          response.data,
        ).catch(() => undefined)
      }
      return url
    } catch {
      return avatarObjectUrl.value
    }
  }

  async function hydrate(force = false) {
    if (!token.value) return null
    if (userInfo.value && !force) return userInfo.value
    if (hydrationPromise && !force) return hydrationPromise

    loading.value = true
    hydrationPromise = api.get<UserInfo>('/api/user/profile', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    }).then(async response => {
      userInfo.value = response.data
      if (cachedAvatarUserId !== response.data.id) {
        replaceAvatarObjectUrl()
        cachedAvatarVersion = null
        cachedAvatarUserId = response.data.id
        await restoreCachedAvatar(response.data.id)
      }
      localStorage.setItem(ACTIVE_AVATAR_USER_KEY, String(response.data.id))
      await loadAvatar()
      return response.data
    }).finally(() => {
      loading.value = false
      hydrationPromise = null
    })
    return hydrationPromise
  }

  // 登录
  async function login(username: string, password: string) {
    const response = await api.post('/api/auth/login', { username, password })
    const data = response.data
    token.value = data.token
    localStorage.setItem('token', data.token)
    userInfo.value = {
      id: 0,
      username: data.username,
      email: data.email,
      role: data.role,
    }
    const [, profile] = await Promise.all([
      usePreferencesStore().hydrate(true),
      hydrate(true),
    ])
    if (profile?.id) {
      await Promise.all([
        hydrateBookCoverPrivacy(profile.id),
        hydrateRandomCoverPrivacy(profile.id),
      ])
    }
    return data
  }

  // 注册
  async function register(username: string, email: string, password: string, nickname?: string) {
    const response = await api.post('/api/auth/register', {
      username,
      email,
      password,
      nickname,
    })
    const data = response.data
    token.value = data.token
    localStorage.setItem('token', data.token)
    userInfo.value = {
      id: 0,
      username: data.username,
      email: data.email,
      role: data.role,
    }
    const [, profile] = await Promise.all([
      usePreferencesStore().hydrate(true),
      hydrate(true),
    ])
    if (profile?.id) {
      await Promise.all([
        hydrateBookCoverPrivacy(profile.id),
        hydrateRandomCoverPrivacy(profile.id),
      ])
    }
    return data
  }

  // 登出
  function logout() {
    token.value = ''
    userInfo.value = null
    replaceAvatarObjectUrl()
    cachedAvatarUserId = null
    cachedAvatarVersion = null
    localStorage.removeItem('token')
    localStorage.removeItem(ACTIVE_AVATAR_USER_KEY)
    usePreferencesStore().resetHydration()
    useDockIconStore().reset()
    resetBookCoverPrivacy()
    resetRandomCoverPrivacy()
  }

  window.addEventListener(AUTH_EXPIRED_EVENT, logout)

  // 检查是否已登录
  function isLoggedIn() {
    return !!token.value
  }

  return {
    token,
    userInfo,
    avatarObjectUrl,
    loading,
    restoreCachedAvatar,
    hydrate,
    loadAvatar,
    login,
    register,
    logout,
    isLoggedIn,
  }
})
