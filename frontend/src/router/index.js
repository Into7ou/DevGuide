import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import StackListView from '../views/StackListView.vue'
import StackDetailView from '../views/StackDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, saved) {
    if (saved) return { ...saved, behavior: 'instant' }
    if (to.hash) {
      const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      return {
        el: to.hash,
        top: parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop) || 0,
        behavior: reduced ? 'instant' : 'smooth'
      }
    }
    // Query changes represent directory filtering, not a new page.
    if (to.path === from.path) return false
    return { top: 0, behavior: 'instant' }
  },
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/stacks', name: 'stacks', component: StackListView },
    { path: '/stack/:name', name: 'stack-detail', component: StackDetailView, props: true }
  ]
})

export default router
