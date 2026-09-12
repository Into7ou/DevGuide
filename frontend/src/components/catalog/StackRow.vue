<script setup>
import { getTags } from '../../utils/catalog'
defineProps({ stack: { type: Object, required: true }, full: Boolean, returnTo: String })
</script>
<template>
  <router-link class="tech-row stack-card" :to="{ name: 'stack-detail', params: { name: stack.name }, query: returnTo ? { from: returnTo } : {} }">
    <span class="tech-mark" aria-hidden="true">{{ stack.name.slice(0, 2) }}</span>
    <span class="tech-copy"><span class="tech-name name">{{ stack.name }}</span><span class="tech-desc">{{ stack.description || '查看资料与学习引导' }}</span></span>
    <span v-if="full" class="membership">{{ getTags(stack).join(' / ') || '待分类' }}</span>
    <span class="row-arrow" aria-hidden="true">↗</span>
  </router-link>
</template>
