<script setup lang="ts">
import { computed } from 'vue'
import { statusMeta } from '../lib/presentation'
import type { TicketStatus } from '../types'

const props = defineProps<{ status: TicketStatus }>()
const meta = computed(() => statusMeta[props.status])
</script>

<template>
  <span class="status-pill" :class="`tone-${meta.tone}`"><span class="status-dot" />{{ meta.label }}</span>
</template>
