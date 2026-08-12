<template>
  <section class="background-settings card glass" aria-labelledby="background-settings-title">
    <header class="panel-header">
      <div>
        <div id="background-settings-title" class="panel-title">
          <span class="surface-icon" aria-hidden="true"><i></i><i></i><i></i></span>
          <span>背景与表面设置</span>
          <span class="theme-badge">{{ currentThemeName }}</span>
        </div>
        <p>独立调整网站背景、导航栏与卡片表面，不会改变按钮等强调色。</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-text" type="button" @click="resetCurrent">恢复当前默认</button>
        <button class="btn btn-text muted-action" type="button" @click="resetAll">重置全部</button>
      </div>
    </header>

    <div class="panel-body">
      <div class="preview-stage" :style="previewStyle">
        <div class="preview-browser">
          <div class="preview-nav">
            <span class="preview-brand"><i></i>汗牛充栋</span>
            <span class="preview-links"><i></i><i></i><i></i></span>
          </div>
          <div class="preview-page">
            <div class="preview-copy">
              <small>{{ config.mode === 'gradient' ? '渐变背景' : '纯色背景' }}</small>
              <b>让阅读空间更像你</b>
              <span></span>
            </div>
            <div class="preview-cards"><i></i><i></i><i></i></div>
          </div>
        </div>
        <span class="preview-caption">实时预览</span>
      </div>

      <div class="controls">
        <section class="control-block">
          <div class="block-heading">
            <div><strong>页面背景</strong><p>选择纯色，或使用两种颜色生成柔和渐变。</p></div>
            <div class="mode-switch" role="group" aria-label="背景模式">
              <button type="button" :class="{ active: config.mode === 'solid' }" @click="updateField('mode', 'solid', true)">纯色</button>
              <button type="button" :class="{ active: config.mode === 'gradient' }" @click="updateField('mode', 'gradient', true)">渐变</button>
            </div>
          </div>
          <div class="color-grid">
            <ColorControl label="主背景" :model-value="config.pageColor" @preview="value => updateField('pageColor', value)" @change="value => updateField('pageColor', value, true)" />
            <ColorControl v-if="config.mode === 'gradient'" label="渐变尾色" :model-value="config.secondaryColor" @preview="value => updateField('secondaryColor', value)" @change="value => updateField('secondaryColor', value, true)" />
          </div>
        </section>

        <section class="control-block surface-controls">
          <div class="surface-row">
            <ColorControl label="导航背景" :model-value="config.navColor" @preview="value => updateField('navColor', value)" @change="value => updateField('navColor', value, true)" />
            <div class="opacity-control">
              <div><strong>导航透明度</strong><span>{{ config.navOpacity }}%</span></div>
              <el-slider :model-value="config.navOpacity" :min="20" :max="100" :show-tooltip="false" @input="value => updateField('navOpacity', value)" @change="value => updateField('navOpacity', value, true)" />
            </div>
          </div>
          <div class="surface-row">
            <ColorControl label="卡片表面" :model-value="config.surfaceColor" @preview="value => updateField('surfaceColor', value)" @change="value => updateField('surfaceColor', value, true)" />
            <div class="opacity-control">
              <div><strong>卡片透明度</strong><span>{{ config.surfaceOpacity }}%</span></div>
              <el-slider :model-value="config.surfaceOpacity" :min="35" :max="100" :show-tooltip="false" @input="value => updateField('surfaceOpacity', value)" @change="value => updateField('surfaceOpacity', value, true)" />
            </div>
          </div>
        </section>

        <section class="control-block">
          <div><strong>推荐方案</strong><p>一键应用为当前主题调校的整套背景组合。</p></div>
          <div class="preset-grid">
            <button
              v-for="preset in currentPresets"
              :key="preset.name"
              type="button"
              class="preset"
              :class="{ active: isPresetActive(preset.config) }"
              :aria-label="`应用${preset.name}背景方案`"
              :aria-pressed="isPresetActive(preset.config)"
              @click="applyPreset(preset.config)"
            >
              <span class="preset-swatch" :style="presetStyle(preset.config)">
                <i></i>
                <span v-if="isPresetActive(preset.config)" class="preset-check" aria-hidden="true">✓</span>
              </span>
              <span><b>{{ preset.name }}</b><small>{{ preset.description }}</small></span>
            </button>
          </div>
        </section>
      </div>
    </div>
    <p v-if="themeStore.currentTheme === 'natural' || themeStore.currentTheme === 'macos26'" class="dock-note">Dock 的玻璃透明度仍由下方 Dock 设置单独控制。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h } from 'vue'
import { ElColorPicker, ElInput } from 'element-plus'
import { useThemeStore } from '@/stores/theme'
import { usePreferencesStore } from '@/stores/preferences'
import { THEMES, type ThemeBackgroundConfig, type ThemeId } from '@/types/theme'
import { createThemeBackgroundTokens } from '@/utils/themeBackground'
import { normalizeHexColor } from '@/utils/themeColor'
import { confirm, message } from '@/utils/message'

const ColorControl = defineComponent({
  name: 'ColorControl',
  props: { label: { type: String, required: true }, modelValue: { type: String, required: true } },
  emits: ['preview', 'change'],
  setup(props, { emit }) {
    const submit = (value: string) => {
      const color = normalizeHexColor(value)
      if (color) emit('change', color)
    }
    return () => h('div', { class: 'color-control' }, [
      h('strong', props.label),
      h('div', { class: 'color-input' }, [
        h(ElColorPicker, {
          modelValue: props.modelValue,
          'onActive-change': (value: string | null) => value && emit('preview', value),
          onChange: (value: string | null) => value && emit('change', value),
        }),
        h(ElInput, {
          modelValue: props.modelValue,
          maxlength: 7,
          onInput: (value: string) => {
            const color = normalizeHexColor(value)
            if (color) emit('preview', color)
          },
          onBlur: (event: FocusEvent) => submit((event.target as HTMLInputElement).value),
          onKeyup: (event: KeyboardEvent) => event.key === 'Enter' && submit((event.target as HTMLInputElement).value),
        }),
      ]),
    ])
  },
})

interface BackgroundPreset { name: string; description: string; config: ThemeBackgroundConfig }
const presetGroups: Record<ThemeId, BackgroundPreset[]> = {
  modern: [
    { name: '冷雾灰', description: '克制、中性', config: { mode: 'solid', pageColor: '#F5F5F5', secondaryColor: '#EEF2F7', navColor: '#FFFFFF', navOpacity: 100, surfaceColor: '#FFFFFF', surfaceOpacity: 100 } },
    { name: '纯净白', description: '明亮、极简', config: { mode: 'solid', pageColor: '#FFFFFF', secondaryColor: '#F7F8FA', navColor: '#FFFFFF', navOpacity: 100, surfaceColor: '#FFFFFF', surfaceOpacity: 100 } },
    { name: '蓝灰', description: '沉静、专业', config: { mode: 'gradient', pageColor: '#EAF0F7', secondaryColor: '#DDE6F0', navColor: '#F8FAFC', navOpacity: 92, surfaceColor: '#FFFFFF', surfaceOpacity: 88 } },
    { name: '柔和银', description: '细腻、轻盈', config: { mode: 'gradient', pageColor: '#F2F3F5', secondaryColor: '#E7E9ED', navColor: '#FAFAFB', navOpacity: 90, surfaceColor: '#FFFFFF', surfaceOpacity: 86 } },
  ],
  warm: [
    { name: '象牙纸', description: '温润、耐看', config: { mode: 'solid', pageColor: '#FAF6F1', secondaryColor: '#F3E9DC', navColor: '#FFFBF5', navOpacity: 100, surfaceColor: '#FFFBF5', surfaceOpacity: 100 } },
    { name: '杏仁', description: '柔软、明亮', config: { mode: 'gradient', pageColor: '#FAEEDD', secondaryColor: '#F4DDC2', navColor: '#FFF9EF', navOpacity: 92, surfaceColor: '#FFF9EF', surfaceOpacity: 88 } },
    { name: '浅咖', description: '安静、复古', config: { mode: 'solid', pageColor: '#EDE2D5', secondaryColor: '#E2D2C0', navColor: '#F8F0E7', navOpacity: 96, surfaceColor: '#FFF9F2', surfaceOpacity: 90 } },
    { name: '淡陶土', description: '温暖、有层次', config: { mode: 'gradient', pageColor: '#F4E1D8', secondaryColor: '#EBCFC2', navColor: '#FFF6F0', navOpacity: 88, surfaceColor: '#FFF8F3', surfaceOpacity: 84 } },
  ],
  natural: [
    { name: '晨雾绿', description: '清新、舒缓', config: { mode: 'gradient', pageColor: '#E8F5E9', secondaryColor: '#E0F2F1', navColor: '#FFFFFF', navOpacity: 75, surfaceColor: '#FFFFFF', surfaceOpacity: 72 } },
    { name: '湖水青', description: '通透、轻快', config: { mode: 'gradient', pageColor: '#DFF4F0', secondaryColor: '#D9EEF4', navColor: '#F7FFFE', navOpacity: 72, surfaceColor: '#FFFFFF', surfaceOpacity: 68 } },
    { name: '竹纸白', description: '自然、素雅', config: { mode: 'solid', pageColor: '#F3F5E9', secondaryColor: '#E8EDDA', navColor: '#FBFCF4', navOpacity: 90, surfaceColor: '#FFFFFA', surfaceOpacity: 84 } },
    { name: '浅天蓝', description: '明净、开阔', config: { mode: 'gradient', pageColor: '#E7F2F8', secondaryColor: '#E2F3EE', navColor: '#F8FCFF', navOpacity: 76, surfaceColor: '#FFFFFF', surfaceOpacity: 72 } },
  ],
  macos26: [
    { name: '虹彩天幕', description: 'Liquid Glass 默认', config: { mode: 'gradient', pageColor: '#DCEBFA', secondaryColor: '#F1E4F8', navColor: '#F8FBFF', navOpacity: 62, surfaceColor: '#FFFFFF', surfaceOpacity: 58 } },
    { name: '海湾晨光', description: '清透、明亮', config: { mode: 'gradient', pageColor: '#CDEDF4', secondaryColor: '#E9E0FA', navColor: '#F5FCFF', navOpacity: 58, surfaceColor: '#FFFFFF', surfaceOpacity: 54 } },
    { name: '银色空间', description: '克制、精密', config: { mode: 'gradient', pageColor: '#DDE3EA', secondaryColor: '#F1F3F7', navColor: '#FFFFFF', navOpacity: 66, surfaceColor: '#F9FBFD', surfaceOpacity: 62 } },
    { name: '落日玻璃', description: '柔紫、暖光', config: { mode: 'gradient', pageColor: '#F3DDE8', secondaryColor: '#DDE8FA', navColor: '#FFF8FC', navOpacity: 60, surfaceColor: '#FFFFFF', surfaceOpacity: 56 } },
  ],
}

const themeStore = useThemeStore()
const preferencesStore = usePreferencesStore()
const config = computed(() => themeStore.currentBackgroundSettings)
const currentThemeName = computed(() => THEMES.find(theme => theme.id === themeStore.currentTheme)?.name || '当前主题')
const currentPresets = computed(() => presetGroups[themeStore.currentTheme])
const previewStyle = computed(() => createThemeBackgroundTokens(config.value))

const isPresetActive = (preset: ThemeBackgroundConfig) =>
  config.value.mode === preset.mode
  && config.value.pageColor.toUpperCase() === preset.pageColor.toUpperCase()
  && config.value.secondaryColor.toUpperCase() === preset.secondaryColor.toUpperCase()
  && config.value.navColor.toUpperCase() === preset.navColor.toUpperCase()
  && config.value.navOpacity === preset.navOpacity
  && config.value.surfaceColor.toUpperCase() === preset.surfaceColor.toUpperCase()
  && config.value.surfaceOpacity === preset.surfaceOpacity

const updateField = <K extends keyof ThemeBackgroundConfig>(key: K, value: ThemeBackgroundConfig[K], persist = false) => {
  preferencesStore.setThemeBackground(themeStore.currentTheme, { ...config.value, [key]: value }, persist)
}
const applyPreset = (value: ThemeBackgroundConfig) => {
  preferencesStore.setThemeBackground(themeStore.currentTheme, value)
  message.success(`${currentThemeName.value}背景方案已更新`)
}
const presetStyle = (value: ThemeBackgroundConfig) => ({
  background: value.mode === 'gradient' ? `linear-gradient(135deg, ${value.pageColor}, ${value.secondaryColor})` : value.pageColor,
  '--preset-nav': value.navColor,
  '--preset-card': value.surfaceColor,
})
const resetCurrent = () => {
  preferencesStore.resetThemeBackground(themeStore.currentTheme)
  message.success(`${currentThemeName.value}背景已恢复默认`)
}
const resetAll = async () => {
  if (!await confirm('确定恢复全部主题的默认背景与表面设置吗？')) return
  preferencesStore.resetAllThemeBackgrounds()
  message.success('全部主题背景已恢复默认')
}
</script>

<style scoped>
.background-settings { margin-top: var(--spacing-xl); overflow: hidden; }
.panel-header { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--spacing-lg); padding: var(--spacing-lg) var(--spacing-xl); border-bottom: 1px solid var(--border-color-light); }
.panel-title { display: flex; align-items: center; gap: 10px; color: var(--text-primary); font-size: var(--font-size-lg); font-weight: 700; }
.panel-header p, .control-block p { margin: 6px 0 0; color: var(--text-secondary); font-size: var(--font-size-sm); }
.surface-icon { position: relative; width: 23px; height: 21px; }.surface-icon i { position: absolute; width: 18px; height: 12px; border: 1px solid var(--primary-alpha-30); border-radius: 5px; background: var(--surface-card); box-shadow: 0 3px 8px var(--shadow-color); }.surface-icon i:nth-child(1) { top: 0; left: 0; }.surface-icon i:nth-child(2) { top: 4px; left: 3px; }.surface-icon i:nth-child(3) { top: 8px; left: 6px; background: var(--primary-alpha-20); }
.theme-badge { padding: 3px 8px; border: 1px solid var(--primary-alpha-20); border-radius: 999px; background: var(--primary-alpha-10); color: var(--primary); font-size: 11px; }.header-actions { display: flex; flex: none; gap: 4px; }.muted-action { color: var(--text-tertiary); }
.panel-body { display: grid; grid-template-columns: minmax(310px,.85fr) minmax(450px,1.15fr); gap: var(--spacing-xl); padding: var(--spacing-xl); }
.preview-stage { position: relative; display: grid; min-height: 430px; place-items: center; overflow: hidden; border: 1px solid var(--border-color); border-radius: 24px; background: var(--bg-page-gradient); box-shadow: inset 0 1px rgba(255,255,255,.45); }.preview-stage::before { position: absolute; width: 240px; height: 240px; border-radius: 50%; background: var(--primary-alpha-15); content: ''; filter: blur(2px); transform: translate(45%,-55%); }
.preview-browser { position: relative; width: min(84%,380px); overflow: hidden; border: 1px solid var(--border-color); border-radius: 17px; background: var(--surface-card); box-shadow: 0 22px 55px var(--shadow-color); backdrop-filter: blur(18px); }.preview-nav { display: flex; height: 49px; align-items: center; justify-content: space-between; padding: 0 15px; border-bottom: 1px solid var(--nav-border); background: var(--nav-bg); color: var(--nav-text-primary); backdrop-filter: blur(14px); }.preview-brand { display: flex; align-items: center; gap: 7px; font-size: 10px; font-weight: 750; }.preview-brand i { width: 15px; height: 15px; border-radius: 5px; background: var(--primary-gradient); }.preview-links { display: flex; gap: 7px; }.preview-links i { width: 17px; height: 4px; border-radius: 99px; background: var(--nav-text-secondary); opacity: .6; }
.preview-page { min-height: 225px; padding: 29px 25px; background: var(--bg-page-gradient); color: var(--text-on-page-bg); }.preview-copy { display: grid; }.preview-copy small { color: var(--primary); font-size: 8px; font-weight: 800; letter-spacing: .08em; }.preview-copy b { margin-top: 7px; font-size: 16px; }.preview-copy > span { width: 65%; height: 5px; margin-top: 9px; border-radius: 99px; background: var(--text-on-page-bg-secondary); opacity: .4; }.preview-cards { display: grid; grid-template-columns: repeat(3,1fr); gap: 9px; margin-top: 27px; }.preview-cards i { height: 63px; border: 1px solid var(--border-color-light); border-radius: 10px; background: var(--surface-card); box-shadow: 0 8px 18px var(--shadow-color); backdrop-filter: blur(12px); }.preview-caption { position: absolute; right: 15px; bottom: 14px; padding: 5px 9px; border: 1px solid var(--border-color); border-radius: 999px; background: var(--surface-card); color: var(--text-primary); font-size: 10px; font-weight: 700; backdrop-filter: blur(12px); }
.controls { display: grid; align-content: start; gap: var(--spacing-lg); }.control-block { padding-bottom: var(--spacing-lg); border-bottom: 1px solid var(--border-color-light); }.control-block:last-child { padding-bottom: 0; border-bottom: 0; }.block-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--spacing-md); }.control-block strong { color: var(--text-primary); font-size: var(--font-size-sm); }
.mode-switch { display: flex; padding: 3px; border: 1px solid var(--border-color-light); border-radius: 10px; background: var(--surface-hover); }.mode-switch button { padding: 6px 12px; border: 0; border-radius: 7px; background: transparent; color: var(--text-secondary); cursor: pointer; font-size: 12px; }.mode-switch button.active { background: var(--surface-elevated); color: var(--primary); box-shadow: var(--shadow-sm); font-weight: 700; }.color-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: var(--spacing-md); margin-top: var(--spacing-md); }
:deep(.color-control) { min-width: 0; }:deep(.color-control > strong) { display: block; margin-bottom: 7px; color: var(--text-secondary); font-size: 12px; }:deep(.color-input) { display: grid; grid-template-columns: 40px minmax(0,1fr); gap: 8px; }:deep(.color-input .el-color-picker__trigger) { width: 40px; height: 32px; border-radius: 9px; }:deep(.color-input .el-input__wrapper) { border-radius: 9px; }
.surface-controls { display: grid; gap: var(--spacing-lg); }.surface-row { display: grid; grid-template-columns: minmax(190px,.8fr) minmax(210px,1.2fr); align-items: end; gap: var(--spacing-lg); }.opacity-control > div { display: flex; justify-content: space-between; margin-bottom: 3px; }.opacity-control span { color: var(--primary); font-size: 12px; font-weight: 700; }.opacity-control :deep(.el-slider) { height: 32px; }
.preset-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 8px; margin-top: var(--spacing-md); }.preset { min-width: 0; padding: 7px; border: 1px solid var(--border-color-light); border-radius: 11px; background: var(--surface-card); color: var(--text-primary); cursor: pointer; text-align: left; transition: .16s ease; }.preset:hover { border-color: var(--primary-alpha-30); transform: translateY(-2px); box-shadow: var(--shadow-sm); }.preset.active { border-color: var(--primary); background: var(--primary-alpha-10); box-shadow: 0 0 0 2px var(--primary-alpha-10); }.preset:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }.preset-swatch { position: relative; display: block; height: 34px; overflow: hidden; border: 1px solid rgba(0,0,0,.07); border-radius: 7px; }.preset-swatch::before { position: absolute; top: 0; bottom: 0; left: 0; width: 26%; background: var(--preset-nav); content: ''; }.preset-swatch i { position: absolute; right: 7px; bottom: 6px; width: 44%; height: 16px; border-radius: 4px; background: var(--preset-card); box-shadow: 0 3px 8px rgba(0,0,0,.12); }.preset-check { position: absolute; top: 4px; right: 4px; display: grid; width: 17px; height: 17px; place-items: center; border: 1px solid rgba(255,255,255,.8); border-radius: 50%; background: var(--primary); color: #fff; font-size: 11px; font-weight: 800; line-height: 1; box-shadow: 0 2px 7px rgba(0,0,0,.18); }.preset > span:last-child { display: grid; margin-top: 6px; }.preset b { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.preset small { margin-top: 2px; color: var(--text-tertiary); font-size: 9px; }
.dock-note { margin: 0; padding: 0 var(--spacing-xl) var(--spacing-lg); color: var(--text-tertiary); font-size: 12px; }
@media (max-width:1100px) { .panel-body { grid-template-columns: 1fr; }.preview-stage { min-height: 330px; } } @media (max-width:700px) { .panel-header { flex-direction: column; padding: var(--spacing-lg); }.header-actions { width: 100%; justify-content: flex-end; }.panel-body { padding: var(--spacing-lg); }.surface-row { grid-template-columns: 1fr; }.preset-grid { grid-template-columns: repeat(2,minmax(0,1fr)); } }
</style>
