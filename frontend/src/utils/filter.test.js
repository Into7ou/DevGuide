import { describe, it, expect } from 'vitest'
import { filterStacks, filterByCategory } from './filter'

const stacks = [
  { name: 'React', id: 1, category: 'frontend' },
  { name: 'Vue', id: 2, category: 'frontend' },
  { name: 'Spring Boot', id: 3, category: 'backend' }
]

describe('filterStacks', () => {
  it('returns all when keyword is blank', () => {
    expect(filterStacks(stacks, '')).toHaveLength(3)
    expect(filterStacks(stacks, '  ')).toHaveLength(3)
  })

  it('filters case-insensitively by name', () => {
    expect(filterStacks(stacks, 'react')).toEqual([{ name: 'React', id: 1, category: 'frontend' }])
    expect(filterStacks(stacks, 'SPRING')).toEqual([{ name: 'Spring Boot', id: 3, category: 'backend' }])
  })

  it('returns empty when no match', () => {
    expect(filterStacks(stacks, 'zzz')).toEqual([])
  })
})

describe('filterByCategory', () => {
  it('returns all when category is blank', () => {
    expect(filterByCategory(stacks, '')).toHaveLength(3)
    expect(filterByCategory(stacks, undefined)).toHaveLength(3)
  })

  it('filters by exact category', () => {
    expect(filterByCategory(stacks, 'frontend')).toHaveLength(2)
    expect(filterByCategory(stacks, 'backend')).toEqual([{ name: 'Spring Boot', id: 3, category: 'backend' }])
  })

  it('returns empty when no category match', () => {
    expect(filterByCategory(stacks, 'infra')).toEqual([])
  })
})
