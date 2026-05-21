import sourceEditor from '../views/SourceEditor.vue'
import { createWebHistory, createRouter } from 'vue-router'

export const sourceRoutes = [
  {
    path: '/bookSource',
    name: 'book-home',
    component: sourceEditor,
  },
  {
    path: '/rssSource',
    name: 'rss-home',
    component: sourceEditor,
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: sourceRoutes,
})

export default router
