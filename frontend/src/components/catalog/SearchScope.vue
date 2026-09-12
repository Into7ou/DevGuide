<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
const props = defineProps({ modelValue: { type: String, default: 'all' }, options: { type: Array, required: true } })
const emit = defineEmits(['update:modelValue'])
const open = ref(false), picker = ref(null), trigger = ref(null), menu = ref(null)
async function toggle() {
  open.value = !open.value
  if (open.value) { await nextTick(); menu.value?.querySelector('[aria-checked="true"]')?.focus() }
}
function close(restore = false) { open.value = false; if (restore) trigger.value?.focus() }
function select(value) { emit('update:modelValue', value); close(true) }
function keyboard(event) {
  const options = [...menu.value.querySelectorAll('button')]
  const index = options.indexOf(document.activeElement)
  const next = { ArrowDown: (index + 1) % options.length, ArrowUp: (index + options.length - 1) % options.length, Home: 0, End: options.length - 1 }[event.key]
  if (next !== undefined) { event.preventDefault(); options[next].focus() }
  if (event.key === 'Escape') { event.preventDefault(); close(true) }
}
function outside(event) { if (!picker.value?.contains(event.target)) close() }
onMounted(() => document.addEventListener('pointerdown', outside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside))
</script>
<template>
  <div ref="picker" class="scope-picker" @focusout="event => { if (!picker?.contains(event.relatedTarget)) close() }">
    <button ref="trigger" class="scope-trigger" aria-haspopup="menu" :aria-expanded="open" aria-controls="catalog-scope-menu" @click="toggle" @keydown.down.prevent="!open && toggle()">
      <span class="scope-caption">范围</span><span>{{ options.find(option => option.value === modelValue)?.label || '全部技术' }}</span><span aria-hidden="true">⌄</span>
    </button>
    <div v-if="open" id="catalog-scope-menu" ref="menu" class="scope-menu" role="menu" aria-label="搜索范围" @keydown="keyboard">
      <div class="scope-heading" role="presentation">搜索范围</div>
      <button v-for="option in options" :key="option.value" role="menuitemradio" :aria-checked="option.value === modelValue" @click="select(option.value)">
        <span><strong>{{ option.label }}</strong><small>{{ option.note }}</small></span><span aria-hidden="true">{{ option.value === modelValue ? '✓' : '' }}</span>
      </button>
    </div>
  </div>
</template>
