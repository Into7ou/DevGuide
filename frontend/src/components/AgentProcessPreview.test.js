import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import AgentProcessPreview from './AgentProcessPreview.vue'

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

function environment(reduced = false) {
  let visibility
  const disconnect = vi.fn()
  const media = { matches: reduced, addEventListener: vi.fn(), removeEventListener: vi.fn() }
  vi.stubGlobal('matchMedia', () => media)
  vi.stubGlobal('IntersectionObserver', class {
    constructor(callback) { visibility = callback }
    observe() {}
    disconnect = disconnect
  })
  return { show: value => visibility([{ isIntersecting: value }]), disconnect, media }
}

it('仅可见时循环，离屏和卸载清理计时器', async () => {
  vi.useFakeTimers()
  const env = environment()
  const wrapper = mount(AgentProcessPreview)
  env.show(true)
  await vi.advanceTimersByTimeAsync(2600)
  expect(wrapper.findAll('li')[1].classes()).toContain('is-active')
  env.show(false)
  await vi.advanceTimersByTimeAsync(5200)
  expect(wrapper.findAll('li')[1].classes()).toContain('is-active')
  env.show(true)
  wrapper.unmount()
  expect(vi.getTimerCount()).toBe(0)
  expect(env.disconnect).toHaveBeenCalledOnce()
  expect(env.media.removeEventListener).toHaveBeenCalled()
})

it('减少动态效果时展示静态完整结果且无循环按钮', async () => {
  vi.useFakeTimers()
  const env = environment(true)
  const wrapper = mount(AgentProcessPreview)
  env.show(true)
  await nextTick()
  expect(wrapper.get('.demo-result').classes()).toContain('is-ready')
  expect(wrapper.find('button').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('过程演示')
  expect(vi.getTimerCount()).toBe(0)
  wrapper.unmount()
})
