import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DIRECTIONS, GROUPS, getMembership, sortStacks, matchesQuery } from '../utils/catalog'

export function useCatalog() {
  const route = useRoute(), router = useRouter()
  const stacks = ref([]), loading = ref(true), error = ref(''), authenticated = ref(false), loginPrompt = ref(false)
  const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
  let request
  const direction = computed(() => DIRECTIONS.find(d => d.id === (route.query.direction || route.query.category)) || null)
  const group = computed(() => direction.value?.groups.includes(route.query.group) ? route.query.group : '')
  const scope = computed(() => route.query.scope === 'group' && group.value ? 'group' : route.query.scope === 'direction' && direction.value ? 'direction' : 'all')
  const scopeOptions = computed(() => [
    { value: 'all', label: '全部技术', note: '搜索整个技术栈目录' },
    ...(direction.value ? [{ value: 'direction', label: direction.value.name, note: '仅搜索当前学习方向' }] : []),
    ...(group.value ? [{ value: 'group', label: GROUPS[group.value].name, note: '仅搜索当前用途分组' }] : [])
  ])
  const globalMatches = computed(() => sortStacks(stacks.value.filter(stack => matchesQuery(stack, keyword.value))))
  const groupStacks = id => sortStacks(stacks.value.filter(stack => getMembership(stack).includes(id)))
  const directionStacks = d => sortStacks(stacks.value.filter(stack => getMembership(stack).some(id => d.groups.includes(id))))
  const results = computed(() => {
    if (!keyword.value.trim()) return group.value ? groupStacks(group.value) : []
    return globalMatches.value.filter(stack => scope.value === 'all' || (scope.value === 'group' ? getMembership(stack).includes(group.value) : getMembership(stack).some(id => direction.value.groups.includes(id))))
  })
  const pageSize = 12
  const pages = computed(() => Math.max(1, Math.ceil(results.value.length / pageSize)))
  const page = computed(() => Math.min(pages.value, Math.max(1, parseInt(route.query.page, 10) || 1)))
  const pageItems = computed(() => results.value.slice((page.value - 1) * pageSize, page.value * pageSize))
  const counts = computed(() => Object.fromEntries(Object.keys(GROUPS).map(id => [id, groupStacks(id).length])))
  const boards = computed(() => direction.value
    ? direction.value.groups.map(id => ({ id, name: GROUPS[id].name, desc: GROUPS[id].desc, icon: GROUPS[id].icon, items: groupStacks(id), query: { direction: direction.value.id, group: id } }))
    : DIRECTIONS.map(d => ({ id: d.id, name: d.name, desc: d.groups.map(id => GROUPS[id].name).join(' / '), icon: d.icon, items: directionStacks(d), query: { direction: d.id } })))
  const showWebSearch = computed(() => !loading.value && !error.value && scope.value === 'all' && keyword.value.trim() !== '' && globalMatches.value.length === 0)
  watch(() => route.query.q, value => { keyword.value = typeof value === 'string' ? value : '' })
  watch(keyword, value => {
    loginPrompt.value = false
    if (value !== (route.query.q || '')) router.replace({ query: { ...route.query, q: value || undefined, page: undefined } })
  })
  function changeScope(value) { router.replace({ query: { ...route.query, scope: value === 'all' ? undefined : value, page: undefined } }) }
  async function navigate({ direction: d, group: g }) {
    await router.push({ name: 'stacks', query: { direction: d === 'overview' ? undefined : d, group: g || undefined } })
    window.scrollTo({ top: 0, behavior: 'instant' })
  }
  async function changePage(value) { await router.push({ query: { ...route.query, page: value === 1 ? undefined : value } }); document.querySelector('#catalog-results')?.scrollIntoView?.({ block: 'start' }) }
  async function load() {
    request?.abort(); request = new AbortController()
    const signal = request.signal
    loading.value = true; error.value = ''
    try {
      const auth = fetch('/api/auth/me', { signal }).then(res => res.ok ? res.json() : null).catch(() => null)
      const response = await fetch('/api/v1/showcase/tech-stacks', { signal })
      if (!response.ok) throw new Error(`目录加载失败（${response.status}）`)
      const items = await response.json()
      const user = await auth
      if (signal.aborted) return
      stacks.value = items; authenticated.value = Boolean(user?.authenticated)
    } catch (e) { if (!signal.aborted) error.value = e.message }
    finally { if (!signal.aborted) loading.value = false }
  }
  function discover() {
    if (!showWebSearch.value) return
    if (!authenticated.value) { loginPrompt.value = true; return }
    router.push({ name: 'stack-detail', params: { name: keyword.value.trim() }, query: { from: route.fullPath } })
  }
  onMounted(load)
  onBeforeUnmount(() => request?.abort())
  return { route, stacks, loading, error, loginPrompt, authenticated, keyword, direction, group, scope, scopeOptions, results, page, pages, pageItems, pageSize, counts, boards, showWebSearch, changeScope, navigate, changePage, load, discover }
}
