<script setup>
defineProps({ stack: { type: Object, required: true } })
</script>

<template>
  <router-link :to="`/stack/${stack.name}`" class="stack-card card">
    <h3 class="name">{{ stack.name }}</h3>
    <p class="desc">{{ stack.description || '暂无简介' }}</p>
  </router-link>
</template>

<style scoped>
.stack-card {
  display: block;
  text-decoration: none;
  color: var(--color-card-foreground);
  transition: border-color 200ms ease, transform 200ms ease;
}
.stack-card:hover {
  border-color: var(--color-accent);
  transform: translateY(-1px);
  text-decoration: none;
}
.name {
  margin: 0 0 var(--space-sm);
  font-size: 16px;
  color: var(--color-foreground);
}
.desc {
  margin: 0;
  font-size: 14px;
  color: var(--color-muted-foreground);
  line-height: 1.5;
}
</style>
