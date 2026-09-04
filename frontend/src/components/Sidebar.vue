<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { CATEGORIES } from '../utils/categories'

const router = useRouter()
const route = useRoute()

// 展开状态：hover 临时展开 + 点击锁定展开（触屏无 hover 场景）
const hovered = ref(false)
const locked = ref(false)

const expanded = computed(() => hovered.value || locked.value)

function toggle() {
  locked.value = !locked.value
}

function goAll() {
  router.push({ name: 'stacks' })
  locked.value = false
}

function goCategory(value) {
  router.push({ name: 'stacks', query: { category: value } })
  locked.value = false
}
</script>

<template>
  <nav
    class="sidebar"
    :class="{ expanded }"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
    aria-label="技术栈分类导航"
  >
    <button class="toggle" :aria-expanded="expanded" aria-label="展开或收起分类导航" @click="toggle">
      <span class="toggle-icon" aria-hidden="true">{{ expanded ? '×' : '☰' }}</span>
    </button>

    <div v-if="expanded" class="content">
      <router-link to="/" class="brand" @click="locked = false">TS Agent</router-link>

      <button class="nav-item" :class="{ active: !route.query.category }" @click="goAll">全部</button>

      <button
        v-for="c in CATEGORIES"
        :key="c.value"
        class="nav-item"
        :class="{ active: route.query.category === c.value }"
        @click="goCategory(c.value)"
      >
        {{ c.label }}
      </button>
    </div>
  </nav>
</template>

<style scoped>
.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  height: 100vh;
  width: 56px;
  background: var(--color-card);
  border-right: 1px solid rgba(148, 163, 184, 0.14);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-md) 0;
  transition: width 220ms ease, box-shadow 220ms ease;
  z-index: 20;
  overflow: hidden;
}
.sidebar.expanded {
  width: 220px;
  align-items: stretch;
  padding: var(--space-md);
  box-shadow: var(--shadow-lg);
}
.toggle {
  background: transparent;
  border: none;
  color: var(--color-muted-foreground);
  font-size: 20px;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 180ms ease, color 180ms ease;
}
.toggle:hover { background: var(--color-muted); color: var(--color-foreground); }
.toggle-icon { font-family: var(--font-mono); line-height: 1; }
.content {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}
.brand {
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 16px;
  color: var(--color-foreground);
  text-decoration: none;
  padding: var(--space-sm) var(--space-md);
  margin-bottom: var(--space-sm);
}
.nav-item {
  background: transparent;
  border: none;
  color: var(--color-muted-foreground);
  text-align: left;
  font-size: 14px;
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 180ms ease, color 180ms ease;
}
.nav-item:hover { background: var(--color-muted); color: var(--color-foreground); }
.nav-item.active { background: rgba(34, 197, 94, 0.12); color: var(--color-accent); }
</style>
