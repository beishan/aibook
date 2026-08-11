<template>
  <span class="dock-glyph" :class="`dock-glyph--${variant}`">
    <img
      v-if="variant === 'custom' && customSrc"
      class="dock-glyph-custom-image"
      :src="customSrc"
      alt=""
    />

    <el-icon v-else-if="variant === 'minimal' || variant === 'custom'" class="dock-glyph-simple" aria-hidden="true">
      <component :is="minimalIcon" />
    </el-icon>

    <svg v-else-if="variant === 'skeuomorphic'" class="dock-glyph-skeuo" viewBox="0 0 64 64" aria-hidden="true">
      <defs>
        <linearGradient :id="`${gradientPrefix}-green`" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#8fd7ae" />
          <stop offset="0.48" stop-color="#3d9a6b" />
          <stop offset="1" stop-color="#185c41" />
        </linearGradient>
        <linearGradient :id="`${gradientPrefix}-paper`" x1="0" y1="0" x2="0.8" y2="1">
          <stop offset="0" stop-color="#fffdf0" />
          <stop offset="0.62" stop-color="#f2dfae" />
          <stop offset="1" stop-color="#c9a96b" />
        </linearGradient>
        <linearGradient :id="`${gradientPrefix}-wood`" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#d79a59" />
          <stop offset="0.52" stop-color="#9a572d" />
          <stop offset="1" stop-color="#5c2d1c" />
        </linearGradient>
        <linearGradient :id="`${gradientPrefix}-metal`" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#fff8d4" />
          <stop offset="0.34" stop-color="#d9bd72" />
          <stop offset="0.72" stop-color="#85652e" />
          <stop offset="1" stop-color="#f1d994" />
        </linearGradient>
        <filter :id="`${gradientPrefix}-shadow`" x="-30%" y="-30%" width="160%" height="170%">
          <feDropShadow dx="0" dy="2.5" stdDeviation="2" flood-color="#193d2d" flood-opacity=".34" />
        </filter>
      </defs>

      <g v-if="name === 'home'" :filter="`url(#${gradientPrefix}-shadow)`">
        <path d="M9 30.5 32 10l23 20.5-4.3 4.8L32 18.7 13.3 35.3Z" :fill="`url(#${gradientPrefix}-green)`" stroke="#174d39" stroke-width="1.5" stroke-linejoin="round" />
        <path d="M15 32 32 17l17 15v20H15Z" :fill="`url(#${gradientPrefix}-paper)`" stroke="#765a2e" stroke-width="1.4" />
        <path d="M27 52V36h10v16" fill="#7d492c" stroke="#4d2a1d" stroke-width="1.3" />
        <rect x="18.5" y="33" width="7" height="7" rx="1.2" fill="#83cae2" stroke="#396a74" stroke-width="1.1" />
        <path d="M20 34.5h4.5M22.25 33.5v5.5" stroke="#ecffff" stroke-width=".9" opacity=".9" />
        <path d="M17 31.5 32 19l15 13.1" fill="none" stroke="#fff" stroke-width="1.5" opacity=".42" />
      </g>

      <g v-else-if="name === 'library'" :filter="`url(#${gradientPrefix}-shadow)`">
        <path d="M9 52.5h46" stroke="#59351f" stroke-width="4" stroke-linecap="round" />
        <rect x="11" y="15" width="11" height="36" rx="2" fill="#a64c3f" stroke="#632b28" stroke-width="1.4" />
        <rect x="23" y="11" width="13" height="40" rx="2" :fill="`url(#${gradientPrefix}-green)`" stroke="#174d39" stroke-width="1.4" />
        <path d="m38 17 10-2.3 8.1 34.5-10 2.3Z" fill="#d5a33d" stroke="#74511f" stroke-width="1.4" />
        <path d="M13 20h7M13 45h7M25.5 17h8M25.5 45h8M40.7 21l9.5-2.2M46.6 46l9.5-2.2" stroke="#fff4cf" stroke-width="1.4" opacity=".75" />
        <path d="M14.5 17h4M26.5 14h6" stroke="#fff" stroke-width="1.2" stroke-linecap="round" opacity=".5" />
      </g>

      <g v-else-if="name === 'shelf'" :filter="`url(#${gradientPrefix}-shadow)`">
        <path d="M7.5 19.5c10-3.3 18.2-.7 24.5 5.5v30c-6.6-5.4-14.8-7.4-24.5-4.4Z" :fill="`url(#${gradientPrefix}-paper)`" stroke="#765a2e" stroke-width="1.5" stroke-linejoin="round" />
        <path d="M56.5 19.5C46.5 16.2 38.3 18.8 32 25v30c6.6-5.4 14.8-7.4 24.5-4.4Z" :fill="`url(#${gradientPrefix}-paper)`" stroke="#765a2e" stroke-width="1.5" stroke-linejoin="round" />
        <path d="M10 15.5c8.8-2 16.2.6 22 7.3v28.4c-6-5.3-13.3-7.3-22-5Z" fill="#fffbea" stroke="#b69255" stroke-width="1.1" />
        <path d="M54 15.5c-8.8-2-16.2.6-22 7.3v28.4c6-5.3 13.3-7.3 22-5Z" fill="#fffbea" stroke="#b69255" stroke-width="1.1" />
        <path d="M15 23c5.2-.5 9.5 1 13 4M15 29c5.2-.5 9.5 1 13 4M49 23c-5.2-.5-9.5 1-13 4M49 29c-5.2-.5-9.5 1-13 4" fill="none" stroke="#bda36f" stroke-width="1.2" stroke-linecap="round" />
        <path d="M32 23v28" stroke="#806137" stroke-width="1.2" />
      </g>

      <g v-else-if="name === 'repair'" :filter="`url(#${gradientPrefix}-shadow)`">
        <path d="M19 21v-5.5a4 4 0 0 1 4-4h18a4 4 0 0 1 4 4V21" fill="none" stroke="#61402b" stroke-width="5" />
        <rect x="8" y="20" width="48" height="34" rx="5" :fill="`url(#${gradientPrefix}-wood)`" stroke="#4c281b" stroke-width="1.6" />
        <path d="M9 31h46" stroke="#e0ab64" stroke-width="1.4" opacity=".72" />
        <rect x="26" y="27" width="12" height="9" rx="2" :fill="`url(#${gradientPrefix}-metal)`" stroke="#6c5329" stroke-width="1.2" />
        <path d="M42.5 38.5 48 44l-3.5 3.5-5.6-5.6-4.1 1.2-9.5-9.5 3.5-3.5 9.5 9.5Z" fill="#dce5e2" stroke="#556460" stroke-width="1.3" stroke-linejoin="round" />
        <path d="M12 23h40" stroke="#fff0ce" stroke-width="1.4" opacity=".36" />
      </g>

      <g v-else :filter="`url(#${gradientPrefix}-shadow)`">
        <g :fill="`url(#${gradientPrefix}-metal)`" stroke="#684e25" stroke-width="1.1">
          <rect x="28" y="7" width="8" height="15" rx="3" />
          <rect x="28" y="42" width="8" height="15" rx="3" />
          <rect x="42" y="28" width="15" height="8" rx="3" />
          <rect x="7" y="28" width="15" height="8" rx="3" />
          <rect x="28" y="7" width="8" height="15" rx="3" transform="rotate(45 32 32)" />
          <rect x="28" y="42" width="8" height="15" rx="3" transform="rotate(45 32 32)" />
          <rect x="42" y="28" width="15" height="8" rx="3" transform="rotate(45 32 32)" />
          <rect x="7" y="28" width="15" height="8" rx="3" transform="rotate(45 32 32)" />
          <circle cx="32" cy="32" r="18" />
        </g>
        <circle cx="32" cy="32" r="8" fill="#5c725f" stroke="#304637" stroke-width="2" />
        <circle cx="29.5" cy="29.5" r="3" fill="#bcd6c1" opacity=".45" />
        <path d="M21 21a16 16 0 0 1 20-1" fill="none" stroke="#fff5cc" stroke-width="2" stroke-linecap="round" opacity=".58" />
      </g>
    </svg>

    <span v-else class="dock-glyph-macos" :class="`dock-glyph-macos--${name}`" aria-hidden="true">
      <span class="dock-glyph-macos-glass"></span>
      <el-icon class="dock-glyph-macos-symbol">
        <component :is="minimalIcon" />
      </el-icon>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance } from 'vue'
import { Collection, HomeFilled, Reading, Setting, Tools } from '@element-plus/icons-vue'
import type { Component } from 'vue'

export type DockIconName = 'home' | 'library' | 'shelf' | 'repair' | 'settings'
export type DockIconStyle = 'minimal' | 'skeuomorphic' | 'macos26' | 'custom'

const props = defineProps<{
  name: DockIconName
  variant: DockIconStyle
  customSrc?: string
}>()

const minimalIcons: Record<DockIconName, Component> = {
  home: HomeFilled,
  library: Collection,
  shelf: Reading,
  repair: Tools,
  settings: Setting,
}

const instanceId = getCurrentInstance()?.uid ?? 0
const minimalIcon = computed(() => minimalIcons[props.name])
const gradientPrefix = computed(() => `dock-${instanceId}-${props.name}`)
</script>

<style scoped>
.dock-glyph {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  color: inherit;
}

.dock-glyph-simple,
.dock-glyph-simple :deep(svg),
.dock-glyph-skeuo,
.dock-glyph-custom-image {
  width: 100%;
  height: 100%;
}

.dock-glyph-custom-image {
  display: block;
  border: 0;
  object-fit: contain;
}

.dock-glyph-simple :deep(svg),
.dock-glyph-skeuo {
  shape-rendering: geometricPrecision;
}

.dock-glyph-skeuo {
  overflow: visible;
}

.dock-glyph-macos {
  position: relative;
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: 27%;
  background: linear-gradient(145deg, #75d7ff, #1689e8 58%, #0755ae);
  box-shadow:
    0 2px 5px rgba(15, 47, 83, 0.28),
    inset 0 1px 1px rgba(255, 255, 255, 0.74),
    inset 0 -1px 2px rgba(0, 35, 91, 0.34);
  isolation: isolate;
}

.dock-glyph-macos--library {
  background: linear-gradient(145deg, #ff9a75, #ef4f48 56%, #ac1d43);
}

.dock-glyph-macos--shelf {
  background: linear-gradient(145deg, #68dfbd, #19a881 56%, #08715d);
}

.dock-glyph-macos--repair {
  background: linear-gradient(145deg, #ffc968, #ed902b 58%, #a84b19);
}

.dock-glyph-macos--settings {
  background: linear-gradient(145deg, #c8d0da, #748294 56%, #3c4959);
}

.dock-glyph-macos-glass {
  position: absolute;
  inset: 1px 2px 48%;
  border-radius: 42% 42% 52% 52%;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.62), rgba(255, 255, 255, 0.06));
  mix-blend-mode: screen;
}

.dock-glyph-macos::after {
  content: '';
  position: absolute;
  inset: auto 12% 5%;
  height: 22%;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  filter: blur(4px);
}

.dock-glyph-macos-symbol {
  z-index: 1;
  width: 58%;
  height: 58%;
  color: rgba(255, 255, 255, 0.96);
  filter: drop-shadow(0 1.5px 1px rgba(14, 42, 68, 0.35));
}

.dock-glyph-macos-symbol :deep(svg) {
  width: 100%;
  height: 100%;
}
</style>
