<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearSession, currentUser, primaryRole } from '../lib/session'

const route = useRoute()
const router = useRouter()

const roleName = computed(() => ({ ADMIN: '系统管理员', SUPPORT: '客服坐席', CUSTOMER: '客户' }[primaryRole.value ?? 'CUSTOMER']))
const queueName = computed(() => ({ ADMIN: '全部工单', SUPPORT: '团队工单', CUSTOMER: '我的工单' }[primaryRole.value ?? 'CUSTOMER']))
const futureModule = computed(() => ({
  ADMIN: '运营指标与知识库', SUPPORT: '会话与 AI 建议', CUSTOMER: '会话消息',
}[primaryRole.value ?? 'CUSTOMER']))

function logout() {
  clearSession()
  void router.replace({ name: 'login' })
}
</script>

<template>
  <div class="workspace-shell">
    <aside class="sidebar">
      <div class="brand-lockup">
        <span class="brand-symbol" aria-hidden="true"><span /><span /><span /></span>
        <span><strong>AgentOps</strong><small>WORKFLOW INTELLIGENCE</small></span>
      </div>

      <div class="workspace-switcher">
        <span class="switcher-icon">A</span>
        <span class="switcher-copy"><strong>{{ currentUser?.tenantId }}</strong><small>当前工作空间</small></span>
        <span class="switcher-caret" aria-hidden="true">⌄</span>
      </div>

      <nav class="sidebar-nav" aria-label="主导航">
        <p class="nav-caption">工作区</p>
        <RouterLink to="/tickets" class="nav-item" :class="{ active: route.path.startsWith('/tickets') && route.query.status !== 'PENDING' && route.query.create !== '1' }">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h11" /></svg>
          <span>{{ queueName }}</span><span class="nav-arrow">↗</span>
        </RouterLink>
        <RouterLink v-if="primaryRole === 'CUSTOMER'" :to="{ name: 'tickets', query: { create: '1' } }" class="nav-item" :class="{ active: route.query.create === '1' }">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg><span>发起新请求</span>
        </RouterLink>
        <RouterLink v-else :to="{ name: 'tickets', query: { status: 'PENDING' } }" class="nav-item" :class="{ active: route.query.status === 'PENDING' }">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16v12H4zM8 10h8m-8 4h5" /></svg><span>待处理队列</span>
        </RouterLink>
        <p class="nav-caption nav-caption-later">即将接入</p>
        <div class="nav-item nav-disabled" aria-disabled="true">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19V8m5 11V5m5 14v-8m5 8V9" /></svg>
          <span>{{ futureModule }}</span><span class="nav-soon">后续</span>
        </div>
      </nav>

      <div class="sidebar-bottom">
        <div class="sidebar-note"><span class="note-signal" /> 专注处理每一个请求</div>
        <div class="profile-row">
          <span class="avatar">{{ currentUser?.displayName?.slice(0, 1) || '用' }}</span>
          <span class="profile-copy"><strong>{{ currentUser?.displayName }}</strong><small>{{ roleName }}</small></span>
          <button class="icon-button logout-button" type="button" title="退出登录" aria-label="退出登录" @click="logout">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4m4-4 4-4-4-4m4 4H9" /></svg>
          </button>
        </div>
      </div>
    </aside>

    <div class="workspace-main">
      <header class="topbar">
        <div class="breadcrumb"><span>AgentOps</span><span class="breadcrumb-sep">/</span><strong>{{ route.meta.title }}</strong></div>
        <div class="topbar-right"><span class="topbar-date">{{ new Intl.DateTimeFormat('zh-CN', { dateStyle: 'long' }).format(new Date()) }}</span><span class="topbar-divider" /><span class="topbar-role">{{ roleName }}</span></div>
      </header>
      <main class="content-area"><RouterView /></main>
    </div>
  </div>
</template>
