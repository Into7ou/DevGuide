<script setup>
import { formatStars } from '../utils/format'

defineProps({ repo: { type: Object, required: true } })
</script>

<template>
  <a :href="repo.htmlUrl" target="_blank" rel="noopener" class="repo-card card">
    <div class="repo-head">
      <span class="repo-name">{{ repo.fullName }}</span>
      <span class="repo-stars">
        <svg class="star" width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M12 2l2.9 6.3 6.9.6-5.2 4.5 1.5 6.8L12 16.8 5.9 20.2l1.5-6.8L2.2 8.9l6.9-.6z" />
        </svg>
        {{ formatStars(repo.stargazersCount) }}
      </span>
    </div>
    <p class="repo-desc">{{ repo.description || '暂无描述' }}</p>
    <div class="repo-meta">
      <span v-if="repo.language" class="lang">{{ repo.language }}</span>
    </div>
  </a>
</template>

<style scoped>
.repo-card {
  display: block;
  text-decoration: none;
  color: var(--color-card-foreground);

}
.repo-card:hover {
  border-color: var(--color-accent);

  text-decoration: none;
}
.repo-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}
.repo-name {
  font-family: var(--font-mono);
  font-size: 14px;
  color: var(--color-accent);
  overflow: hidden;

  overflow-wrap: anywhere;
  min-width: 0;
}
.repo-stars {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: 13px;
  color: var(--color-muted-foreground);
  flex-shrink: 0;
}
.repo-desc {
  margin: 0 0 var(--space-sm);
  font-size: 13px;
  color: var(--color-muted-foreground);
  line-height: 1.5;

}
.lang {
  font-size: 12px;
  color: var(--color-muted-foreground);
}
</style>
