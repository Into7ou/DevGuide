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

function responseJson(data) {
  return { ok: true, json: async () => data }
}

it('点击侧边栏分类后显示对应卡片，显示全部后恢复清单', async () => {
  vi.stubGlobal('fetch', vi.fn()
    .mockResolvedValueOnce(responseJson({ authenticated: false }))
    .mockResolvedValueOnce(responseJson(stacks)))
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

it('匿名用户无本地匹配时提示登录且不进入动态详情', async () => {
  vi.stubGlobal('fetch', vi.fn()
    .mockResolvedValueOnce(responseJson({ authenticated: false }))
    .mockResolvedValueOnce(responseJson(stacks)))
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'stacks', component: StackListView },
      { path: '/stack/:name', name: 'stack-detail', component: { template: '<div />' } }
    ]
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(StackListView, { global: { plugins: [router] } })
  await flushPromises()
  await wrapper.get('input').setValue('尚未收录的技术')
  await wrapper.get('.btn-primary').trigger('click')
  expect(wrapper.get('.login-prompt').text()).toContain('请先登录')
  expect(wrapper.get('.login-prompt a').attributes('href')).toBe('/oauth2/authorization/github')
  expect(router.currentRoute.value.name).toBe('stacks')
  wrapper.unmount()
})
