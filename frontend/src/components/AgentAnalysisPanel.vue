<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { agentApi, ApiError } from '../lib/api'
import { formatDate, priorityMeta } from '../lib/presentation'
import { hasPermission } from '../lib/session'
import type { AgentRun, AgentRunStatus, AgentStep, AgentStepType, TicketAnalysis } from '../types'
import StatePanel from './StatePanel.vue'

const props = defineProps<{ ticketId: string }>()
const runs = ref<AgentRun[]>([])
const analysis = ref<TicketAnalysis | null>(null)
const selectedRunId = ref<string | null>(null)
const steps = ref<AgentStep[]>([])
const loading = ref(true)
const stepsLoading = ref(false)
const error = ref('')
const rerunning = ref(false)
const actionError = ref('')
let timer: ReturnType<typeof setTimeout> | null = null
let disposed = false
let loadingRuns = false

const canRerun = computed(() => hasPermission('agent:rerun'))
const selectedRun = computed(() => runs.value.find(run => run.id === selectedRunId.value) ?? null)
const hasActiveRun = computed(() => runs.value.some(run => run.status === 'PENDING' || run.status === 'RUNNING'))
const failedRuns = computed(() => runs.value.filter(run => run.status === 'FAILED'))

const runLabels: Record<AgentRunStatus, string> = {
  PENDING: '等待执行', RUNNING: '执行中', SUCCEEDED: '已完成', FAILED: '执行失败',
}
const runTypeLabels = { TICKET_ANALYSIS: '工单分析', REPLY_SUGGESTION: '回复建议' } as const
const stepLabels: Record<AgentStepType, string> = {
  CONTENT_PREPROCESSING: '内容预处理', TICKET_CLASSIFICATION: '工单分类', RISK_EVALUATION: '风险评估',
  RESULT_PERSISTENCE: '结果保存', KNOWLEDGE_RETRIEVAL: '知识检索', REPLY_GENERATION: '回复生成',
  CITATION_VALIDATION: '引用校验', SUGGESTION_PERSISTENCE: '建议保存',
}
const categoryLabels = { ACCOUNT: '账号', PAYMENT: '支付', NETWORK: '网络', SOFTWARE: '软件', OTHER: '其他' } as const
const sentimentLabels = { POSITIVE: '正向', NEUTRAL: '中性', NEGATIVE: '负向' } as const
const riskLabels = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' } as const

function clearPoll() {
  if (timer) clearTimeout(timer)
  timer = null
}

function schedulePoll() {
  clearPoll()
  if (!disposed && hasActiveRun.value) {
    timer = setTimeout(() => void loadRuns(true), 3000)
  }
}

async function loadRuns(polling = false) {
  if (loadingRuns || disposed) return
  loadingRuns = true
  if (!polling) loading.value = true
  if (!polling) error.value = ''
  try {
    const [runData, analysisData] = await Promise.all([
      agentApi.runs(props.ticketId), agentApi.analysis(props.ticketId),
    ])
    if (disposed) return
    runs.value = runData
    analysis.value = analysisData
    if (!selectedRunId.value || !runData.some(run => run.id === selectedRunId.value)) {
      selectedRunId.value = runData[0]?.id ?? null
    }
    if (selectedRunId.value) await loadSteps(selectedRunId.value, polling)
  } catch (cause) {
    if (!disposed && !polling) error.value = cause instanceof ApiError ? cause.message : 'Agent 轨迹加载失败。'
  } finally {
    loadingRuns = false
    if (!disposed) {
      loading.value = false
      schedulePoll()
    }
  }
}

async function loadSteps(runId: string, quiet = false) {
  selectedRunId.value = runId
  if (!quiet) stepsLoading.value = true
  try {
    steps.value = await agentApi.steps(runId)
  } catch (cause) {
    if (!quiet) actionError.value = cause instanceof ApiError ? cause.message : '执行步骤加载失败。'
  } finally {
    stepsLoading.value = false
  }
}

async function rerun() {
  rerunning.value = true
  actionError.value = ''
  try {
    const run = await agentApi.rerun(props.ticketId)
    selectedRunId.value = run.id
    await loadRuns()
  } catch (cause) {
    actionError.value = cause instanceof ApiError ? cause.message : '重新分析未能启动。'
  } finally {
    rerunning.value = false
  }
}

function tokenTotal(run: AgentRun): number { return run.inputTokens + run.outputTokens }
function durationLabel(value: number | null): string { return value == null ? '—' : value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s` }
function percent(value: number): string { return `${Math.round(Number(value) * 100)}%` }

onMounted(() => void loadRuns())
onUnmounted(() => { disposed = true; clearPoll() })
</script>

<template>
  <section class="detail-card agent-panel">
    <div class="workspace-section-head">
      <div><span class="section-kicker">AGENT TRACE / LIVE</span><h2>Agent 分析</h2><p>查看模型判断、执行步骤和异常轨迹。运行中的任务每 3 秒轻量刷新。</p></div>
      <div class="section-head-actions"><span v-if="hasActiveRun" class="live-indicator"><i /> 自动刷新中</span><button v-if="canRerun" class="button button-outline" type="button" :disabled="rerunning" @click="rerun">{{ rerunning ? '正在创建…' : '重新分析' }}</button></div>
    </div>
    <div v-if="actionError" class="inline-alert" role="alert">{{ actionError }}</div>

    <StatePanel v-if="loading && runs.length === 0" kind="loading" title="正在读取 Agent 轨迹" description="正在获取运行记录、分析结果和执行步骤…" />
    <StatePanel v-else-if="error && runs.length === 0" kind="error" title="Agent 轨迹暂时无法加载" :description="error"><button class="button button-outline" type="button" @click="loadRuns()">重新加载</button></StatePanel>
    <StatePanel v-else-if="runs.length === 0" title="尚无 Agent 运行记录" description="工单创建事件可能仍在投递，也可以手动发起一次分析。"><button v-if="canRerun" class="button button-primary" type="button" :disabled="rerunning" @click="rerun">发起分析</button></StatePanel>
    <template v-else>
      <div v-if="failedRuns.length" class="manual-warning"><span class="manual-warning-mark">!</span><div><strong>存在失败的 Agent 运行，请人工处理</strong><p>模型失败不会影响原始工单。请检查错误码、执行步骤或手动重新分析。</p></div></div>

      <section v-if="analysis" class="analysis-board">
        <div class="analysis-score"><span>置信度</span><strong>{{ percent(analysis.confidence) }}</strong><div><i :style="{ width: percent(analysis.confidence) }" /></div></div>
        <dl><div><dt>分类</dt><dd>{{ categoryLabels[analysis.categoryCode] }}</dd></div><div><dt>建议优先级</dt><dd>{{ priorityMeta[analysis.priority].label }}</dd></div><div><dt>情绪</dt><dd>{{ sentimentLabels[analysis.sentiment] }}</dd></div><div><dt>风险</dt><dd :class="`risk-${analysis.riskLevel.toLowerCase()}`">{{ riskLabels[analysis.riskLevel] }}</dd></div></dl>
        <div class="analysis-reason"><span>分析依据</span><p>{{ analysis.reason }}</p><p v-if="analysis.manualRequired" class="manual-reason">需人工处理 · {{ analysis.manualReason || '高风险或置信度不足' }}</p></div>
      </section>
      <div v-else class="analysis-pending-note">尚未产生成功分析结果；请结合下方运行状态人工判断。</div>

      <div class="agent-workbench">
        <aside class="run-list" aria-label="Agent Run 列表">
          <button v-for="run in runs" :key="run.id" type="button" :class="{ active: selectedRunId === run.id }" @click="loadSteps(run.id)">
            <span class="run-status-mark" :class="`run-${run.status.toLowerCase()}`" /><span class="run-copy"><strong>{{ runTypeLabels[run.runType] }} · #{{ run.attemptNo }}</strong><small>{{ runLabels[run.status] }} · {{ formatDate(run.createdAt) }}</small></span><span class="run-tokens">{{ tokenTotal(run) }} T</span>
          </button>
        </aside>
        <section class="step-detail">
          <div v-if="selectedRun" class="run-summary"><div><span>状态</span><strong :class="`run-text-${selectedRun.status.toLowerCase()}`">{{ runLabels[selectedRun.status] }}</strong></div><div><span>模型</span><strong>{{ selectedRun.modelName || '—' }}</strong></div><div><span>输入 / 输出 Token</span><strong>{{ selectedRun.inputTokens }} / {{ selectedRun.outputTokens }}</strong></div><div><span>执行次数</span><strong>{{ selectedRun.executionCount }}</strong></div></div>
          <div v-if="selectedRun?.status === 'FAILED'" class="run-error"><span>错误码</span><strong>{{ selectedRun.errorCode || 'UNKNOWN_ERROR' }}</strong><p>本次运行未产生可信结果，请由人工继续处理工单。</p></div>
          <div v-if="stepsLoading" class="step-loading">正在读取执行步骤…</div>
          <div v-else-if="steps.length === 0" class="subtle-empty">该运行尚未写入执行步骤。</div>
          <ol v-else class="agent-steps"><li v-for="step in steps" :key="step.id"><span class="step-index">{{ String(step.sequenceNo).padStart(2, '0') }}</span><div class="step-copy"><header><strong>{{ stepLabels[step.stepType] }}</strong><span :class="step.status === 'FAILED' ? 'step-failed' : 'step-succeeded'">{{ step.status === 'FAILED' ? '失败' : '完成' }}</span></header><p v-if="step.outputSummary">{{ step.outputSummary }}</p><div class="step-metrics"><span>{{ durationLabel(step.durationMs) }}</span><span>{{ step.inputTokens + step.outputTokens }} Token</span><span v-if="step.retryCount">重试 {{ step.retryCount }} 次</span><span v-if="step.errorCode" class="step-error-code">{{ step.errorCode }}</span></div></div></li></ol>
        </section>
      </div>
    </template>
  </section>
</template>
