<template>
  <div class="website-settings-stack">
    <section class="card glass website-card">
      <header class="website-header">
        <div>
          <h2>🌐 网站基本信息</h2>
          <p>统一设置登录页品牌、浏览器标题与注册入口。</p>
        </div>
        <button class="btn btn-primary" :disabled="loading || saving" @click="saveSettings">
          {{ saving ? '保存中…' : '保存网站设置' }}
        </button>
      </header>

      <div v-if="loading" class="website-loading"><span class="loading-spinner"></span>加载中...</div>
      <div v-else class="website-form">
        <div class="field-grid">
          <label class="field-item">
            <span>登录页网站名称</span>
            <input v-model.trim="form.siteName" class="input" maxlength="40" placeholder="汗牛充栋" />
            <small>显示在登录页主标题。</small>
          </label>
          <label class="field-item">
            <span>浏览器标签页名称</span>
            <input v-model.trim="form.browserTitle" class="input" maxlength="80" placeholder="汗牛充栋 - 私人书库" />
            <small>保存后立即应用到当前标签页。</small>
          </label>
        </div>

        <label class="field-item">
          <span>登录页网站简介</span>
          <textarea v-model.trim="form.loginDescription" class="input description-input" maxlength="200" rows="3" placeholder="您的私人书库管理系统"></textarea>
          <small>{{ form.loginDescription.length }}/200</small>
        </label>

        <div class="registration-row">
          <div><strong>展示注册功能</strong><p>关闭后登录页不显示注册入口，服务端也会拒绝新用户注册。</p></div>
          <el-switch v-model="form.registrationEnabled" inline-prompt active-text="开" inactive-text="关" />
        </div>

        <div class="login-icon-section">
          <div class="section-heading"><strong>登录页图标</strong><span>支持 JPG、PNG、WebP，建议使用透明背景正方形图片。</span></div>
          <div class="login-icon-row">
            <div class="login-icon-preview">
              <img v-if="iconPreview" :src="iconPreview" alt="当前登录页图标" />
              <span v-else aria-hidden="true">📚</span>
            </div>
            <div class="login-icon-actions">
              <input ref="iconInput" type="file" hidden accept="image/jpeg,image/png,image/webp" @change="uploadIcon" />
              <button class="btn btn-secondary" :disabled="uploadingIcon" @click="iconInput?.click()">
                {{ uploadingIcon ? '上传中…' : '上传新图标' }}
              </button>
              <button v-if="form.hasLoginIcon" class="btn btn-text btn-danger" :disabled="uploadingIcon" @click="restoreIcon">
                恢复默认
              </button>
              <small>单张不超过 5MB。</small>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="!loading" class="card glass website-card">
      <header class="website-header style-header">
        <div><h2>🎨 登录页样式</h2><p>每个网站主题独立保存一套登录布局。</p></div>
      </header>
      <div class="style-content">
        <div class="theme-selector" role="tablist" aria-label="选择要配置的主题">
          <button
            v-for="theme in themes"
            :key="theme.id"
            type="button"
            :class="{ active: selectedTheme === theme.id }"
            @click="selectedTheme = theme.id"
          ><span>{{ theme.icon }}</span>{{ theme.name }}</button>
        </div>
        <div class="login-style-grid">
          <button
            v-for="option in styleOptions"
            :key="option.value"
            type="button"
            class="login-style-card"
            :class="[{ active: form.loginStyles[selectedTheme] === option.value }, `preview-${option.value}`]"
            @click="form.loginStyles[selectedTheme] = option.value"
          >
            <span class="style-mini-preview" aria-hidden="true"><i></i><b></b><em></em></span>
            <span><strong>{{ option.label }}</strong><small>{{ option.description }}</small></span>
            <i class="style-check">✓</i>
          </button>
        </div>
      </div>
    </section>

    <SiteFaviconSettingsPanel />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/utils/api'
import { confirm, message } from '@/utils/message'
import SiteFaviconSettingsPanel from '@/components/SiteFaviconSettingsPanel.vue'
import { useThemeStore } from '@/stores/theme'
import { THEMES, type ThemeId } from '@/types/theme'
import {
  applyWebsiteSettings,
  loadWebsiteSettings,
  loginIconSrc,
  type LoginPageStyle,
  type WebsiteSettings,
} from '@/utils/siteSettings'

const themes = THEMES
const themeStore = useThemeStore()
const selectedTheme = ref<ThemeId>(themeStore.currentTheme)
const loading = ref(true)
const saving = ref(false)
const uploadingIcon = ref(false)
const iconInput = ref<HTMLInputElement | null>(null)

const form = reactive<WebsiteSettings>({
  siteName: '', browserTitle: '', loginDescription: '', registrationEnabled: true,
  loginStyles: { modern: 'glass', warm: 'glass', natural: 'glass', macos26: 'glass' },
  hasLoginIcon: false, loginIconVersion: 0,
})

const styleOptions: Array<{ value: LoginPageStyle; label: string; description: string }> = [
  { value: 'glass', label: '主题玻璃', description: '经典居中卡片，强调主题材质' },
  { value: 'split', label: '品牌分屏', description: '品牌介绍与登录表单左右分区' },
  { value: 'minimal', label: '简洁聚焦', description: '弱化装饰，聚焦账号登录' },
]

const syncForm = (settings: WebsiteSettings) => {
  form.siteName = settings.siteName
  form.browserTitle = settings.browserTitle
  form.loginDescription = settings.loginDescription
  form.registrationEnabled = settings.registrationEnabled
  form.loginStyles = { ...settings.loginStyles }
  form.hasLoginIcon = settings.hasLoginIcon
  form.loginIconUrl = settings.loginIconUrl
  form.loginIconVersion = settings.loginIconVersion
}

const iconPreview = computed(() => form.hasLoginIcon && form.loginIconUrl
  ? `${form.loginIconUrl}?v=${form.loginIconVersion}`
  : '')

const saveSettings = async () => {
  if (!form.siteName.trim() || !form.browserTitle.trim()) {
    message.warning('网站名称和浏览器标签页名称不能为空')
    return
  }
  saving.value = true
  try {
    const { data } = await api.put<WebsiteSettings>('/api/site/settings', {
      siteName: form.siteName,
      browserTitle: form.browserTitle,
      loginDescription: form.loginDescription,
      registrationEnabled: form.registrationEnabled,
      loginStyles: form.loginStyles,
    })
    syncForm(data)
    applyWebsiteSettings(data)
    message.success('网站设置已保存')
  } catch (error: any) {
    message.error(error.response?.data?.message || '网站设置保存失败')
  } finally {
    saving.value = false
  }
}

const uploadIcon = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    message.warning('登录页图标不能超过 5MB')
    return
  }
  uploadingIcon.value = true
  try {
    const body = new FormData()
    body.append('file', file)
    const { data } = await api.post<WebsiteSettings>('/api/site/login-icon', body)
    syncForm(data)
    applyWebsiteSettings(data)
    message.success('登录页图标已更新')
  } catch (error: any) {
    message.error(error.response?.data?.message || '登录页图标上传失败')
  } finally {
    uploadingIcon.value = false
  }
}

const restoreIcon = async () => {
  if (!await confirm('确定恢复默认登录页图标吗？')) return
  uploadingIcon.value = true
  try {
    const { data } = await api.delete<WebsiteSettings>('/api/site/login-icon')
    syncForm(data)
    applyWebsiteSettings(data)
    message.success('已恢复默认登录页图标')
  } catch (error: any) {
    message.error(error.response?.data?.message || '恢复默认图标失败')
  } finally {
    uploadingIcon.value = false
  }
}

onMounted(async () => {
  const settings = await loadWebsiteSettings(true)
  syncForm(settings)
  if (!loginIconSrc()) form.hasLoginIcon = false
  loading.value = false
})
</script>

<style scoped>
.website-settings-stack { display: grid; gap: var(--spacing-xl); }
.website-card { overflow: hidden; }
.website-header { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: var(--spacing-lg) var(--spacing-xl); border-bottom: 1px solid var(--border-color-light); }
.website-header h2 { color: var(--text-primary); font-size: var(--font-size-lg); }
.website-header p { margin-top: 5px; color: var(--text-secondary); font-size: 12px; }
.website-loading { display: flex; min-height: 220px; align-items: center; justify-content: center; gap: 10px; color: var(--text-secondary); }
.website-form, .style-content { display: grid; gap: 22px; padding: var(--spacing-xl); }
.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.field-item { display: grid; gap: 7px; color: var(--text-primary); font-size: 13px; font-weight: 650; }
.field-item small, .section-heading span, .login-icon-actions small { color: var(--text-tertiary); font-size: 11px; font-weight: 400; }
.description-input { min-height: 84px; resize: vertical; line-height: 1.6; }
.registration-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 16px 18px; border: 1px solid var(--border-color-light); border-radius: var(--radius-lg); background: var(--surface-hover); }
.registration-row strong { color: var(--text-primary); font-size: 14px; }
.registration-row p { margin-top: 4px; color: var(--text-tertiary); font-size: 12px; }
.login-icon-section { display: grid; gap: 14px; padding-top: 4px; }
.section-heading { display: grid; gap: 4px; color: var(--text-primary); }
.login-icon-row { display: flex; align-items: center; gap: 18px; }
.login-icon-preview { display: grid; width: 88px; height: 88px; flex: none; padding: 10px; place-items: center; overflow: hidden; border: 1px solid var(--border-color-light); border-radius: 24px; background: var(--surface-hover); font-size: 48px; }
.login-icon-preview img { width: 100%; height: 100%; object-fit: contain; }
.login-icon-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.login-icon-actions small { width: 100%; }
.theme-selector { display: flex; flex-wrap: wrap; gap: 8px; }
.theme-selector button { display: flex; align-items: center; gap: 6px; padding: 8px 13px; border: 1px solid var(--border-color); border-radius: 999px; background: var(--surface-card); color: var(--text-secondary); cursor: pointer; }
.theme-selector button.active { border-color: var(--primary); background: var(--primary-alpha-10); color: var(--primary); }
.login-style-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.login-style-card { position: relative; display: grid; gap: 12px; padding: 14px; border: 1px solid var(--border-color); border-radius: 18px; background: color-mix(in srgb, var(--surface-card) 88%, transparent); color: var(--text-primary); text-align: left; cursor: pointer; }
.login-style-card.active { border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary-alpha-20); }
.login-style-card > span:last-of-type { display: grid; gap: 3px; }
.login-style-card small { color: var(--text-tertiary); font-size: 11px; font-weight: 400; }
.style-mini-preview { position: relative; display: block; height: 92px; overflow: hidden; border-radius: 12px; background: linear-gradient(135deg, var(--primary-alpha-20), var(--surface-hover)); }
.style-mini-preview i, .style-mini-preview b, .style-mini-preview em { position: absolute; display: block; border-radius: 6px; background: color-mix(in srgb, var(--surface-card) 90%, white); box-shadow: 0 5px 15px rgba(0,0,0,.08); }
.preview-glass .style-mini-preview i { inset: 17px 29%; }
.preview-glass .style-mini-preview b { left: 37%; right: 37%; bottom: 25px; height: 5px; background: var(--primary); }
.preview-split .style-mini-preview i { inset: 10px 50% 10px 10px; background: var(--primary-alpha-20); }
.preview-split .style-mini-preview b { inset: 10px 10px 10px 53%; }
.preview-minimal .style-mini-preview i { inset: 13px 22%; background: transparent; box-shadow: none; border: 1px solid var(--border-color); }
.preview-minimal .style-mini-preview b { left: 32%; right: 32%; bottom: 24px; height: 4px; background: var(--primary); }
.style-check { position: absolute; top: 10px; right: 10px; display: grid; width: 21px; height: 21px; place-items: center; border-radius: 50%; background: var(--primary); color: white; font-size: 12px; font-style: normal; opacity: 0; }
.login-style-card.active .style-check { opacity: 1; }
@media (max-width: 760px) { .field-grid, .login-style-grid { grid-template-columns: 1fr; } .website-header { align-items: flex-start; flex-direction: column; } .registration-row { align-items: flex-start; } }
</style>
