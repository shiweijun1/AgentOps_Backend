import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TicketListView from '../src/views/TicketListView.vue'
import { ApiError, ticketApi } from '../src/lib/api'
import { currentUser } from '../src/lib/session'
import type { CurrentUser, TicketPage } from '../src/types'

const emptyPage: TicketPage = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

function user(role: 'CUSTOMER' | 'SUPPORT', permissions: string[]): CurrentUser {
  return {
    id: 'user-1', tenantId: 'default', username: 'test', displayName: '测试用户', email: null,
    teamId: null, roles: [role], permissions,
  }
}

async function render(path = '/tickets') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/tickets', component: TicketListView }] })
  await router.push(path)
  await router.isReady()
  return mount(TicketListView, { global: { plugins: [router] } })
}

describe('工单列表状态与角色', () => {
  beforeEach(() => { currentUser.value = user('CUSTOMER', ['ticket:create', 'ticket:read:self']) })
  afterEach(() => { currentUser.value = null; vi.restoreAllMocks() })

  it('加载时展示明确的等待状态', async () => {
    vi.spyOn(ticketApi, 'list').mockReturnValue(new Promise<TicketPage>(() => {}))
    const wrapper = await render()
    expect(wrapper.text()).toContain('正在读取工单')
    wrapper.unmount()
  })

  it('真实接口返回空列表时展示空状态', async () => {
    vi.spyOn(ticketApi, 'list').mockResolvedValue(emptyPage)
    const wrapper = await render()
    await flushPromises()
    expect(wrapper.text()).toContain('当前没有符合条件的工单')
    expect(wrapper.text()).toContain('创建工单')
    wrapper.unmount()
  })

  it('接口失败时展示后端错误和重试按钮', async () => {
    vi.spyOn(ticketApi, 'list').mockRejectedValue(new ApiError(503, 'COMMON_500', '系统暂时不可用'))
    const wrapper = await render()
    await flushPromises()
    expect(wrapper.text()).toContain('系统暂时不可用')
    expect(wrapper.text()).toContain('重新加载')
    wrapper.unmount()
  })

  it('客服没有创建权限时不展示创建操作', async () => {
    currentUser.value = user('SUPPORT', ['ticket:read:team', 'ticket:assign'])
    vi.spyOn(ticketApi, 'list').mockResolvedValue(emptyPage)
    const wrapper = await render()
    await flushPromises()
    expect(wrapper.text()).toContain('团队工单')
    expect(wrapper.find('.heading-action').exists()).toBe(false)
    wrapper.unmount()
  })

  it('客户从导航进入发起请求时打开真实创建表单', async () => {
    vi.spyOn(ticketApi, 'list').mockResolvedValue(emptyPage)
    const wrapper = await render('/tickets?create=1')
    await flushPromises()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.find('#ticket-description').exists()).toBe(true)
    wrapper.unmount()
  })
})
