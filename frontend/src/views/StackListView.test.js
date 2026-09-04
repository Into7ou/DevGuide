import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { afterEach, expect, it, vi } from 'vitest'
import Sidebar from '../components/Sidebar.vue'
import StackListView from './StackListView.vue'

const stacks = [
  { id: 1, name: 'Nuxt', category: 'frontend' },
  { id: 2, name: 'Spring Boot', category: 'backend' },
  { id: 3, name: 'PyTorch', category: 'ml-data' },
  { id: 4, name: 'Docker', category: 'infra' }
]

afterEach(() => vi.unstubAllGlobals())

it('点击侧边栏分类后显示对应卡片，显示全部后恢复清单', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => stacks }))
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'stacks', component: StackListView },
      { path: '/stack/:name', component: { template: '<div />' } }
    ]
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount({ components: { Sidebar }, template: '<Sidebar /><router-view />' }, {
    global: { plugins: [router] }
  })
  await flushPromises()

  for (const [label, expected] of [['前端', 'Nuxt'], ['后端', 'Spring Boot'], ['ML·数据', 'PyTorch'], ['基础设施', 'Docker']]) {
    await wrapper.get('button.toggle').trigger('click')
    await wrapper.findAll('button.nav-item').find(button => button.text() === label).trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.stack-card .name').map(card => card.text())).toEqual([expected])
    expect(wrapper.get('.category-current').text()).toBe(`分类：${label}`)
  }

  await wrapper.get('.category-clear').trigger('click')
  await flushPromises()
  expect(wrapper.findAll('.stack-card')).toHaveLength(4)
  wrapper.unmount()
})
