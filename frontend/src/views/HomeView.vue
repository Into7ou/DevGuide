<script setup>
  import {
    ref
  } from 'vue'
  import HomeAmbient from '../components/HomeAmbient.vue'
  import AgentProcessPreview from '../components/AgentProcessPreview.vue'
  import LearningPreview from '../components/LearningPreview.vue'
  import AppFooter from '../components/AppFooter.vue'
  import {
    useHomeScene
  } from '../composables/useHomeScene'
  const home = ref(null)
  const {
    active
  } = useHomeScene(home)
  const technologies = ['React', 'Spring Boot', 'Python', 'TypeScript', 'PyTorch', 'Docker']
</script>

<template>
  <div ref="home" class="home">
    <HomeAmbient />
    <nav class="chapters" aria-label="页面章节">
      <router-link :aria-current="active === 0 ? 'step' : undefined"
        :to="{ hash: '#question' }" aria-label="第一屏：提出问题">01</router-link>
      <router-link :aria-current="active === 1 ? 'step' : undefined"
        :to="{ hash: '#research' }" aria-label="第二屏：寻找依据">02</router-link>
      <router-link :aria-current="active === 2 ? 'step' : undefined"
        :to="{ hash: '#understand' }" aria-label="第三屏：形成理解">03</router-link>
      <router-link :aria-current="active === 3 ? 'step' : undefined"
        :to="{ hash: '#explore' }" aria-label="第四屏：开始探索">04</router-link>
    </nav>
    <section class="chapter" id="question">
      <div class="copy">
        <h1>从一个问题，<br>到真正理解。</h1>
        <p class="intro">连接官方文档与开源项目，<br>为开发者找到有依据的学习方向。</p>
        <div class="actions"><router-link class="btn-primary" :to="{ name: 'stacks' }">开始探索 ↗</router-link><router-link :to="{ hash: '#research' }">了解工作方式 ↓</router-link></div>
      </div>
      <AgentProcessPreview /><span class="screen-note">向下滚动，跟随一次知识探索 ↓</span>
    </section>
    <section class="chapter" id="research">
      <div class="copy">
        <h2>先找到依据。<br>再向前一步。</h2>
        <p class="intro">先检索本地学习资料，<br>遇到知识缺口，再联网补充。</p>
        <p class="footnote">固定流程示意 · 不触发真实生成</p>
      </div>
      <div class="visual research">
        <div class="flow-step"><span>01</span>
          <div><strong>检索本地资料</strong><small>官方文档 / 开源项目</small></div>
        </div>
        <div class="flow-line"></div>
        <div class="flow-step"><span>02</span>
          <div><strong>判断资料是否充分</strong><small>是否覆盖当前问题？</small></div>
        </div>
        <aside class="branch"><span>资料充分 → 直接整理回答<br>资料不足 → 联网补充后整理回答</span></aside>
        <div class="flow-line"></div>
        <div class="flow-step"><span>03</span>
          <div><strong>整理回答与引用</strong><small>将结论与依据连接</small></div>
        </div>
      </div>
    </section>
    <section class="chapter" id="understand">
      <div class="copy">
        <h2>答案之外，<br>还有来处。</h2>
        <p class="intro">读懂解释，也能回到原文。<br>每一次追问，都有继续深入的起点。</p>
      </div>
      <LearningPreview />
    </section>
    <section class="chapter final" id="explore">
      <div class="copy">
        <h2>下一步，<br>想弄懂什么？</h2>
        <p class="intro">从你感兴趣的技术开始。</p><router-link class="btn-primary" :to="{ name: 'stacks' }">探索技术栈 ↗</router-link>
      </div>
      <div class="visual technologies" aria-label="技术栈入口">
        <router-link v-for="name in technologies" :key="name" :to="{ name: 'stacks', query: { q: name } }">{{ name }} ↗</router-link>
      </div>
      <AppFooter />
    </section>
  </div>
</template>
<style scoped src="../styles/home.css"></style>