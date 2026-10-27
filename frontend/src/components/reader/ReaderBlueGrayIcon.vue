<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(path, index) in paths[name] || paths.book" :key="index" :d="path" />
  </svg>
</template>

<script setup lang="ts">
defineProps<{ name: string }>()

const paths: Record<string, string[]> = {
  search: ['M21 21l-5-5', 'M18 10.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0'],
  book: ['M12 5C9 2 4 3 2 4v15c3-1 7-1 10 2 3-3 7-3 10-2V4c-2-1-7-2-10 1Z', 'M12 5v16'],
  shelf: ['M3 5h4v15H3z', 'M10 3h4v17h-4z', 'M17 5l4-1 3 15-4 1z'],
  bookmark: ['M6 3h12v19l-6-4-6 4V3Z'],
  note: ['M20 13v7H4V4h9', 'm11 11 8-8 3 3-8 8-4 1 1-4Z'],
  highlight: ['m4 15 11-11 5 5-11 11H4v-5Z', 'm12 7 5 5', 'M3 22h18'],
  fullscreen: ['M8 3H3v5', 'M16 3h5v5', 'M21 16v5h-5', 'M8 21H3v-5'],
  close: ['m6 6 12 12', 'M18 6 6 18'],
  left: ['m15 5-7 7 7 7'],
  right: ['m9 5 7 7-7 7'],
  more: ['M4 12h.01', 'M12 12h.01', 'M20 12h.01'],
  user: ['M16 8a4 4 0 1 1-8 0 4 4 0 0 1 8 0', 'M4 21v-2a8 8 0 0 1 16 0v2'],
  download: ['M12 3v12', 'm7 10 5 5 5-5', 'M4 16v5h16v-5'],
  copy: ['M9 9h12v12H9z', 'M15 5V3H3v12h2'],
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  check: ['m5 12 4 4L19 6'],
  plus: ['M12 5v14', 'M5 12h14'],
  trash: ['M3 6h18', 'M9 6V3h6v3', 'm6 6 1 15h10l1-15', 'M10 10v7', 'M14 10v7'],
  cloud: ['M6 19a5 5 0 0 1-1-10 7 7 0 0 1 13-1 5.5 5.5 0 0 1 0 11H6'],
}
</script>

<style scoped>
svg {
  display: block;
  width: 21px;
  height: 21px;
  flex: 0 0 auto;
}
</style>
