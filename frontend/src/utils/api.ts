import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { clearStoredAuthSession } from '@/utils/authSession'

let authExpiryHandled = false

const isAuthEndpoint = (url?: string) => url?.startsWith('/api/auth/') === true

const api = axios.create({
  baseURL: '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    if (isAuthEndpoint(response.config.url)) {
      authExpiryHandled = false
    }
    return response
  },
  (error) => {
    const message = error.response?.data?.message || '请求失败'
    const status = error.response?.status
    const authRequest = isAuthEndpoint(error.config?.url)
    const suppressErrorToast = String(
      error.config?.headers?.get?.('X-Suppress-Error-Toast')
        ?? error.config?.headers?.['X-Suppress-Error-Toast']
        ?? ''
    ) === 'true'

    if (status === 401 && !authRequest) {
      clearStoredAuthSession()

      if (!authExpiryHandled) {
        authExpiryHandled = true
        const currentRoute = router.currentRoute.value
        const redirect = currentRoute.path === '/login' ? undefined : currentRoute.fullPath
        void router.replace({
          path: '/login',
          query: redirect ? { redirect } : undefined,
        })
        ElMessage.error('登录已过期，请重新登录')
      }
    } else if (status === 403 && !suppressErrorToast) {
      ElMessage.error('没有权限执行此操作')
    } else if (!(status === 401 && authRequest) && !suppressErrorToast) {
      ElMessage.error(message)
    }

    return Promise.reject(error)
  }
)

export default api
