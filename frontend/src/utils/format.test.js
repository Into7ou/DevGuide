import { describe, it, expect } from 'vitest'
import { formatStars } from './format'

describe('formatStars', () => {
  it('formats values below 1000 as-is', () => {
    expect(formatStars(0)).toBe('0')
    expect(formatStars(999)).toBe('999')
  })

  it('formats values >= 1000 with k suffix', () => {
    expect(formatStars(1000)).toBe('1.0k')
    expect(formatStars(1500)).toBe('1.5k')
    expect(formatStars(454805)).toBe('454.8k')
  })
})
