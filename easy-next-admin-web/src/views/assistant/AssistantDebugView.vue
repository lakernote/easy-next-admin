<template>
  <section class="resource-page assistant-debug-page">
    <div class="resource-hero">
      <div>
        <h1>助手调试</h1>
        <p>直接调试请求上下文、权限过滤后的 Tool 面、企业助手主循环、执行策略和最终回复。</p>
      </div>
      <div class="resource-actions">
        <el-button :icon="DocumentCopy" :disabled="!lastResult" @click="copyFullResponse">复制调试响应 JSON</el-button>
        <el-button :icon="Refresh" :disabled="submitting" @click="resetDebugState">重置</el-button>
      </div>
    </div>

    <div class="resource-metrics is-four">
      <div v-for="item in metrics" :key="item.label" class="resource-metric">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <section class="surface resource-panel assistant-debug-panel">
      <div class="assistant-debug-grid">
        <div class="assistant-debug-input">
          <div class="section-head">
            <div>
              <h2>请求</h2>
              <p>选择带预期结果的场景，按 Ctrl / ⌘ + Enter 快速发送。</p>
            </div>
            <el-tag effect="plain">会话 {{ debugConversationId || '新建' }}</el-tag>
          </div>

          <el-form label-position="top">
            <el-form-item label="消息">
              <el-input
                v-model="message"
                type="textarea"
                :rows="8"
                maxlength="2000"
                show-word-limit
                placeholder="例如：我想今天下午请假"
                @keydown.ctrl.enter.prevent="submitMessage"
                @keydown.meta.enter.prevent="submitMessage"
              />
            </el-form-item>
            <el-form-item label="快速场景">
              <AssistantSamplePicker :disabled="submitting" @select="selectSample" />
            </el-form-item>
            <el-form-item>
              <el-button
                v-permission:disable="{ permissions: PermissionCodes.assistant.debug, reason: '缺少助手调试权限' }"
                type="primary"
                :icon="Promotion"
                :loading="submitting"
                :disabled="!message.trim()"
                @click="submitMessage"
              >
                运行测试
              </el-button>
            </el-form-item>
          </el-form>

          <el-alert
            v-if="errorText"
            class="assistant-debug-alert"
            type="error"
            :title="errorText"
            show-icon
            :closable="false"
          />
        </div>

        <div class="assistant-debug-result">
          <div class="section-head">
            <div>
              <h2>响应</h2>
              <p>先看本轮结论和问题定位，再逐层展开执行链、Tool 证据、模型调用与原始 JSON。</p>
            </div>
            <el-tag v-if="lastResult" :type="decisionStatusType" effect="plain">{{ lastResult.plan.status }}</el-tag>
          </div>

          <el-empty v-if="!lastResult" description="发送消息后查看结果" />
          <template v-else>
            <el-alert
              class="assistant-debug-truth"
              type="info"
              :closable="false"
              show-icon
              title="学习模式：执行链每一步的输入输出和模型交互均来自本次真实运行；相同 conversationId 会读取并写回同一份会话状态。"
            />
            <AssistantRunInspector :result="lastResult" />
          </template>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Promotion, Refresh } from '@element-plus/icons-vue'
import { runAssistantDebugTurn } from '@/features/assistant/api'
import { DEFAULT_ASSISTANT_SAMPLE } from '@/features/assistant/samples'
import type { AssistantSample } from '@/features/assistant/samples'
import type { AssistantEntryResponse } from '@/features/assistant/types'
import { PermissionCodes } from '@/permissions/codes'
import AssistantSamplePicker from './components/AssistantSamplePicker.vue'
import AssistantRunInspector from './components/AssistantRunInspector.vue'

const message = ref(DEFAULT_ASSISTANT_SAMPLE)
const submitting = ref(false)
const errorText = ref('')
const lastResult = ref<AssistantEntryResponse>()
const debugConversationId = ref<string | number | null>(null)

const metrics = computed(() => [
  { label: '运行状态', value: lastResult.value?.plan.status || '-' },
  { label: '总 Token', value: lastResult.value?.trace.tokenUsage.totalTokens ?? '-' },
  { label: '总耗时', value: formatDuration(lastResult.value?.trace.latency.totalDurationMs) },
  {
    label: '模型 / Tool 调用',
    value: lastResult.value
      ? `${lastResult.value.trace.latency.modelCallCount} / ${lastResult.value.trace.latency.toolCallCount}`
      : '-'
  }
])

const formattedResponse = computed(() => (lastResult.value ? JSON.stringify(lastResult.value, null, 2) : ''))
const decisionStatusType = computed(() => (
  lastResult.value?.plan.status === 'BLOCKED' ? 'warning' : 'success'
))

async function submitMessage() {
  const normalized = message.value.trim()
  if (!normalized) return
  submitting.value = true
  errorText.value = ''
  try {
    const result = await runAssistantDebugTurn({
      message: normalized,
      conversationId: debugConversationId.value
    })
    lastResult.value = result
    debugConversationId.value = result.conversationId ?? null
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '助手请求失败'
  } finally {
    submitting.value = false
  }
}

function resetDebugState() {
  message.value = DEFAULT_ASSISTANT_SAMPLE
  errorText.value = ''
  lastResult.value = undefined
  debugConversationId.value = null
}

function selectSample(sample: AssistantSample) {
  if (sample.session === 'NEW') {
    errorText.value = ''
    lastResult.value = undefined
    debugConversationId.value = null
  } else if (!debugConversationId.value) {
    ElMessage.warning('该场景需要续接当前会话，请先运行同组的起始场景')
  }
  message.value = sample.message
}

async function copyFullResponse() {
  await copyText(formattedResponse.value, '调试响应 JSON 已复制')
}

async function copyText(text: string, successMessage: string) {
  if (!text) return
  await navigator.clipboard.writeText(text)
  ElMessage.success(successMessage)
}

function formatDuration(value?: number | null) {
  if (typeof value !== 'number') return '-'
  return `${value} ms`
}

</script>

<style scoped>
.assistant-debug-panel {
  padding: 20px;
}

.assistant-debug-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.8fr) minmax(420px, 1.2fr);
  gap: 20px;
}

.assistant-debug-input,
.assistant-debug-result {
  min-width: 0;
}

.assistant-debug-alert {
  margin-top: 12px;
}

.assistant-debug-truth {
  margin-bottom: 14px;
}

@media (max-width: 1080px) {
  .assistant-debug-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .assistant-debug-panel {
    padding: 14px;
  }
}
</style>
