<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const frame = ref<HTMLIFrameElement>()
const auth = useAuthStore()

// 只向本页同源的诊断 iframe 提供当前登录令牌，不把令牌放进 URL 或持久化到诊断页。
function handleAuthRequest(event: MessageEvent) {
  if (event.origin !== window.location.origin
    || event.source !== frame.value?.contentWindow
    || event.data?.type !== 'easy-observability:auth-request') {
    return
  }

  frame.value?.contentWindow?.postMessage({
    type: 'easy-observability:auth-response',
    token: auth.accessToken
  }, window.location.origin)
}

window.addEventListener('message', handleAuthRequest)
onBeforeUnmount(() => window.removeEventListener('message', handleAuthRequest))
</script>

<template>
  <section class="observability-frame" aria-label="Easy Observability 运行诊断">
    <iframe
      ref="frame"
      class="observability-frame__content"
      src="/easy-observability/"
      title="Easy Observability 运行诊断"
      loading="eager"
      referrerpolicy="same-origin"
    />
  </section>
</template>

<style scoped>
.observability-frame {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--ea-border);
  border-radius: 10px;
  background: #f4f6f9;
}

.observability-frame__content {
  display: block;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  border: 0;
  background: #f4f6f9;
}
</style>
