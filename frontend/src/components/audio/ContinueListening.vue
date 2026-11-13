<template>
  <section v-if="items.length && !dismissed" class="continue-listening glass">
    <header>
      <h2>继续收听</h2>
      <div class="header-actions">
        <router-link to="/audiobooks">全部有声书 →</router-link>
        <button
          type="button"
          class="close-button"
          aria-label="关闭继续收听卡片"
          title="关闭继续收听卡片"
          @click="dismissed = true"
        >
          <Close aria-hidden="true" />
        </button>
      </div>
    </header>
    <div>
      <router-link
        v-for="item in items"
        :key="item.id"
        :to="`/audiobooks/${item.id}/listen`"
        class="btn"
        >♫ {{ displayBookName(item.title) }} · {{ item.name }}</router-link
      >
    </div>
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { audioApi } from '@/utils/audiobook'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
const items = ref<any[]>([])
const dismissed = ref(false)
const { displayBookName } = useBookNameDisplay()
onMounted(async () => {
  try {
    items.value = (await audioApi.list({ status: 'LISTENING', size: 3 })).content
  } catch {
    /* Keep homepage available. */
  }
})
</script>
<style scoped>
.continue-listening {
  padding: 20px;
  border-radius: 20px;
  margin-bottom: 20px;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  gap: 12px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.close-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  padding: 6px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}
.close-button:hover,
.close-button:active {
  background: var(--bg-tertiary, rgba(128, 128, 128, 0.12));
}
.close-button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
h2 {
  font-size: 20px;
}
a {
  color: var(--primary);
}
.continue-listening > div {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
