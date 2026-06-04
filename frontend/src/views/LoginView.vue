<template>
  <div class="login-container">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-circle bg-circle-3"></div>
    </div>

    <div class="login-card glass">
      <div class="login-header">
        <div class="logo-icon">📚</div>
        <h1>汗牛充栋</h1>
        <p>您的私人书库管理系统</p>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <div class="input-wrapper">
            <span class="input-icon">👤</span>
            <input
              v-model="loginForm.username"
              type="text"
              class="input"
              placeholder="请输入用户名"
            />
          </div>
          <span v-if="errors.username" class="error-text">{{ errors.username }}</span>
        </div>

        <div class="form-group">
          <label class="form-label">密码</label>
          <div class="input-wrapper">
            <span class="input-icon">🔒</span>
            <input
              v-model="loginForm.password"
              type="password"
              class="input"
              placeholder="请输入密码"
            />
          </div>
          <span v-if="errors.password" class="error-text">{{ errors.password }}</span>
        </div>

        <button type="submit" class="btn btn-primary login-button" :disabled="loading">
          <span v-if="loading" class="loading-spinner"></span>
          <span>{{ loading ? '登录中...' : '登录' }}</span>
        </button>
      </form>

      <div class="login-footer">
        <span>还没有账号？</span>
        <router-link to="/register" class="link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '@/utils/message'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
})

const errors = reactive({
  username: '',
  password: '',
})

const validate = () => {
  errors.username = ''
  errors.password = ''

  if (!loginForm.username) {
    errors.username = '请输入用户名'
  } else if (loginForm.username.length < 3 || loginForm.username.length > 20) {
    errors.username = '用户名长度在 3 到 20 个字符'
  }

  if (!loginForm.password) {
    errors.password = '请输入密码'
  } else if (loginForm.password.length < 6 || loginForm.password.length > 40) {
    errors.password = '密码长度在 6 到 40 个字符'
  }

  return !errors.username && !errors.password
}

const handleLogin = async () => {
  if (!validate()) return

  loading.value = true
  try {
    await userStore.login(loginForm.username, loginForm.password)
    message.success('登录成功')
    router.push('/')
  } catch (error: any) {
    message.error(error.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--bg-page-gradient);
  padding: var(--spacing-lg);
  position: relative;
  overflow: hidden;
}

/* 背景装饰 */
.bg-decoration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}

.bg-circle-1 {
  width: 600px;
  height: 600px;
  top: -200px;
  right: -200px;
  animation: float 20s ease-in-out infinite;
}

.bg-circle-2 {
  width: 400px;
  height: 400px;
  bottom: -100px;
  left: -100px;
  animation: float 15s ease-in-out infinite reverse;
}

.bg-circle-3 {
  width: 200px;
  height: 200px;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: float 25s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-30px) rotate(180deg);
  }
}

/* 登录卡片 */
.login-card {
  width: 100%;
  max-width: 420px;
  padding: var(--spacing-xl);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-xl);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  position: relative;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: var(--spacing-xl);
}

.logo-icon {
  font-size: 64px;
  margin-bottom: var(--spacing-md);
  animation: bounce 2s ease-in-out infinite;
}

@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.login-header h1 {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: var(--spacing-sm);
}

.login-header p {
  color: var(--text-secondary);
  font-size: var(--font-size-base);
}

.login-form {
  width: 100%;
}

.form-group {
  margin-bottom: var(--spacing-lg);
}

.form-label {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 16px;
  font-size: 16px;
  pointer-events: none;
}

.input-wrapper .input {
  padding-left: 44px;
}

.login-button {
  width: 100%;
  padding: 14px;
  font-size: var(--font-size-lg);
  font-weight: 600;
  margin-top: var(--spacing-md);
  position: relative;
  overflow: hidden;
}

.login-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s;
}

.login-button:hover::before {
  left: 100%;
}

.loading-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: white;
  animation: spin 0.8s linear infinite;
  margin-right: var(--spacing-sm);
  vertical-align: middle;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.login-footer {
  text-align: center;
  margin-top: var(--spacing-xl);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.link {
  color: var(--primary);
  text-decoration: none;
  font-weight: 500;
  margin-left: var(--spacing-xs);
  transition: color var(--transition-fast);
}

.link:hover {
  color: var(--primary-dark);
  text-decoration: underline;
}

.error-text {
  color: var(--danger);
  font-size: var(--font-size-xs);
  margin-top: var(--spacing-xs);
  display: block;
}
</style>
