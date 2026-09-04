import { afterEach, describe, expect, it, vi } from 'vitest'
import { csrfHeaders } from './csrf'

afterEach(() => vi.unstubAllGlobals())

describe('CSRF 请求头', () => {
  it('从公开端点取得服务端指定的令牌头', async () => {
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ token: 'token-123', headerName: 'X-XSRF-TOKEN' })
    })
    vi.stubGlobal('fetch', fetch)

    await expect(csrfHeaders()).resolves.toEqual({ 'X-XSRF-TOKEN': 'token-123' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/csrf')
  })

  it('令牌端点失败时阻止状态变更请求继续发送', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503 }))

    await expect(csrfHeaders()).rejects.toThrow('无法获取安全令牌')
  })
})
