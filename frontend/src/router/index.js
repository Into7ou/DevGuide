import { createRouter, createWebHistory } from 'vue-router'
import StackListView from '../views/StackListView.vue'
import StackDetailView from '../views/StackDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'stacks', component: StackListView },
    { path: '/stack/:name', name: 'stack-detail', component: StackDetailView, props: true }
  ]
})

export default router
