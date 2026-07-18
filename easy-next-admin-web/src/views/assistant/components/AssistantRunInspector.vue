<template>
  <div class="assistant-run-inspector">
    <section class="run-verdict" :class="`is-${inspection.tone}`" aria-live="polite">
      <div>
        <span>本轮结论</span>
        <strong>{{ inspection.title }}</strong>
        <p>{{ inspection.summary }}</p>
      </div>
      <div class="run-verdict-tags">
        <el-tag :type="inspection.tone" effect="plain">{{ terminationLabel(result.terminationReason) }}</el-tag>
        <el-tag :type="inspection.tone" effect="dark">{{ decisionLabel(result.plan.status) }}</el-tag>
        <el-tag v-if="result.plan.toolName" effect="plain">
          {{ result.plan.toolName }}
        </el-tag>
      </div>
    </section>

    <div class="run-check-grid">
      <article v-for="check in inspection.checks" :key="check.label" class="run-check">
        <div class="run-check-head">
          <span>{{ check.label }}</span>
          <el-tag :type="check.tone" effect="plain" size="small">{{ check.value }}</el-tag>
        </div>
        <p>{{ check.detail }}</p>
      </article>
    </div>

    <el-tabs v-model="activeTab" class="run-inspector-tabs">
      <el-tab-pane label="结果与诊断" name="overview">
        <div class="run-overview-grid">
          <section class="run-section">
            <div class="run-section-head">
              <strong>最终回复</strong>
              <el-button :icon="DocumentCopy" text @click="copyText(result.reply || '', '最终回复已复制')">
                复制回复
              </el-button>
            </div>
            <pre class="assistant-reply-text">{{ result.reply || '本轮没有可见回复' }}</pre>
          </section>

          <section class="run-section">
            <div class="run-section-head">
              <strong>延迟归因</strong>
              <span>总耗时 {{ formatDuration(result.trace.latency.totalDurationMs) }}</span>
            </div>
            <div class="latency-list">
              <div v-for="segment in inspection.latencySegments" :key="segment.key" class="latency-row">
                <div>
                  <span>{{ segment.label }}</span>
                  <em>{{ formatDuration(segment.valueMs) }} · {{ segment.percentage }}%</em>
                </div>
                <el-progress
                  :percentage="Math.min(100, segment.percentage)"
                  :stroke-width="8"
                  :show-text="false"
                  :color="latencyColor(segment.tone)"
                />
              </div>
            </div>
          </section>
        </div>

        <section class="run-section run-observations">
          <div class="run-section-head">
            <strong>问题定位提示</strong>
            <span>先看结论，再进入执行链验证</span>
          </div>
          <ul>
            <li v-for="item in inspection.observations" :key="item">{{ item }}</li>
          </ul>
        </section>
      </el-tab-pane>

      <el-tab-pane :label="`执行链 ${inspection.steps.length}`" name="steps">
        <AssistantStepExplorer :steps="inspection.steps" />
      </el-tab-pane>

      <el-tab-pane :label="`Tool 与证据 ${inspection.toolCalls.length}`" name="tools">
        <div class="run-evidence-grid">
          <section class="run-section">
            <div class="run-section-head">
              <strong>执行结果</strong>
              <el-tag :type="statusType(result.execution.status)" effect="plain">
                {{ result.execution.status }}
              </el-tag>
            </div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="Tool">{{ result.execution.toolName || '未调用' }}</el-descriptions-item>
              <el-descriptions-item label="产物类型">{{ result.execution.artifactType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="事实摘要">{{ result.execution.summary || '-' }}</el-descriptions-item>
            </el-descriptions>
            <pre class="assistant-json is-compact">{{ formatJson(result.execution.data) }}</pre>
          </section>

          <section class="run-section">
            <div class="run-section-head">
              <strong>参数合同</strong>
              <span>{{ result.plan.filledArguments.length }} 已填 / {{ result.plan.missingParameters.length }} 缺失</span>
            </div>
            <el-empty
              v-if="!result.plan.filledArguments.length && !result.plan.missingParameters.length"
              description="本轮没有 Tool 参数"
              :image-size="56"
            />
            <el-descriptions v-else :column="1" border>
              <el-descriptions-item
                v-for="argument in result.plan.filledArguments"
                :key="argument.key"
                :label="argument.label"
              >
                {{ argument.displayValue }}
              </el-descriptions-item>
              <el-descriptions-item
                v-for="parameter in result.plan.missingParameters"
                :key="parameter.key"
                :label="parameter.label"
              >
                <el-tag type="warning" effect="plain">等待用户补充</el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </section>
        </div>

        <section v-if="inspection.toolCalls.length" class="run-section tool-call-strip">
          <article v-for="(call, index) in inspection.toolCalls" :key="`${call.name}-${call.phase}-${index}`">
            <div>
              <strong>{{ call.name }}</strong>
              <span>{{ call.phase }}</span>
            </div>
            <el-tag :type="statusType(call.status)" effect="plain" size="small">{{ call.status }}</el-tag>
            <em>{{ formatDuration(call.durationMs) }}</em>
          </article>
        </section>
      </el-tab-pane>

      <el-tab-pane :label="`模型调用 ${inspection.modelCalls.length}`" name="models">
        <el-alert
          v-if="inspection.modelCalls.length"
          class="model-truth-note"
          type="info"
          :closable="false"
          show-icon
          title="这里展示模型实际可见的消息数组、Tool Schema、生成选项，以及供应商语义输出和 Runtime 归一化结果；本地调试会脱敏，单个 Payload 上限 100000 字符。"
        />
        <el-empty v-if="!inspection.modelCalls.length" description="本轮没有模型调用明细" />
        <el-collapse v-else class="model-call-collapse">
          <el-collapse-item
            v-for="(call, index) in inspection.modelCalls"
            :key="`${call.name}-${index}`"
            :name="index"
          >
            <template #title>
              <div class="model-call-title">
                <strong>{{ index + 1 }}. {{ call.name }}</strong>
                <span>{{ call.model }}</span>
                <el-tag :type="statusType(call.status)" effect="plain" size="small">{{ call.status }}</el-tag>
                <em>{{ formatDuration(call.timing.totalDurationMs) }} · {{ call.tokenUsage.totalTokens }} tokens</em>
              </div>
            </template>
            <div class="model-call-body">
              <div class="model-timing-strip">
                <span>准备 {{ formatDuration(call.timing.requestPreparationDurationMs) }}</span>
                <span>网关 {{ formatDuration(call.timing.providerRoundTripDurationMs) }}</span>
                <span>处理 {{ formatDuration(call.timing.responseProcessingDurationMs) }}</span>
              </div>
              <el-tabs model-value="request">
                <el-tab-pane label="真实模型输入" name="request">
                  <pre class="assistant-json">{{ formatJson(call.requestPayload) }}</pre>
                </el-tab-pane>
                <el-tab-pane label="供应商输出 / Runtime 归一化" name="response">
                  <pre class="assistant-json">{{ formatJson(call.responsePayload || call.errorMessage) }}</pre>
                </el-tab-pane>
              </el-tabs>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>

      <el-tab-pane label="原始 JSON" name="json">
        <div class="run-section-head">
          <strong>完整调试响应</strong>
          <el-button :icon="DocumentCopy" text @click="copyText(formattedResult, '完整调试 JSON 已复制')">
            复制 JSON
          </el-button>
        </div>
        <pre class="assistant-json is-raw">{{ formattedResult }}</pre>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { decisionLabel, inspectAssistantRun, terminationLabel } from '@/features/assistant/inspector'
import type { AssistantEntryResponse } from '@/features/assistant/types'
import AssistantStepExplorer from './AssistantStepExplorer.vue'

const props = defineProps<{
  result: AssistantEntryResponse
}>()

const activeTab = ref('steps')
const inspection = computed(() => inspectAssistantRun(props.result))
const formattedResult = computed(() => formatJson(props.result))

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

function latencyColor(tone: 'primary' | 'warning' | 'success' | 'info') {
  const colors = {
    primary: 'var(--el-color-primary)',
    warning: 'var(--el-color-warning)',
    success: 'var(--el-color-success)',
    info: 'var(--el-color-info)'
  }
  return colors[tone]
}

async function copyText(text: string, message: string) {
  if (!text) return
  await navigator.clipboard.writeText(text)
  ElMessage.success(message)
}
</script>

<style scoped>
.assistant-run-inspector {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.run-verdict {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-left-width: 4px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.run-verdict.is-success { border-left-color: var(--el-color-success); }
.run-verdict.is-warning { border-left-color: var(--el-color-warning); }
.run-verdict.is-danger { border-left-color: var(--el-color-danger); }
.run-verdict.is-info { border-left-color: var(--el-color-info); }

.run-verdict span,
.run-verdict strong,
.run-verdict p {
  display: block;
}

.run-verdict > div:first-child > span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.run-verdict strong {
  margin-top: 4px;
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.run-verdict p {
  margin: 6px 0 0;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.run-verdict-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.run-check-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.run-check {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.run-check-head,
.run-section-head,
.latency-row > div,
.tool-call-strip article,
.model-call-title,
.model-timing-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.run-check-head > span,
.run-section-head > span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.run-check p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.run-inspector-tabs {
  min-width: 0;
}

.run-overview-grid,
.run-evidence-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.run-section {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.assistant-reply-text {
  min-height: 180px;
  max-height: 360px;
  margin: 12px 0 0;
  overflow: auto;
  color: var(--el-text-color-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.latency-list {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}

.latency-row {
  display: grid;
  gap: 6px;
}

.latency-row span {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.latency-row em,
.tool-call-strip em,
.model-call-title em {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-style: normal;
}

.run-observations {
  margin-top: 12px;
}

.run-observations ul {
  display: grid;
  gap: 8px;
  margin: 12px 0 0;
  padding-left: 20px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.step-cell {
  display: grid;
  gap: 3px;
}

.step-cell span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.assistant-json {
  margin: 10px 0 0;
  padding: 14px;
  min-height: 180px;
  max-height: 420px;
  overflow: auto;
  border: 1px solid var(--el-border-color-darker);
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.assistant-json.is-compact {
  min-height: 120px;
  max-height: 260px;
}

.assistant-json.is-raw {
  min-height: 420px;
  max-height: 720px;
}

.tool-call-strip {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.tool-call-strip article {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.tool-call-strip article:last-child {
  border-bottom: 0;
}

.tool-call-strip article > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  margin-right: auto;
}

.tool-call-strip span,
.model-call-title span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.model-call-title {
  justify-content: flex-start;
  width: calc(100% - 28px);
  min-width: 0;
}

.model-call-title em {
  margin-left: auto;
}

.model-call-body {
  padding: 4px 0 12px;
}

.model-truth-note {
  margin-bottom: 12px;
}

.model-timing-strip {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-bottom: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .run-overview-grid,
  .run-evidence-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .run-verdict,
  .run-check-head,
  .run-section-head,
  .model-call-title {
    align-items: flex-start;
    flex-direction: column;
  }

  .run-verdict-tags {
    justify-content: flex-start;
  }

  .run-check-grid {
    grid-template-columns: 1fr;
  }

  .model-call-title {
    width: calc(100% - 28px);
  }

  .model-call-title em {
    margin-left: 0;
  }
}
</style>
