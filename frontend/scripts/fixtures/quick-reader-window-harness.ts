import { createApp, h, onMounted, ref } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import BookQuickReader from '../../src/components/BookQuickReader.vue'
import { usePreferencesStore } from '../../src/stores/preferences'
import type { Book } from '../../src/stores/book'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/reader/:id', name: 'Reader', component: { render: () => null } }],
})
const app = createApp({
  setup() {
    const reader = ref<InstanceType<typeof BookQuickReader>>()
    const preferences = usePreferencesStore()
    const ready = ref(false)
    onMounted(async () => {
      await preferences.hydrate()
      ready.value = true
    })
    return () => h('div', [
      h('button', {
        disabled: !ready.value,
        onClick: () => reader.value?.open({ id: 1, title: '人间词话', format: 'txt' } as Book),
      }, '打开阅读小窗'),
      h(BookQuickReader, { ref: reader }),
    ])
  },
})
app.use(createPinia()).use(router).use(ElementPlus).mount('#app')
