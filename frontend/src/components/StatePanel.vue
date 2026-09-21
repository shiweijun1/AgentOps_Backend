<script setup lang="ts">
withDefaults(defineProps<{ title: string; description: string; kind?: 'empty' | 'error' | 'loading' }>(), {
  kind: 'empty',
})
</script>

<template>
  <div class="state-panel" :class="`state-${kind}`" role="status">
    <div class="state-mark" aria-hidden="true">{{ kind === 'error' ? '!' : kind === 'loading' ? '···' : '○' }}</div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>
