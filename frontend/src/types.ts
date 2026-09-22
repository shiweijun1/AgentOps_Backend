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

export type TicketMessageType = 'CUSTOMER_REPLY' | 'SUPPORT_REPLY' | 'INTERNAL_NOTE' | 'AI_SUGGESTION'
export type TicketActorType = 'USER' | 'SUPPORT' | 'ADMIN'
export type MessageIntent = 'PUBLIC_REPLY' | 'INTERNAL_NOTE'

export interface TicketMessage {
  id: string
  ticketId: string
  senderType: TicketActorType
  senderId: string
  messageType: TicketMessageType
  content: string
  sourceSuggestionId: string | null
  clientRequestId: string
  visibleToRequester: boolean
  createdAt: string
}

export type AgentRunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type AgentRunType = 'TICKET_ANALYSIS' | 'REPLY_SUGGESTION'
export type AgentStepStatus = 'SUCCEEDED' | 'FAILED'
export type AgentStepType = 'CONTENT_PREPROCESSING' | 'TICKET_CLASSIFICATION' | 'RISK_EVALUATION'
  | 'RESULT_PERSISTENCE' | 'KNOWLEDGE_RETRIEVAL' | 'REPLY_GENERATION'
  | 'CITATION_VALIDATION' | 'SUGGESTION_PERSISTENCE'

export interface AgentRun {
  id: string
  ticketId: string
  runType: AgentRunType
  inputRevision: number
  attemptNo: number
  executionCount: number
  triggerType: 'TICKET_CREATED' | 'MANUAL' | 'ANALYSIS_SUCCEEDED'
  status: AgentRunStatus
  modelProvider: string | null
  modelName: string | null
  inputTokens: number
  outputTokens: number
  errorCode: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

export interface AgentStep {
  id: string
  stepType: AgentStepType
  sequenceNo: number
  status: AgentStepStatus
  inputSummary: string | null
  outputSummary: string | null
  modelName: string | null
  inputTokens: number
  outputTokens: number
  durationMs: number | null
  retryCount: number
  errorCode: string | null
  startedAt: string | null
  finishedAt: string | null
}

export type CategoryCode = 'ACCOUNT' | 'PAYMENT' | 'NETWORK' | 'SOFTWARE' | 'OTHER'
export type Sentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface TicketAnalysis {
  runId: string
  ticketId: string
  categoryCode: CategoryCode
  priority: TicketPriority
  sentiment: Sentiment
  riskLevel: RiskLevel
  confidence: number
  manualRequired: boolean
  manualReason: string | null
  reason: string
  createdAt: string
}

export type AiSuggestionStatus = 'READY' | 'EDITED' | 'ADOPTED' | 'REJECTED' | 'SUPERSEDED'

export interface KnowledgeCitation {
  articleId: string
  versionId: string
  chunkId: string
  contentSnapshot: string
  retrievalScore: number
  usedInAnswer: boolean
}

export interface AiSuggestion {
  id: string
  ticketId: string
  runId: string
  status: AiSuggestionStatus
  originalContent: string
  editedContent: string | null
  finalContentSnapshot: string | null
  confidence: number
  version: number
  citations: KnowledgeCitation[]
}
