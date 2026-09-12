<script setup>
import { ref, reactive, watch, onBeforeUnmount } from 'vue'
import { parseSseChunk, parseSseRemainder } from '../utils/sse'
import { csrfHeaders } from '../utils/csrf'

const props = defineProps({ techStack: { type: String, required: true } })
const question = ref('')
const turns = ref([])
const loading = ref(false)
let activeController = null
let nextId = 1

function resetConversation() {
  activeController?.abort()
  activeController = null
  loading.value = false
  question.value = ''
  turns.value = []
}
watch(() => props.techStack, resetConversation)
onBeforeUnmount(resetConversation)

async function learn() {
  if (loading.value || !question.value.trim()) return
  const history = turns.value.filter(t => t.state === 'done').slice(-3)
    .map(t => ({ question: t.question, answer: t.answer, sources: t.sources }))
  const turn = reactive({
    id: nextId++, question: question.value.trim(), answer: '', sources: [], scope: '',
    status: '正在检索本地知识库…', steps: [], state: 'running', collapsed: false, error: '', partial: false
  })
  turns.value.push(turn)
  question.value = ''
  loading.value = true
  const controller = new AbortController()
  activeController = controller
  try {
    const securityHeaders = await csrfHeaders()
    const res = await fetch('/api/v1/agent/multi/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...securityHeaders },
      signal: controller.signal,
      body: JSON.stringify({ techStack: props.techStack, question: turn.question, history })
    })
    if (!res.ok || !res.body) {
      const data = await res.json().catch(() => ({}))
      if (res.status === 401) throw new Error('登录状态已失效，请重新登录后再试。')
      if (res.status === 429) throw new Error(data.message || '今日生成额度已用完或服务正忙，请稍后再试。')
      throw new Error(data.message || data.error || `请求失败 (${res.status})`)
    }
    await readStream(res.body, turn, controller.signal)
    if (!controller.signal.aborted && turn.state === 'running') throw new Error('连接中断，本轮回答未完成，请重新提问。')
  } catch (e) {
    if (!controller.signal.aborted) {
      turn.error = e.message
      turn.state = 'error'
    }
  } finally {
    if (activeController === controller) {
      activeController = null
      loading.value = false
    }
  }
}

function applyEvent(turn, event) {
  if (typeof event === 'string') { turn.answer += event; return }
  switch (event.type) {
    case 'status':
      turn.status = event.data
      turn.steps.push(event.data)
      break
    case 'scope': turn.scope = event.data; break
    case 'answer': turn.answer += event.data; break
    case 'sources': turn.sources = event.data; break
    case 'clarification': turn.answer += event.data; break
    case 'done':
      turn.state = 'done'
      turn.partial = event.data?.sufficient === false
      turn.status = event.data?.clarification ? '等待你的补充' : turn.partial ? '已回答有依据的部分' : '回答完成'
      break
    case 'error': turn.state = 'error'; turn.error = event.data; break
  }
}

async function readStream(body, turn, signal) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  const cancel = () => { reader.cancel().catch(() => {}) }
  signal.addEventListener('abort', cancel, { once: true })
  let buffer = ''
  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const { payloads, rest } = parseSseChunk(buffer)
      buffer = rest
      if (!signal.aborted) for (const event of payloads) applyEvent(turn, event)
    }
    buffer += decoder.decode()
    if (!signal.aborted) for (const event of parseSseRemainder(buffer)) applyEvent(turn, event)
  } finally {
    signal.removeEventListener('abort', cancel)
    reader.releaseLock()
  }
}

function sourceLabel(type) {
  return { official: '官方资料', 'third-party': '第三方资料', unknown: '来源身份待确认' }[type] || '来源身份待确认'
}
function sourceUrl(url) {
  return /^https?:\/\//i.test(url || '') ? url : undefined
}
</script>

<template>
  <section class="learn-panel card">
    <h3 class="title">学习对话</h3>
    <p class="hint">先检索本地知识库，资料不足时联网补充。回答附原文与出处，可以继续追问。</p>
    <p v-if="!turns.length" class="empty">从一个具体问题开始，例如“这个概念解决什么问题？”</p>
    <div class="conversation">
      <article v-for="(turn, index) in turns" :key="turn.id" class="turn" :data-turn="index + 1">
        <header class="turn-header">
          <span>第 {{ index + 1 }} 轮</span>
          <span class="turn-state">{{ turn.state === 'running' ? '进行中' : turn.state === 'error' ? '未完成' : turn.status }}</span>
          <button v-if="turn.state !== 'running'" class="fold-button" :aria-expanded="!turn.collapsed"
            @click="turn.collapsed = !turn.collapsed">{{ turn.collapsed ? '展开回答' : '折叠回答' }}</button>
        </header>
        <p class="user-question"><span class="speaker">你</span>{{ turn.question }}</p>
        <div v-show="!turn.collapsed" class="agent-response">
          <p v-if="turn.scope" class="scope">适用范围：{{ turn.scope }}</p>
          <p v-if="turn.state === 'running'" class="status" role="status">{{ turn.status }}</p>
          <details v-if="turn.steps.length" class="retrieval-steps">
            <summary>查看本轮检索过程</summary>
            <ol><li v-for="(step, stepIndex) in turn.steps" :key="stepIndex">{{ step }}</li></ol>
          </details>
          <p v-if="turn.answer" class="answer-text">{{ turn.answer }}</p>
          <p v-if="turn.error" class="error" role="alert">{{ turn.error }}</p>
          <div v-if="turn.sources.length" class="sources">
            <h4>本轮引用 · {{ turn.sources.length }} 个原文片段</h4>
            <details v-for="source in turn.sources" :key="source.id" class="source">
              <summary>[{{ source.id }}] {{ source.title }}</summary>
              <div class="source-tags">
                <span>{{ source.retrievedFrom === 'local' ? '本地知识库' : '本轮联网' }}</span>
                <span>{{ sourceLabel(source.sourceType) }}</span>
                <span>{{ source.techStacks?.join(' / ') }}</span>
              </div>
              <a :href="sourceUrl(source.url)" target="_blank" rel="noopener noreferrer">查看原文页面 ↗</a>
              <p class="source-url">{{ source.url }}</p>
              <blockquote>{{ source.excerpt }}</blockquote>
            </details>
          </div>
        </div>
      </article>
    </div>
    <form class="input-row" @submit.prevent="learn">
      <input v-model="question" class="input" aria-label="学习问题" maxlength="500"
        :placeholder="turns.length ? '继续追问，或提出新的问题…' : `例如：${techStack} 怎么入门？`" />
      <button class="btn-primary" type="submit" :disabled="loading || !question.trim()">
        {{ loading ? '回答中…' : turns.length ? '继续提问' : '提问' }}
      </button>
    </form>
    <p class="session-note">本页保留对话；刷新或切换技术栈后清空。</p>
  </section>
</template>

<style scoped>
.title { margin: 0 0 var(--space-sm); font-size: 16px; }
.hint, .empty, .session-note { color: var(--color-muted-foreground); font-size: 13px; }
.hint { margin: 0 0 var(--space-md); }
.empty { padding: var(--space-md) 0; }
.turn { padding: var(--space-lg) 0; border-top: 1px solid var(--color-border); }
.turn-header { display: flex; align-items: center; gap: var(--space-sm); color: var(--color-muted-foreground); font-size: 12px; }
.turn-state { flex: 1; }
.fold-button { background: transparent; border: 1px solid var(--color-border); color: var(--color-foreground); border-radius: var(--radius-sm); padding: 4px 8px; }
.user-question { display: flex; gap: var(--space-sm); font-weight: 600; overflow-wrap: anywhere; }
.speaker { color: var(--color-accent); flex-shrink: 0; }
.scope { border-left: 3px solid var(--color-accent); padding-left: var(--space-sm); font-size: 13px; color: var(--color-muted-foreground); }
.status { color: var(--color-accent); font-size: 13px; }
.retrieval-steps { font-size: 12px; color: var(--color-muted-foreground); margin-bottom: var(--space-md); }
summary { cursor: pointer; overflow-wrap: anywhere; }
.answer-text { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 14px; line-height: 1.8; }
.error { color: var(--color-destructive); font-size: 14px; }
.sources { margin-top: var(--space-md); }
.sources h4 { font-size: 13px; }
.source { margin-top: var(--space-sm); padding: var(--space-sm) var(--space-md); background: var(--color-background); border-radius: var(--radius-sm); font-size: 13px; }
.source-tags { display: flex; flex-wrap: wrap; gap: var(--space-sm); margin: var(--space-sm) 0; color: var(--color-muted-foreground); }
.source-tags span { border: 1px solid var(--color-border); border-radius: 4px; padding: 2px 6px; }
.source-url { overflow-wrap: anywhere; color: var(--color-muted-foreground); font-size: 12px; }
blockquote { margin: var(--space-sm) 0; padding-left: var(--space-sm); border-left: 2px solid var(--color-border); white-space: pre-wrap; overflow-wrap: anywhere; }
.input-row { display: flex; gap: var(--space-sm); margin-top: var(--space-md); }
.input-row .input { flex: 1; min-width: 0; }
.btn-primary { flex-shrink: 0; }
.session-note { margin-bottom: 0; font-size: 12px; }
@media (max-width: 600px) { .input-row { flex-direction: column; } .turn-header { flex-wrap: wrap; } .learn-panel { padding: var(--space-md); } }
</style>
