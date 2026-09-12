<template>
  <footer class="site-footer page-container">
    <router-link class="site-brand" to="/"><span aria-hidden="true">&gt;_</span> DevGuide</router-link>
    <div class="footer-contact">
      <p>如果有开发建议，请联系开发者。</p>
      <a href="mailto:1429246179@qq.com">1429246179@qq.com ↗</a>
    </div>
  </footer>
</template>

<style scoped>
.site-footer { display: flex; justify-content: space-between; gap: var(--space-lg); padding-block: var(--space-xl); border-top: 1px solid var(--color-border); }
.footer-contact { text-align: right; font-size: 13px; }
p { margin: 0 0 var(--space-xs); color: var(--color-muted-foreground); }
@media (max-width: 600px) { .site-footer { flex-direction: column; } .footer-contact { text-align: left; } }
</style>
