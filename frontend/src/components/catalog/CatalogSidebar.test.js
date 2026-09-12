import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { afterEach, expect, it, vi } from 'vitest'
import CatalogSidebar from './CatalogSidebar.vue'

let wrapper
afterEach(() => { wrapper?.unmount(); document.body.innerHTML = ''; vi.unstubAllGlobals() })
async function setup(mobile = false) {
  vi.stubGlobal('matchMedia', () => ({ matches: mobile, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/stacks', name: 'stacks', component: { template: '<div />' } }] })
  await router.push('/stacks')
  document.body.innerHTML = '<header class="header"></header><main id="main-content"></main>'
  wrapper = mount(CatalogSidebar, { attachTo: document.body, props: { direction: 'overview', group: '', counts: {} }, global: { plugins: [router] } })
  await flushPromises()
}
it('方向选择焦点跟随所选链接，二级选择后恢复到可见按钮', async () => {
  await setup()
  document.querySelector('.rail button').click()
  await flushPromises()
  const chosen = document.querySelector('.nav-panel .direction[href*="direction=frontend"]')
  chosen.click()
  await flushPromises()
  expect(document.activeElement).toBe(chosen)
  expect(document.querySelector('.catalog-sidebar').classList.contains('open')).toBe(true)
  await wrapper.setProps({ direction: 'frontend' })
  document.querySelector('.subgroups a').click()
  await flushPromises()
  expect(document.querySelector('.catalog-sidebar').classList.contains('open')).toBe(false)
  expect(document.activeElement).toBe(document.querySelector('.rail button'))
})
it('手机关闭或卸载侧栏恢复页面交互与滚动', async () => {
  await setup(true)
  await wrapper.get('.mobile-toggle').trigger('click')
  await flushPromises()
  expect(document.querySelector('#main-content').hasAttribute('inert')).toBe(true)
  expect(document.body.style.overflow).toBe('hidden')
  await wrapper.setProps({ direction: 'frontend' })
  document.querySelector('.subgroups a').click()
  await flushPromises()
  expect(document.activeElement).toBe(wrapper.get('.mobile-toggle').element)
  expect(document.querySelector('#main-content').hasAttribute('inert')).toBe(false)
  await wrapper.get('.mobile-toggle').trigger('click')
  wrapper.unmount(); wrapper = null
  expect(document.body.style.overflow).toBe('')
  expect(document.querySelector('#main-content').hasAttribute('inert')).toBe(false)
})
