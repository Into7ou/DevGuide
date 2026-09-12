<script setup>
  import {
    ref,
    onMounted,
    onBeforeUnmount
  } from 'vue'
  const element = ref(null)
  const stage = ref(0)
  let preference, observer, timer
  let visible = false
  const stepClass = index => ({
    'is-active': stage.value === index,
    'is-done': stage.value > index
  })

  function sync() {
    clearInterval(timer)
    if (preference.matches) stage.value = 3
    if (visible && !document.hidden && !preference.matches) {
      timer = setInterval(() => {
        stage.value = (stage.value + 1) % 4
      }, 2600)
    }
  }
  onMounted(() => {
    preference = matchMedia('(prefers-reduced-motion: reduce)')
    observer = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      sync()
    }, {
      threshold: .2
    })
    observer.observe(element.value)
    preference.addEventListener('change', sync)
    document.addEventListener('visibilitychange', sync)
    sync()
  })
  onBeforeUnmount(() => {
    clearInterval(timer)
    observer?.disconnect()
    preference?.removeEventListener('change', sync)
    document.removeEventListener('visibilitychange', sync)
  })
</script>

<template>
  <div ref="element" class="visual opening" aria-label="Agent 处理过程固定演示">
    <div class="demo-top"><span>DevGuide</span></div>
    <p class="question-card">React 的 state，<br>为什么像一张快照？</p>
    <ol class="agent-steps">
      <li :class="stepClass(0)"><span class="step-dot"></span>
        <div><strong>检索本地资料</strong><small>查找与问题相关的官方文档</small></div>
      </li>
      <li :class="stepClass(1)"><span class="step-dot"></span>
        <div><strong>判断资料是否充分</strong><small>识别当前问题的证据缺口</small></div>
      </li>
      <li :class="stepClass(2)"><span class="step-dot"></span>
        <div><strong>整理回答与引用</strong><small>连接结论与原文依据</small></div>
      </li>
    </ol>
    <div class="demo-result" :class="{ 'is-ready': stage === 3 }"><span>回答预览</span>
      <p>每次渲染，都是一张快照。<sup>[1]</sup></p>
    </div>
    <div class="demo-controls"><small>固定流程 · 资料不足时才联网补充</small></div>
  </div>
</template>
<style scoped src="../styles/agent-preview.css"></style>