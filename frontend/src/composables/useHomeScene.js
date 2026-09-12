import { ref, onMounted, onBeforeUnmount } from 'vue'

const scenes = [
  { color: [247, 247, 245], field: [190, 213, 241], x: 0, y: 0, scale: 1 },
  { color: [226, 236, 249], field: [150, 189, 237], x: -28, y: 8, scale: 1.3 },
  { color: [234, 230, 244], field: [195, 183, 229], x: -8, y: -20, scale: .9 },
  { color: [247, 247, 245], field: [201, 219, 223], x: 20, y: 8, scale: 1.1 }
];
const mix = (a, b, t) => a + (b - a) * t
const rgb = (a, b, t) => `rgb(${a.map((value, i) => Math.round(mix(value, b[i], t))).join(',')})`

export function useHomeScene(home) {
  const active = ref(0)
  let preference, observer, frame, chapters = []
  function render() {
    frame = undefined
    if (!home.value) return
    const positions = chapters.map(chapter => chapter.getBoundingClientRect().top + window.scrollY)
    const y = window.scrollY + 77
    active.value = positions.reduce((found, top, i) => y + innerHeight / 2 >= top ? i : found, 0)
    if (preference.matches) { home.value.removeAttribute('style'); return }
    const index = positions.reduce((found, top, i) => y >= top ? i : found, 0)
    const next = Math.min(index + 1, scenes.length - 1)
    const end = Math.min(positions[next], document.documentElement.scrollHeight - innerHeight + 77)
    const t = Math.max(0, Math.min(1, (y - positions[index]) / Math.max(1, end - positions[index])))
    const a = scenes[index], b = scenes[next]
    const properties = {
      '--scene-color': rgb(a.color, b.color, t), '--field-color': rgb(a.field, b.field, t),
      '--field-x': `${mix(a.x, b.x, t)}%`, '--field-y': `${mix(a.y, b.y, t)}%`,
      '--field-scale': mix(a.scale, b.scale, t), '--field-two-x': `${-mix(a.x, b.x, t)}%`,
      '--path-x': `${(index + t) * -45}px`, '--path-rotate': `${(index + t) * 6}deg`,
      '--path-y': `${(index + t) * -28}px`
    }
    Object.entries(properties).forEach(([key, value]) => home.value.style.setProperty(key, value))
  }
  function schedule() { if (frame === undefined) frame = requestAnimationFrame(render) }
  function measure() {
    const fits = chapters.every(chapter => chapter.offsetHeight <= innerHeight - 76 + 2)
    document.documentElement.classList.toggle('home-snap', fits && !preference.matches)
    schedule()
  }
  onMounted(() => {
    document.documentElement.classList.add('home-scroll')
    chapters = [...home.value.querySelectorAll('.chapter')]
    preference = matchMedia('(prefers-reduced-motion: reduce)')
    observer = new ResizeObserver(measure)
    chapters.forEach(chapter => observer.observe(chapter))
    window.addEventListener('scroll', schedule, { passive: true })
    window.addEventListener('resize', measure)
    preference.addEventListener('change', measure)
    measure()
  })
  onBeforeUnmount(() => {
    if (frame !== undefined) cancelAnimationFrame(frame)
    observer?.disconnect()
    window.removeEventListener('scroll', schedule)
    window.removeEventListener('resize', measure)
    preference?.removeEventListener('change', measure)
    document.documentElement.classList.remove('home-snap', 'home-scroll')
  })
  return { active }
}
