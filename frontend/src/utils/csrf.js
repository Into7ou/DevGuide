export async function csrfHeaders() {
  const response = await fetch('/api/auth/csrf')
  if (!response.ok) throw new Error(`无法获取安全令牌 (${response.status})`)
  const data = await response.json()
  if (!data?.token || !data?.headerName) throw new Error('安全令牌响应无效')
  return { [data.headerName]: data.token }
}
