import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/base.css'
import './styles/themes.css'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'
import { usePreferencesStore } from './stores/preferences'
import { useDockIconStore } from './stores/dockIcons'
import { useUserStore } from './stores/user'
import { loadSiteFavicon } from './utils/siteFavicon'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)

// 初始化主题
const themeStore = useThemeStore(pinia)
themeStore.initTheme()
const preferencesStore = usePreferencesStore(pinia)
void preferencesStore.hydrate()
const dockIconStore = useDockIconStore(pinia)
const userStore = useUserStore(pinia)
void loadSiteFavicon()

const bootstrap = async () => {
  if (localStorage.getItem('token')) {
    await Promise.all([
      dockIconStore.restoreCached(),
      userStore.restoreCachedAvatar(),
    ])
    void dockIconStore.hydrate()
    void userStore.hydrate()
  }
  app.mount('#app')
}

void bootstrap()
