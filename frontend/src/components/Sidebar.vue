<script setup>
import { useRoute } from 'vue-router'
import { CATEGORIES } from '../utils/categories'
const route = useRoute()
</script>

<template>
  <nav class="category-nav" aria-label="技术栈分类导航">
    <router-link :to="{ name: 'stacks' }" :aria-current="!route.query.category ? 'page' : undefined">全部</router-link>
    <router-link v-for="category in CATEGORIES" :key="category.value"
      :to="{ name: 'stacks', query: { category: category.value } }"
      :aria-current="route.query.category === category.value ? 'page' : undefined">
      {{ category.label }}
    </router-link>
  </nav>
</template>

<style scoped>
.category-nav { display: flex; flex-wrap: wrap; gap: var(--space-sm); margin-block: var(--space-lg); }
a { padding: 8px 16px; border: 1px solid var(--color-border); border-radius: 24px; color: var(--color-muted-foreground); font-size: 14px; }
a:hover, a[aria-current] { color: var(--color-accent); border-color: var(--color-accent); text-decoration: none; }
</style>
