<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import StackCard from '../components/StackCard.vue'
import { filterStacks, filterByCategory } from '../utils/filter'
import { categoryLabel } from '../utils/categories'

const router = useRouter()
const route = useRoute()
const stacks = ref([])
const keyword = ref('')
const loading = ref(true)
const error = ref('')

const activeCategory = computed(() => route.query.category || '')

const filtered = computed(() =>
  filterStacks(filterByCategory(stacks.value, activeCategory.value), keyword.value)
)
const trimmedKeyword = computed(() => keyword.value.trim())
// 输入非空且无本地匹配时，展示「联网查询」入口（任意技术栈，不限于预置清单）
const showWebSearch = computed(() => !loading.value && trimmedKeyword.value !== '' && filtered.value.length === 0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await fetch('/api/v1/tech-stacks')
    if (!res.ok) throw new Error(`加载失败 (${res.status})`)
    stacks.value = await res.json()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function searchArbitrary() {
  const k = trimmedKeyword.value
  if (k) router.push({ name: 'stack-detail', params: { name: k } })
}

function onEnter() {
  if (showWebSearch.value) searchArbitrary()
}

onMounted(load)
</script>

<template>
  <section>
    <header class="hero">
      <h1>技术栈学习 Agent</h1>
      <p class="subtitle">查找技术栈的官方文档与 GitHub star Top10 项目，生成带引用的学习引导。</p>
      <input
        v-model="keyword"
        class="input search"
        placeholder="搜索技术栈，如 React、Spring Boot、PyTorch…"
        @keyup.enter="onEnter"
      />
    </header>

    <p v-if="loading" class="state">加载中…</p>
    <p v-else-if="error" class="state error">{{ error }}</p>

    <template v-else>
      <div v-if="activeCategory" class="category-bar">
        <span class="category-current">分类：{{ categoryLabel(activeCategory) }}</span>
        <button class="category-clear" @click="router.push({ name: 'stacks' })">显示全部</button>
      </div>

      <div v-if="showWebSearch" class="web-search">
        <p class="web-search-hint">「{{ trimmedKeyword }}」暂未收录，可联网搜索官方文档与 GitHub 项目并生成学习引导。</p>
        <button class="btn-primary" @click="searchArbitrary">联网查询「{{ trimmedKeyword }}」</button>
      </div>

      <div v-else-if="!filtered.length" class="state">没有匹配的技术栈</div>
      <div v-else class="grid">
        <StackCard v-for="s in filtered" :key="s.id" :stack="s" />
      </div>
    </template>
  </section>
</template>

<style scoped>
.hero { margin: var(--space-lg) 0 var(--space-xl); text-align: center; }
.hero h1 { margin: 0 0 var(--space-sm); font-size: 28px; }
.subtitle { margin: 0 auto var(--space-lg); max-width: 520px; color: var(--color-muted-foreground); font-size: 15px; }
.search { max-width: 480px; margin: 0 auto; }
.state { text-align: center; color: var(--color-muted-foreground); padding: var(--space-2xl) 0; }
.error { color: var(--color-destructive); }
.web-search {
  text-align: center;
  padding: var(--space-2xl) 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
}
.web-search-hint { margin: 0; color: var(--color-muted-foreground); font-size: 15px; }
.category-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}
.category-current { font-size: 14px; color: var(--color-foreground); font-family: var(--font-mono); }
.category-clear {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-muted-foreground);
  border-radius: var(--radius-sm);
  padding: 4px 12px;
  font-size: 13px;
  transition: color 180ms ease, border-color 180ms ease;
}
.category-clear:hover { color: var(--color-foreground); border-color: var(--color-accent); }
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--space-md);
}
</style>
