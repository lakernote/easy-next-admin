<template>
  <div v-if="selectedStep" class="step-explorer">
    <nav class="step-rail" aria-label="Agent 执行链">
      <button
        v-for="(step, index) in steps"
        :key="step.sequence"
        type="button"
        :class="{ 'is-active': step.sequence === selectedStep.sequence }"
        :style="{ '--step-delay': `${Math.min(index * 35, 280)}ms` }"
        @click="selectedSequence = step.sequence"
      >
        <span>{{ String(step.sequence).padStart(2, '0') }}</span>
        <div>
          <strong>{{ step.title }}</strong>
          <small>{{ step.component }}</small>
        </div>
        <em>{{ formatDuration(step.durationMs) }}</em>
      </button>
    </nav>

    <section class="step-detail">
      <header>
        <div>
          <small>STEP {{ selectedStep.sequence }} · {{ selectedStep.name }}</small>
          <h3>{{ selectedStep.title }}</h3>
          <p>{{ selectedStep.purpose }}</p>
        </div>
        <el-tag :type="statusType(selectedStep.status)" effect="plain">{{ selectedStep.status }}</el-tag>
      </header>

      <div class="step-facts">
        <span>耗时 <strong>{{ formatDuration(selectedStep.durationMs) }}</strong></span>
        <span>模型 <strong>{{ selectedStep.modelCalls.length }}</strong></span>
        <span>Tool <strong>{{ selectedStep.toolCalls.length }}</strong></span>
        <span>Token <strong>{{ selectedStep.tokenUsage.totalTokens }}</strong></span>
      </div>

      <p v-if="selectedStep.summary" class="step-summary">{{ selectedStep.summary }}</p>

      <el-tabs model-value="input" class="step-payload-tabs">
        <el-tab-pane label="步骤真实输入" name="input">
          <pre class="debug-json">{{ formatJson(selectedStep.inputPayload) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="步骤真实输出" name="output">
          <pre class="debug-json">{{ formatJson(selectedStep.outputPayload) }}</pre>
        </el-tab-pane>
      </el-tabs>

      <section v-if="selectedStep.modelCalls.length" class="step-model-calls">
        <header>
          <strong>该步骤中的真实模型交互</strong>
          <span>{{ selectedStep.modelCalls.length }} 次</span>
        </header>
        <details v-for="(call, index) in selectedStep.modelCalls" :key="`${call.name}-${index}`" :open="index === 0">
          <summary>
            <span>LLM {{ index + 1 }} · {{ call.model }}</span>
            <em>{{ call.tokenUsage.totalTokens }} tokens · {{ formatDuration(call.timing.totalDurationMs) }}</em>
          </summary>
          <div class="model-io-grid">
            <section>
              <strong>实际模型输入</strong>
              <small>消息数组、Tool Schema、生成选项</small>
              <pre class="debug-json">{{ formatJson(call.requestPayload) }}</pre>
            </section>
            <section>
              <strong>供应商输出 / Runtime 归一化</strong>
              <small>模型原始语义输出与 AgentLoop 实际消费结果</small>
              <pre class="debug-json">{{ formatJson(call.responsePayload || call.errorMessage) }}</pre>
            </section>
          </div>
        </details>
      </section>
    </section>
  </div>

  <el-empty v-else description="本轮没有执行步骤" />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AssistantTraceStepSnapshot } from '@/features/assistant/types'

const props = defineProps<{
  steps: AssistantTraceStepSnapshot[]
}>()

const selectedSequence = ref(0)
const selectedStep = computed(() => (
  props.steps.find(step => step.sequence === selectedSequence.value) || props.steps[0]
))

watch(
  () => props.steps,
  steps => {
    if (!steps.some(step => step.sequence === selectedSequence.value)) {
      selectedSequence.value = steps[0]?.sequence || 0
    }
  },
  { immediate: true }
)

function formatDuration(value?: number | null) {
  return typeof value === 'number' ? `${value} ms` : '-'
}

function formatJson(value: unknown) {
  return JSON.stringify(value ?? '-', null, 2)
}

function statusType(status: string) {
  if (status === 'FAILED' || status === 'BLOCKED') return 'danger'
  if (status === 'INPUT_REQUIRED' || status === 'APPROVAL_REQUIRED') return 'warning'
  if (status === 'SKIPPED' || status === 'INDETERMINATE') return 'info'
  return 'success'
}
</script>

<style scoped>
.step-explorer {
  display: grid;
  grid-template-columns: minmax(210px, 0.34fr) minmax(0, 1fr);
  min-height: 560px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.step-rail {
  padding: 8px;
  overflow-y: auto;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.step-rail button {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-height: 56px;
  padding: 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  text-align: left;
  cursor: pointer;
  animation: step-rail-enter 220ms both;
  animation-delay: var(--step-delay);
  transition: background-color 150ms ease, color 150ms ease;
}

.step-rail button:hover {
  background: var(--el-fill-color-light);
}

.step-rail button.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.step-rail button > span {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 1px solid var(--el-border-color);
  border-radius: 50%;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.step-rail button.is-active > span {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-bg-color);
}

.step-rail button > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.step-rail strong,
.step-rail small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-rail strong { font-size: 12px; }
.step-rail small,
.step-rail em {
  color: var(--el-text-color-secondary);
  font-size: 10px;
  font-style: normal;
}

.step-detail {
  min-width: 0;
  padding: 20px;
  overflow: auto;
}

.step-detail > header,
.step-model-calls > header,
.step-model-calls summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.step-detail > header small {
  color: var(--el-color-primary);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.step-detail h3 {
  margin: 5px 0 0;
  color: var(--el-text-color-primary);
  font-size: 19px;
}

.step-detail header p,
.step-summary {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.step-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin: 16px 0;
  padding: 10px 0;
  border-block: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.step-facts strong { color: var(--el-text-color-primary); }

.debug-json {
  min-height: 210px;
  max-height: 460px;
  margin: 8px 0 0;
  padding: 14px;
  overflow: auto;
  border: 1px solid #26344d;
  border-radius: 7px;
  background: #0f172a;
  color: #dbeafe;
  font-size: 11px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.step-model-calls {
  display: grid;
  gap: 10px;
  margin-top: 20px;
}

.step-model-calls > header span,
.step-model-calls summary em,
.model-io-grid small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-style: normal;
}

.step-model-calls details {
  padding: 11px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.step-model-calls summary {
  cursor: pointer;
}

.model-io-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.model-io-grid section { min-width: 0; }
.model-io-grid section > strong,
.model-io-grid section > small { display: block; }

@keyframes step-rail-enter {
  from { opacity: 0; transform: translateX(-6px); }
  to { opacity: 1; transform: translateX(0); }
}

@media (max-width: 900px) {
  .step-explorer { grid-template-columns: 1fr; }
  .step-rail {
    display: flex;
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .step-rail button { min-width: 190px; }
  .model-io-grid { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .step-rail button { animation: none; transition: none; }
}
</style>
