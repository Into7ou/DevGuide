/** 按名称过滤技术栈（忽略大小写、忽略首尾空白），空关键字返回全部 */
export function filterStacks(stacks, keyword) {
  const k = (keyword || '').trim().toLowerCase()
  if (!k) return stacks
  return stacks.filter((s) => s.name.toLowerCase().includes(k))
}

/** 按分类过滤技术栈，空分类返回全部 */
export function filterByCategory(stacks, category) {
  if (!category) return stacks
  return stacks.filter((s) => s.category === category)
}
