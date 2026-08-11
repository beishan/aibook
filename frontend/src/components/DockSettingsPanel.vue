<template>
  <section class="dock-settings card glass" aria-labelledby="dock-settings-title">
    <div class="dock-settings-header">
      <div>
        <div id="dock-settings-title" class="dock-settings-title">
          <span class="dock-settings-symbol" aria-hidden="true"></span>
          <span>Dock 设置</span>
          <span class="dock-settings-badge">MACOS</span>
        </div>
        <p>调整液态玻璃 Dock 的尺寸、通透感与悬浮反馈，效果会实时应用。</p>
      </div>
      <button type="button" class="btn btn-text dock-reset" @click="handleReset">恢复默认</button>
    </div>

    <div class="dock-settings-body">
      <div class="dock-preview-stage">
        <div class="preview-wallpaper" aria-hidden="true">
          <span class="preview-orb preview-orb-one"></span>
          <span class="preview-orb preview-orb-two"></span>
        </div>
        <div class="dock-preview" :style="previewStyle">
          <div
            v-for="(item, index) in previewItems"
            :key="item.label"
            class="dock-preview-item"
            :class="{
              active: index === 1,
              magnified: index === 2,
              custom: preferencesStore.dockIconStyle === 'custom',
            }"
          >
            <DockIcon
              class="dock-preview-icon"
              :name="item.icon"
              :variant="preferencesStore.dockIconStyle"
              :custom-src="dockIconStore.iconUrls[item.icon]"
              aria-hidden="true"
            />
            <span class="dock-preview-dot"></span>
          </div>
        </div>
        <span class="preview-caption">实时预览</span>
      </div>

      <div class="dock-controls">
        <section class="dock-icon-config" aria-labelledby="dock-icon-style-title">
          <div class="dock-icon-config-copy">
            <div id="dock-icon-style-title" class="dock-control-label">图标风格</div>
            <p>选择清晰克制的现代图标，或带真实材质和光影的经典拟物图标。</p>
          </div>
          <div class="dock-icon-options">
            <button
              v-for="option in iconStyleOptions"
              :key="option.value"
              type="button"
              class="dock-icon-option"
              :class="{ active: preferencesStore.dockIconStyle === option.value }"
              :aria-pressed="preferencesStore.dockIconStyle === option.value"
              @click="preferencesStore.setDockIconStyle(option.value)"
            >
              <span class="dock-icon-option-preview" aria-hidden="true">
                <DockIcon
                  v-for="icon in option.previewIcons"
                  :key="icon"
                  :name="icon"
                  :variant="option.value"
                  :custom-src="option.value === 'custom' ? dockIconStore.iconUrls[icon] : undefined"
                />
              </span>
              <span class="dock-icon-option-text">
                <strong>{{ option.label }}</strong>
                <small>{{ option.description }}</small>
              </span>
              <span class="dock-icon-option-check" aria-hidden="true">✓</span>
            </button>
          </div>

          <div v-if="preferencesStore.dockIconStyle === 'custom'" class="custom-icon-settings">
            <div class="custom-icon-heading">
              <strong>自定义图标</strong>
              <small>支持 JPG、PNG、WebP，单张不超过 5MB；建议使用透明背景的正方形图片。</small>
            </div>
            <div class="custom-icon-grid">
              <article v-for="item in previewItems" :key="item.icon" class="custom-icon-card">
                <div class="custom-icon-image" :class="{ empty: !dockIconStore.iconUrls[item.icon] }">
                  <img
                    v-if="dockIconStore.iconUrls[item.icon]"
                    :src="dockIconStore.iconUrls[item.icon]"
                    :alt="`${item.label}自定义图标`"
                  />
                  <DockIcon v-else :name="item.icon" variant="minimal" aria-hidden="true" />
                </div>
                <span class="custom-icon-name">{{ item.label }}</span>
                <div class="custom-icon-actions">
                  <label
                    class="btn btn-secondary custom-icon-upload"
                    :class="{ disabled: dockIconStore.uploading[item.icon] }"
                  >
                    <span>{{ dockIconStore.uploading[item.icon] ? '处理中…' : '上传' }}</span>
                    <input
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      :disabled="dockIconStore.uploading[item.icon]"
                      @change="event => handleIconUpload(item, event)"
                    />
                  </label>
                  <button
                    v-if="dockIconStore.iconUrls[item.icon]"
                    type="button"
                    class="btn btn-text custom-icon-remove"
                    :disabled="dockIconStore.uploading[item.icon]"
                    @click="handleIconRemove(item)"
                  >
                    移除
                  </button>
                </div>
              </article>
            </div>
          </div>
        </section>

        <div v-for="control in controls" :key="control.key" class="dock-control">
          <div class="dock-control-copy">
            <div class="dock-control-label">
              <span>{{ control.label }}</span>
              <output>{{ control.value }}{{ control.unit }}</output>
            </div>
            <p>{{ control.description }}</p>
          </div>
          <el-slider
            :model-value="control.value"
            :min="control.min"
            :max="control.max"
            :step="control.step"
            :show-tooltip="false"
            :aria-label="control.label"
            @input="value => updateControl(control.key, value, false)"
            @change="value => updateControl(control.key, value, true)"
          />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import DockIcon, { type DockIconName, type DockIconStyle } from '@/components/DockIcon.vue'
import { usePreferencesStore } from '@/stores/preferences'
import { useDockIconStore } from '@/stores/dockIcons'
import { message } from '@/utils/message'

type DockControlKey = 'size' | 'opacity' | 'magnification' | 'blur'

const preferencesStore = usePreferencesStore()
const dockIconStore = useDockIconStore()
const previewItems: Array<{ icon: DockIconName; label: string }> = [
  { icon: 'home', label: '首页' },
  { icon: 'library', label: '书库' },
  { icon: 'shelf', label: '书架' },
  { icon: 'repair', label: '修复' },
  { icon: 'settings', label: '设置' },
]
const iconStyleOptions: Array<{
  value: DockIconStyle
  label: string
  description: string
  previewIcons: DockIconName[]
}> = [
  {
    value: 'minimal',
    label: '现代简洁',
    description: '高对比矢量图形',
    previewIcons: ['home', 'library', 'settings'],
  },
  {
    value: 'skeuomorphic',
    label: '经典拟物',
    description: '纸张、木纹与金属质感',
    previewIcons: ['home', 'library', 'settings'],
  },
  {
    value: 'macos26',
    label: 'macOS 26',
    description: '液态玻璃与彩色渐变',
    previewIcons: ['home', 'library', 'settings'],
  },
  {
    value: 'custom',
    label: '自定义',
    description: '完整显示自己上传的图片',
    previewIcons: ['home', 'library', 'settings'],
  },
]

const controls = computed(() => [
  {
    key: 'size' as const,
    label: 'Dock 大小',
    description: '调整图标和玻璃托盘的整体尺寸。',
    value: preferencesStore.dockSize,
    min: 44,
    max: 76,
    step: 1,
    unit: ' px',
  },
  {
    key: 'opacity' as const,
    label: '透明度',
    description: '控制玻璃底色的浓淡，数值越低越通透。',
    value: preferencesStore.dockOpacity,
    min: 40,
    max: 96,
    step: 1,
    unit: '%',
  },
  {
    key: 'magnification' as const,
    label: '悬浮放大',
    description: '设置指针靠近图标时的最大放大比例。',
    value: preferencesStore.dockMagnification,
    min: 100,
    max: 150,
    step: 1,
    unit: '%',
  },
  {
    key: 'blur' as const,
    label: '玻璃模糊',
    description: '调整背景折射的柔和程度。',
    value: preferencesStore.dockBlur,
    min: 8,
    max: 40,
    step: 1,
    unit: ' px',
  },
])

const previewStyle = computed(() => ({
  '--preview-size': `${Math.round(preferencesStore.dockSize * 0.72)}px`,
  '--preview-opacity': String(preferencesStore.dockOpacity / 100),
  '--preview-scale': String(preferencesStore.dockMagnification / 100),
  '--preview-blur': `${preferencesStore.dockBlur}px`,
}))

const numericValue = (value: number | number[]) => Array.isArray(value) ? value[0] : value

const updateControl = (key: DockControlKey, value: number | number[], persist: boolean) => {
  const nextValue = numericValue(value)
  if (key === 'size') preferencesStore.setDockSize(nextValue, persist)
  if (key === 'opacity') preferencesStore.setDockOpacity(nextValue, persist)
  if (key === 'magnification') preferencesStore.setDockMagnification(nextValue, persist)
  if (key === 'blur') preferencesStore.setDockBlur(nextValue, persist)
}

const handleReset = () => {
  preferencesStore.resetDockAppearance()
  message.success('Dock 外观已恢复默认设置')
}

const errorMessage = (error: unknown) => {
  if (error instanceof Error && error.message) return error.message
  return '图标操作失败，请稍后重试'
}

const handleIconUpload = async (
  item: { icon: DockIconName; label: string },
  event: Event,
) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    await dockIconStore.upload(item.icon, file)
    message.success(`${item.label}图标已更新`)
  } catch (error) {
    message.error(errorMessage(error))
  }
}

const handleIconRemove = async (item: { icon: DockIconName; label: string }) => {
  try {
    await dockIconStore.remove(item.icon)
    message.success(`${item.label}图标已移除`)
  } catch (error) {
    message.error(errorMessage(error))
  }
}

onMounted(() => {
  void dockIconStore.hydrate()
})
</script>

<style scoped>
.dock-settings {
  margin-top: var(--spacing-xl);
  overflow: hidden;
}

.dock-settings-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-lg);
  padding: var(--spacing-lg) var(--spacing-xl);
  border-bottom: 1px solid var(--border-color-light);
}

.dock-settings-title {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-primary);
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.dock-settings-symbol {
  width: 25px;
  height: 8px;
  border: 1px solid rgba(255, 255, 255, 0.84);
  border-radius: 999px;
  background: linear-gradient(110deg, rgba(255, 255, 255, 0.92), rgba(164, 218, 190, 0.52));
  box-shadow: 0 4px 12px rgba(24, 92, 63, 0.18), inset 0 1px 2px white;
}

.dock-settings-badge {
  padding: 3px 8px;
  border: 1px solid var(--primary-alpha-20);
  border-radius: var(--radius-full);
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-size: 11px;
  font-weight: 600;
}

.dock-settings-header p {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.dock-reset {
  flex: none;
}

.dock-settings-body {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(340px, 1.1fr);
  gap: var(--spacing-xl);
  padding: var(--spacing-xl);
}

.dock-preview-stage {
  position: relative;
  display: flex;
  min-height: 300px;
  align-items: flex-end;
  justify-content: center;
  padding: 36px 24px 48px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 24px;
  background: linear-gradient(155deg, #cfeadd 0%, #e5f5ed 52%, #bcded0 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9), 0 18px 45px rgba(31, 94, 68, 0.12);
  isolation: isolate;
}

.preview-wallpaper {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: -1;
}

.preview-wallpaper::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 72% 18%, rgba(255, 255, 255, 0.8), transparent 34%),
    linear-gradient(120deg, transparent 35%, rgba(255, 255, 255, 0.28) 35.5%, transparent 36%);
}

.preview-orb {
  position: absolute;
  border-radius: 42% 58% 63% 37% / 46% 38% 62% 54%;
  filter: blur(1px);
}

.preview-orb-one {
  top: -38px;
  right: -42px;
  width: 210px;
  height: 210px;
  background: linear-gradient(145deg, rgba(40, 133, 93, 0.34), rgba(255, 255, 255, 0.12));
  transform: rotate(24deg);
}

.preview-orb-two {
  bottom: -76px;
  left: -52px;
  width: 240px;
  height: 190px;
  background: linear-gradient(155deg, rgba(252, 255, 231, 0.7), rgba(99, 180, 141, 0.25));
  transform: rotate(-14deg);
}

.dock-preview {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 7px 10px 8px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: calc(var(--preview-size) * 0.43);
  background: rgba(248, 255, 251, var(--preview-opacity));
  box-shadow: 0 16px 38px rgba(17, 74, 51, 0.2), inset 0 1px 1px white;
  backdrop-filter: blur(var(--preview-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--preview-blur)) saturate(180%);
}

.dock-preview::after {
  content: '';
  position: absolute;
  inset: 1px 8% auto;
  height: 42%;
  border-radius: inherit;
  background: linear-gradient(to bottom, rgba(255, 255, 255, 0.6), transparent);
  pointer-events: none;
}

.dock-preview-item {
  position: relative;
  display: grid;
  width: var(--preview-size);
  height: var(--preview-size);
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 27%;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.82), rgba(212, 239, 226, 0.64));
  box-shadow: 0 6px 16px rgba(18, 82, 55, 0.13), inset 0 1px 0 white;
  transform-origin: bottom center;
}

.dock-preview-item.magnified {
  z-index: 2;
  transform: translateY(-6px) scale(var(--preview-scale));
}

.dock-preview-item.custom {
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.dock-preview-icon {
  width: calc(var(--preview-size) * 0.46);
  height: calc(var(--preview-size) * 0.46);
  color: #236f4d;
  font-size: calc(var(--preview-size) * 0.46);
  filter: drop-shadow(0 1px 1px rgba(14, 67, 45, 0.1));
}

.dock-preview-icon :deep(svg) {
  width: 100%;
  height: 100%;
  shape-rendering: geometricPrecision;
}

.dock-preview-icon.dock-glyph--skeuomorphic {
  width: calc(var(--preview-size) * 0.66);
  height: calc(var(--preview-size) * 0.66);
  filter: none;
}

.dock-preview-icon.dock-glyph--macos26 {
  width: calc(var(--preview-size) * 0.66);
  height: calc(var(--preview-size) * 0.66);
  filter: none;
}

.dock-preview-icon.dock-glyph--custom {
  width: 100%;
  height: 100%;
  filter: none;
}

.dock-preview-dot {
  position: absolute;
  bottom: -5px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: transparent;
}

.dock-preview-item.active .dock-preview-dot {
  background: #236f4d;
}

.preview-caption {
  position: absolute;
  top: 16px;
  left: 18px;
  padding: 5px 9px;
  border: 1px solid rgba(255, 255, 255, 0.65);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.42);
  color: rgba(26, 74, 53, 0.72);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.04em;
  backdrop-filter: blur(10px);
}

.dock-controls {
  display: grid;
  align-content: center;
  gap: 6px;
}

.dock-icon-config {
  display: grid;
  gap: 13px;
  padding: 4px 4px 16px;
  border-bottom: 1px solid var(--border-color-light);
}

.dock-icon-config-copy p {
  margin-top: 5px;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.dock-icon-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(118px, 1fr));
  gap: 10px;
}

.dock-icon-option {
  position: relative;
  display: grid;
  min-width: 0;
  gap: 9px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 15px;
  background: color-mix(in srgb, var(--surface-card) 82%, transparent);
  color: var(--text-primary);
  text-align: left;
  transition: border-color 160ms ease, background 160ms ease, transform 160ms ease;
  cursor: pointer;
}

.dock-icon-option:hover {
  border-color: var(--primary-alpha-30);
  background: var(--primary-alpha-10);
  transform: translateY(-1px);
}

.dock-icon-option.active {
  border-color: var(--primary);
  background: linear-gradient(145deg, var(--primary-alpha-10), rgba(255, 255, 255, 0.56));
  box-shadow: inset 0 0 0 1px var(--primary-alpha-10);
}

.dock-icon-option-preview {
  display: flex;
  height: 36px;
  align-items: center;
  gap: 7px;
  color: var(--primary-dark);
}

.dock-icon-option-preview :deep(.dock-glyph) {
  width: 30px;
  height: 30px;
}

.dock-icon-option-preview :deep(.dock-glyph--minimal) {
  width: 21px;
  height: 21px;
  padding: 4px;
  border-radius: 8px;
  background: var(--primary-alpha-10);
}

.dock-icon-option-text {
  display: grid;
  gap: 2px;
}

.dock-icon-option-text strong {
  font-size: 13px;
  font-weight: 700;
}

.dock-icon-option-text small {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dock-icon-option-check {
  position: absolute;
  top: 9px;
  right: 9px;
  display: grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border-radius: 50%;
  background: var(--primary);
  color: white;
  font-size: 11px;
  font-weight: 800;
  opacity: 0;
  transform: scale(0.7);
  transition: opacity 160ms ease, transform 160ms ease;
}

.dock-icon-option.active .dock-icon-option-check {
  opacity: 1;
  transform: scale(1);
}

.custom-icon-settings {
  display: grid;
  gap: 12px;
  padding-top: 2px;
}

.custom-icon-heading {
  display: grid;
  gap: 3px;
}

.custom-icon-heading strong {
  color: var(--text-primary);
  font-size: 13px;
}

.custom-icon-heading small {
  color: var(--text-tertiary);
  font-size: 11px;
  line-height: 1.5;
}

.custom-icon-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(82px, 1fr));
  gap: 9px;
}

.custom-icon-card {
  display: grid;
  min-width: 0;
  justify-items: center;
  gap: 7px;
  padding: 10px 7px;
  border: 1px solid var(--border-color-light);
  border-radius: 13px;
  background: color-mix(in srgb, var(--surface-card) 76%, transparent);
}

.custom-icon-image {
  width: 54px;
  height: 54px;
}

.custom-icon-image img {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  object-fit: contain;
}

.custom-icon-image.empty {
  display: grid;
  width: 42px;
  height: 42px;
  padding: 9px;
  place-items: center;
  border-radius: 11px;
  background: var(--primary-alpha-10);
  color: var(--primary);
}

.custom-icon-name {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 650;
}

.custom-icon-actions {
  display: flex;
  min-height: 26px;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.custom-icon-upload {
  position: relative;
  padding: 5px 8px;
  overflow: hidden;
  font-size: 10px;
  cursor: pointer;
}

.custom-icon-upload.disabled {
  cursor: wait;
  opacity: 0.6;
}

.custom-icon-upload input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.custom-icon-remove {
  padding: 5px;
  color: var(--danger);
  font-size: 10px;
}

.dock-control {
  display: grid;
  grid-template-columns: minmax(180px, 0.92fr) minmax(150px, 1.08fr);
  align-items: center;
  gap: var(--spacing-xl);
  padding: 16px 4px;
  border-bottom: 1px solid var(--border-color-light);
}

.dock-control:last-child {
  border-bottom: 0;
}

.dock-control-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  font-weight: 650;
}

.dock-control-label output {
  color: var(--primary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.dock-control p {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.dock-control :deep(.el-slider__runway) {
  height: 5px;
  background: var(--primary-alpha-10);
}

.dock-control :deep(.el-slider__bar) {
  height: 5px;
  background: linear-gradient(90deg, var(--primary-light), var(--primary));
}

.dock-control :deep(.el-slider__button) {
  width: 18px;
  height: 18px;
  border: 3px solid white;
  background: var(--primary);
  box-shadow: 0 2px 8px var(--primary-alpha-30);
}

@media (max-width: 1080px) {
  .dock-settings-body {
    grid-template-columns: 1fr;
  }

  .dock-preview-stage {
    min-height: 250px;
  }
}

@media (max-width: 640px) {
  .dock-settings-header {
    padding: var(--spacing-lg);
  }

  .dock-settings-body {
    padding: var(--spacing-lg);
  }

  .dock-control {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .dock-icon-options {
    grid-template-columns: 1fr;
  }

  .custom-icon-grid {
    grid-template-columns: repeat(2, minmax(110px, 1fr));
  }

  .dock-preview-stage {
    min-height: 220px;
    padding-inline: 12px;
  }

  .dock-preview {
    gap: 4px;
    padding-inline: 7px;
  }
}
</style>
