import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import './styles.css'

window.addEventListener('agentops:unauthorized', () => {
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({ name: 'login', query: { reason: 'expired', redirect: router.currentRoute.value.fullPath } })
  }
})

createApp(App).use(router).mount('#app')
