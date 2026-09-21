import { createRouter, createWebHistory } from 'vue-router'
import { authApi } from './lib/api'
import { accessToken, clearSession, currentUser } from './lib/session'
import LoginView from './views/LoginView.vue'
import WorkspaceLayout from './views/WorkspaceLayout.vue'
import TicketListView from './views/TicketListView.vue'
import TicketDetailView from './views/TicketDetailView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true, title: '登录' } },
    {
      path: '/', component: WorkspaceLayout, redirect: '/tickets',
      children: [
        { path: 'tickets', name: 'tickets', component: TicketListView, meta: { title: '工单列表' } },
        { path: 'tickets/:id', name: 'ticket-detail', component: TicketDetailView, meta: { title: '工单详情' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/tickets' },
  ],
})

router.beforeEach(async (to) => {
  if (!accessToken.value) {
    return to.meta.public ? true : { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!currentUser.value) {
    try {
      currentUser.value = await authApi.me()
    } catch {
      clearSession()
      return to.meta.public ? true : { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  if (to.name === 'login') return { name: 'tickets' }
  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '工作台')} · AgentOps`
})
