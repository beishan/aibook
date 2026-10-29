<template>
  <section v-if="items.length" class="continue-listening glass">
    <header>
      <h2>继续收听</h2>
      <router-link to="/audiobooks">全部有声书 →</router-link>
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
import { audioApi } from '@/utils/audiobook'
import { useBookNameDisplay } from '@/composables/useBookNameDisplay'
const items = ref<any[]>([])
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
