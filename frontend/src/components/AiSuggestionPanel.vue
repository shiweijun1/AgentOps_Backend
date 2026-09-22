<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, conversationApi, suggestionApi } from '../lib/api'
import { hasPermission } from '../lib/session'
import { missingCitationMarkers, suggestionContent } from '../lib/suggestions'
import type { AiSuggestion, AiSuggestionStatus, TicketMessage } from '../types'
import StatePanel from './StatePanel.vue'

const props = defineProps<{ ticketId: string; closed: boolean }>()
const emit = defineEmits<{ ticketUpdated: [] }>()
const suggestions = ref<AiSuggestion[]>([])
const sentSuggestionIds = ref(new Set<string>())
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const actionSuccess = ref('')
const conflict = ref(false)
const busyId = ref<string | null>(null)
const editingId = ref<string | null>(null)
const editContent = ref('')
const expandedCitations = ref(new Set<string>())
const rejectId = ref<string | null>(null)
const rejectReason = ref('')
const sendRequests = new Map<string, string>()

const canReview = computed(() => hasPermission('suggestion:review'))
const canSend = computed(() => hasPermission('ticket:suggestion:send'))
const statusLabels: Record<AiSuggestionStatus, string> = {
  READY: '待审核', EDITED: '已编辑', ADOPTED: '已采纳', REJECTED: '已拒绝', SUPERSEDED: '已替代',
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [suggestionData, messageData] = await Promise.all([
      suggestionApi.list(props.ticketId), conversationApi.list(props.ticketId),
    ])
    suggestions.value = suggestionData
    sentSuggestionIds.value = new Set(messageData
      .filter((message: TicketMessage) => message.sourceSuggestionId)
      .map((message: TicketMessage) => message.sourceSuggestionId as string))
    conflict.value = false
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '回复建议加载失败。'
  } finally {
    loading.value = false
  }
}

function startEdit(suggestion: AiSuggestion) {
  editingId.value = suggestion.id
  editContent.value = suggestion.editedContent ?? suggestion.originalContent
  actionError.value = ''; actionSuccess.value = ''; conflict.value = false
}

async function saveEdit(suggestion: AiSuggestion) {
  const normalized = editContent.value.trim()
  const missing = missingCitationMarkers(normalized, suggestion.citations)
  if (!normalized) {
    actionError.value = '建议正文不能为空。'
    return
  }
  if (missing.length) {
    actionError.value = `编辑内容必须保留 ${missing.length} 个已验证的 citation 标记。`
    return
  }
  await perform(suggestion, () => suggestionApi.edit(suggestion.id, suggestion.version, normalized), '建议已保存，原始建议仍被保留。')
  if (!actionError.value) editingId.value = null
}

async function adopt(suggestion: AiSuggestion) {
  await perform(suggestion, () => suggestionApi.adopt(suggestion.id, suggestion.version),
    '建议已采纳，但尚未发送。请再次确认后执行“发送给客户”。')
}

async function reject(suggestion: AiSuggestion) {
  const reason = rejectReason.value.trim()
  if (!reason) { actionError.value = '请填写拒绝原因。'; return }
  await perform(suggestion, () => suggestionApi.reject(suggestion.id, suggestion.version, reason), '建议已拒绝。')
  if (!actionError.value) { rejectId.value = null; rejectReason.value = '' }
}

async function perform(suggestion: AiSuggestion, operation: () => Promise<AiSuggestion>, success: string) {
  busyId.value = suggestion.id
  actionError.value = ''; actionSuccess.value = ''; conflict.value = false
  try {
    const updated = await operation()
    suggestions.value = suggestions.value.map(item => item.id === updated.id ? updated : item)
    actionSuccess.value = success
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 409) {
      conflict.value = true
      actionError.value = `${cause.message} 建议版本可能已变化，请刷新最新建议后再操作。`
    } else {
      actionError.value = cause instanceof ApiError ? cause.message : '建议操作失败，请稍后重试。'
    }
  } finally {
    busyId.value = null
  }
}

async function send(suggestion: AiSuggestion) {
  if (props.closed || sentSuggestionIds.value.has(suggestion.id)) return
  let requestId = sendRequests.get(suggestion.id)
  if (!requestId) {
    requestId = crypto.randomUUID()
    sendRequests.set(suggestion.id, requestId)
  }
  busyId.value = suggestion.id
  actionError.value = ''; actionSuccess.value = ''; conflict.value = false
  try {
    await conversationApi.sendSuggestion(props.ticketId, suggestion.id, requestId)
    sendRequests.delete(suggestion.id)
    sentSuggestionIds.value = new Set([...sentSuggestionIds.value, suggestion.id])
    actionSuccess.value = '已采纳建议现已作为公开消息发送，可在工单会话中查看。'
    emit('ticketUpdated')
  } catch (cause) {
    actionError.value = cause instanceof ApiError
      ? `${cause.message} 重试会复用同一 clientRequestId。`
      : '发送失败；重试会复用同一 clientRequestId。'
  } finally {
    busyId.value = null
  }
}

function toggleCitation(chunkId: string) {
  const next = new Set(expandedCitations.value)
  next.has(chunkId) ? next.delete(chunkId) : next.add(chunkId)
  expandedCitations.value = next
}

function percent(value: number): string { return `${Math.round(Number(value) * 100)}%` }
function reviewable(suggestion: AiSuggestion): boolean { return suggestion.status === 'READY' || suggestion.status === 'EDITED' }

onMounted(() => void load())
</script>

<template>
  <section class="detail-card suggestion-panel">
    <div class="workspace-section-head">
      <div><span class="section-kicker">REPLY SUGGESTIONS / REVIEW</span><h2>AI 回复建议</h2><p>核对知识引用后编辑、采纳；采纳不会自动发送，发送是独立操作。</p></div>
      <button class="text-button" type="button" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新建议' }}</button>
    </div>
    <div v-if="actionError" class="inline-alert" role="alert">{{ actionError }} <button v-if="conflict" class="inline-action" type="button" @click="load">刷新最新建议</button></div>
    <div v-if="actionSuccess" class="inline-success" role="status">{{ actionSuccess }}</div>

    <StatePanel v-if="loading && suggestions.length === 0" kind="loading" title="正在读取回复建议" description="正在获取建议正文和知识引用…" />
    <StatePanel v-else-if="error && suggestions.length === 0" kind="error" title="回复建议暂时无法加载" :description="error"><button class="button button-outline" type="button" @click="load">重新加载</button></StatePanel>
    <StatePanel v-else-if="suggestions.length === 0" title="还没有可审核的回复建议" description="Agent 可能仍在分析，或因风险与知识命中不足而转为人工处理。" />
    <div v-else class="suggestion-list">
      <article v-for="(suggestion, index) in suggestions" :key="suggestion.id" class="suggestion-card" :class="`suggestion-${suggestion.status.toLowerCase()}`">
        <header class="suggestion-head"><div><span class="suggestion-sequence">SUGGESTION / {{ String(index + 1).padStart(2, '0') }}</span><h3>回复建议 <small>v{{ suggestion.version }}</small></h3></div><div class="suggestion-badges"><span class="confidence-badge">置信度 {{ percent(suggestion.confidence) }}</span><span class="suggestion-status">{{ statusLabels[suggestion.status] }}</span><span v-if="sentSuggestionIds.has(suggestion.id)" class="sent-badge">已发送</span></div></header>

        <div v-if="editingId === suggestion.id" class="suggestion-editor"><label :for="`edit-${suggestion.id}`">编辑建议正文</label><textarea :id="`edit-${suggestion.id}`" v-model="editContent" maxlength="2000" rows="8" /><p>必须保留正文中所有已验证的 <code>[citation:chunkId]</code> 标记。</p><div><button class="button button-outline" type="button" @click="editingId = null">取消</button><button class="button button-primary" type="button" :disabled="busyId === suggestion.id" @click="saveEdit(suggestion)">保存编辑</button></div></div>
        <div v-else class="suggestion-content">{{ suggestionContent(suggestion) }}</div>

        <section class="citation-section"><div class="citation-heading"><strong>知识引用</strong><span>{{ suggestion.citations.filter(item => item.usedInAnswer).length }} 个用于回答</span></div><div v-if="suggestion.citations.length === 0" class="subtle-empty">该建议没有引用记录。</div><div v-else class="citation-list"><article v-for="(citation, citationIndex) in suggestion.citations" :key="citation.chunkId" :class="{ 'citation-used': citation.usedInAnswer }"><button type="button" @click="toggleCitation(citation.chunkId)"><span class="citation-rank">{{ citationIndex + 1 }}</span><span class="citation-name"><strong>{{ citation.usedInAnswer ? '已用于回答' : '检索候选' }}</strong><small>{{ citation.chunkId }}</small></span><span class="citation-score">{{ Number(citation.retrievalScore).toFixed(3) }}</span><span>{{ expandedCitations.has(citation.chunkId) ? '−' : '+' }}</span></button><p v-if="expandedCitations.has(citation.chunkId)">{{ citation.contentSnapshot }}</p></article></div></section>

        <div v-if="rejectId === suggestion.id" class="reject-box"><label :for="`reject-${suggestion.id}`">拒绝原因</label><textarea :id="`reject-${suggestion.id}`" v-model="rejectReason" maxlength="500" rows="3" placeholder="说明为什么不采用这条建议" /><div><button class="button button-outline" type="button" @click="rejectId = null">取消</button><button class="button button-danger" type="button" :disabled="busyId === suggestion.id" @click="reject(suggestion)">确认拒绝</button></div></div>

        <footer class="suggestion-actions">
          <template v-if="canReview && reviewable(suggestion)"><button class="button button-outline" type="button" @click="startEdit(suggestion)">编辑</button><button class="button button-outline danger-outline" type="button" @click="rejectId = suggestion.id; rejectReason = ''">拒绝</button><button class="button button-primary" type="button" :disabled="busyId === suggestion.id" @click="adopt(suggestion)">采纳建议</button></template>
          <template v-else-if="suggestion.status === 'ADOPTED'"><span class="adopted-note">✓ 已完成采纳审批</span><button v-if="canSend" class="button button-primary send-suggestion-button" type="button" :disabled="busyId === suggestion.id || sentSuggestionIds.has(suggestion.id) || closed" @click="send(suggestion)">{{ sentSuggestionIds.has(suggestion.id) ? '已发送到会话' : closed ? '工单关闭，无法发送' : '发送给客户 →' }}</button></template>
          <span v-else class="terminal-suggestion-note">{{ reviewable(suggestion) ? '当前账号仅可查看，不能审核建议。' : '该建议已结束审核，不能再次修改。' }}</span>
        </footer>
      </article>
    </div>
  </section>
</template>
