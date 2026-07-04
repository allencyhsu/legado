import { createWebHistory, createRouter } from 'vue-router'
import { bookRoutes } from './bookRouter'
import { sourceRoutes } from './sourceRouter'

const router = createRouter({
  history: createWebHistory(),
  routes: [bookRoutes, sourceRoutes].flat(),
})

router.afterEach(to => {
  if (to.name == 'shelf') document.title = '书架'
})

export default router
