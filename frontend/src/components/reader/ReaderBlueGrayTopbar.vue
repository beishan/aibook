<template>
  <header class="blue-topbar">
    <button
      class="blue-brand"
      type="button"
      aria-label="返回首页"
      @click="$router.push('/')"
    >
      <span class="blue-brand-mark">
        <ReaderBlueGrayIcon name="book" />
      </span>
      <strong>汗牛充栋</strong>
      <span class="blue-brand-subtitle">私人图书馆</span>
    </button>
    <form class="blue-global-search" role="search" @submit.prevent="searchLibrary">
      <ReaderBlueGrayIcon name="search" />
      <input
        v-model="query"
        type="search"
        aria-label="搜索书库"
        placeholder="搜索书籍、作者或 ISBN…"
      />
      <kbd>↵</kbd>
    </form>
    <nav aria-label="阅读工作台导航">
      <button type="button" @click="$router.push('/shelf')">
        <ReaderBlueGrayIcon name="shelf" />
        <span>书架</span>
      </button>
      <button
        type="button"
        class="active"
        aria-current="page"
        @click="$emit('read')"
      >
        <ReaderBlueGrayIcon name="book" />
        <span>阅读</span>
      </button>
      <button
        type="button"
        :class="{ selected: activeTab === 'notes' }"
        @click="$emit('panel', 'notes')"
      >
        <ReaderBlueGrayIcon name="note" />
        <span>笔记</span>
      </button>
      <button
        type="button"
        :class="{ selected: activeTab === 'highlights' }"
        @click="$emit('panel', 'highlights')"
      >
        <ReaderBlueGrayIcon name="highlight" />
        <span>书摘</span>
      </button>
    </nav>
    <button
      v-if="quickWindow"
      type="button"
      class="blue-window-close"
      aria-label="关闭小窗"
      title="关闭小窗"
      @click="$emit('close')"
    >
      <ReaderBlueGrayIcon name="close" />
    </button>
    <button
      class="blue-avatar"
      type="button"
      aria-label="个人设置"
      @click="$router.push('/settings?tab=profile')"
    >
      <img v-if="userStore.avatarObjectUrl" :src="userStore.avatarObjectUrl" alt="" />
      <ReaderBlueGrayIcon v-else name="user" />
    </button>
  </header>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import ReaderBlueGrayIcon from './ReaderBlueGrayIcon.vue'

defineProps<{ activeTab: string; quickWindow?: boolean }>()
defineEmits<{ read: []; panel: [value: 'notes' | 'highlights']; close: [] }>()
const router = useRouter()
const userStore = useUserStore()
const query = ref('')
const searchLibrary = () => {
  if (query.value.trim()) void router.push({ path: '/books', query: { search: query.value.trim() } })
}
</script>

<style scoped>
.blue-topbar {
  display: flex;
  flex: 0 0 62px;
  align-items: center;
  gap: 28px;
  padding: 0 24px;
  border-bottom: 1px solid var(--blue-line);
  background: linear-gradient(110deg, #e5ebf2, #eef3f8 60%, #e5ecf3);
  color: #172235;
}

button {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.blue-brand {
  flex-shrink: 0;
  padding: 0;
  gap: 12px;
}

.blue-brand strong {
  font-size: 21px;
  letter-spacing: 1px;
}

.blue-brand-mark {
  display: grid;
  width: 25px;
  height: 27px;
  place-items: center;
  border-radius: 5px;
  background: linear-gradient(130deg, #a7b5c4, #5e7b92);
  color: #fff;
}

.blue-brand-mark svg {
  width: 18px;
  height: 18px;
}

.blue-brand-subtitle {
  color: #7183a0;
  font-size: 13px;
  letter-spacing: 1px;
}

.blue-global-search {
  display: flex;
  align-items: center;
  width: min(430px, 32vw);
  gap: 10px;
  margin: 0 auto;
  padding: 10px 12px;
  border: 1px solid #d6e0eb;
  border-radius: 11px;
  background: #ffffffa8;
}

.blue-global-search svg {
  width: 17px;
  height: 17px;
}

input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: #172235;
  font: inherit;
  font-size: 13px;
}

input::placeholder {
  color: #8190a5;
}

kbd {
  color: #8996aa;
  font: inherit;
  font-size: 12px;
}

nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

nav button {
  padding: 11px 13px;
  border-radius: 11px;
  color: #586b87;
  font-size: 14px;
  white-space: nowrap;
}

nav button svg {
  width: 18px;
  height: 18px;
}

nav button.active, nav button.selected {
  background: #d8e9fb;
  color: #075dc6;
}

.blue-avatar {
  display: grid;
  width: 31px;
  height: 31px;
  flex-shrink: 0;
  place-items: center;
  padding: 0;
  overflow: hidden;
  border-radius: 50%;
  background: #b5c3d2;
  color: #52677e;
}

.blue-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

button:focus-visible, .blue-global-search:focus-within {
  outline: 2px solid #1264cf;
  outline-offset: 3px;
}

@media (max-width: 1100px) {
  .blue-topbar {
    gap: 16px;
    padding: 0 16px;
  }
  .blue-brand-subtitle {
    display: none;
  }
  nav {
    gap: 0;
  }
  nav button {
    padding: 10px;
  }
}

@media (max-width: 700px) {
  .blue-topbar {
    flex-basis: 54px;
    gap: 10px;
    padding: 0 12px;
  }
  .blue-brand strong {
    font-size: 17px;
  }
  .blue-brand-mark, .blue-global-search, nav button span {
    display: none;
  }
  nav {
    margin-left: auto;
  }
  nav button {
    padding: 8px;
  }
}
</style>
