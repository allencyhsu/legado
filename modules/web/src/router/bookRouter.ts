import { createWebHistory, createRouter } from 'vue-router'

export const bookRoutes = [
  {
    path: '/',
    name: 'shelf',
    component: () => import('../views/BookShelf.vue'),
  },
  {
    path: '/chapter',
    name: 'chapter',
    component: () => import('../views/BookChapter.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: bookRoutes,
})

export default router
