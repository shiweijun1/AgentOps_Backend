<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatePanel from '../components/StatePanel.vue'
import StatusPill from '../components/StatusPill.vue'
import { ApiError, ticketApi } from '../lib/api'
import { formatDate, priorityMeta, relativeDate, statusMeta } from '../lib/presentation'
import { hasPermission, primaryRole } from '../lib/session'
import type { TicketPage, TicketPriority, TicketStatus } from '../types'

const router = useRouter()
const route = useRoute()
const page = ref(0)
const initialStatus = route.query.status
const status = ref<TicketStatus | ''>(typeof initialStatus === 'string' && initialStatus in statusMeta
  ? initialStatus as TicketStatus : '')
const priority = ref<TicketPriority | ''>('')
const keywordDraft = ref('')
const keyword = ref('')
const result = ref<TicketPage | null>(null)
const loading = ref(false)
const error = ref('')
let requestNumber = 0

const canCreate = computed(() => hasPermission('ticket:create'))
const scopeDescription = computed(() => ({
  ADMIN: '查看租户内全部工单，掌握每个请求的处理进度。',
  SUPPORT: '仅显示所属团队或分配给你的工单。',
  CUSTOMER: '这里汇集了你发起的全部服务请求。',
}[primaryRole.value ?? 'CUSTOMER']))
const pageStart = computed(() => result.value && result.value.totalElements > 0 ? page.value * result.value.size + 1 : 0)
const pageEnd = computed(() => result.value ? Math.min((page.value + 1) * result.value.size, result.value.totalElements) : 0)

async function loadTickets() {
  const current = ++requestNumber
  loading.value = true
  error.value = ''
  try {
    const response = await ticketApi.list({ status: status.value, priority: priority.value,
      keyword: keyword.value, page: page.value, size: 20 })
    if (current === requestNumber) result.value = response
  } catch (cause) {
    if (current === requestNumber) {
      result.value = null
      error.value = cause instanceof ApiError ? cause.message : '工单加载失败，请稍后重试。'
    }
  } finally {
    if (current === requestNumber) loading.value = false
  }
}

watch([page, status, priority, keyword], () => void loadTickets(), { immediate: true })
watch(() => route.query.status, (value) => {
  status.value = typeof value === 'string' && value in statusMeta ? value as TicketStatus : ''
  page.value = 0
})
function applySearch() {
  const next = keywordDraft.value.trim()
  if (keyword.value !== next) keyword.value = next
  else if (page.value === 0) void loadTickets()
  page.value = 0
}
function clearFilters() {
  status.value = ''; priority.value = ''; keywordDraft.value = ''; keyword.value = ''; page.value = 0
}

const showCreate = ref(false)
watch(() => route.query.create, (value) => { if (value === '1' && canCreate.value) showCreate.value = true }, { immediate: true })
const creating = ref(false)
const createError = ref('')
const newTitle = ref('')
const newDescription = ref('')
const newPriority = ref<TicketPriority>('MEDIUM')
let lastSubmission: { payload: string; key: string } | null = null

function closeCreate() {
  showCreate.value = false
  if (route.query.create === '1') void router.replace({ name: 'tickets' })
}

async function createTicket() {
  createError.value = ''
  const payload = { title: newTitle.value.trim(), description: newDescription.value.trim(), priority: newPriority.value }
  if (!payload.title || !payload.description) {
    createError.value = '请填写标题和问题描述。'
    return
  }
  const serialized = JSON.stringify(payload)
  if (!lastSubmission || lastSubmission.payload !== serialized) {
    lastSubmission = { payload: serialized, key: crypto.randomUUID() }
  }
  creating.value = true
  try {
    const ticket = await ticketApi.create(payload, lastSubmission.key)
    lastSubmission = null
    showCreate.value = false
    await router.push({ name: 'ticket-detail', params: { id: ticket.id } })
  } catch (cause) {
    createError.value = cause instanceof ApiError ? cause.message : '创建工单失败，请稍后重试。'
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div class="page-enter">
    <div class="page-heading list-heading">
      <div><div class="eyebrow">TICKET WORKSPACE <span class="eyebrow-line" /> 工单队列</div><h1>{{ primaryRole === 'CUSTOMER' ? '我的工单' : primaryRole === 'SUPPORT' ? '团队工单' : '全部工单' }}</h1><p>{{ scopeDescription }}</p></div>
      <button v-if="canCreate" class="button button-primary heading-action" type="button" @click="showCreate = true"><span class="button-plus">＋</span> 创建工单</button>
    </div>

    <section class="list-panel" aria-label="工单列表">
      <div class="list-toolbar">
        <div class="toolbar-title"><span class="section-kicker">QUEUE / 01</span><h2>工单视图</h2><span v-if="result" class="count-chip">{{ result.totalElements }} 条</span></div>
        <div class="toolbar-hint">按创建时间从新到旧排列</div>
      </div>
      <form class="filter-row" @submit.prevent="applySearch">
        <div class="search-field"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6"/><path d="m16 16 5 5"/></svg><input v-model="keywordDraft" aria-label="搜索标题或描述" maxlength="100" placeholder="搜索标题或描述关键词" /><button type="submit" aria-label="搜索">↵</button></div>
        <select v-model="status" aria-label="筛选工单状态" @change="page = 0"><option value="">全部状态</option><option v-for="(meta, code) in statusMeta" :key="code" :value="code">{{ meta.label }}</option></select>
        <select v-model="priority" aria-label="筛选优先级" @change="page = 0"><option value="">全部优先级</option><option v-for="(meta, code) in priorityMeta" :key="code" :value="code">{{ meta.label }}优先级</option></select>
        <button v-if="status || priority || keyword" type="button" class="text-button filter-clear" @click="clearFilters">清除筛选</button>
      </form>

      <StatePanel v-if="loading && !result" kind="loading" title="正在读取工单" description="正在从服务端获取当前可查看的工单…" />
      <StatePanel v-else-if="error" kind="error" title="工单暂时无法加载" :description="error"><button class="button button-outline" type="button" @click="loadTickets">重新加载</button></StatePanel>
      <StatePanel v-else-if="result && result.items.length === 0" title="当前没有符合条件的工单" description="试试调整筛选条件，或创建一张新的工单。"><button v-if="status || priority || keyword" class="button button-outline" type="button" @click="clearFilters">查看全部工单</button></StatePanel>
      <template v-else-if="result">
        <div class="table-scroll" :class="{ 'table-refreshing': loading }">
          <table class="ticket-table"><thead><tr><th>工单 / 问题</th><th>状态</th><th>优先级</th><th>创建时间</th><th><span class="sr-only">查看</span></th></tr></thead>
            <tbody><tr v-for="ticket in result.items" :key="ticket.id">
              <td class="ticket-main-cell"><RouterLink :to="{ name: 'ticket-detail', params: { id: ticket.id } }" class="ticket-title-link"><span class="ticket-no">{{ ticket.ticketNo }}</span><strong>{{ ticket.title }}</strong><small>{{ ticket.description }}</small></RouterLink></td>
              <td><StatusPill :status="ticket.status" /></td>
              <td><span class="priority-label" :class="`priority-${priorityMeta[ticket.priority].tone}`"><span class="priority-dash" />{{ priorityMeta[ticket.priority].label }}</span></td>
              <td class="date-cell"><span>{{ relativeDate(ticket.createdAt) }}</span><small>{{ formatDate(ticket.createdAt) }}</small></td>
              <td><RouterLink :to="{ name: 'ticket-detail', params: { id: ticket.id } }" class="row-arrow" :aria-label="`查看工单 ${ticket.ticketNo}`">↗</RouterLink></td>
            </tr></tbody>
          </table>
        </div>
        <div class="table-footer"><span>显示 {{ pageStart }}–{{ pageEnd }} 条，共 {{ result.totalElements }} 条</span><div class="pagination"><button type="button" :disabled="page === 0 || loading" @click="page--">← 上一页</button><span>{{ page + 1 }} / {{ Math.max(result.totalPages, 1) }}</span><button type="button" :disabled="page + 1 >= result.totalPages || loading" @click="page++">下一页 →</button></div></div>
      </template>
    </section>

    <div v-if="showCreate" class="modal-backdrop" @click.self="closeCreate">
      <section class="dialog-card" role="dialog" aria-modal="true" aria-labelledby="create-title">
        <div class="dialog-header"><div><span class="eyebrow">NEW REQUEST</span><h2 id="create-title">创建工单</h2></div><button class="icon-button dialog-close" aria-label="关闭" type="button" @click="closeCreate">×</button></div>
        <form @submit.prevent="createTicket"><label for="ticket-title">问题标题</label><input id="ticket-title" v-model="newTitle" maxlength="200" placeholder="用一句话概括遇到的问题" required />
          <label for="ticket-priority">优先级</label><select id="ticket-priority" v-model="newPriority"><option v-for="(meta, code) in priorityMeta" :key="code" :value="code">{{ meta.label }}</option></select>
          <label for="ticket-description">详细描述</label><textarea id="ticket-description" v-model="newDescription" maxlength="10000" rows="6" placeholder="请说明影响范围、出现时间和已尝试的操作" required />
          <p class="form-help">提交后可在工单详情跟踪处理进度。</p><p v-if="createError" class="form-error" role="alert">{{ createError }}</p>
          <div class="dialog-actions"><button class="button button-outline" type="button" @click="closeCreate">取消</button><button class="button button-primary" type="submit" :disabled="creating">{{ creating ? '正在提交…' : '提交工单' }}</button></div>
        </form>
      </section>
    </div>
  </div>
</template>
