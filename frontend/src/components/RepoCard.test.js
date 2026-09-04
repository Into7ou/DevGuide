import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import RepoCard from './RepoCard.vue'

describe('RepoCard', () => {
  it('renders repo name, description and formatted star count', () => {
    const wrapper = mount(RepoCard, {
      props: {
        repo: {
          fullName: 'owner/repo',
          stargazersCount: 1500,
          description: 'A sample repo',
          language: 'Java',
          htmlUrl: 'https://github.com/owner/repo',
          topics: []
        }
      }
    })

    expect(wrapper.text()).toContain('owner/repo')
    expect(wrapper.text()).toContain('1.5k')
    expect(wrapper.text()).toContain('A sample repo')
    expect(wrapper.text()).toContain('Java')
  })
})
