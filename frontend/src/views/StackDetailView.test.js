import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import StackDetailView from './StackDetailView.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: { name: '红烧肉' }, query: {} }), useRouter: () => ({ back: vi.fn(), push: vi.fn() }) }))
afterEach(() => vi.unstubAllGlobals())

describe('技术栈准入反馈', () => {
  it.each([
    [400, '请输入具体的软件开发技术栈名称，例如 React、Python 或 Docker。'],
    [503, '暂时无法确认该技术栈及其官方来源，尚未收录。请使用准确名称或稍后重试。']
  ])('显示 %s 的具体原因且不展示学习入口', async (status, error) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status, json: async () => ({ error }) }))
    const wrapper = mount(StackDetailView, { global: { stubs: { LearnPanel: true, RouterLink: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain(error)
    expect(wrapper.find('learn-panel-stub').exists()).toBe(false)
    expect(wrapper.find('a.doc-link').exists()).toBe(false)
    wrapper.unmount()
  })

  it('匿名已收录详情使用 showcase 接口并展示登录入口', async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ authenticated: false }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ name: 'React', topRepos: [] }) })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(StackDetailView, { global: { stubs: { LearnPanel: true, RouterLink: true } } })
    await flushPromises()
    expect(fetch.mock.calls[1][0]).toContain('/api/v1/showcase/tech-stacks/')
    expect(wrapper.find('learn-panel-stub').exists()).toBe(false)
    expect(wrapper.get('.login-card').text()).toContain('登录后开始学习对话')
    wrapper.unmount()
  })
})
