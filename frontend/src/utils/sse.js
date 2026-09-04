/**
 * SSE 流解析：把累积的原始文本切成「完整事件行」与「残留缓冲区」。
 * 纯函数，便于单元测试。
 *
 * data 支持旧版 JSON 字符串 token，以及学习对话的 { type, data } 事件。
 * JSON.parse 会还原 token 内换行，不会将原文或事件结构拼成 [object Object]。
 *
 * @param {string} buffer 当前累积的未处理文本（含上次残留）
 * @returns {{ payloads: Array<string|object>, rest: string }}
 *   payloads —— 本次完整解析出的 data 载荷（JSON.parse 后的真实字符串）
 *   rest     —— 未完整接收的残留（可能是被 chunk 截断的半行）
 */
export function parseSseChunk(buffer) {
  const payloads = []
  const lines = buffer.split('\n')
  // 最后一行可能是不完整的（尚未遇到换行符），留到下次
  const rest = lines.pop() || ''
  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed.startsWith('data:')) continue
    const payload = trimmed.slice(5).trim()
    if (payload === '' || payload === '[DONE]') continue
    const decoded = decodePayload(payload)
    if (decoded !== null) payloads.push(decoded)
  }
  return { payloads, rest }
}

/**
 * 解析最终残留的缓冲区（流结束时调用）。
 * @param {string} buffer 最后残留的文本
 * @returns {Array<string|object>} 完整的 data 载荷（JSON.parse 后）
 */
export function parseSseRemainder(buffer) {
  const trimmed = buffer.trim()
  if (!trimmed.startsWith('data:')) return []
  const payload = trimmed.slice(5).trim()
  if (payload === '' || payload === '[DONE]') return []
  const decoded = decodePayload(payload)
  return decoded === null ? [] : [decoded]
}

/**
 * 解码单个 data 载荷：JSON.parse 字符串字面量。
 * 后端 data 形如 "你好\n"（带引号的 JSON 字符串），parse 后得到含换行的真实 token。
 * @returns {string|object|null} 非事件的 JSON 数据跳过；旧版文本按原样返回。
 */
function decodePayload(payload) {
  try {
    const value = JSON.parse(payload)
    return typeof value === 'string' || (value && typeof value.type === 'string') ? value : null
  } catch {
    // 兼容非 JSON 载荷（如未来后端改动），按原样返回
    return payload
  }
}
