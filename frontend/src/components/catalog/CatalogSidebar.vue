<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import CatalogIcon from './CatalogIcon.vue'
import { DIRECTIONS, GROUPS } from '../../utils/catalog'
const props = defineProps({ direction: String, group: String, counts: { type: Object, default: () => ({}) } })
const emit = defineEmits(['navigate'])
const router = useRouter()
const items = [{ id: 'overview', name: '总览', short: '总览', icon: 'overview', groups: [] }, ...DIRECTIONS]
const open = ref(false), pinned = ref(false), mobile = ref(false)
const panel = ref(null), sidebar = ref(null), railToggle = ref(null), mobileToggle = ref(null)
let media, timer, originalOverflow = '', locked = false
const href = (d, g) => router.resolve({ name: 'stacks', query: { direction: d === 'overview' ? undefined : d, group: g || undefined } }).href
function unlock() {
  if (!locked) return
  document.body.style.overflow = originalOverflow
  document.querySelector('#main-content')?.removeAttribute('inert')
  document.querySelector('.header')?.removeAttribute('inert')
  locked = false
}
async function setOpen(value, focus = false, pin = false) {
  clearTimeout(timer)
  open.value = value
  if (!value) pinned.value = false
  else if (pin) pinned.value = true
  if (value && mobile.value && !locked) {
    originalOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    document.querySelector('#main-content')?.setAttribute('inert', '')
    document.querySelector('.header')?.setAttribute('inert', '')
    locked = true
  } else if (!value) unlock()
  if (focus) { await nextTick(); (value ? panel.value?.querySelector('button') : mobile.value ? mobileToggle.value : railToggle.value)?.focus({ preventScroll: true }) }
}
function leave() {
  clearTimeout(timer)
  timer = setTimeout(() => {
    if (pinned.value) return
    if (sidebar.value?.contains(document.activeElement) && document.activeElement.matches(':focus-visible')) return
    setOpen(false)
  }, 220)
}
async function activate(event, direction, group = '') {
  if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  emit('navigate', { direction, group })
  if (group) { setOpen(false, true); return }
  await setOpen(true, false, true)
  await nextTick()
  const selectedLink = [...(panel.value?.querySelectorAll('.direction') || [])].find(link => link.getAttribute('href') === href(direction))
  selectedLink?.focus({ preventScroll: true })
}
function keys(event) {
  if (!open.value) return
  if (event.key === 'Escape') { event.preventDefault(); setOpen(false, true) }
  if (event.key !== 'Tab' || !mobile.value) return
  const targets = [...panel.value.querySelectorAll('button,a[href]')]
  if (event.shiftKey && document.activeElement === targets[0]) { event.preventDefault(); targets.at(-1).focus() }
  else if (!event.shiftKey && document.activeElement === targets.at(-1)) { event.preventDefault(); targets[0].focus() }
}
function resize() { setOpen(false); mobile.value = media.matches }
onMounted(() => {
  media = matchMedia('(max-width: 760px)'); mobile.value = media.matches
  media.addEventListener('change', resize); document.addEventListener('keydown', keys)
})
onBeforeUnmount(() => { clearTimeout(timer); unlock(); media?.removeEventListener('change', resize); document.removeEventListener('keydown', keys) })
</script>
<template>
  <button ref="mobileToggle" class="mobile-toggle" :aria-expanded="open" aria-controls="catalog-nav-panel" @click="setOpen(!open, true, true)">分类 <CatalogIcon name="menu" /></button>
  <Teleport to="body">
    <button v-if="open" class="catalog-scrim" aria-label="关闭分类侧栏" tabindex="-1" @click="setOpen(false, true)"></button>
    <aside ref="sidebar" class="catalog-sidebar" :class="{ open }" aria-label="学习方向导航"
      @pointerenter="event => { if (event.pointerType === 'mouse' && !mobile) setOpen(true) }"
      @pointerleave="event => { if (event.pointerType === 'mouse' && !mobile) leave() }"
      @focusout="event => { if (!mobile && !sidebar?.contains(event.relatedTarget)) leave() }">
      <div class="rail">
        <button ref="railToggle" class="catalog-icon-button" :aria-label="open && pinned ? '收起分类侧栏' : '展开分类侧栏'" :aria-expanded="open" aria-controls="catalog-nav-panel" @click="setOpen(!(open && pinned), true, true)"><CatalogIcon name="menu" /></button>
        <div class="rail-rule"></div>
        <a v-for="item in items" :key="item.id" :href="href(item.id)" :aria-label="item.name" :aria-current="direction === item.id ? 'page' : undefined" @click="activate($event, item.id)"><CatalogIcon :name="item.icon" /><span>{{ item.short }}</span></a>
        <span class="rail-bottom" aria-hidden="true">DG</span>
      </div>
      <div id="catalog-nav-panel" ref="panel" class="nav-panel" :inert="!open" :role="mobile && open ? 'dialog' : undefined" :aria-modal="mobile && open ? true : undefined" aria-label="学习方向">
        <div class="panel-heading"><span>学习方向</span><button class="catalog-icon-button" aria-label="收起分类侧栏" @click="setOpen(false, true)"><CatalogIcon name="close" /></button></div>
        <nav aria-label="方向与用途分组">
          <template v-for="item in items" :key="item.id">
            <a class="direction" :href="href(item.id)" :aria-current="direction === item.id ? 'page' : undefined" @click="activate($event, item.id)"><CatalogIcon :name="item.icon" />{{ item.name }}<span class="chevron" aria-hidden="true">{{ direction === item.id ? '⌄' : '›' }}</span></a>
            <div v-if="direction === item.id && item.groups.length" class="subgroups">
              <a v-for="id in item.groups" :key="id" :href="href(item.id, id)" :aria-current="group === id ? 'page' : undefined" @click="activate($event, item.id, id)">{{ GROUPS[id].name }}<span>{{ counts[id] || 0 }}</span></a>
            </div>
          </template>
        </nav>
        <p class="panel-note">选一个方向，找到下一步。</p>
      </div>
    </aside>
  </Teleport>
</template>
