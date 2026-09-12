<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const header = ref(null)
let headerObserver
const user = ref(null)
const avatarFailed = ref(false)

const initial = computed(() => (user.value?.username?.charAt(0) || '?').toUpperCase())

function onAvatarError() {
  avatarFailed.value = true
}

async function loadUser() {
  try {
    const res = await fetch('/api/auth/me')
    if (res.ok) {
      const data = await res.json()
      user.value = data.authenticated ? data : null
      avatarFailed.value = false
    }
  } catch {
    // 后端未启动或未登录，忽略
  }
}

function login() {
  window.location.href = '/oauth2/authorization/github'
}

onMounted(() => {
  loadUser()
  headerObserver = new ResizeObserver(() => document.documentElement.style.setProperty('--app-header-height', `${header.value.getBoundingClientRect().height}px`))
  headerObserver.observe(header.value)
})
onBeforeUnmount(() => headerObserver?.disconnect())
</script>

<template>
  <header ref="header" class="header">
    <div class="header-inner page-container">
      <router-link to="/" class="brand site-brand">DevGuide<span aria-hidden="true"> / </span></router-link>
      <div class="right">
        <router-link to="/stacks" class="nav-link">技术栈</router-link>
        <template v-if="user">
          <img
            v-if="user.avatarUrl && !avatarFailed"
            :src="user.avatarUrl"
            class="avatar"
            :alt="`${user.username} 的头像`"
            @error="onAvatarError"
          />
          <span v-else class="avatar avatar-fallback">{{ initial }}</span>
          <span class="username">{{ user.username }}</span>
        </template>
        <button v-else class="btn-login" @click="login">登录</button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  background: #f7f7f5ed;
  position: sticky;
  top: 0;
  z-index: 40;
}
.header-inner {
  min-height: 76px;
  margin: 0 auto;
  padding-block: var(--space-md);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand {
  font-family: var(--font-body);
  font-weight: 600;
  font-size: 23px;
  color: var(--color-foreground);
  text-decoration: none;
}
.right {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.nav-link {
  font-size: 14px;
  color: var(--color-muted-foreground);
  text-decoration: none;
  transition: color 200ms ease;
}
.nav-link:hover { color: var(--color-foreground); text-decoration: none; }
.avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}
.avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent);
  color: var(--color-on-accent);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
}
.username {
  font-size: 13px;
  color: var(--color-foreground);
}
.btn-login {
  background: transparent;
  color: var(--color-accent);
  border: 1px solid var(--color-accent);
  border-radius: var(--radius-sm);
  padding: 6px 14px;
  font-size: 13px;
  transition: background 200ms ease, color 200ms ease;
}
.btn-login:hover {
  background: var(--color-accent);
  color: var(--color-on-accent);
}
@media (max-width: 600px) { .header-inner { min-height: 68px; gap: 12px; flex-wrap: wrap; } .right { gap: 12px; } .username { display: none; } }
</style>
