/**
 * 技术栈分类元数据（单一数据源：侧边栏导航 + 首页筛选共用，避免重复）。
 * 分类值与后端 tech_stacks.category 字段一致。
 */
export const CATEGORIES = [
  { value: 'frontend', label: '前端' },
  { value: 'backend', label: '后端' },
  { value: 'ml-data', label: 'ML·数据' },
  { value: 'infra', label: '基础设施' }
]

/** 根据分类值取中文标签，未知分类返回原值或空串。 */
export function categoryLabel(value) {
  const found = CATEGORIES.find((c) => c.value === value)
  return found ? found.label : value || ''
}
