<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, conversationApi } from '../lib/api'
import { formatDate } from '../lib/presentation'
import { currentUser, hasPermission, primaryRole } from '../lib/session'
import type { MessageIntent, Ticket, TicketMessage } from '../types'
import StatePanel from './StatePanel.vue'

const props = defineProps<{ ticket: Ticket }>()
const emit = defineEmits<{ ticketUpdated: [] }>()
const messages = ref<TicketMessage[]>([])
const loading = ref(true)
const error = ref('')
const content = ref('')
const intent = ref<MessageIntent>(hasPermission('ticket:message:reply') ? 'PUBLIC_REPLY' : 'INTERNAL_NOTE')
const sending = ref(false)
const sendError = ref('')
const sendSuccess = ref('')
let pendingRequest: { signature: string; id: string } | null = null

const isStaff = computed(() => primaryRole.value === 'SUPPORT' || primaryRole.value === 'ADMIN')
const canPublicReply = computed(() => hasPermission('ticket:message:reply'))
const canInternalNote = computed(() => isStaff.value && hasPermission('ticket:message:note'))
const isClosed = computed(() => props.ticket.status === 'CLOSED')
const visibleMessages = computed(() => isStaff.value ? messages.value
  : messages.value.filter(message => message.visibleToRequester && message.messageType !== 'INTERNAL_NOTE'))

async function loadMessages() {
  loading.value = true
  error.value = ''
  try {
    messages.value = await conversationApi.list(props.ticket.id)
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '会话加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function send() {
  sendError.value = ''; sendSuccess.value = ''
  const normalized = content.value.trim()
  if (!normalized) {
    sendError.value = intent.value === 'INTERNAL_NOTE' ? '请输入内部备注。' : '请输入回复内容。'
    return
  }
  const signature = JSON.stringify({ ticketId: props.ticket.id, intent: intent.value, content: normalized })
  if (!pendingRequest || pendingRequest.signature !== signature) {
    pendingRequest = { signature, id: crypto.randomUUID() }
  }
  sending.value = true
  try {
    await conversationApi.post(props.ticket.id, intent.value, normalized, pendingRequest.id)
    pendingRequest = null
    content.value = ''
    sendSuccess.value = intent.value === 'INTERNAL_NOTE' ? '内部备注已保存。' : '公开回复已发送。'
    await loadMessages()
    emit('ticketUpdated')
  } catch (cause) {
    sendError.value = cause instanceof ApiError ? cause.message : '发送失败；重试将使用同一请求标识。'
  } finally {
    sending.value = false
  }
}

function messageLabel(message: TicketMessage): string {
  if (message.messageType === 'INTERNAL_NOTE') return '内部备注'
  if (message.messageType === 'AI_SUGGESTION') return 'AI 建议 · 人工发送'
  if (message.messageType === 'CUSTOMER_REPLY') return '客户'
  return message.senderType === 'ADMIN' ? '管理员' : '客服'
}

function messageClass(message: TicketMessage): string[] {
  return [
    `message-${message.messageType.toLowerCase().replace('_', '-')}`,
    message.senderId === currentUser.value?.id ? 'message-own' : '',
  ]
}

onMounted(() => void loadMessages())
</script>

<template>
  <section class="detail-card conversation-panel">
    <div class="workspace-section-head">
      <div><span class="section-kicker">CONVERSATION / LIVE</span><h2>工单会话</h2><p>{{ isStaff ? '公开回复对客户可见；内部备注仅客服与管理员可见。' : '这里只显示与你相关的公开会话。' }}</p></div>
      <button class="text-button" type="button" :disabled="loading" @click="loadMessages">{{ loading ? '刷新中…' : '刷新会话' }}</button>
    </div>

    <StatePanel v-if="loading && messages.length === 0" kind="loading" title="正在读取会话" description="正在加载这张工单的消息…" />
    <StatePanel v-else-if="error && messages.length === 0" kind="error" title="会话暂时无法加载" :description="error"><button class="button button-outline" type="button" @click="loadMessages">重新加载</button></StatePanel>
    <div v-else class="message-stream" aria-live="polite">
      <div v-if="visibleMessages.length === 0" class="conversation-empty"><span>○</span><strong>还没有会话消息</strong><p>发送第一条回复，开始围绕这张工单沟通。</p></div>
      <article v-for="message in visibleMessages" :key="message.id" class="message-item" :class="messageClass(message)">
        <div class="message-rail"><span class="message-avatar">{{ message.messageType === 'AI_SUGGESTION' ? 'AI' : message.messageType === 'INTERNAL_NOTE' ? '记' : message.messageType === 'CUSTOMER_REPLY' ? '客' : '服' }}</span></div>
        <div class="message-body"><header><strong>{{ messageLabel(message) }}</strong><span v-if="message.senderId === currentUser?.id" class="message-self">我</span><time>{{ formatDate(message.createdAt) }}</time></header><p>{{ message.content }}</p><footer v-if="message.messageType === 'INTERNAL_NOTE'">仅内部可见</footer><footer v-else-if="message.sourceSuggestionId">来源建议 · {{ message.sourceSuggestionId }}</footer></div>
      </article>
    </div>

    <div class="composer" :class="{ 'composer-closed': isClosed }">
      <div v-if="isClosed" class="closed-notice"><strong>工单已关闭</strong><span>发送消息前，请先通过右侧状态流转将工单重新打开。</span></div>
      <template v-else-if="canPublicReply || canInternalNote">
        <div v-if="canInternalNote" class="composer-modes"><button v-if="canPublicReply" type="button" :class="{ active: intent === 'PUBLIC_REPLY' }" @click="intent = 'PUBLIC_REPLY'">公开回复</button><button type="button" :class="{ active: intent === 'INTERNAL_NOTE' }" @click="intent = 'INTERNAL_NOTE'">内部备注</button></div>
        <label class="sr-only" for="conversation-content">{{ intent === 'INTERNAL_NOTE' ? '内部备注' : '公开回复' }}</label>
        <textarea id="conversation-content" v-model="content" maxlength="4000" rows="5" :placeholder="intent === 'INTERNAL_NOTE' ? '记录仅团队可见的处理信息…' : '输入给客户的公开回复…'" />
        <div v-if="sendError" class="inline-alert" role="alert">{{ sendError }}<span v-if="sendError.includes('重试')"> 当前内容再次提交会复用 clientRequestId。</span></div>
        <div v-if="sendSuccess" class="inline-success" role="status">{{ sendSuccess }}</div>
        <div class="composer-footer"><span>{{ content.length }} / 4000 · {{ intent === 'INTERNAL_NOTE' ? '仅内部可见' : '客户可见' }}</span><button class="button button-primary" type="button" :disabled="sending || !content.trim()" @click="send">{{ sending ? '正在发送…' : intent === 'INTERNAL_NOTE' ? '保存备注' : '发送回复' }} →</button></div>
      </template>
      <div v-else class="subtle-empty">当前账号没有发送会话消息的权限。</div>
    </div>
  </section>
</template>
