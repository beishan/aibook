<template>
  <span
    class="site-source-tag"
    :style="{ '--site-theme-color': safeThemeColor }"
    :title="name"
    :aria-label="`来源网站：${name}`"
  >
    <i aria-hidden="true" />
    <span>{{ name }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  name:string
  color?:string
}>(), {
  color: '#286D63',
})

const safeThemeColor = computed(() =>
  /^#[0-9a-fA-F]{6}$/.test(props.color || '') ? props.color : '#286D63',
)
</script>

<style scoped>
.site-source-tag {
  display: inline-flex;
  min-width: 0;
  max-width: 220px;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  overflow: hidden;
  border: 1px solid color-mix(
    in srgb,
    var(--site-theme-color) 34%,
    var(--border-color-light)
  );
  border-radius: 999px;
  background: color-mix(
    in srgb,
    var(--site-theme-color) 10%,
    var(--surface-card)
  );
  color: color-mix(in srgb, var(--site-theme-color) 88%, var(--text-primary));
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
  vertical-align: middle;
}

.site-source-tag i {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: 50%;
  background: var(--site-theme-color);
}

.site-source-tag > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (prefers-reduced-motion: no-preference) {
  .site-source-tag {
    transition: border-color 160ms ease, background-color 160ms ease;
  }
}
</style>
