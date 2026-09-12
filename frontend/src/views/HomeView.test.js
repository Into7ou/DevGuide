import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { afterEach, expect, it, vi } from 'vitest'
import HomeView from './HomeView.vue'

afterEach(() => vi.unstubAllGlobals())

it('固定首页不触发生成请求，探索入口进入独立目录', async () => {
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/stacks', name: 'stacks', component: { template: '<div>目录</div>' } }
  ] })
  await router.push('/')
  const wrapper = mount(HomeView, { global: { plugins: [router], stubs: { HomeAmbient: true, AgentProcessPreview: true } } })
  await flushPromises()
  expect(fetch).not.toHaveBeenCalled()
  expect(wrapper.get('.source').attributes('href')).toBe('https://react.dev/learn/state-as-a-snapshot')
  await wrapper.get('.actions .btn-primary').trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.path).toBe('/stacks')
  wrapper.unmount()
})


it('重复章节点击交由路由处理，阻止原生锚点与历史恢复竞争', async () => {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/stacks', name: 'stacks', component: { template: '<div />' } }
  ] })
  await router.push('/')
  const wrapper = mount(HomeView, { global: { plugins: [router], stubs: { HomeAmbient: true, AgentProcessPreview: true } } })
  for (const hash of ['#research', '#question', '#research', '#question', '#research']) {
    const event = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 })
    wrapper.get(`.chapters a[href$="${hash}"]`).element.dispatchEvent(event)
    await flushPromises()
    expect(event.defaultPrevented).toBe(true)
    expect(router.currentRoute.value.hash).toBe(hash)
  }
  wrapper.unmount()
})
