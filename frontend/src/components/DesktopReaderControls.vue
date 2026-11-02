<template>
  <aside class="desktop-controls" :class="{ 'desktop-controls--quiet': immersive }" aria-label="桌面阅读控制">
    <div class="desktop-controls__bar">
      <span class="desktop-controls__drag" title="拖动窗口">⠿</span>
      <button v-if="reading" :aria-pressed="immersive" @click="immersive = !immersive">
        {{ immersive ? '退出隐身' : '隐身阅读' }}
      </button>
      <button :aria-expanded="expanded" @click="expanded = !expanded">窗口设置</button>
      <button aria-label="隐藏窗口" title="Ctrl / ⌘ + Shift + H 恢复" @click="action('hide')">隐藏</button>
      <button aria-label="最小化窗口" @click="action('minimize')">−</button>
    </div>
    <div v-if="expanded" class="desktop-controls__panel">
      <p class="desktop-controls__heading">留一隅，给阅读。</p>
      <section v-if="supportsIcon" aria-label="图标设置">
        <div class="desktop-controls__icon">
          <img v-if="iconPreview" :src="iconPreview" alt="当前应用图标" width="48" height="48">
          <label>
            应用图标
            <select :value="selectedIcon" aria-label="应用图标" @change="updateIcon(($event.target as HTMLSelectElement).value)">
              <option value="book">书页（默认）</option>
              <option value="note">便签</option>
              <option value="grid">方格</option>
              <option value="calendar">日历</option>
              <option v-if="selectedIcon === 'custom'" value="custom" disabled>自定义 PNG</option>
            </select>
          </label>
        </div>
        <label>
          上传 PNG 图标（最大 2 MiB）
          <input type="file" accept="image/png,.png" aria-label="上传 PNG 图标" :disabled="iconBusy" @change="uploadIcon">
        </label>
        <p>图标按账户保存，更新运行中的窗口、托盘及 Mac Dock；EXE 文件、Finder 应用包和已固定快捷方式的图标保持打包时的样式。</p>
      </section>
      <label v-for="slider in sliders" :key="slider.key">
        <span>{{ slider.label }} <output>{{ desktop[slider.key] }}%</output></span>
        <input
          type="range"
          :aria-label="slider.label"
          :min="slider.min"
          max="100"
          :value="desktop[slider.key]"
          @input="update(slider.key, Number(($event.target as HTMLInputElement).value))"
          @change="flush"
        >
      </label>
      <label class="desktop-controls__check">
        <input type="checkbox" :checked="desktop.alwaysOnTop" @change="update('alwaysOnTop', ($event.target as HTMLInputElement).checked)">
        隐身阅读时置顶
      </label>
      <p>进入隐身阅读后应用透明度。背景 0% 可透出其他窗口。PDF 页面和 EPUB 内嵌页面保留原背景。</p>
      <div class="desktop-controls__actions">
        <button :disabled="!immersive" @click="action('passthrough')">鼠标穿透</button>
        <button @click="action('resize-small')">小窗尺寸</button>
        <button @click="action('resize-large')">常规尺寸</button>
        <button @click="action('server')">切换书库</button>
        <button @click="action('quit')">退出应用</button>
      </div>
      <p>Ctrl / ⌘ + Shift + H：隐藏 / 恢复<br>Ctrl / ⌘ + Shift + R：恢复并退出穿透</p>
      <p v-if="error" role="alert">{{ error }}</p>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { DEFAULT_DESKTOP_READER_SETTINGS, usePreferencesStore, type DesktopReaderSettings } from '@/stores/preferences'
import '@/assets/desktop-reader.css'
import { prepareDesktopIcon } from '@/utils/desktopIcon'

const bridge = window.desktopReader!
const supportsIcon = typeof bridge.icon === 'function'
const route = useRoute()
const preferences = usePreferencesStore()
const reading = computed(() => route.name === 'Reader')
const immersive = ref(false)
const expanded = ref(false)
const error = ref('')
const iconPreview = ref('')
const iconBusy = ref(false)
const desktop = computed(() => preferences.readerSettings.desktop ?? DEFAULT_DESKTOP_READER_SETTINGS)
const selectedIcon = computed(() => desktop.value.icon?.startsWith('data:') ? 'custom' : desktop.value.icon ?? 'book')
const sliders = [
  { key: 'opacity' as const, label: '整体不透明度', min: 15 },
  { key: 'backgroundOpacity' as const, label: '背景不透明度', min: 0 },
  { key: 'textOpacity' as const, label: '文字不透明度', min: 15 },
]

function update(key: keyof DesktopReaderSettings, value: number | boolean) {
  preferences.readerSettings.desktop = { ...desktop.value, [key]: value }
  preferences.setReaderSettings({ ...preferences.readerSettings })
}

async function updateIcon(value: string, accountToken = localStorage.getItem('token')) {
  error.value = ''
  try {
    if (localStorage.getItem('token') !== accountToken) return
    // Validate in the native process before changing the saved preference.
    iconPreview.value = await bridge.icon(value)
    if (localStorage.getItem('token') !== accountToken) return
    preferences.readerSettings.desktop = { ...desktop.value, icon: value }
    preferences.setReaderSettings({ ...preferences.readerSettings })
    await flush()
  } catch (reason) {
    error.value = String(reason)
  }
}

async function uploadIcon(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  iconBusy.value = true
  error.value = ''
  const accountToken = localStorage.getItem('token')
  try {
    await updateIcon(await prepareDesktopIcon(file), accountToken)
  } catch (reason) {
    error.value = String(reason)
  } finally {
    iconBusy.value = false
    input.value = ''
  }
}

watch(() => desktop.value.icon, value => {
  if (!supportsIcon) return
  void bridge.icon(value ?? 'book')
    .then(preview => { iconPreview.value = preview })
    .catch(reason => { error.value = String(reason) })
}, { immediate: true })

async function flush() {
  try { await preferences.flushReaderSettings() } catch { error.value = '设置同步失败，请检查网络后重试。' }
}

async function action(name: string) {
  error.value = ''
  if (name === 'server' || name === 'quit') await flush()
  try { await bridge.action(name) } catch (reason) { error.value = String(reason) }
}

watch(() => route.fullPath, () => { immersive.value = false })
// Logout/account changes must never leave a transparent login screen.
watch(() => preferences.readerSettings, () => {
  if (!localStorage.getItem('token')) immersive.value = false
})
watchEffect(() => {
  const enabled = immersive.value && reading.value
  document.documentElement.classList.toggle('desktop-reading', enabled)
  document.documentElement.style.setProperty('--desktop-background-alpha', String(desktop.value.backgroundOpacity / 100))
  document.documentElement.style.setProperty('--desktop-text-alpha', String(desktop.value.textOpacity / 100))
  void bridge.settings({ immersive: enabled, opacity: desktop.value.opacity, alwaysOnTop: desktop.value.alwaysOnTop })
    .catch(reason => { error.value = String(reason) })
})

const removeRecover = bridge.onRecover(() => {
  immersive.value = false
  expanded.value = true
})
onBeforeUnmount(() => {
  removeRecover()
  document.documentElement.classList.remove('desktop-reading')
  void flush()
})
</script>

<style scoped>
.desktop-controls {
  position: fixed;
  right: 12px;
  bottom: 12px;
  z-index: 20000;
  max-width: calc(100vw - 24px);
  color: #343b36;
  font: 13px/1.6 "Songti SC", "Microsoft YaHei", serif;
}
.desktop-controls__bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px;
  border: 1px solid #c7ccbd;
  border-radius: 12px;
  background: #f5f3eb;
}
.desktop-controls button {
  padding: 6px 9px;
  color: inherit;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  font: inherit;
  -webkit-app-region: no-drag;
}
.desktop-controls button:hover { background: #e1e6d9; }
.desktop-controls button:disabled { opacity: .45; cursor: default; }
.desktop-controls button:focus-visible,
.desktop-controls input:focus-visible,
.desktop-controls select:focus-visible {
  outline: 2px solid #63765a;
  outline-offset: 2px;
}
.desktop-controls__drag { padding: 6px 12px; -webkit-app-region: drag; cursor: move; }
.desktop-controls__panel {
  width: 320px;
  max-width: 100%;
  max-height: calc(100vh - 90px);
  overflow: auto;
  padding: 20px;
  margin-top: 8px;
  border: 1px solid #c7ccbd;
  border-radius: 12px;
  background: #f5f3eb;
}
.desktop-controls__heading { font-size: 19px; margin-bottom: 16px; }
.desktop-controls__icon {
  display: flex;
  gap: 14px;
  align-items: center;
}
.desktop-controls__icon img {
  border-radius: 10px;
  flex-shrink: 0;
}
.desktop-controls select {
  padding: 6px 10px;
  border: 1px solid #c7ccbd;
  border-radius: 6px;
  background: #fffdf8;
  color: inherit;
  font: inherit;
}
.desktop-controls label { display: grid; gap: 6px; margin: 14px 0; }
.desktop-controls output { float: right; font-variant-numeric: tabular-nums; }
.desktop-controls input[type="range"] { width: 100%; accent-color: #63765a; }
.desktop-controls .desktop-controls__check { display: flex; align-items: center; }
.desktop-controls__actions { display: flex; flex-wrap: wrap; gap: 4px; margin: 12px 0; }
.desktop-controls__panel p:not(.desktop-controls__heading) { font-size: 12px; color: #656d60; }
.desktop-controls--quiet { opacity: .04; transition: opacity .2s ease; }
.desktop-controls--quiet:hover, .desktop-controls--quiet:focus-within { opacity: 1; }
@media (prefers-reduced-motion: reduce) {
  .desktop-controls--quiet { transition: none; }
}
</style>
