<template>
  <div class="register-container">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-circle bg-circle-3"></div>
    </div>

    <div class="register-card glass">
      <div class="register-header">
        <div class="logo-icon">📚</div>
        <h1>汗牛充栋</h1>
        <p>创建新账号</p>
      </div>

      <form class="register-form" @submit.prevent="handleRegister">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <div class="input-wrapper">
            <span class="input-icon">👤</span>
            <input
              v-model="registerForm.username"
              type="text"
              class="input"
              placeholder="请输入用户名"
            />
          </div>
          <span v-if="errors.username" class="error-text">{{ errors.username }}</span>
        </div>

        <div class="form-group">
          <label class="form-label">邮箱</label>
          <div class="input-wrapper">
            <span class="input-icon">📧</span>
            <input
              v-model="registerForm.email"
              type="email"
              class="input"
              placeholder="请输入邮箱"
            />
          </div>
          <span v-if="errors.email" class="error-text">{{ errors.email }}</span>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label class="form-label">密码</label>
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <input
                v-model="registerForm.password"
                type="password"
                class="input"
                placeholder="请输入密码"
              />
            </div>
            <span v-if="errors.password" class="error-text">{{ errors.password }}</span>
          </div>

          <div class="form-group">
            <label class="form-label">确认密码</label>
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <input
                v-model="registerForm.confirmPassword"
                type="password"
                class="input"
                placeholder="请确认密码"
              />
            </div>
            <span v-if="errors.confirmPassword" class="error-text">{{ errors.confirmPassword }}</span>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">昵称 <span class="optional">（选填）</span></label>
          <div class="input-wrapper">
            <span class="input-icon">✨</span>
            <input
              v-model="registerForm.nickname"
              type="text"
              class="input"
              placeholder="给自己起个昵称吧"
            />
          </div>
        </div>

        <button type="submit" class="btn btn-primary register-button" :disabled="loading">
          <span v-if="loading" class="loading-spinner"></span>
          <span>{{ loading ? '注册中...' : '创建账号' }}</span>
        </button>
      </form>

      <div class="register-footer">
        <span>已有账号？</span>
        <router-link to="/login" class="link">立即登录</router-link>
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

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  nickname: '',
})

const errors = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const validate = () => {
  errors.username = ''
  errors.email = ''
  errors.password = ''
  errors.confirmPassword = ''

  if (!registerForm.username) {
    errors.username = '请输入用户名'
  } else if (registerForm.username.length < 3 || registerForm.username.length > 20) {
    errors.username = '用户名长度在 3 到 20 个字符'
  }

  if (!registerForm.email) {
    errors.email = '请输入邮箱'
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) {
    errors.email = '请输入正确的邮箱格式'
  }

  if (!registerForm.password) {
    errors.password = '请输入密码'
  } else if (registerForm.password.length < 6 || registerForm.password.length > 40) {
    errors.password = '密码长度在 6 到 40 个字符'
  }

  if (!registerForm.confirmPassword) {
    errors.confirmPassword = '请确认密码'
  } else if (registerForm.confirmPassword !== registerForm.password) {
    errors.confirmPassword = '两次输入的密码不一致'
  }

  return !errors.username && !errors.email && !errors.password && !errors.confirmPassword
}

const handleRegister = async () => {
  if (!validate()) return

  loading.value = true
  try {
    await userStore.register(
      registerForm.username,
      registerForm.email,
      registerForm.password,
      registerForm.nickname || undefined
    )
    message.success('注册成功')
    router.push('/')
  } catch (error: any) {
    message.error(error.response?.data?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
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

/* 注册卡片 */
.register-card {
  width: 100%;
  max-width: 520px;
  padding: var(--spacing-xl);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-xl);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  position: relative;
  z-index: 1;
}

.register-header {
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

.register-header h1 {
  font-size: var(--font-size-4xl);
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: var(--spacing-sm);
}

.register-header p {
  color: var(--text-secondary);
  font-size: var(--font-size-base);
}

.register-form {
  width: 100%;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
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

.optional {
  font-weight: 400;
  color: var(--text-tertiary);
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

.register-button {
  width: 100%;
  padding: 14px;
  font-size: var(--font-size-lg);
  font-weight: 600;
  margin-top: var(--spacing-md);
  position: relative;
  overflow: hidden;
}

.register-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s;
}

.register-button:hover::before {
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

.register-footer {
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

/* 响应式 */
@media (max-width: 480px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
