import { accessToken, clearSession } from './session'
import type {
  AgentRun, AgentStep, AiSuggestion, CurrentUser, LoginResponse, MessageIntent, Ticket,
  TicketAnalysis, TicketAssignment, TicketMessage, TicketPage, TicketPriority, TicketStatus,
  TicketTransition,
} from '../types'

interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
  traceId?: string
}

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string,
    public readonly traceId?: string) {
    super(message)
    this.name = 'ApiError'
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown }

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (accessToken.value) headers.set('Authorization', `Bearer ${accessToken.value}`)

  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, {
      ...options,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '无法连接服务器，请检查后端是否已启动。')
  }

  let envelope: ApiEnvelope<T> | null = null
  try {
    envelope = await response.json() as ApiEnvelope<T>
  } catch {
    throw new ApiError(response.status, 'INVALID_RESPONSE', '服务器返回了无法识别的响应。')
  }
  if (!response.ok || !envelope.success) {
    if (response.status === 401 && path !== '/auth/login') {
      clearSession()
      window.dispatchEvent(new Event('agentops:unauthorized'))
    }
    throw new ApiError(response.status, envelope.code ?? 'REQUEST_FAILED',
      envelope.message ?? '请求失败，请稍后再试。', envelope.traceId)
  }
  return envelope.data
}

export const authApi = {
  login: (tenantId: string, username: string, password: string) =>
    request<LoginResponse>('/auth/login', { method: 'POST', body: { tenantId, username, password } }),
  me: () => request<CurrentUser>('/auth/me'),
}

export interface TicketQuery {
  status?: TicketStatus | ''
  priority?: TicketPriority | ''
  keyword?: string
  page?: number
  size?: number
}

export const ticketApi = {
  list: (query: TicketQuery) => {
    const params = new URLSearchParams()
    if (query.status) params.set('status', query.status)
    if (query.priority) params.set('priority', query.priority)
    if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
    params.set('page', String(query.page ?? 0))
    params.set('size', String(query.size ?? 20))
    return request<TicketPage>(`/tickets?${params.toString()}`)
  },
  get: (id: string) => request<Ticket>(`/tickets/${encodeURIComponent(id)}`),
  transitions: (id: string) => request<TicketTransition[]>(`/tickets/${encodeURIComponent(id)}/transitions`),
  assignments: (id: string) => request<TicketAssignment[]>(`/tickets/${encodeURIComponent(id)}/assignments`),
  create: (input: { title: string; description: string; priority: TicketPriority }, idempotencyKey: string) =>
    request<Ticket>('/tickets', {
      method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: input,
    }),
  transition: (id: string, toStatus: TicketStatus, expectedVersion: number, reason: string, commandId: string) =>
    request<Ticket>(`/tickets/${encodeURIComponent(id)}/transitions`, {
      method: 'POST', body: { toStatus, expectedVersion, reason, commandId },
    }),
  assign: (id: string, expectedVersion: number, teamId: string | null, assigneeId: string | null, reason: string) =>
    request<Ticket>(`/tickets/${encodeURIComponent(id)}/assignments`, {
      method: 'POST', body: { expectedVersion, teamId, assigneeId, reason },
    }),
}

export const conversationApi = {
  list: (ticketId: string) => request<TicketMessage[]>(`/tickets/${encodeURIComponent(ticketId)}/messages`),
  post: (ticketId: string, intent: MessageIntent, content: string, clientRequestId: string) =>
    request<TicketMessage>(`/tickets/${encodeURIComponent(ticketId)}/messages`, {
      method: 'POST', body: { intent, content, clientRequestId },
    }),
  sendSuggestion: (ticketId: string, suggestionId: string, clientRequestId: string) =>
    request<TicketMessage>(`/tickets/${encodeURIComponent(ticketId)}/suggestions/${encodeURIComponent(suggestionId)}/send`, {
      method: 'POST', body: { clientRequestId },
    }),
}

export const agentApi = {
  runs: (ticketId: string) => request<AgentRun[]>(`/tickets/${encodeURIComponent(ticketId)}/agent-runs`),
  run: (runId: string) => request<AgentRun>(`/agent-runs/${encodeURIComponent(runId)}`),
  steps: (runId: string) => request<AgentStep[]>(`/agent-runs/${encodeURIComponent(runId)}/steps`),
  analysis: (ticketId: string) => request<TicketAnalysis | null>(`/tickets/${encodeURIComponent(ticketId)}/analysis`),
  rerun: (ticketId: string) => request<AgentRun>(`/tickets/${encodeURIComponent(ticketId)}/agent-runs`, { method: 'POST' }),
}

export const suggestionApi = {
  list: (ticketId: string) => request<AiSuggestion[]>(`/tickets/${encodeURIComponent(ticketId)}/suggestions`),
  get: (id: string) => request<AiSuggestion>(`/suggestions/${encodeURIComponent(id)}`),
  edit: (id: string, expectedVersion: number, content: string) =>
    request<AiSuggestion>(`/suggestions/${encodeURIComponent(id)}`, {
      method: 'PATCH', body: { expectedVersion, content },
    }),
  adopt: (id: string, expectedVersion: number) =>
    request<AiSuggestion>(`/suggestions/${encodeURIComponent(id)}/adopt`, {
      method: 'POST', body: { expectedVersion },
    }),
  reject: (id: string, expectedVersion: number, reason: string) =>
    request<AiSuggestion>(`/suggestions/${encodeURIComponent(id)}/reject`, {
      method: 'POST', body: { expectedVersion, reason },
    }),
}
