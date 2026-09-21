import type { TicketPriority, TicketStatus } from '../types'

export const statusMeta: Record<TicketStatus, { label: string; tone: string }> = {
  NEW: { label: '新建', tone: 'slate' },
  PENDING: { label: '待处理', tone: 'amber' },
  PROCESSING: { label: '处理中', tone: 'blue' },
  WAITING_CUSTOMER: { label: '等待客户', tone: 'violet' },
  RESOLVED: { label: '已解决', tone: 'green' },
  CLOSED: { label: '已关闭', tone: 'muted' },
}

export const priorityMeta: Record<TicketPriority, { label: string; tone: string }> = {
  LOW: { label: '低', tone: 'muted' },
  MEDIUM: { label: '中', tone: 'slate' },
  HIGH: { label: '高', tone: 'amber' },
  URGENT: { label: '紧急', tone: 'red' },
}

export const statusTransitions: Record<TicketStatus, TicketStatus[]> = {
  NEW: ['PENDING'],
  PENDING: ['PROCESSING'],
  PROCESSING: ['WAITING_CUSTOMER', 'RESOLVED'],
  WAITING_CUSTOMER: ['PROCESSING'],
  RESOLVED: ['CLOSED', 'PROCESSING'],
  CLOSED: ['PROCESSING'],
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(date)
}

export function relativeDate(value: string | null | undefined): string {
  if (!value) return '—'
  const delta = Date.now() - new Date(value).getTime()
  if (!Number.isFinite(delta)) return '—'
  if (delta < 60_000 && delta >= 0) return '刚刚'
  if (delta >= 0 && delta < 3_600_000) return `${Math.floor(delta / 60_000)} 分钟前`
  if (delta >= 0 && delta < 86_400_000) return `${Math.floor(delta / 3_600_000)} 小时前`
  return formatDate(value)
}
