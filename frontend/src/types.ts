export type Role = 'CUSTOMER' | 'SUPPORT' | 'ADMIN'
export type TicketStatus = 'NEW' | 'PENDING' | 'PROCESSING' | 'WAITING_CUSTOMER' | 'RESOLVED' | 'CLOSED'
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface CurrentUser {
  id: string
  tenantId: string
  username: string
  displayName: string
  email: string | null
  teamId: string | null
  roles: Role[]
  permissions: string[]
}

export interface LoginResponse {
  tokenType: string
  accessToken: string
  expiresIn: number
  expiresAt: string
}

export interface Ticket {
  id: string
  ticketNo: string
  requesterId: string
  title: string
  description: string
  status: TicketStatus
  priority: TicketPriority
  categoryId: string | null
  teamId: string | null
  assigneeId: string | null
  reopenCount: number
  firstResponseAt: string | null
  resolvedAt: string | null
  closedAt: string | null
  submittedAt: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface TicketPage {
  items: Ticket[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TicketTransition {
  id: string
  fromStatus: TicketStatus | null
  toStatus: TicketStatus
  actorType: string
  actorId: string
  reason: string | null
  occurredAt: string
}

export interface TicketAssignment {
  id: string
  assignmentType: string
  fromTeamId: string | null
  fromAssigneeId: string | null
  toTeamId: string | null
  toAssigneeId: string | null
  operatorId: string
  reason: string
  occurredAt: string
}
