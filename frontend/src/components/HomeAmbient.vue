<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const paths = [
  'M 1000,-100 C 400,250 1400,380 750,1100',
  'M 1150,-100 C 550,250 1550,380 900,1100',
  'M 1300,-100 C 700,250 1700,380 1050,1100'
]
const hidden = ref(false)
const updateVisibility = () => { hidden.value = document.hidden }
onMounted(() => {
  updateVisibility()
  document.addEventListener('visibilitychange', updateVisibility)
})
onBeforeUnmount(() => document.removeEventListener('visibilitychange', updateVisibility))
</script>

<template>
  <div class="atmosphere" :class="{ 'is-hidden': hidden }" aria-hidden="true">
    <div class="wash"></div>
    <div class="field field-one"></div>
    <div class="field field-two"></div><svg class="knowledge-paths" viewBox="0 0 1400 1000" preserveAspectRatio="xMidYMid slice">
      <g v-for="(path, index) in paths" :key="path">
        <path :d="path" />
        <circle v-for="particle in 4" :key="particle" class="particle" r="3"
          :style="{
            offsetPath: `path('${path}')`,
            animationDuration: `${18 + index * 4}s`,
            animationDelay: `${-particle * (18 + index * 4) / 4}s`
          }" />
      </g>
    </svg>
  </div>
</template>
<style scoped>
  .atmosphere {
    position: fixed;
    inset: 0;
    z-index: -1;
    overflow: hidden;
    pointer-events: none;
  }

  .wash {
    position: absolute;
    inset: 0;
    background: var(--scene-color, #f7f7f5);
  }

  .field {
    position: absolute;
    width: 90vw;
    height: 100vh;
    top: 0;
    left: 30%;
    border-radius: 50%;
    filter: blur(55px);
    will-change: transform;
  }

  .field-one {
    background: radial-gradient(ellipse, var(--field-color, #bed5f1) 0%, transparent 64%);
    opacity: .65;
    transform: translate(var(--field-x, 0%), var(--field-y, 0%)) scale(var(--field-scale, 1));
  }

  .field-two {
    background: radial-gradient(ellipse, #d6d5ef 0%, transparent 62%);
    opacity: .35;
    left: 55%;
    transform: translate(var(--field-two-x, 0%), 25%);
  }

  .knowledge-paths {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    fill: none;
    stroke: #4976a8;
    stroke-width: 1;
    opacity: .5;
    transform: translate(var(--path-x, 0px), var(--path-y, 0px)) rotate(var(--path-rotate, 0deg));
  }

  .knowledge-paths path { opacity: .36; }
  .particle {
    fill: #4976a8;
    stroke: none;
    animation: knowledge-flow 18s linear infinite;
  }
  .is-hidden .particle { animation-play-state: paused; }
  @keyframes knowledge-flow {
    from { offset-distance: 0%; }
    to { offset-distance: 100%; }
  }
  @media (max-width: 800px) {
    .particle:nth-of-type(even) { display: none; }
  }
  @media (prefers-reduced-motion: reduce) {
    .particle { display: none; animation: none; }


    .field,
    .knowledge-paths {
      transform: none;
      will-change: auto;
    }
  }
</style>