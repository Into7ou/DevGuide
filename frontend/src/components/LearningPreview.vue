<template>
  <article class="visual answer">
    <div class="answer-top">DevGuide <span>React · 固定回答示例</span></div>
    <h3>每次渲染，都是一张快照。</h3>
    <p>更新 state 会请求下一次渲染；当前事件处理函数读到的，仍是这次渲染的值。<sup>[1]</sup></p>
    <pre><code>setCount(count + 1);
console.log(count); <span>// 当前渲染的值</span></code></pre><a class="source" href="https://react.dev/learn/state-as-a-snapshot" target="_blank" rel="noreferrer"><span>[1] 官方文档</span><strong>State as a Snapshot ↗</strong><small>react.dev</small></a>
  </article>
</template>
<style scoped src="../styles/learning-preview.css"></style>