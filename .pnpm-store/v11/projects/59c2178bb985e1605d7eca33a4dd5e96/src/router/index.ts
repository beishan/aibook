import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layouts/ThemeLayoutWrapper.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/HomeView.vue'),
      },
      {
        path: 'books',
        name: 'Books',
        component: () => import('@/views/BooksView.vue'),
      },
      {
        path: 'books/:id',
        name: 'BookDetail',
        component: () => import('@/views/BookDetailView.vue'),
      },
      {
        path: 'books/:id/repair',
        name: 'TextRepair',
        component: () => import('@/views/TextRepairView.vue'),
      },
      {
        path: 'text-repair',
        name: 'TextRepairConfig',
        component: () => import('@/views/TextRepairConfigView.vue'),
      },
      {
        path: 'reader/:id',
        name: 'Reader',
        component: () => import('@/views/ReaderView.vue'),
      },
      {
        path: 'shelf',
        name: 'Shelf',
        component: () => import('@/views/ShelfView.vue'),
      },
      {
        path: 'booklists/:id',
        name: 'BookList',
        component: () => import('@/views/BookListView.vue'),
      },
      {
        path: 'connections',
        name: 'Connections',
        redirect: { path: '/settings', query: { tab: 'connections' } },
      },
      {
        path: 'categories',
        name: 'Categories',
        redirect: { path: '/settings', query: { tab: 'categories' } },
      },
      {
        path: 'tags',
        name: 'Tags',
        redirect: { path: '/settings', query: { tab: 'tags' } },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/SettingsView.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if ((to.path === '/login' || to.path === '/register') && token) {
    next('/')
  } else {
    next()
  }
})

export default router
