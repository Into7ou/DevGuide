import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import LearnPanel from './LearnPanel.vue'

vi.mock('../utils/csrf', () => ({
  csrfHeaders: vi.fn().mockResolvedValue({ 'X-XSRF-TOKEN': 'test-token' })
}))

const source = { id: 'S1', title: 'Controller 文档', url: 'https://docs.spring.io/reference',
  excerpt: 'A controller handles requests.', techStacks: ['Spring MVC'], sourceType: 'official', retrievedFrom: 'local' }
const event = (type, data) => ({ type, data })
function response(events, close = true) {
  let streamController
  const body = new ReadableStream({ start(controller) {
    streamController = controller
    for (const item of events) controller.enqueue(new TextEncoder().encode(`data: ${JSON.stringify(item)}\n\n`))
    if (close) controller.close()
  } })
  return { ok: true, body, finish: () => streamController.close() }
}
function answer(text = '第一轮回答 [S1]') {
  return response([event('status', '本地检索完成'), event('scope', '适用于 Spring MVC'),
    event('answer', text), event('sources', [source]), event('done', { sufficient: true })])
}
async function ask(wrapper, text) {
  await wrapper.get('input').setValue(text)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}
let wrapper
afterEach(() => { wrapper?.unmount(); vi.unstubAllGlobals() })

describe('学习对话', () => {
  it('第二轮搜索时保留上一答，可折叠并展示独立轮次及原文', async () => {
    const second = response([event('status', '第 1/2 轮联网补充')], false)
    const fetch = vi.fn().mockResolvedValueOnce(answer()).mockResolvedValueOnce(second)
    vi.stubGlobal('fetch', fetch)
    wrapper = mount(LearnPanel, { props: { techStack: 'Java' } })
    await ask(wrapper, 'Controller 是什么？')
    await ask(wrapper, '还有更多吗？')
    expect(wrapper.findAll('.turn')).toHaveLength(2)
    expect(wrapper.text()).toContain('第一轮回答 [S1]')
    expect(wrapper.text()).toContain('第 2 轮')
    expect(wrapper.text()).toContain('第 1/2 轮联网补充')
    expect(wrapper.get('blockquote').text()).toBe(source.excerpt)
    expect(wrapper.get('.source a').attributes('href')).toBe(source.url)
    expect(wrapper.get('.source-tags').text()).toContain('本地知识库')
    expect(wrapper.get('.scope').text()).toContain('Spring MVC')
    expect(JSON.parse(fetch.mock.calls[1][1].body).history).toEqual([
      { question: 'Controller 是什么？', answer: '第一轮回答 [S1]', sources: [source] }
    ])
    await wrapper.get('.fold-button').trigger('click')
    expect(wrapper.get('.fold-button').attributes('aria-expanded')).toBe('false')
    expect(wrapper.findAll('.agent-response')[0].element.style.display).toBe('none')
    await wrapper.get('.fold-button').trigger('click')
    expect(wrapper.findAll('.agent-response')[0].element.style.display).not.toBe('none')
    second.finish()
    await flushPromises()
  })

  it('页面保留全部回答，但后端仅收到最近三轮已完成上下文', async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(answer()))
    vi.stubGlobal('fetch', fetch)
    wrapper = mount(LearnPanel, { props: { techStack: 'Java' } })
    for (let i = 1; i <= 5; i++) await ask(wrapper, `问题 ${i}`)
    expect(wrapper.findAll('.turn')).toHaveLength(5)
    expect(JSON.parse(fetch.mock.calls[4][1].body).history.map(t => t.question)).toEqual(['问题 2', '问题 3', '问题 4'])
  })

  it('断流保留已输出文字并显示未完成，失败轮次不作为完成上下文', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response([event('answer', '尚未完成的半句')])).mockResolvedValueOnce(answer())
    vi.stubGlobal('fetch', fetch)
    wrapper = mount(LearnPanel, { props: { techStack: 'Java' } })
    await ask(wrapper, '第一次')
    expect(wrapper.get('.error').text()).toContain('未完成')
    expect(wrapper.text()).toContain('尚未完成的半句')
    await ask(wrapper, '再试一次')
    expect(JSON.parse(fetch.mock.calls[1][1].body).history).toEqual([])
  })

  it('切换技术栈时取消旧流并清空会话，新问题不会携带上一技术栈历史', async () => {
    const pending = response([event('answer', '旧页面')], false)
    const fetch = vi.fn().mockResolvedValueOnce(pending).mockResolvedValueOnce(answer('新页面'))
    vi.stubGlobal('fetch', fetch)
    wrapper = mount(LearnPanel, { props: { techStack: 'Java' } })
    await ask(wrapper, '旧问题')
    await wrapper.setProps({ techStack: 'Django' })
    await flushPromises()
    expect(fetch.mock.calls[0][1].signal.aborted).toBe(true)
    expect(wrapper.findAll('.turn')).toHaveLength(0)
    await ask(wrapper, '新问题')
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toMatchObject({ techStack: 'Django', history: [] })
    expect(wrapper.text()).not.toContain('旧页面')
  })

  it('澄清回答保留为上下文，第三方及部分回答明确标注', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response([
      event('clarification', '你指的是哪一种 Controller？'), event('done', { clarification: true })
    ])).mockResolvedValueOnce(response([
      event('answer', '部分解释 [S1]'), event('sources', [{ ...source, sourceType: 'third-party', retrievedFrom: 'web' }]),
      event('done', { sufficient: false })
    ]))
    vi.stubGlobal('fetch', fetch)
    wrapper = mount(LearnPanel, { props: { techStack: 'Java' } })
    await ask(wrapper, 'Controller？')
    expect(wrapper.text()).toContain('等待你的补充')
    await ask(wrapper, 'Spring MVC')
    expect(JSON.parse(fetch.mock.calls[1][1].body).history[0].answer).toContain('哪一种')
    expect(wrapper.text()).toContain('第三方资料')
    expect(wrapper.text()).toContain('本轮联网')
    expect(wrapper.text()).toContain('已回答有依据的部分')
  })
})
