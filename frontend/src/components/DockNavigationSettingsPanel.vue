<template>
  <section class="dock-navigation-settings" aria-labelledby="dock-navigation-title">
    <header>
        <div>
          <strong id="dock-navigation-title">导航项目</strong>
          <p>调整 Dock 项目的显示、名称、顺序和图标。设置页仍可从用户菜单打开。</p>
      </div>
    </header>

    <div class="dock-navigation-list">
      <article v-for="(item, index) in draft" :key="item.key" class="dock-navigation-row">
        <div class="dock-navigation-order">
          <button
            type="button"
            :disabled="index === 0"
            :aria-label="`上移${item.title}`"
            @click="moveItem(index, -1)"
          >↑</button>
          <button
            type="button"
            :disabled="index === draft.length - 1"
            :aria-label="`下移${item.title}`"
            @click="moveItem(index, 1)"
          >↓</button>
        </div>
        <DockIcon
          class="dock-navigation-preview"
          :name="item.icon"
          :variant="preferencesStore.dockIconStyle"
          :custom-src="dockIconStore.iconUrls[item.icon]"
          aria-hidden="true"
        />
        <el-input
          v-model="item.title"
          maxlength="30"
          :aria-label="`${item.title}显示名称`"
        />
        <el-select v-model="item.icon" aria-label="导航图标">
          <el-option
            v-for="option in iconOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-switch v-model="item.enabled" :aria-label="`显示${item.title}`" />
      </article>
    </div>

    <footer>
      <small>自定义图标可在上方“自定义图标”区域上传，再在此处指定到任意导航项目。</small>
      <el-button type="primary" :loading="saving" @click="save">保存导航设置</el-button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import DockIcon from '@/components/DockIcon.vue'
import { usePreferencesStore, type DockNavigationIcon, type DockNavigationItem } from '@/stores/preferences'
import { useDockIconStore } from '@/stores/dockIcons'
import { message } from '@/utils/message'

const preferencesStore = usePreferencesStore()
const dockIconStore = useDockIconStore()
const draft = ref<DockNavigationItem[]>([])
const saving = ref(false)
const iconOptions: Array<{ value: DockNavigationIcon; label: string }> = [
  { value: 'home', label: '首页' },
  { value: 'library', label: '书库' },
  { value: 'rewrite', label: '重写' },
  { value: 'shelf', label: '书架' },
  { value: 'repair', label: '修复' },
  { value: 'conversion', label: '转换' },
  { value: 'crawler', label: '爬虫' },
  { value: 'statistics', label: '统计' },
  { value: 'settings', label: '设置' },
  { value: 'trashEmpty', label: '空回收站' },
  { value: 'trashFull', label: '有内容的回收站' },
]

watch(() => preferencesStore.dockNavigationItems, value => {
  draft.value = value.map(item => ({ ...item }))
}, { deep: true, immediate: true })

const moveItem = (index: number, direction: -1 | 1) => {
  const destination = index + direction
  if (destination < 0 || destination >= draft.value.length) return
  const items = [...draft.value]
  ;[items[index], items[destination]] = [items[destination], items[index]]
  draft.value = items.map((item, order) => ({ ...item, order }))
}

const save = async () => {
  if (draft.value.some(item => !item.title.trim())) return message.warning('导航名称不能为空')
  saving.value = true
  try {
    await preferencesStore.saveDockNavigationItems(draft.value.map((item, order) => ({
      ...item,
      title: item.title.trim(),
      order,
    })))
    message.success('Dock 导航设置已保存到账户')
  } catch {
    message.error('Dock 导航设置保存失败，请检查网络后重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.dock-navigation-settings {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--border-color-light);
  border-radius: 18px;
  background: var(--surface-elevated);
}

.dock-navigation-settings header strong {
  font-size: 16px;
}

.dock-navigation-settings header p,
.dock-navigation-settings footer small {
  color: var(--text-secondary);
  font-size: 12px;
}

.dock-navigation-settings header p {
  margin-top: 4px;
}

.dock-navigation-list {
  display: grid;
  gap: 8px;
}

.dock-navigation-row {
  display: grid;
  grid-template-columns: 44px 38px minmax(120px, 1fr) minmax(140px, 190px) auto;
  align-items: center;
  gap: 10px;
  padding: 9px 11px;
  border: 1px solid var(--border-color-light);
  border-radius: 12px;
  background: var(--surface-card);
}

.dock-navigation-order {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
}

.dock-navigation-order button {
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.dock-navigation-order button:disabled {
  opacity: .35;
  cursor: default;
}

.dock-navigation-preview {
  width: 30px;
  height: 30px;
}

.dock-navigation-row :deep(.el-switch) {
  justify-self: end;
}

.dock-navigation-settings footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 680px) {
  .dock-navigation-row {
    grid-template-columns: 38px 30px minmax(0, 1fr) auto;
  }

  .dock-navigation-row :deep(.el-select) {
    grid-column: 3 / 5;
    grid-row: 2;
  }
}
</style>
