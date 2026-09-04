<script setup>
import AppHeader from './components/AppHeader.vue'
import Sidebar from './components/Sidebar.vue'
</script>

<template>
  <Sidebar />
  <div class="app-shell">
    <AppHeader />
    <main class="page">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  /* 左侧留出收起态侧边栏宽度，整体右移 */
  margin-left: 56px;
}
.page {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--space-lg);
}
</style>
