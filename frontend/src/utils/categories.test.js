import { describe, it, expect } from 'vitest'
import { CATEGORIES, categoryLabel } from './categories'

describe('CATEGORIES', () => {
  it('has four groups with matching backend values', () => {
    expect(CATEGORIES.map((c) => c.value)).toEqual(['frontend', 'backend', 'ml-data', 'infra'])
  })
})

describe('categoryLabel', () => {
  it('returns Chinese label for known category', () => {
    expect(categoryLabel('frontend')).toBe('前端')
    expect(categoryLabel('backend')).toBe('后端')
    expect(categoryLabel('ml-data')).toBe('ML·数据')
    expect(categoryLabel('infra')).toBe('基础设施')
  })

  it('returns original value for unknown category', () => {
    expect(categoryLabel('mystery')).toBe('mystery')
  })

  it('returns empty string for blank', () => {
    expect(categoryLabel('')).toBe('')
    expect(categoryLabel(null)).toBe('')
  })
})
