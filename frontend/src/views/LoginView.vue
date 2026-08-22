<template>
  <div class="login-container" :class="`login-style-${loginStyle}`">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-circle bg-circle-3"></div>
    </div>

    <div class="login-card glass">
      <div class="window-chrome" aria-hidden="true">
        <span class="window-control window-control-close"></span>
        <span class="window-control window-control-minimize"></span>
        <span class="window-control window-control-expand"></span>
      </div>

      <div class="login-header login-brand-panel">
        <div class="logo-icon" aria-hidden="true">
          <img v-if="loginIcon" :src="loginIcon" alt="" />
          <span v-else>📚</span>
        </div>
        <h1>{{ websiteSettings.siteName }}</h1>
        <p v-if="websiteSettings.loginDescription">{{ websiteSettings.loginDescription }}</p>
        <span class="liquid-glass-badge">Liquid Glass</span>
      </div>

      <div class="login-auth-panel">
        <form class="login-form" @submit.prevent="handleLogin">
          <div class="form-group">
            <label class="form-label" for="login-username">用户名</label>
            <div class="input-wrapper">
              <span class="input-icon">👤</span>
              <input
                v-model="loginForm.username"
                id="login-username"
                type="text"
                class="input"
                placeholder="请输入用户名"
                autocomplete="username"
              />
            </div>
            <span v-if="errors.username" class="error-text">{{ errors.username }}</span>
          </div>

          <div class="form-group">
            <label class="form-label" for="login-password">密码</label>
            <div class="input-wrapper password-input-wrapper">
              <span class="input-icon">🔒</span>
              <input
                v-model="loginForm.password"
                id="login-password"
                :type="showPassword ? 'text' : 'password'"
                class="input"
                placeholder="请输入密码"
                autocomplete="current-password"
              />
              <button
                type="button"
                class="password-toggle"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                :aria-pressed="showPassword"
                :title="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <svg v-if="showPassword" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 4.2A10.8 10.8 0 0 1 12 4c5.5 0 9 5.3 9 5.3a13.8 13.8 0 0 1-2.2 2.7M6.2 6.2C4.1 7.6 3 9.3 3 9.3s3.5 5.3 9 5.3c1 0 1.9-.2 2.7-.4" />
                </svg>
                <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M3 12s3.5-5.3 9-5.3S21 12 21 12s-3.5 5.3-9 5.3S3 12 3 12Z" /><circle cx="12" cy="12" r="2.4" />
                </svg>
              </button>
            </div>
            <span v-if="errors.password" class="error-text">{{ errors.password }}</span>
          </div>

          <button type="submit" class="btn btn-primary login-button" :disabled="loading">
            <span v-if="loading" class="loading-spinner"></span>
            <span>{{ loading ? '登录中...' : '登录' }}</span>
          </button>
        </form>

        <div v-if="websiteSettings.registrationEnabled" class="login-footer">
          <span>还没有账号？</span>
          <router-link to="/register" class="link">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from '@/utils/message'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { loginIconSrc, websiteSettings } from '@/utils/siteSettings'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const themeStore = useThemeStore()

const loading = ref(false)
const showPassword = ref(false)
const loginStyle = computed(() => websiteSettings.loginStyles[themeStore.currentTheme] || 'glass')
const loginIcon = computed(loginIconSrc)

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
    const redirect = route.query.redirect
    const target = typeof redirect === 'string'
      && redirect.startsWith('/')
      && !redirect.startsWith('//')
      ? redirect
      : '/'
    await router.replace(target)
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

.window-chrome,
.liquid-glass-badge {
  display: none;
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

.logo-icon span {
  display: block;
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

.password-input-wrapper .input {
  padding-right: 48px;
}

.password-toggle {
  position: absolute;
  right: 9px;
  z-index: 2;
  display: grid;
  width: 34px;
  height: 34px;
  padding: 7px;
  place-items: center;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: color var(--transition-fast), background var(--transition-fast);
}

.password-toggle:hover {
  background: var(--primary-alpha-10);
  color: var(--primary);
}

.password-toggle:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 1px;
}

.password-toggle svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
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

/* MACOS26 登录窗口 */
:global(html[data-theme="macos26"] .login-container) {
  isolation: isolate;
  background:
    radial-gradient(circle at 14% 12%, rgba(78, 202, 255, 0.72), transparent 30%),
    radial-gradient(circle at 86% 18%, rgba(206, 141, 255, 0.58), transparent 34%),
    radial-gradient(circle at 64% 92%, rgba(83, 232, 199, 0.46), transparent 38%),
    var(--bg-page-gradient);
}

:global(html[data-theme="macos26"] .login-container::before) {
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
    linear-gradient(112deg, rgba(255, 255, 255, 0.2), transparent 28%, rgba(255, 255, 255, 0.12) 62%, transparent),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.025) 0 1px, transparent 1px 72px);
  content: '';
  pointer-events: none;
}

:global(html[data-theme="macos26"] .bg-circle) {
  border: 1px solid rgba(255, 255, 255, 0.42);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0.05));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.62), 0 30px 80px rgba(58, 76, 113, 0.08);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
}

:global(html[data-theme="macos26"] .bg-circle-1) {
  background: radial-gradient(circle at 30% 26%, rgba(255, 255, 255, 0.6), rgba(116, 197, 255, 0.12) 42%, rgba(255, 255, 255, 0.05));
}

:global(html[data-theme="macos26"] .bg-circle-2) {
  background: radial-gradient(circle at 62% 30%, rgba(255, 255, 255, 0.52), rgba(193, 132, 255, 0.12) 46%, rgba(255, 255, 255, 0.04));
}

:global(html[data-theme="macos26"] .bg-circle-3) {
  opacity: 0.72;
  background: radial-gradient(circle at 36% 28%, rgba(255, 255, 255, 0.68), rgba(82, 226, 199, 0.1) 48%, rgba(255, 255, 255, 0.04));
}

:global(html[data-theme="macos26"] .login-card) {
  max-width: 440px;
  padding: 58px 38px 38px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 30px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.66), rgba(244, 249, 255, 0.32)),
    rgba(255, 255, 255, 0.22);
  box-shadow:
    0 36px 90px rgba(42, 58, 91, 0.22),
    0 8px 24px rgba(42, 58, 91, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.98),
    inset 0 -1px 0 rgba(83, 105, 141, 0.12);
  backdrop-filter: blur(34px) saturate(190%) contrast(104%);
  -webkit-backdrop-filter: blur(34px) saturate(190%) contrast(104%);
}

:global(html[data-theme="macos26"] .login-card::before) {
  position: absolute;
  inset: 0 0 auto;
  height: 110px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.36), transparent);
  content: '';
  pointer-events: none;
}

:global(html[data-theme="macos26"] .window-chrome) {
  position: absolute;
  top: 20px;
  left: 22px;
  z-index: 1;
  display: flex;
  gap: 8px;
}

:global(html[data-theme="macos26"] .window-control) {
  width: 12px;
  height: 12px;
  border: 0.5px solid rgba(68, 57, 57, 0.18);
  border-radius: 50%;
  box-shadow: inset 0 0.5px 0 rgba(255, 255, 255, 0.58), 0 1px 2px rgba(52, 59, 76, 0.12);
}

:global(html[data-theme="macos26"] .window-control-close) { background: #ff5f57; }
:global(html[data-theme="macos26"] .window-control-minimize) { background: #febc2e; }
:global(html[data-theme="macos26"] .window-control-expand) { background: #28c840; }

:global(html[data-theme="macos26"] .login-header) {
  position: relative;
}

:global(html[data-theme="macos26"] .logo-icon) {
  display: grid;
  width: 82px;
  height: 82px;
  margin: 0 auto var(--spacing-md);
  place-items: center;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  font-size: 43px;
  animation: macos-logo-float 5s ease-in-out infinite;
}

:global(html[data-theme="macos26"] .logo-icon span) {
  filter: drop-shadow(0 4px 8px rgba(22, 54, 91, 0.18));
}

@keyframes macos-logo-float {
  0%, 100% { transform: translateY(0) rotate(-1deg); }
  50% { transform: translateY(-5px) rotate(1deg); }
}

:global(html[data-theme="macos26"] .login-header h1) {
  letter-spacing: -0.04em;
  text-shadow: 0 1px 18px rgba(255, 255, 255, 0.48);
}

:global(html[data-theme="macos26"] .liquid-glass-badge) {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  margin-top: 12px;
  padding: 3px 10px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.28);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
  color: rgba(23, 34, 53, 0.58);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

:global(html[data-theme="macos26"] .form-label) {
  color: rgba(23, 34, 53, 0.72);
  font-weight: 600;
}

:global(html[data-theme="macos26"] .input-wrapper::after) {
  position: absolute;
  inset: 1px 1px auto;
  height: 45%;
  border-radius: 13px 13px 50% 50%;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.32), transparent);
  content: '';
  pointer-events: none;
}

:global(html[data-theme="macos26"] .input-icon) {
  z-index: 1;
  opacity: 0.66;
  filter: saturate(0.72);
}

:global(html[data-theme="macos26"] .input-wrapper .input) {
  position: relative;
  z-index: 0;
  min-height: 48px;
  border-color: rgba(255, 255, 255, 0.76);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.38);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9), 0 6px 18px rgba(48, 66, 100, 0.08);
}

:global(html[data-theme="macos26"] .input-wrapper .input:hover:not(:focus)) {
  border-color: rgba(255, 255, 255, 0.96);
  background: rgba(255, 255, 255, 0.5);
}

:global(html[data-theme="macos26"] .login-button) {
  min-height: 50px;
  border-radius: 15px;
  box-shadow: 0 12px 28px var(--primary-alpha-30), inset 0 1px 0 rgba(255, 255, 255, 0.58);
}

:global(html[data-theme="macos26"] .login-button:hover:not(:disabled)) {
  transform: translateY(-2px);
  box-shadow: 0 16px 34px var(--primary-alpha-30), inset 0 1px 0 rgba(255, 255, 255, 0.68);
}

:global(html[data-theme="macos26"] .login-footer) {
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.38);
}

.logo-icon img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

:global(.login-container.login-style-split .login-card) {
  display: grid;
  width: min(920px, 100%);
  max-width: 920px;
  grid-template-columns: minmax(300px, 0.92fr) minmax(360px, 1.08fr);
  padding: 0;
}

:global(.login-container.login-style-split .login-brand-panel) {
  display: flex;
  min-height: 520px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 64px 42px;
  border-right: 1px solid var(--border-color-light);
  background: linear-gradient(145deg, var(--primary-alpha-20), transparent 68%);
}

:global(.login-container.login-style-split .login-auth-panel) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 64px 48px 48px;
}

:global(.login-container.login-style-split .window-chrome) {
  z-index: 3;
}

:global(.login-container.login-style-minimal .bg-decoration) {
  opacity: 0.22;
}

:global(.login-container.login-style-minimal .login-card) {
  max-width: 390px;
  padding: 30px;
  border: 1px solid var(--border-color-light);
  border-radius: 18px;
  background: color-mix(in srgb, var(--surface-card) 94%, transparent);
  box-shadow: 0 14px 42px rgba(24, 35, 52, 0.1);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

:global(.login-container.login-style-minimal .window-chrome),
:global(.login-container.login-style-minimal .liquid-glass-badge) {
  display: none;
}

:global(.login-container.login-style-minimal .login-card::before) {
  display: none;
}

:global(.login-container.login-style-minimal .login-header) {
  margin-bottom: 24px;
}

:global(.login-container.login-style-minimal .logo-icon) {
  width: 62px;
  height: 62px;
  font-size: 34px;
  animation: none;
}

:global(.login-container.login-style-minimal .login-header h1) {
  font-size: 30px;
}

@media (max-width: 760px) {
  :global(.login-container.login-style-split .login-card) {
    display: block;
    max-width: 440px;
  }

  :global(.login-container.login-style-split .login-brand-panel) {
    min-height: 0;
    padding: 54px 24px 24px;
    border-right: 0;
    border-bottom: 1px solid var(--border-color-light);
  }

  :global(.login-container.login-style-split .login-auth-panel) {
    padding: 30px 24px;
  }
}

@media (max-width: 520px) {
  :global(html[data-theme="macos26"] .login-container) {
    padding: 16px;
  }

  :global(html[data-theme="macos26"] .login-card) {
    padding: 54px 24px 30px;
    border-radius: 26px;
  }
}

@media (prefers-reduced-motion: reduce) {
  :global(html[data-theme="macos26"] .bg-circle),
  :global(html[data-theme="macos26"] .logo-icon) {
    animation: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  :global(html[data-theme="macos26"] .login-card) {
    background: rgba(247, 250, 255, 0.94);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (max-width: 520px) {
  :global(html[data-theme="macos26"] .login-container.login-style-split .login-card) {
    padding: 0;
  }
}
</style>
