import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TicketConversationPanel from '../src/components/TicketConversationPanel.vue'
import AgentAnalysisPanel from '../src/components/AgentAnalysisPanel.vue'
import AiSuggestionPanel from '../src/components/AiSuggestionPanel.vue'
import { agentApi, ApiError, conversationApi, suggestionApi } from '../src/lib/api'
import { currentUser } from '../src/lib/session'
import { missingCitationMarkers } from '../src/lib/suggestions'
import type { AgentRun, AiSuggestion, CurrentUser, Ticket, TicketMessage } from '../src/types'

const ticket: Ticket = {
  id: 'ticket-1', ticketNo: 'T-001', requesterId: 'customer-1', title: '登录失败', description: '无法登录',
  status: 'PROCESSING', priority: 'MEDIUM', categoryId: null, teamId: null, assigneeId: null,
  reopenCount: 0, firstResponseAt: null, resolvedAt: null, closedAt: null,
  submittedAt: '2026-01-01T00:00:00Z', createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z', version: 0,
}

const customer: CurrentUser = {
  id: 'customer-1', tenantId: 'default', username: 'customer', displayName: '客户', email: null,
  teamId: null, roles: ['CUSTOMER'], permissions: ['ticket:message:read', 'ticket:message:reply'],
}
const support: CurrentUser = {
  id: 'support-1', tenantId: 'default', username: 'support', displayName: '客服', email: null,
  teamId: 'team-1', roles: ['SUPPORT'], permissions: [
    'ticket:message:read', 'ticket:message:reply', 'ticket:message:note', 'ticket:suggestion:send',
    'agent:read', 'agent:rerun', 'suggestion:read', 'suggestion:review',
  ],
}

const messages: TicketMessage[] = [
  { id: 'm1', ticketId: 'ticket-1', senderType: 'USER', senderId: 'customer-1', messageType: 'CUSTOMER_REPLY',
    content: '公开内容', sourceSuggestionId: null, clientRequestId: 'r1', visibleToRequester: true, createdAt: '2026-01-01T00:00:00Z' },
  { id: 'm2', ticketId: 'ticket-1', senderType: 'SUPPORT', senderId: 'support-1', messageType: 'INTERNAL_NOTE',
    content: '内部秘密', sourceSuggestionId: null, clientRequestId: 'r2', visibleToRequester: false, createdAt: '2026-01-01T01:00:00Z' },
]

const run: AgentRun = {
  id: 'run-1', ticketId: 'ticket-1', runType: 'TICKET_ANALYSIS', inputRevision: 1, attemptNo: 1,
  executionCount: 1, triggerType: 'TICKET_CREATED', status: 'RUNNING', modelProvider: 'fake', modelName: 'fake-v1',
  inputTokens: 10, outputTokens: 0, errorCode: null, startedAt: '2026-01-01T00:00:00Z', finishedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
}

const suggestion: AiSuggestion = {
  id: 'suggestion-1', ticketId: 'ticket-1', runId: 'run-1', status: 'READY',
  originalContent: '请参考。[citation:chunk-1]', editedContent: null, finalContentSnapshot: null,
  confidence: .91, version: 2,
  citations: [{ articleId: 'article-1', versionId: 'version-1', chunkId: 'chunk-1',
    contentSnapshot: '知识片段', retrievalScore: .87, usedInAnswer: true }],
}

describe('工单详情处理工作区', () => {
  beforeEach(() => { currentUser.value = customer })
  afterEach(() => { currentUser.value = null; vi.restoreAllMocks(); vi.useRealTimers() })

  it('客户界面防御性隐藏内部备注', async () => {
    vi.spyOn(conversationApi, 'list').mockResolvedValue(messages)
    const wrapper = mount(TicketConversationPanel, { props: { ticket } })
    await flushPromises()
    expect(wrapper.text()).toContain('公开内容')
    expect(wrapper.text()).not.toContain('内部秘密')
    wrapper.unmount()
  })

  it('会话发送失败后以同一内容重试复用 clientRequestId', async () => {
    vi.spyOn(conversationApi, 'list').mockResolvedValue([])
    const post = vi.spyOn(conversationApi, 'post').mockRejectedValue(new ApiError(0, 'NETWORK_ERROR', '网络中断'))
    const uuid = vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111')
    const wrapper = mount(TicketConversationPanel, { props: { ticket } })
    await flushPromises()
    await wrapper.get('textarea').setValue('同一条回复')
    await wrapper.get('.composer-footer button').trigger('click')
    await flushPromises()
    await wrapper.get('.composer-footer button').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledTimes(2)
    expect(post.mock.calls[0][3]).toBe(post.mock.calls[1][3])
    expect(uuid).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('关闭工单禁用会话输入并提示重新打开', async () => {
    vi.spyOn(conversationApi, 'list').mockResolvedValue([])
    const wrapper = mount(TicketConversationPanel, { props: { ticket: { ...ticket, status: 'CLOSED' } } })
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.text()).toContain('请先通过右侧状态流转将工单重新打开')
    wrapper.unmount()
  })

  it('Agent 组件卸载后停止运行中任务的轮询', async () => {
    currentUser.value = support
    vi.useFakeTimers()
    const runs = vi.spyOn(agentApi, 'runs').mockResolvedValue([run])
    vi.spyOn(agentApi, 'analysis').mockResolvedValue(null)
    vi.spyOn(agentApi, 'steps').mockResolvedValue([])
    const wrapper = mount(AgentAnalysisPanel, { props: { ticketId: 'ticket-1' } })
    await flushPromises()
    expect(wrapper.text()).toContain('自动刷新中')
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(3500)
    expect(runs).toHaveBeenCalledTimes(1)
  })

  it('编辑建议缺失已验证 citation 标记时在前端拦截', async () => {
    currentUser.value = support
    vi.spyOn(suggestionApi, 'list').mockResolvedValue([suggestion])
    vi.spyOn(conversationApi, 'list').mockResolvedValue([])
    const edit = vi.spyOn(suggestionApi, 'edit')
    const wrapper = mount(AiSuggestionPanel, { props: { ticketId: 'ticket-1', closed: false } })
    await flushPromises()
    await wrapper.get('.suggestion-actions .button-outline').trigger('click')
    await wrapper.get('.suggestion-editor textarea').setValue('删除了引用的正文')
    await wrapper.get('.suggestion-editor .button-primary').trigger('click')
    expect(wrapper.text()).toContain('必须保留 1 个已验证的 citation 标记')
    expect(edit).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('引用校验只要求用于回答的片段', () => {
    const optional = { ...suggestion.citations[0], chunkId: 'chunk-2', usedInAnswer: false }
    expect(missingCitationMarkers('正文 [citation:chunk-1]', [...suggestion.citations, optional])).toEqual([])
    expect(missingCitationMarkers('正文', suggestion.citations)).toEqual(['chunk-1'])
  })

  it('采纳与发送是独立操作，只有已采纳建议显示发送按钮', async () => {
    currentUser.value = support
    const adopted = { ...suggestion, status: 'ADOPTED' as const, finalContentSnapshot: suggestion.originalContent }
    vi.spyOn(suggestionApi, 'list').mockResolvedValue([adopted])
    vi.spyOn(conversationApi, 'list').mockResolvedValue([])
    const send = vi.spyOn(conversationApi, 'sendSuggestion').mockResolvedValue({ ...messages[0],
      id: 'sent-1', sourceSuggestionId: adopted.id, messageType: 'AI_SUGGESTION' })
    const wrapper = mount(AiSuggestionPanel, { props: { ticketId: 'ticket-1', closed: false } })
    await flushPromises()
    expect(wrapper.text()).toContain('已完成采纳审批')
    expect(wrapper.text()).toContain('发送给客户')
    expect(wrapper.text()).not.toContain('采纳建议')
    await wrapper.get('.send-suggestion-button').trigger('click')
    await flushPromises()
    expect(send).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('已发送到会话')
    expect(wrapper.get('.send-suggestion-button').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('建议版本冲突提示刷新最新建议', async () => {
    currentUser.value = support
    vi.spyOn(suggestionApi, 'list').mockResolvedValue([suggestion])
    vi.spyOn(conversationApi, 'list').mockResolvedValue([])
    vi.spyOn(suggestionApi, 'adopt').mockRejectedValue(new ApiError(409, 'COMMON_409', '建议版本已变化'))
    const wrapper = mount(AiSuggestionPanel, { props: { ticketId: 'ticket-1', closed: false } })
    await flushPromises()
    await wrapper.get('.suggestion-actions .button-primary').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('请刷新最新建议后再操作')
    expect(wrapper.text()).toContain('刷新最新建议')
    wrapper.unmount()
  })
})
