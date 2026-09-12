import taxonomy from '../data/catalog-taxonomy.json'

export const DIRECTIONS = taxonomy.directions
export const GROUPS = taxonomy.groups

const registry = taxonomy.registry

function stackName(stack) {
  return typeof stack === 'string' ? stack : stack?.name
}

function registryEntry(stack) {
  const name = stackName(stack)
  return typeof name === 'string' ? registry[name] : undefined
}

/** Return only developer-confirmed group ids for this exact technology name. */
export function getMembership(stack) {
  const entry = registryEntry(stack)
  if (!entry || entry.status === 'deferred' || !Array.isArray(entry.groups)) return []
  return [...new Set(entry.groups.filter((id) => GROUPS[id]))]
}

/** Return deduplicated direction and group labels for the confirmed memberships. */
export function getTags(stack) {
  const memberships = getMembership(stack)
  const labels = []

  for (const direction of DIRECTIONS) {
    if (direction.groups.some((id) => memberships.includes(id))) labels.push(direction.name)
  }
  for (const id of memberships) labels.push(GROUPS[id].name)

  return [...new Set(labels)]
}

/** Stable catalog order: explicit developer order first, then technology name. */
export function sortStacks(items) {
  return [...items].sort((left, right) => {
    const leftOrder = Number.isFinite(registryEntry(left)?.order) ? registryEntry(left).order : Infinity
    const rightOrder = Number.isFinite(registryEntry(right)?.order) ? registryEntry(right).order : Infinity
    return leftOrder - rightOrder || String(left?.name || '').localeCompare(String(right?.name || ''), 'zh-CN')
  })
}

/** Match locally against the API record's searchable text. */
export function matchesQuery(stack, query) {
  const needle = String(query || '').trim().toLocaleLowerCase()
  if (!needle) return true

  const aliases = Array.isArray(stack?.aliases)
    ? stack.aliases
    : String(stack?.aliases || '').split(',')
  const haystack = [stack?.name, stack?.description, ...aliases]
    .filter(Boolean)
    .join(' ')
    .toLocaleLowerCase()
  return haystack.includes(needle)
}

/** Count actual API records assigned to a confirmed group. */
export function countGroup(items, id) {
  return items.reduce((count, stack) => count + Number(getMembership(stack).includes(id)), 0)
}
