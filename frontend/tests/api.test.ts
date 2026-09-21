import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request, ticketApi } from '../src/lib/api'
import { accessToken, clearSession, saveToken } from '../src/lib/session'

const success = <T>(data: T) => new Response(JSON.stringify({ success: true, code: 'OK', message: 'success', data }),
  { status: 200, headers: { 'Content-Type': 'application/json' } })

describe('统一 API 请求', () => {
  beforeEach(() => {
    clearSession()
    vi.unstubAllGlobals()
  })

  it('携带当前 Access Token 并解包后端 ApiResponse', async () => {
    saveToken('test-token')
    const fetchMock = vi.fn().mockResolvedValue(success({ id: 'ticket-1' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request<{ id: string }>('/tickets/ticket-1')).resolves.toEqual({ id: 'ticket-1' })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tickets/ticket-1', expect.objectContaining({
      headers: expect.any(Headers),
    }))
    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer test-token')
  })

  it('401 清除会话，并通知界面跳转登录页', async () => {
    saveToken('expired-token')
    const listener = vi.fn()
    window.addEventListener('agentops:unauthorized', listener)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: false, code: 'AUTH_TOKEN_EXPIRED', message: 'Access Token 已过期',
    }), { status: 401 })))

    await expect(request('/auth/me')).rejects.toMatchObject({ status: 401, code: 'AUTH_TOKEN_EXPIRED' })
    expect(accessToken.value).toBeNull()
    expect(listener).toHaveBeenCalledOnce()
    window.removeEventListener('agentops:unauthorized', listener)
  })

  it('登录失败不触发过期事件，并保留后端错误说明', async () => {
    const listener = vi.fn()
    window.addEventListener('agentops:unauthorized', listener)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: false, code: 'AUTH_401', message: '用户名或密码不正确', traceId: 'trace-test',
    }), { status: 401 })))

    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toEqual(
      expect.objectContaining({ message: '用户名或密码不正确', traceId: 'trace-test' }),
    )
    expect(listener).not.toHaveBeenCalled()
    window.removeEventListener('agentops:unauthorized', listener)
  })

  it('网络失败提供可理解的错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(ticketApi.list({ page: 0 })).rejects.toBeInstanceOf(ApiError)
    await expect(ticketApi.list({ page: 0 })).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  })

  it('分页与状态筛选按后端约定编码', async () => {
    const fetchMock = vi.fn().mockResolvedValue(success({ items: [], page: 1, size: 20, totalElements: 0, totalPages: 0 }))
    vi.stubGlobal('fetch', fetchMock)
    await ticketApi.list({ status: 'PENDING', keyword: '账户 登录', page: 1 })
    expect(fetchMock.mock.calls[0][0]).toContain('status=PENDING')
    expect(fetchMock.mock.calls[0][0]).toContain('keyword=%E8%B4%A6%E6%88%B7+%E7%99%BB%E5%BD%95')
    expect(fetchMock.mock.calls[0][0]).toContain('page=1')
  })
})
