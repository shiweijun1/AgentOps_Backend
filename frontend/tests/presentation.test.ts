import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusPill from '../src/components/StatusPill.vue'
import { statusTransitions } from '../src/lib/presentation'

describe('工单呈现', () => {
  it('六种状态均有明确名称', () => {
    expect(mount(StatusPill, { props: { status: 'WAITING_CUSTOMER' } }).text()).toContain('等待客户')
    expect(Object.keys(statusTransitions)).toHaveLength(6)
  })

  it('前端提供合法流转选项，关闭工单的管理员限制由页面额外控制', () => {
    expect(statusTransitions.NEW).toEqual(['PENDING'])
    expect(statusTransitions.PROCESSING).toEqual(['WAITING_CUSTOMER', 'RESOLVED'])
    expect(statusTransitions.CLOSED).toEqual(['PROCESSING'])
  })
})
