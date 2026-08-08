<template>
  <section class="theme-color-settings card glass" aria-labelledby="theme-color-title">
    <header class="settings-header">
      <div>
        <div id="theme-color-title" class="settings-title">
          <span class="color-wheel" :style="{ '--accent': activeColor }" aria-hidden="true"></span>
          <span>主题色设置</span>
          <span class="theme-badge">{{ currentThemeName }}</span>
        </div>
        <p>当前主题拥有独立配色，按钮、导航及 Element Plus 组件会实时同步。</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-text" type="button" @click="resetCurrent">恢复当前默认</button>
        <button class="btn btn-text muted-action" type="button" @click="resetAll">重置全部</button>
      </div>
    </header>

    <div class="settings-body">
      <div class="live-preview" :style="previewStyle">
        <span class="ambient ambient-one"></span>
        <span class="ambient ambient-two"></span>
        <div class="preview-window">
          <div class="window-bar"><i></i><i></i><i></i></div>
          <div class="window-body">
            <aside class="mini-nav">
              <span class="mini-logo"></span>
              <i></i><i class="active"></i><i></i>
            </aside>
            <main class="mini-content">
              <small>{{ currentThemeName }}</small>
              <span class="mini-title"></span>
              <span class="mini-line"></span>
              <div class="mini-actions"><b>主题色</b><i>预览</i></div>
            </main>
          </div>
        </div>
        <div class="color-value"><i :style="{ background: activeColor }"></i>{{ activeColor }}</div>
      </div>

      <div class="controls">
        <section class="control-section">
          <div class="picker-heading">
            <div>
              <strong>自定义颜色</strong>
              <p>打开色盘自由取色，或输入六位 HEX 色值。</p>
            </div>
            <el-color-picker
              :model-value="activeColor"
              :predefine="currentPresets.map(item => item.color)"
              color-format="hex"
              aria-label="选择主题色"
              @active-change="previewColor"
              @change="commitColor"
            />
          </div>
          <div class="hex-row">
            <el-input
              v-model="draftColor"
              maxlength="7"
              placeholder="#2563EB"
              aria-label="主题色 HEX 值"
              @keyup.enter="applyDraft"
            >
              <template #prefix><span class="hex-prefix">HEX</span></template>
            </el-input>
            <el-button type="primary" @click="applyDraft">应用</el-button>
          </div>
          <p v-if="colorError" class="color-error">请输入完整的六位 HEX 色值，例如 #2563EB。</p>
        </section>

        <section class="control-section">
          <strong>常用色</strong>
          <p>为「{{ currentThemeName }}」挑选的协调色组。</p>
          <div class="preset-grid">
            <button
              v-for="preset in currentPresets"
              :key="preset.color"
              type="button"
              class="preset"
              :class="{ active: activeColor === preset.color }"
              :aria-label="`选择${preset.name} ${preset.color}`"
              :aria-pressed="activeColor === preset.color"
              @click="selectPreset(preset.color)"
            >
              <span class="swatch" :style="{ background: preset.color }">
                {{ activeColor === preset.color ? '✓' : '' }}
              </span>
              <span><b>{{ preset.name }}</b><small>{{ preset.color }}</small></span>
            </button>
          </div>
        </section>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { usePreferencesStore } from '@/stores/preferences'
import { THEMES, type ThemeId } from '@/types/theme'
import { createThemeColorTokens, normalizeHexColor } from '@/utils/themeColor'
import { confirm, message } from '@/utils/message'

const presetGroups: Record<ThemeId, Array<{ name: string; color: string }>> = {
  modern: [
    { name: '钴蓝', color: '#2563EB' }, { name: '科技青', color: '#0891B2' },
    { name: '翡翠', color: '#059669' }, { name: '石墨', color: '#475569' },
    { name: '炽橙', color: '#EA580C' }, { name: '朱红', color: '#DC2626' },
  ],
  warm: [
    { name: '陶土', color: '#A0522D' }, { name: '焦糖', color: '#C56A3A' },
    { name: '琥珀', color: '#B7791F' }, { name: '橄榄', color: '#6F713F' },
    { name: '酒红', color: '#9B4A4A' }, { name: '胡桃', color: '#7C5C47' },
  ],
  natural: [
    { name: '苔藓', color: '#2E7D5A' }, { name: '青瓷', color: '#3C8D78' },
    { name: '湖水', color: '#2F7F83' }, { name: '松针', color: '#316B52' },
    { name: '鼠尾草', color: '#718C62' }, { name: '银杏', color: '#A77A3D' },
  ],
}

const themeStore = useThemeStore()
const preferencesStore = usePreferencesStore()
const draftColor = ref(themeStore.currentAccentColor)
const colorError = ref(false)
const activeColor = computed(() => themeStore.currentAccentColor)
const currentThemeName = computed(() => THEMES.find(item => item.id === themeStore.currentTheme)?.name || '当前主题')
const currentPresets = computed(() => presetGroups[themeStore.currentTheme])
const previewStyle = computed(() => createThemeColorTokens(activeColor.value))

watch([() => themeStore.currentTheme, activeColor], () => {
  draftColor.value = activeColor.value
  colorError.value = false
})

const applyColor = (value: string, persist: boolean) => {
  const color = normalizeHexColor(value)
  if (!color) {
    colorError.value = true
    return false
  }
  colorError.value = false
  draftColor.value = color
  preferencesStore.setThemeAccentColor(themeStore.currentTheme, color, persist)
  return true
}

const previewColor = (value: string | null) => value && applyColor(value, false)
const commitColor = (value: string | null) => {
  if (value && applyColor(value, true)) message.success(`${currentThemeName.value}主题色已更新`)
}
const applyDraft = () => {
  if (applyColor(draftColor.value, true)) message.success(`${currentThemeName.value}主题色已更新`)
}
const selectPreset = (color: string) => {
  applyColor(color, true)
  message.success(`${currentThemeName.value}主题色已更新`)
}
const resetCurrent = () => {
  preferencesStore.resetThemeAccentColor(themeStore.currentTheme)
  message.success(`${currentThemeName.value}主题色已恢复默认`)
}
const resetAll = async () => {
  if (!await confirm('确定恢复全部主题的默认颜色吗？')) return
  preferencesStore.resetAllThemeAccentColors()
  message.success('全部主题色已恢复默认')
}
</script>

<style scoped>
.theme-color-settings { margin-top: var(--spacing-xl); overflow: hidden; }
.settings-header { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--spacing-lg); padding: var(--spacing-lg) var(--spacing-xl); border-bottom: 1px solid var(--border-color-light); }
.settings-title { display: flex; align-items: center; gap: 10px; color: var(--text-primary); font-size: var(--font-size-lg); font-weight: 700; }
.settings-header p, .control-section p { margin-top: 6px; color: var(--text-secondary); font-size: var(--font-size-sm); }
.color-wheel { width: 22px; height: 22px; border: 3px solid rgba(255,255,255,.88); border-radius: 50%; background: conic-gradient(var(--accent), #f1c46b, #79b99a, var(--accent)); box-shadow: 0 3px 10px var(--primary-alpha-20); }
.theme-badge { padding: 3px 8px; border: 1px solid var(--primary-alpha-20); border-radius: 999px; background: var(--primary-alpha-10); color: var(--primary); font-size: 11px; }
.header-actions { display: flex; flex: none; gap: 4px; }
.muted-action { color: var(--text-tertiary); }
.settings-body { display: grid; grid-template-columns: minmax(300px,.9fr) minmax(380px,1.1fr); gap: var(--spacing-xl); padding: var(--spacing-xl); }
.live-preview { position: relative; display: flex; min-height: 330px; align-items: center; justify-content: center; overflow: hidden; border: 1px solid var(--primary-alpha-20); border-radius: 24px; background: linear-gradient(145deg, var(--el-color-primary-light-9), var(--el-color-primary-light-7)); box-shadow: inset 0 1px white, 0 18px 42px var(--primary-alpha-15); isolation: isolate; }
.ambient { position: absolute; z-index: -1; border-radius: 50%; }
.ambient-one { top: -70px; right: -50px; width: 240px; height: 240px; background: var(--primary-alpha-20); }
.ambient-two { bottom: -90px; left: -60px; width: 230px; height: 230px; background: rgba(255,255,255,.55); }
.preview-window { width: min(82%,340px); overflow: hidden; border: 1px solid rgba(255,255,255,.76); border-radius: 16px; background: rgba(255,255,255,.8); box-shadow: 0 20px 45px var(--primary-alpha-20); backdrop-filter: blur(18px); }
.window-bar { display: flex; gap: 5px; padding: 9px 11px; border-bottom: 1px solid var(--primary-alpha-10); }
.window-bar i { width: 6px; height: 6px; border-radius: 50%; background: var(--el-color-primary-light-7); }
.window-body { display: grid; grid-template-columns: 52px 1fr; min-height: 172px; }
.mini-nav { display: flex; align-items: center; flex-direction: column; gap: 14px; padding: 15px 10px; background: var(--el-color-primary-light-9); }
.mini-nav i { width: 18px; height: 4px; border-radius: 9px; background: var(--el-color-primary-light-7); }
.mini-nav i.active { width: 25px; height: 7px; background: var(--primary); }
.mini-logo { width: 20px; height: 20px; margin-bottom: 6px; border-radius: 7px; background: var(--primary-gradient); box-shadow: 0 4px 10px var(--primary-alpha-30); }
.mini-content { padding: 26px 24px; }
.mini-content small { color: var(--primary); font-size: 9px; font-weight: 750; letter-spacing: .08em; }
.mini-title, .mini-line { display: block; border-radius: 999px; }
.mini-title { width: 68%; height: 12px; margin-top: 10px; background: var(--primary-dark); opacity: .82; }
.mini-line { width: 88%; height: 6px; margin-top: 10px; background: var(--el-color-primary-light-7); }
.mini-actions { display: flex; gap: 8px; margin-top: 24px; }
.mini-actions b, .mini-actions i { padding: 6px 10px; border-radius: 7px; font-size: 8px; font-style: normal; }
.mini-actions b { background: var(--primary-gradient); color: white; box-shadow: 0 5px 12px var(--primary-alpha-20); }
.mini-actions i { border: 1px solid var(--primary-alpha-20); color: var(--primary); }
.color-value { position: absolute; right: 14px; bottom: 14px; display: flex; align-items: center; gap: 6px; padding: 5px 9px; border: 1px solid rgba(255,255,255,.7); border-radius: 999px; background: rgba(255,255,255,.6); color: var(--primary-dark); font-size: 10px; font-weight: 700; backdrop-filter: blur(10px); }
.color-value i { width: 8px; height: 8px; border-radius: 50%; }
.controls { display: grid; align-content: center; gap: var(--spacing-xl); }
.control-section { padding-bottom: var(--spacing-xl); border-bottom: 1px solid var(--border-color-light); }
.control-section:last-child { padding-bottom: 0; border-bottom: 0; }
.control-section > strong, .picker-heading strong { color: var(--text-primary); font-size: var(--font-size-sm); }
.picker-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--spacing-lg); }
.picker-heading :deep(.el-color-picker__trigger) { width: 46px; height: 46px; border-radius: 13px; border-color: var(--primary-alpha-20); }
.hex-row { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: var(--spacing-sm); margin-top: var(--spacing-lg); }
.hex-prefix { color: var(--text-tertiary); font-size: 10px; font-weight: 750; letter-spacing: .08em; }
.color-error { color: var(--danger) !important; font-size: 12px !important; }
.preset-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 9px; margin-top: var(--spacing-md); }
.preset { display: flex; align-items: center; gap: 9px; min-width: 0; padding: 9px; border: 1px solid var(--border-color-light); border-radius: 11px; background: var(--surface-card); color: var(--text-primary); cursor: pointer; text-align: left; transition: .16s ease; }
.preset:hover { border-color: var(--primary-alpha-30); background: var(--surface-hover); transform: translateY(-2px); }
.preset.active { border-color: var(--primary); background: var(--primary-alpha-10); box-shadow: 0 0 0 2px var(--primary-alpha-10); }
.swatch { display: grid; width: 28px; height: 28px; flex: 0 0 28px; place-items: center; border: 2px solid rgba(255,255,255,.9); border-radius: 9px; box-shadow: 0 2px 7px rgba(22,45,34,.16); color: white; }
.preset > span:last-child { display: grid; min-width: 0; }
.preset b { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.preset small { margin-top: 2px; color: var(--text-tertiary); font-size: 9px; }
@media (max-width:1080px) { .settings-body { grid-template-columns: 1fr; } .live-preview { min-height: 280px; } }
@media (max-width:640px) { .settings-header { flex-direction: column; padding: var(--spacing-lg); } .header-actions { width: 100%; justify-content: flex-end; } .settings-body { padding: var(--spacing-lg); } .preset-grid { grid-template-columns: repeat(2,minmax(0,1fr)); } }
</style>
