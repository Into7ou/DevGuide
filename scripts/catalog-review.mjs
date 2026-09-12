#!/usr/bin/env node

import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const taxonomyPath = path.join(repoRoot, 'frontend', 'src', 'data', 'catalog-taxonomy.json')

function usage() {
  console.log(`用法:
  node scripts/catalog-review.mjs list --input <目录导出.json>
  node scripts/catalog-review.mjs confirm "技术名称" --groups <id,id> [--order N] [--apply]
  node scripts/catalog-review.mjs defer "技术名称" [--apply]

confirm/defer 默认只打印变更预览；只有传入 --apply 才写入 registry。`)
}

function option(args, name) {
  const index = args.indexOf(name)
  if (index < 0) return undefined
  if (!args[index + 1] || args[index + 1].startsWith('--')) {
    throw new Error(`${name} 缺少值`)
  }
  return args[index + 1]
}

function readCatalogRecords(payload) {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.items)) return payload.items
  if (Array.isArray(payload?.data)) return payload.data
  throw new Error('输入 JSON 必须是技术数组，或包含 items/data 数组')
}

function normalizedGroups(value) {
  return [...new Set(String(value || '').split(',').map((id) => id.trim()).filter(Boolean))]
}

function snapshot(entry) {
  if (!entry) return null
  const value = { groups: Array.isArray(entry.groups) ? [...entry.groups] : [] }
  if (Number.isFinite(entry.order)) value.order = entry.order
  if (entry.status) value.status = entry.status
  return value
}

function present(entry, groups) {
  if (!entry || entry.status === 'deferred' || !entry.groups?.length) return '待分类'
  return entry.groups.map((id) => `${groups[id]?.name || '未知分组'} (${id})`).join(' / ')
}

async function main() {
  const args = process.argv.slice(2)
  const command = args[0]
  const taxonomy = JSON.parse(await readFile(taxonomyPath, 'utf8'))

  if (command === 'list') {
    const input = option(args, '--input')
    if (!input) {
      console.log('请先将公开目录接口 /api/v1/showcase/tech-stacks 的响应保存为 JSON，再传入 --input <文件>。')
      console.log('该命令只读取导出文件，不请求接口或调用付费 API。')
      return
    }

    const inputPath = path.resolve(process.cwd(), input)
    const records = readCatalogRecords(JSON.parse(await readFile(inputPath, 'utf8')))
    const rows = records.map((stack) => {
      const entry = typeof stack?.name === 'string' ? taxonomy.registry[stack.name] : undefined
      return {
        name: stack?.name || '(缺少名称)',
        status: entry?.groups?.length && entry.status !== 'deferred' ? '已确认' : '待分类',
        groups: present(entry, taxonomy.groups)
      }
    })
    console.table(rows)
    const pending = rows.filter((row) => row.status === '待分类').length
    console.log(`实际记录 ${rows.length} 条；已确认 ${rows.length - pending} 条；待分类 ${pending} 条。`)
    return
  }

  if (command !== 'confirm' && command !== 'defer') {
    usage()
    if (command) process.exitCode = 1
    return
  }

  const name = args[1]
  if (!name || name.startsWith('--')) throw new Error('缺少技术名称')
  const previousEntry = taxonomy.registry[name]
  const previous = snapshot(previousEntry)
  const at = new Date().toISOString()
  let next

  if (command === 'confirm') {
    const groups = normalizedGroups(option(args, '--groups'))
    if (!groups.length) throw new Error('confirm 至少需要一个 --groups 分组')
    const unknown = groups.filter((id) => !taxonomy.groups[id])
    if (unknown.length) throw new Error(`未知分组: ${unknown.join(', ')}`)
    const orderValue = option(args, '--order')
    const order = orderValue === undefined ? previousEntry?.order : Number(orderValue)
    if (orderValue !== undefined && (!Number.isFinite(order) || order < 0)) {
      throw new Error('--order 必须是大于等于 0 的数字')
    }
    next = { groups }
    if (Number.isFinite(order)) next.order = order
  } else {
    next = { groups: [], status: 'deferred' }
  }

  const history = Array.isArray(previousEntry?.history) ? previousEntry.history : []
  const updated = {
    ...next,
    updatedAt: at,
    history: [...history, { action: command, at, previous, next }]
  }

  console.log(JSON.stringify({ name, apply: args.includes('--apply'), previous, next: updated }, null, 2))
  if (!args.includes('--apply')) {
    console.log('预览完成；确认无误后添加 --apply 写入。')
    return
  }

  taxonomy.registry[name] = updated
  await writeFile(taxonomyPath, `${JSON.stringify(taxonomy, null, 2)}\n`, 'utf8')
  console.log(`已更新 ${path.relative(repoRoot, taxonomyPath)}。`)
}

main().catch((error) => {
  console.error(`目录确认失败: ${error.message}`)
  process.exitCode = 1
})
