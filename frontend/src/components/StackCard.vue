<script setup>
defineProps({ stack: { type: Object, required: true } })
</script>

<template>
  <router-link :to="{ name: 'stack-detail', params: { name: stack.name } }" class="stack-card card">
    <h3 class="name">{{ stack.name }}</h3>
    <p class="desc">{{ stack.description || '暂无简介' }}</p>
  </router-link>
</template>

<style scoped>
.stack-card {
  display: block;
  text-decoration: none;
  color: var(--color-card-foreground);

}
.stack-card:hover {
  border-color: var(--color-accent);

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
