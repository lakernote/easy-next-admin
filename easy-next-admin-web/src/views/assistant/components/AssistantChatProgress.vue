<template>
  <details v-if="compact" class="chat-progress-disclosure">
    <summary>查看本轮 {{ steps.length }} 个执行步骤</summary>
    <AssistantChatProgress :steps="steps" />
  </details>

  <ol v-else class="chat-progress" aria-label="Agent 实时执行步骤">
    <TransitionGroup name="chat-step">
      <li
        v-for="item in steps"
        :key="item.sequence"
        :class="[`is-${item.state}`, `phase-${item.phase.toLowerCase()}`]"
      >
        <span class="chat-step-node" aria-hidden="true">
          <i></i>
        </span>
        <div>
          <small>STEP {{ item.sequence }}</small>
          <strong>{{ item.message }}</strong>
        </div>
        <em>{{ item.state === 'running' ? '进行中' : '完成' }}</em>
      </li>
    </TransitionGroup>
  </ol>
</template>

<script setup lang="ts">
import type { AssistantChatProgressItem } from '@/features/assistant/types'

withDefaults(defineProps<{
  steps: AssistantChatProgressItem[]
  compact?: boolean
}>(), {
  compact: false
})
</script>

<style scoped>
.chat-progress {
  display: grid;
  gap: 0;
  max-width: 620px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.chat-progress li {
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  gap: 9px;
  min-height: 38px;
  color: var(--el-text-color-secondary);
}

.chat-progress li:not(:last-child)::after {
  position: absolute;
  top: 18px;
  bottom: -2px;
  left: 5px;
  width: 1px;
  background: var(--el-border-color);
  content: '';
}

.chat-step-node {
  position: relative;
  z-index: 1;
  display: grid;
  width: 12px;
  height: 12px;
  margin-top: 4px;
  place-items: center;
  border: 1px solid var(--el-border-color-darker);
  border-radius: 50%;
  background: var(--el-bg-color);
}

.chat-step-node i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.chat-progress li.is-running {
  color: var(--el-text-color-primary);
}

.chat-progress li.is-running .chat-step-node {
  border-color: var(--el-color-primary);
  outline: 3px solid var(--el-color-primary-light-9);
  outline-offset: 1px;
}

.chat-progress li.is-running .chat-step-node i {
  background: var(--el-color-primary);
  animation: agent-step-pulse 1.25s ease-in-out infinite;
}

.chat-progress li.is-completed .chat-step-node {
  border-color: var(--el-color-success-light-3);
  background: var(--el-color-success-light-9);
}

.chat-progress li.is-completed .chat-step-node i {
  background: var(--el-color-success);
}

.chat-progress li > div {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.chat-progress small {
  color: var(--el-text-color-placeholder);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.chat-progress strong {
  color: inherit;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.45;
}

.chat-progress em {
  padding-top: 3px;
  color: var(--el-text-color-placeholder);
  font-size: 10px;
  font-style: normal;
}

.chat-progress-disclosure {
  max-width: 620px;
  margin-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.chat-progress-disclosure summary {
  padding-top: 9px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
}

.chat-step-enter-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.chat-step-enter-from {
  opacity: 0;
  transform: translateY(5px);
}

@keyframes agent-step-pulse {
  0%, 100% { opacity: 0.45; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.35); }
}

@media (prefers-reduced-motion: reduce) {
  .chat-step-enter-active {
    transition: none;
  }

  .chat-progress li.is-running .chat-step-node i {
    animation: none;
  }
}
</style>
