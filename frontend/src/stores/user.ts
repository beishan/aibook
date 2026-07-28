import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/utils/api'
import { usePreferencesStore } from '@/stores/preferences'
import { AUTH_EXPIRED_EVENT } from '@/utils/authSession'

interface UserInfo {
  id: number
  username: string
  email: string
  nickname?: string
  avatarUrl?: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

  // 登录
  async function login(username: string, password: string) {
    const response = await api.post('/api/auth/login', { username, password })
    const data = response.data
    token.value = data.token
    localStorage.setItem('token', data.token)
    await usePreferencesStore().hydrate(true)
    userInfo.value = {
      id: 0,
      username: data.username,
      email: data.email,
      role: data.role,
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
    await usePreferencesStore().hydrate(true)
    userInfo.value = {
      id: 0,
      username: data.username,
      email: data.email,
      role: data.role,
    }
    return data
  }

  // 登出
  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    usePreferencesStore().resetHydration()
  }

  window.addEventListener(AUTH_EXPIRED_EVENT, logout)

  // 检查是否已登录
  function isLoggedIn() {
    return !!token.value
  }

  return {
    token,
    userInfo,
    login,
    register,
    logout,
    isLoggedIn,
  }
})
