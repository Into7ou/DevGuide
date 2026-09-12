import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { afterEach, expect, it, vi } from 'vitest'
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

it('总览进入方向并显示用途分组，返回总览恢复入口', async () => {
  vi.stubGlobal('fetch', vi.fn(async url => responseJson(url.endsWith('/auth/me') ? { authenticated: false } : stacks)))
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/stacks', name: 'stacks', component: StackListView },
    { path: '/stack/:name', name: 'stack-detail', component: { template: '<div />' } }
  ] })
  await router.push('/stacks')
  const wrapper = mount(StackListView, { global: { plugins: [router], stubs: { Teleport: true } } })
  await flushPromises()
  expect(wrapper.findAll('.group-section')).toHaveLength(5)
  await wrapper.findAll('.more-link').find(link => link.text().includes('前端开发')).trigger('click')
  await flushPromises()
  expect(wrapper.get('h1').text()).toBe('前端开发')
  expect(wrapper.findAll('.tech-name').map(item => item.text())).toEqual(['Nuxt'])
  await wrapper.get('.catalog-breadcrumbs a').trigger('click')
  await flushPromises()
  expect(wrapper.get('h1').text()).toBe('探索技术栈')
  expect(wrapper.findAll('.group-section')).toHaveLength(5)
  wrapper.unmount()
})

it('匿名用户无本地匹配时提示登录且不进入动态详情', async () => {
  vi.stubGlobal('fetch', vi.fn()
    .mockResolvedValueOnce(responseJson({ authenticated: false }))
    .mockResolvedValueOnce(responseJson(stacks)))
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/stacks', name: 'stacks', component: StackListView },
      { path: '/stack/:name', name: 'stack-detail', component: { template: '<div />' } }
    ]
  })
  await router.push('/stacks')
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


it('修改筛选后进入详情再返回，保留筛选词和结果', async () => {
  vi.stubGlobal('fetch', vi.fn(async url => responseJson(url.endsWith('/auth/me') ? { authenticated: false } : stacks)))
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/stacks', name: 'stacks', component: StackListView },
    { path: '/stack/:name', name: 'stack-detail', component: { template: '<div>详情</div>' } }
  ] })
  await router.push('/stacks?q=Nuxt')
  const wrapper = mount({ template: '<router-view />' }, { global: { plugins: [router] } })
  await flushPromises()
  await wrapper.get('input').setValue('Docker')
  await flushPromises()
  expect(router.currentRoute.value.query.q).toBe('Docker')
  await wrapper.get('.stack-card').trigger('click')
  await flushPromises()
  const returned = new Promise(resolve => {
    const stop = router.afterEach(() => { stop(); resolve() })
  })
  router.back()
  await returned
  await flushPromises()
  expect(wrapper.get('input').element.value).toBe('Docker')
  expect(wrapper.findAll('.stack-card .name').map(card => card.text())).toEqual(['Docker'])
  wrapper.unmount()
})


it('局部无匹配只提供扩大范围，全目录能找到其他方向的技术', async () => {
  const fetch = vi.fn(async url => responseJson(url.endsWith('/auth/me') ? { authenticated: true } : stacks))
  vi.stubGlobal('fetch', fetch)
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/stacks', name: 'stacks', component: StackListView },
    { path: '/stack/:name', name: 'stack-detail', component: { template: '<div />' } }
  ] })
  await router.push('/stacks?direction=frontend&scope=direction&q=Docker')
  const wrapper = mount(StackListView, { global: { plugins: [router] } })
  await flushPromises()
  expect(wrapper.get('.catalog-empty').text()).toContain('当前范围没有匹配结果')
  expect(wrapper.find('.catalog-empty .btn-primary').exists()).toBe(false)
  await wrapper.get('.catalog-action').trigger('click')
  await flushPromises()
  expect(wrapper.get('.tech-name').text()).toBe('Docker')
  expect(wrapper.get('input').element.value).toBe('Docker')
  expect(fetch).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})

it('LLM旧分类不让未知技术进入分组，但全目录搜索仍可找到', async () => {
  const records = [...stacks, { id: 9, name: 'NewThing', category: 'frontend', description: '未确认分类的技术' }]
  vi.stubGlobal('fetch', vi.fn(async url => responseJson(url.endsWith('/auth/me') ? { authenticated: false } : records)))
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/stacks', name: 'stacks', component: StackListView },
    { path: '/stack/:name', name: 'stack-detail', component: { template: '<div />' } }
  ] })
  await router.push('/stacks?direction=frontend')
  const wrapper = mount(StackListView, { global: { plugins: [router] } })
  await flushPromises()
  expect(wrapper.findAll('.tech-name').map(item => item.text())).not.toContain('NewThing')
  await wrapper.get('input').setValue('NewThing')
  await flushPromises()
  expect(wrapper.get('.tech-name').text()).toBe('NewThing')
  expect(wrapper.get('.membership').text()).toBe('待分类')
  expect(wrapper.get('.stack-card').attributes('href')).toContain('from=')
  wrapper.unmount()
})
