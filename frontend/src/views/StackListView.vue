<script setup>
import { computed } from 'vue'
import { useCatalog } from '../composables/useCatalog'
import { GROUPS } from '../utils/catalog'
import CatalogSidebar from '../components/catalog/CatalogSidebar.vue'
import SearchScope from '../components/catalog/SearchScope.vue'
import StackRow from '../components/catalog/StackRow.vue'
import CatalogIcon from '../components/catalog/CatalogIcon.vue'
import '../styles/catalog.css'
const catalog = useCatalog()
const { route, stacks, loading, error, loginPrompt, authenticated, keyword, direction, group, scope, scopeOptions, results, page, pages, pageItems, pageSize, counts, boards, showWebSearch, changeScope, navigate, changePage, load, discover } = catalog
const list = computed(() => Boolean(keyword.value.trim() || group.value))
const title = computed(() => group.value ? GROUPS[group.value].name : direction.value?.name || '探索技术栈')
const intro = computed(() => group.value ? GROUPS[group.value].desc : direction.value?.intro || '从一个学习方向，找到你的下一步。')
const summary = computed(() => list.value ? `${keyword.value.trim() ? (scopeOptions.value.find(item => item.value === scope.value)?.label || '全部技术') : title.value} · ${results.value.length} 项技术` : `${boards.value.length} 个${direction.value ? '用途分组' : '学习方向'} · ${direction.value ? new Set(boards.value.flatMap(board => board.items.map(stack => stack.name))).size : stacks.value.length} 项已收录技术`)
</script>
<template>
  <section class="catalog">
    <div class="breadcrumb-row">
      <CatalogSidebar :direction="direction?.id || 'overview'" :group="group" :counts="counts" @navigate="navigate" />
      <nav class="catalog-breadcrumbs" aria-label="当前位置"><router-link :to="{ name: 'stacks' }">技术栈目录</router-link><span aria-hidden="true">/</span><router-link v-if="group" :to="{ name: 'stacks', query: { direction: direction.id } }">{{ direction.name }}</router-link><span v-if="group" aria-hidden="true">/</span><span>{{ group ? GROUPS[group].name : direction?.name || '总览' }}</span></nav>
    </div>
    <header class="catalog-hero"><div><h1>{{ title }}</h1><p>{{ intro }}</p></div><div class="hero-art" aria-hidden="true"><span class="art-line"></span><span class="art-node node-a">{ }</span><span class="art-node node-b">↗</span><span class="art-node node-c">&lt;/&gt;</span></div></header>
    <div class="search-bar" role="search" aria-label="搜索技术栈">
      <CatalogIcon name="search" /><label class="sr-only" for="stack-search">技术名称或用途</label>
      <input id="stack-search" v-model="keyword" type="search" placeholder="搜索技术名称或用途…" autocomplete="off" />
      <SearchScope :model-value="scope" :options="scopeOptions" @update:model-value="changeScope" />
    </div>
    <p v-if="loading" class="catalog-state" role="status">加载目录中…</p>
    <div v-else-if="error" class="catalog-state" role="alert"><p>{{ error }}</p><button class="catalog-action" @click="load">重新加载</button></div>
    <template v-else>
      <div class="content-toolbar"><p role="status" aria-live="polite">{{ summary }}</p><span>{{ list ? '展示顺序 · 名称' : direction ? '分组概览' : '目录总览' }}</span></div>
      <div id="catalog-results">
        <div v-if="!list" class="group-grid">
          <section v-for="board in boards" :key="board.id" class="group-section">
            <div class="group-heading"><div><h2><CatalogIcon :name="board.icon" />{{ board.name }}</h2><p>{{ board.desc }}</p></div><span class="group-count">{{ board.items.length }}</span></div>
            <StackRow v-for="stack in board.items.slice(0, 4)" :key="stack.id || stack.name" :stack="stack" :return-to="route.fullPath" />
            <p v-if="!board.items.length" class="group-empty">暂无已确认归属的技术</p>
            <router-link class="more-link" :to="{ name: 'stacks', query: board.query }">{{ direction ? `查看全部 ${board.items.length} 项` : `探索${board.name}` }} <span aria-hidden="true">→</span></router-link>
          </section>
        </div>
        <div v-else-if="!results.length" class="catalog-empty">
          <CatalogIcon name="search" /><h2>{{ scope !== 'all' && keyword.trim() ? '当前范围没有匹配结果' : keyword.trim() ? '没有找到匹配的技术' : '这个分组还没有技术条目' }}</h2>
          <template v-if="scope !== 'all' && keyword.trim()"><p>换到全部技术，继续找一找。</p><button class="catalog-action" @click="changeScope('all')">搜索全部技术</button></template>
          <template v-else-if="showWebSearch"><p>可联网查询官方资料与开源项目。</p><button class="btn-primary" @click="discover">{{ authenticated ? `联网查询「${keyword.trim()}」` : '登录后联网查询' }}</button><p v-if="loginPrompt" class="login-prompt" role="status">动态搜索会调用外部服务，请先登录。<a href="/oauth2/authorization/github">使用 GitHub 登录</a></p></template>
          <p v-else>可以通过全目录搜索查找已收录的技术。</p>
        </div>
        <template v-else>
          <div class="list-head"><span>技术 / 用途</span><span>技术标签</span></div>
          <div class="result-list"><StackRow v-for="stack in pageItems" :key="stack.id || stack.name" :stack="stack" full :return-to="route.fullPath" /></div>
          <nav class="catalog-pagination" aria-label="结果分页"><span>共 {{ results.length }} 项 · 每页 {{ pageSize }} 项</span><div><button :disabled="page === 1" @click="changePage(page - 1)">上一页</button><span>{{ page }} / {{ pages }}</span><button :disabled="page === pages" @click="changePage(page + 1)">下一页</button></div></nav>
        </template>
      </div>
    </template>
  </section>
</template>
