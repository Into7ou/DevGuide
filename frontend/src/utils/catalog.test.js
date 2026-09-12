import { describe, expect, it } from 'vitest'
import { DIRECTIONS, GROUPS, countGroup, getMembership, getTags, matchesQuery, sortStacks } from './catalog'

describe('catalog taxonomy', () => {
  it('exports directions whose group ids resolve to configured labels', () => {
    expect(DIRECTIONS.length).toBeGreaterThan(0)
    for (const direction of DIRECTIONS) {
      expect(direction).toEqual(expect.objectContaining({
        id: expect.any(String),
        name: expect.any(String),
        short: expect.any(String),
        icon: expect.any(String),
        intro: expect.any(String),
        groups: expect.any(Array)
      }))
      direction.groups.forEach((id) => expect(GROUPS[id]).toBeDefined())
    }
  })

  it('does not turn the legacy category into a confirmed membership', () => {
    expect(getMembership({ name: 'Unreviewed Tool', category: 'frontend' })).toEqual([])
    expect(getTags({ name: 'Unreviewed Tool', category: 'backend' })).toEqual([])
  })

  it('supports multiple groups and deduplicates direction labels', () => {
    expect(getMembership({ name: 'Kafka' })).toEqual(['queues', 'streams'])
    expect(getTags({ name: 'Kafka' })).toEqual(['后端开发', '数据处理', '消息队列', '流式处理'])

    const tags = getTags({ name: 'Apache Spark' })
    expect(tags).toEqual(['数据处理', '分布式计算', '流式处理'])
    expect(new Set(tags).size).toBe(tags.length)
  })

  it('uses exact technology names for confirmed mappings', () => {
    expect(getMembership({ name: 'React' })).toEqual(['ui'])
    expect(getMembership({ name: 'react', aliases: 'React' })).toEqual([])
  })
})

describe('catalog helpers', () => {
  it('sorts developer-ordered records before pending records, then by name', () => {
    const items = [
      { name: 'Zulu Unknown' },
      { name: 'Vue' },
      { name: 'Alpha Unknown' },
      { name: 'React' }
    ]
    expect(sortStacks(items).map((item) => item.name)).toEqual([
      'React', 'Vue', 'Alpha Unknown', 'Zulu Unknown'
    ])
    expect(items[0].name).toBe('Zulu Unknown')
  })

  it('matches name, description and array or comma-separated aliases', () => {
    const stack = {
      name: 'Spring Boot',
      description: 'Java 企业级应用开发框架',
      aliases: 'springboot,spring-boot'
    }
    expect(matchesQuery(stack, 'SPRING BOOT')).toBe(true)
    expect(matchesQuery(stack, '企业级')).toBe(true)
    expect(matchesQuery(stack, 'spring-boot')).toBe(true)
    expect(matchesQuery({ name: 'Vue', aliases: ['vuejs', 'vue.js'] }, 'VUE.JS')).toBe(true)
    expect(matchesQuery(stack, 'react')).toBe(false)
    expect(matchesQuery(stack, '  ')).toBe(true)
  })

  it('counts only actual records with a confirmed membership', () => {
    const items = [
      { name: 'Kafka' },
      { name: 'Apache Spark' },
      { name: 'Unreviewed Tool', category: 'ml-data' }
    ]
    expect(countGroup(items, 'streams')).toBe(2)
    expect(countGroup(items, 'models')).toBe(0)
  })
})
