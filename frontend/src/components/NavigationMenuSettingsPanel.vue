<template>
  <section class="card glass navigation-order-settings">
    <h2>导航菜单顺序</h2>
    <p>调整顶部导航、侧边栏和手机导航的顺序。“工具”下的功能一起移动，Dock 使用自己的导航配置。</p>
    <ol class="navigation-order-list">
      <li v-for="(path, index) in draft" :key="path">
        <span>{{ label(path) }}</span>
        <div>
          <el-button :disabled="saving || index === 0" :aria-label="`上移${label(path)}`" @click="move(index, -1)">上移</el-button>
          <el-button :disabled="saving || index === draft.length - 1" :aria-label="`下移${label(path)}`" @click="move(index, 1)">下移</el-button>
        </div>
      </li>
    </ol>
    <div class="navigation-order-actions">
      <el-button :disabled="saving" @click="draft = defaultNavigationMenuOrder()">恢复默认</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存菜单顺序</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { usePreferencesStore } from '@/stores/preferences'
import { defaultNavigationMenuOrder, navigationMenuOptions } from '@/utils/navigationMenu'
import { message } from '@/utils/message'
const preferences = usePreferencesStore()
const draft = ref(defaultNavigationMenuOrder())
const saving = ref(false)
watch(() => preferences.navigationMenuOrder, value => { draft.value = [...value] }, { immediate: true })
const label = (path: string) => navigationMenuOptions.find(item => item.path === path)?.title || path
function move(index: number, direction: number) {
  const target = index + direction
  if (saving.value || target < 0 || target >= draft.value.length) return
  const next = [...draft.value]
  const moving = next[index]
  next[index] = next[target]
  next[target] = moving
  draft.value = next
}
async function save() {
  if (saving.value) return
  saving.value = true
  try {
    await preferences.saveNavigationMenuOrder([...draft.value])
    message.success('导航菜单顺序已保存到账户')
  } catch (error: any) {
    message.error(error.response?.data?.message || '保存导航顺序失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.navigation-order-settings {
  padding: 24px;
}
.navigation-order-settings p {
  margin: 12px 0 20px;
  color: var(--text-secondary);
}
.navigation-order-list {
  display: grid;
  gap: 10px;
  list-style: none;
  padding: 0;
}
.navigation-order-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 12px;
}
.navigation-order-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}
</style>
