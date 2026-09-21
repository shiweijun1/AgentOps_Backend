<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, authApi } from '../lib/api'
import { clearSession, currentUser, saveToken } from '../lib/session'

const route = useRoute()
const router = useRouter()
const tenantId = ref('default')
const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref('')
const expired = computed(() => route.query.reason === 'expired')

async function login() {
  error.value = ''
  if (!tenantId.value.trim() || !username.value.trim() || !password.value) {
    error.value = '请填写租户、用户名和密码。'
    return
  }
  submitting.value = true
  try {
    const result = await authApi.login(tenantId.value.trim(), username.value.trim(), password.value)
    saveToken(result.accessToken)
    currentUser.value = await authApi.me()
    password.value = ''
    const target = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      && !route.query.redirect.startsWith('//') ? route.query.redirect : '/tickets'
    await router.replace(target)
  } catch (cause) {
    clearSession()
    error.value = cause instanceof ApiError ? cause.message : '登录未完成，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-story" aria-label="产品介绍">
      <div class="story-top"><span class="brand-symbol brand-symbol-light" aria-hidden="true"><span /><span /><span /></span><strong>AgentOps</strong><span class="story-edition">企业工作台 / 01</span></div>
      <div class="story-content">
        <span class="eyebrow eyebrow-light">TICKET OPERATIONS · INTELLIGENT WORKFLOW</span>
        <h1>让每一张工单，<br /><em>都有清晰的下一步。</em></h1>
        <p>在同一个工作台中管理请求、协作处理与状态流转。AI 提供分析线索，关键决策始终由人掌握。</p>
        <div class="story-rule" />
        <div class="story-facts"><span><b>01</b> 清晰的处理队列</span><span><b>02</b> 可追踪的每次流转</span><span><b>03</b> 按角色聚焦工作</span></div>
      </div>
      <div class="story-bottom"><span>AGENTOPS / SERVICE DESK</span><span>把复杂留给系统，把判断留给你。</span></div>
      <div class="story-orbit orbit-one" /><div class="story-orbit orbit-two" />
    </section>
    <section class="login-form-side">
      <div class="login-mobile-brand">AgentOps <span>企业工单工作台</span></div>
      <div class="login-form-wrap">
        <div class="form-heading"><span class="eyebrow">WELCOME BACK / 欢迎回来</span><h2>登录工作台</h2><p>使用企业账号继续处理今天的工单。</p></div>
        <div v-if="expired" class="inline-alert" role="alert">登录状态已过期，请重新登录。</div>
        <form class="login-form" @submit.prevent="login">
          <label for="tenant">租户标识</label><input id="tenant" v-model="tenantId" autocomplete="organization" placeholder="例如 default" maxlength="64" />
          <label for="username">用户名</label><input id="username" v-model="username" autocomplete="username" placeholder="请输入用户名" maxlength="64" />
          <label for="password">密码</label><input id="password" v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" maxlength="128" />
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button class="button button-primary login-submit" type="submit" :disabled="submitting">{{ submitting ? '正在验证身份…' : '进入工作台' }} <span aria-hidden="true">→</span></button>
        </form>
        <div class="login-footnote"><span class="footnote-mark">◆</span> 仅供已授权的企业成员使用。请勿共享账号或登录凭证。</div>
      </div>
      <div class="login-copyright">© {{ new Date().getFullYear() }} AgentOps · Internal Service Platform</div>
    </section>
  </div>
</template>
