import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'
import { usePreferencesStore } from '@/stores/preferences'
import { useDockIconStore } from '@/stores/dockIcons'
import { AUTH_EXPIRED_EVENT } from '@/utils/authSession'

interface UserInfo {
  id: number
  username: string
  email: string
  nickname?: string
  avatarUrl?: string
  hasAvatar?: boolean
  mood?: string
  notes?: string
  birthDate?: string
  bookPreferences?: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const avatarObjectUrl = ref('')
  let hydrationPromise: Promise<UserInfo | null> | null = null

  const replaceAvatarObjectUrl = (url = '') => {
    if (avatarObjectUrl.value.startsWith('blob:')) {
      URL.revokeObjectURL(avatarObjectUrl.value)
    }
    avatarObjectUrl.value = url
  }

  async function loadAvatar() {
    if (!userInfo.value?.hasAvatar) {
      replaceAvatarObjectUrl()
      return ''
    }
    try {
      const response = await api.get('/api/user/profile/avatar', {
        responseType: 'blob',
        headers: { 'X-Suppress-Error-Toast': 'true' },
      })
      const url = URL.createObjectURL(response.data)
      replaceAvatarObjectUrl(url)
      return url
    } catch {
      replaceAvatarObjectUrl()
      return ''
    }
  }

  async function hydrate(force = false) {
    if (!token.value) return null
    if (userInfo.value && !force) return userInfo.value
    if (hydrationPromise && !force) return hydrationPromise

    hydrationPromise = api.get<UserInfo>('/api/user/profile', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    }).then(async response => {
      userInfo.value = response.data
      await loadAvatar()
      return response.data
    }).finally(() => {
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
    await Promise.all([
      usePreferencesStore().hydrate(true),
      hydrate(true),
    ])
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
    await Promise.all([
      usePreferencesStore().hydrate(true),
      hydrate(true),
    ])
    return data
  }

  // 登出
  function logout() {
    token.value = ''
    userInfo.value = null
    replaceAvatarObjectUrl()
    localStorage.removeItem('token')
    usePreferencesStore().resetHydration()
    useDockIconStore().reset()
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
    hydrate,
    loadAvatar,
    login,
    register,
    logout,
    isLoggedIn,
  }
})
