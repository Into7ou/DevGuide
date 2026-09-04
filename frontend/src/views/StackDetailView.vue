<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import RepoCard from '../components/RepoCard.vue'
import LearnPanel from '../components/LearnPanel.vue'

const route = useRoute()
const overview = ref(null)
const loading = ref(true)
const error = ref('')

async function load(name) {
  loading.value = true
  error.value = ''
  overview.value = null
  try {
    const res = await fetch(`/api/v1/tech-stacks/${encodeURIComponent(name)}`)
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.error || `查询失败，请稍后重试（${res.status}）`)
    }
    overview.value = await res.json()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => load(route.params.name))
watch(() => route.params.name, (n) => { if (n) load(n) })
</script>

<template>
  <section>
    <p v-if="loading" class="state">加载中…</p>
    <p v-else-if="error" class="state error">{{ error }}</p>

    <template v-else-if="overview">
      <header class="detail-head">
        <h1 class="name">{{ overview.name }}</h1>
        <p v-if="overview.description" class="desc">{{ overview.description }}</p>
      </header>

      <section v-if="overview.officialDocUrl || overview.docKeyPages?.length" class="section">
        <h2>官方文档</h2>
        <div class="doc-links">
          <a v-if="overview.officialDocUrl" :href="overview.officialDocUrl" target="_blank" rel="noopener" class="doc-link">
            官网首页
          </a>
          <a v-for="p in overview.docKeyPages" :key="p" :href="p" target="_blank" rel="noopener" class="doc-link">
            {{ p }}
          </a>
        </div>
      </section>

      <section class="section">
        <h2>GitHub star Top10</h2>
        <div v-if="overview.topRepos?.length" class="repo-grid">
          <RepoCard v-for="r in overview.topRepos" :key="r.fullName" :repo="r" />
        </div>
        <p v-else class="state">暂无项目数据</p>
      </section>

      <LearnPanel :tech-stack="overview.name" />
    </template>
  </section>
</template>

<style scoped>
.state { text-align: center; color: var(--color-muted-foreground); padding: var(--space-2xl) 0; }
.error { color: var(--color-destructive); }
.detail-head { margin: var(--space-lg) 0 var(--space-xl); }
.name { margin: 0 0 var(--space-sm); font-size: 28px; }
.desc { margin: 0; color: var(--color-muted-foreground); }
.section { margin-bottom: var(--space-2xl); }
.section h2 { margin: 0 0 var(--space-md); font-size: 18px; }
.doc-links { display: flex; flex-direction: column; gap: var(--space-sm); }
.doc-link { font-size: 14px; word-break: break-all; }
.repo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-md);
}
</style>
