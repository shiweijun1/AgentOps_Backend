<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StatePanel from '../components/StatePanel.vue'
import StatusPill from '../components/StatusPill.vue'
import TicketConversationPanel from '../components/TicketConversationPanel.vue'
import AgentAnalysisPanel from '../components/AgentAnalysisPanel.vue'
import AiSuggestionPanel from '../components/AiSuggestionPanel.vue'
import { ApiError, ticketApi } from '../lib/api'
import { formatDate, priorityMeta, statusMeta, statusTransitions } from '../lib/presentation'
import { currentUser, hasPermission, primaryRole } from '../lib/session'
import type { Ticket, TicketAssignment, TicketStatus, TicketTransition } from '../types'

const route = useRoute()
const ticketId = computed(() => String(route.params.id ?? ''))
const ticket = ref<Ticket | null>(null)
const transitions = ref<TicketTransition[]>([])
const assignments = ref<TicketAssignment[]>([])
const loading = ref(true)
const error = ref('')
const historyError = ref('')
const activeSection = ref<'summary' | 'conversation' | 'agent' | 'suggestions'>('summary')
let requestNumber = 0

const availableTransitions = computed(() => {
  if (!ticket.value || !hasPermission('ticket:transition')) return []
  const next = statusTransitions[ticket.value.status]
  return ticket.value.status === 'CLOSED' && primaryRole.value !== 'ADMIN'
    ? [] : next
})
const canAssign = computed(() => hasPermission('ticket:assign') && primaryRole.value !== 'CUSTOMER')
const canReadConversation = computed(() => hasPermission('ticket:message:read'))
const canReadAgent = computed(() => primaryRole.value !== 'CUSTOMER' && hasPermission('agent:read'))
const canReadSuggestions = computed(() => primaryRole.value !== 'CUSTOMER' && hasPermission('suggestion:read'))
const selectedStatus = ref<TicketStatus | ''>('')
const transitionReason = ref('')
const actionError = ref('')
const actionSuccess = ref('')
const submitting = ref(false)
const showAssign = ref(false)
const assignTeamId = ref('')
const assignAssigneeId = ref('')
const assignReason = ref('')
let lastTransition: { payload: string; key: string } | null = null

async function loadTicket() {
  const current = ++requestNumber
  loading.value = true
  error.value = ''
  try {
    const value = await ticketApi.get(ticketId.value)
    if (current !== requestNumber) return
    ticket.value = value
    selectedStatus.value = ''
    const [transitionResult, assignmentResult] = await Promise.allSettled([
      ticketApi.transitions(ticketId.value), ticketApi.assignments(ticketId.value),
    ])
    if (current !== requestNumber) return
    transitions.value = transitionResult.status === 'fulfilled' ? transitionResult.value : []
    assignments.value = assignmentResult.status === 'fulfilled' ? assignmentResult.value : []
    historyError.value = transitionResult.status === 'rejected' || assignmentResult.status === 'rejected'
      ? '部分处理记录暂时无法读取，请刷新重试。' : ''
  } catch (cause) {
    if (current === requestNumber) {
      ticket.value = null
      error.value = cause instanceof ApiError ? cause.message : '工单详情加载失败，请稍后重试。'
    }
  } finally {
    if (current === requestNumber) loading.value = false
  }
}

watch(ticketId, () => { activeSection.value = 'summary'; void loadTicket() }, { immediate: true })

async function transition() {
  if (!ticket.value || !selectedStatus.value) return
  actionError.value = ''; actionSuccess.value = ''; submitting.value = true
  const reason = transitionReason.value.trim()
  const payload = JSON.stringify({ id: ticket.value.id, toStatus: selectedStatus.value,
    expectedVersion: ticket.value.version, reason })
  if (!lastTransition || lastTransition.payload !== payload) {
    lastTransition = { payload, key: crypto.randomUUID() }
  }
  try {
    await ticketApi.transition(ticket.value.id, selectedStatus.value, ticket.value.version, reason, lastTransition.key)
    lastTransition = null
    transitionReason.value = ''
    await loadTicket()
    actionSuccess.value = '状态已更新，处理记录已同步。'
  } catch (cause) {
    actionError.value = cause instanceof ApiError ? cause.message : '状态更新失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}

function openAssign() {
  if (!ticket.value) return
  assignTeamId.value = ticket.value.teamId ?? currentUser.value?.teamId ?? ''
  assignAssigneeId.value = ticket.value.assigneeId ?? ''
  assignReason.value = ''
  actionError.value = ''
  showAssign.value = true
}

async function assign() {
  if (!ticket.value) return
  actionError.value = ''; actionSuccess.value = ''
  if (!assignTeamId.value.trim() && !assignAssigneeId.value.trim()) {
    actionError.value = '请填写团队 ID 或负责人 ID。'
    return
  }
  if (!assignReason.value.trim()) {
    actionError.value = '请填写分派原因。'
    return
  }
  submitting.value = true
  try {
    await ticketApi.assign(ticket.value.id, ticket.value.version,
      assignTeamId.value.trim() || null, assignAssigneeId.value.trim() || null, assignReason.value.trim())
    showAssign.value = false
    await loadTicket()
    actionSuccess.value = '分派已更新，处理记录已同步。'
  } catch (cause) {
    actionError.value = cause instanceof ApiError ? cause.message : '分派失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-enter detail-page">
    <RouterLink to="/tickets" class="back-link">← 返回工单列表</RouterLink>
    <StatePanel v-if="loading && !ticket" kind="loading" title="正在读取工单" description="正在从服务端获取工单内容与处理记录…" />
    <StatePanel v-else-if="error || !ticket" kind="error" title="无法打开这张工单" :description="error || '工单不存在或你没有访问权限。'"><button class="button button-outline" type="button" @click="loadTicket">重新加载</button></StatePanel>
    <template v-else>
      <div class="detail-head">
        <div class="detail-title-block"><div class="eyebrow">TICKET / {{ ticket.ticketNo }}</div><h1>{{ ticket.title }}</h1><div class="detail-head-meta"><StatusPill :status="ticket.status" /><span class="meta-divider" /><span>创建于 {{ formatDate(ticket.createdAt) }}</span><span class="meta-divider" /><span>版本 {{ ticket.version }}</span></div></div>
        <div class="detail-head-actions"><button class="button button-outline" type="button" :disabled="loading" @click="loadTicket">{{ loading ? '刷新中…' : '刷新详情' }}</button><button v-if="canAssign" class="button button-primary" type="button" @click="openAssign">分派工单 ↗</button></div>
      </div>

      <div v-if="actionSuccess" class="inline-success" role="status">{{ actionSuccess }}</div>
      <div v-if="actionError && !showAssign" class="inline-alert" role="alert">{{ actionError }}</div>

      <div class="detail-grid">
        <div class="detail-primary">
          <nav class="section-tabs" aria-label="工单详情区域"><button type="button" :class="{ active: activeSection === 'summary' }" @click="activeSection = 'summary'">概览与记录</button><button v-if="canReadConversation" type="button" :class="{ active: activeSection === 'conversation' }" @click="activeSection = 'conversation'">会话</button><button v-if="canReadAgent" type="button" :class="{ active: activeSection === 'agent' }" @click="activeSection = 'agent'">Agent 分析</button><button v-if="canReadSuggestions" type="button" :class="{ active: activeSection === 'suggestions' }" @click="activeSection = 'suggestions'">回复建议</button></nav>
          <template v-if="activeSection === 'summary'">
            <section class="detail-card description-card"><div class="card-heading"><span class="section-kicker">REQUEST / 01</span><h2>问题描述</h2></div><p class="description-text">{{ ticket.description }}</p></section>
            <section class="detail-card history-card"><div class="card-heading"><span class="section-kicker">ACTIVITY / 02</span><h2>状态流转</h2></div><div v-if="historyError" class="inline-alert" role="alert">{{ historyError }}</div><div v-if="transitions.length === 0" class="subtle-empty">暂无流转记录。</div><ol v-else class="timeline"><li v-for="item in [...transitions].reverse()" :key="item.id"><span class="timeline-node" /><div><div class="timeline-title"><strong>{{ item.fromStatus ? statusMeta[item.fromStatus].label : '创建工单' }} → {{ statusMeta[item.toStatus].label }}</strong><time>{{ formatDate(item.occurredAt) }}</time></div><p v-if="item.reason">{{ item.reason }}</p><small>操作类型 · {{ item.actorType }}</small></div></li></ol></section>
            <section class="detail-card assignment-card"><div class="card-heading"><span class="section-kicker">OWNERSHIP / 03</span><h2>分派记录</h2></div><div v-if="assignments.length === 0" class="subtle-empty">尚未产生分派记录。</div><ol v-else class="compact-list"><li v-for="item in [...assignments].reverse()" :key="item.id"><span class="compact-mark" /><div><strong>{{ item.assignmentType === 'INITIAL' ? '首次分派' : '重新分派' }}</strong><p>{{ item.reason }}</p><small>{{ formatDate(item.occurredAt) }}</small></div></li></ol></section>
          </template>
          <TicketConversationPanel v-else-if="activeSection === 'conversation' && canReadConversation" :ticket="ticket" @ticket-updated="loadTicket" />
          <AgentAnalysisPanel v-else-if="activeSection === 'agent' && canReadAgent" :ticket-id="ticket.id" />
          <AiSuggestionPanel v-else-if="activeSection === 'suggestions' && canReadSuggestions" :ticket-id="ticket.id" :closed="ticket.status === 'CLOSED'" @ticket-updated="loadTicket" />
        </div>

        <aside class="detail-aside">
          <section class="aside-card facts-card"><span class="section-kicker">TICKET PROFILE</span><h2>工单信息</h2><dl><div><dt>工单编号</dt><dd class="mono">{{ ticket.ticketNo }}</dd></div><div><dt>优先级</dt><dd><span class="priority-label" :class="`priority-${priorityMeta[ticket.priority].tone}`"><span class="priority-dash" />{{ priorityMeta[ticket.priority].label }}</span></dd></div><div><dt>当前状态</dt><dd>{{ statusMeta[ticket.status].label }}</dd></div><div><dt>首次响应</dt><dd>{{ formatDate(ticket.firstResponseAt) }}</dd></div><div><dt>最后更新</dt><dd>{{ formatDate(ticket.updatedAt) }}</dd></div><div><dt>重新打开</dt><dd>{{ ticket.reopenCount }} 次</dd></div></dl></section>
          <section v-if="availableTransitions.length" class="aside-card action-card"><span class="section-kicker">NEXT ACTION</span><h2>推进处理</h2><p>状态变更会记录到工单流转历史。</p><form @submit.prevent="transition"><label for="next-status">流转至</label><select id="next-status" v-model="selectedStatus" required><option value="" disabled>选择目标状态</option><option v-for="target in availableTransitions" :key="target" :value="target">{{ statusMeta[target].label }}</option></select><label for="transition-reason">处理说明 <span>选填</span></label><textarea id="transition-reason" v-model="transitionReason" rows="3" maxlength="500" placeholder="记录本次处理依据" /><button class="button button-primary action-submit" type="submit" :disabled="submitting || !selectedStatus">{{ submitting ? '正在更新…' : '确认流转' }}</button></form></section>
          <section class="aside-note"><span class="note-signal" /> 对工单的每一次操作，都保留可追踪的记录。</section>
        </aside>
      </div>
    </template>

    <div v-if="showAssign && ticket" class="modal-backdrop" @click.self="showAssign = false">
      <section class="dialog-card" role="dialog" aria-modal="true" aria-labelledby="assign-title"><div class="dialog-header"><div><span class="eyebrow">OWNERSHIP</span><h2 id="assign-title">分派工单</h2></div><button class="icon-button dialog-close" type="button" aria-label="关闭" @click="showAssign = false">×</button></div><form @submit.prevent="assign"><p class="dialog-intro">填写目标团队或负责人 ID。系统会校验其是否属于当前租户；若同时填写，两者必须匹配。</p><label for="team-id">团队 ID <span>可选</span></label><input id="team-id" v-model="assignTeamId" placeholder="UUID" /><label for="assignee-id">负责人 ID <span>可选</span></label><input id="assignee-id" v-model="assignAssigneeId" placeholder="UUID" /><label for="assign-reason">分派原因</label><textarea id="assign-reason" v-model="assignReason" rows="3" maxlength="500" placeholder="说明此次分派原因" required /><p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p><div class="dialog-actions"><button class="button button-outline" type="button" @click="showAssign = false">取消</button><button class="button button-primary" type="submit" :disabled="submitting">{{ submitting ? '正在分派…' : '确认分派' }}</button></div></form></section>
    </div>
  </div>
</template>
