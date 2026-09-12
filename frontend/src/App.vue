<script setup>
import AppHeader from './components/AppHeader.vue'
import AppFooter from './components/AppFooter.vue'
</script>

<template>
  <a class="skip-link" href="#main-content">跳转至主要内容</a>
  <AppHeader />
  <main id="main-content" class="route-stage">
    <router-view v-slot="{ Component, route: pageRoute }">
      <Transition name="page"
        @before-leave="element => element.inert = true"
        @leave-cancelled="element => element.inert = false">
        <div :key="pageRoute.path" class="route-page">
          <div :class="pageRoute.name === 'stacks' ? 'catalog-layout' : pageRoute.name !== 'home' ? 'page-container content-page' : ''">
            <component :is="Component" />
          </div>
          <AppFooter v-if="pageRoute.name !== 'home'" :class="{ 'catalog-footer': pageRoute.name === 'stacks' }" />
        </div>
      </Transition>
    </router-view>
  </main>
</template>
