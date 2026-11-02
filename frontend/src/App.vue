<template>
  <router-view />
  <DesktopReaderControls v-if="desktop" />
</template>

<script setup lang="ts">
import DesktopReaderControls from '@/components/DesktopReaderControls.vue'
const desktop = !!window.desktopReader
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: var(--font-family);
}
</style>
