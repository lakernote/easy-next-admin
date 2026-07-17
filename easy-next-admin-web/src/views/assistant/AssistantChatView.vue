<template>
  <section class="resource-page assistant-chat-page">
    <div class="resource-hero assistant-chat-hero">
      <div>
        <h1>智能对话</h1>
        <p>用自然语言查询企业信息、处理待办或发起业务动作。助手只会调用你有权限使用的能力。</p>
      </div>
      <div class="resource-actions">
        <el-tag :type="conversationId ? 'success' : 'info'" effect="plain">
          {{ conversationId ? '上下文已连接' : '新对话' }}
        </el-tag>
        <el-button :icon="Plus" :disabled="submitting" @click="startNewConversation">新对话</el-button>
      </div>
    </div>

    <section class="surface assistant-chat-workbench">
      <div ref="threadRef" class="assistant-chat-thread" aria-live="polite">
        <div v-if="!messages.length" class="assistant-chat-empty">
          <div class="assistant-mark" aria-hidden="true">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <h2>今天想处理什么？</h2>
          <p>可以直接描述目标，助手会自己判断是否需要使用企业 Tool；涉及写操作时会先让你确认。</p>
          <div class="assistant-suggestions" aria-label="对话建议">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion"
              type="button"
              :disabled="submitting"
              @click="sendSuggestion(suggestion)"
            >
              <span>{{ suggestion }}</span>
              <el-icon><Right /></el-icon>
            </button>
          </div>
        </div>

        <article
          v-for="item in messages"
          :key="item.id"
          class="assistant-chat-message"
          :class="`is-${item.role}`"
        >
          <div class="message-avatar" aria-hidden="true">
            <el-icon><User v-if="item.role === 'user'" /><ChatDotRound v-else /></el-icon>
          </div>
          <div class="message-body">
            <div class="message-meta">
              <strong>{{ item.role === 'user' ? '我' : '智能助手' }}</strong>
              <el-tag
                v-if="item.role === 'assistant' && item.status && item.status !== 'COMPLETED'"
                :type="statusMeta(item.status).type"
                effect="plain"
                size="small"
              >
                {{ statusMeta(item.status).label }}
              </el-tag>
            </div>
            <div class="message-content">
              <AssistantMessageContent :content="item.content" />
            </div>
            <AssistantChatProgress v-if="item.steps?.length" :steps="item.steps" compact />
          </div>
        </article>

        <article v-if="submitting" class="assistant-chat-message is-assistant is-pending">
          <div class="message-avatar" aria-hidden="true">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="message-body">
            <div class="message-meta">
              <strong>智能助手</strong>
              <el-tag type="primary" effect="plain" size="small">实时处理中</el-tag>
            </div>
            <AssistantChatProgress v-if="activeSteps.length" :steps="activeSteps" />
            <div v-else class="thinking-indicator">
              <span></span><span></span><span></span>
              <em>正在连接 Agent Runtime</em>
            </div>
          </div>
        </article>
      </div>

      <div class="assistant-composer-shell">
        <el-alert
          v-if="errorText"
          type="error"
          :title="errorText"
          show-icon
          closable
          @close="errorText = ''"
        />
        <div class="assistant-composer" :class="{ 'is-focused': composerFocused }">
          <el-input
            v-model="composer"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            maxlength="2000"
            resize="none"
            placeholder="输入你想查询或处理的事项…"
            aria-label="发送给智能助手的消息"
            :disabled="submitting"
            @focus="composerFocused = true"
            @blur="composerFocused = false"
            @keydown="handleComposerKeydown"
          />
          <div class="assistant-composer-actions">
            <span>Enter 发送 · Shift + Enter 换行</span>
            <el-button
              v-permission:disable="{ permissions: PermissionCodes.assistant.chat, reason: '缺少智能对话权限' }"
              type="primary"
              :icon="Promotion"
              :loading="submitting"
              :disabled="!composer.trim()"
              @click="sendMessage"
            >
              发送
            </el-button>
          </div>
        </div>
        <p class="assistant-chat-footnote">助手可能会犯错；重要写操作必须经过确认，并以系统实际结果为准。</p>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ChatDotRound, Plus, Promotion, Right, User } from '@element-plus/icons-vue'
import { streamAssistantChatTurn } from '@/features/assistant/api'
import type {
  AssistantChatProgressItem,
  AssistantChatResponse,
  AssistantChatStatus
} from '@/features/assistant/types'
import { PermissionCodes } from '@/permissions/codes'
import AssistantMessageContent from './components/AssistantMessageContent.vue'
import AssistantChatProgress from './components/AssistantChatProgress.vue'

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  status?: AssistantChatStatus
  steps?: AssistantChatProgressItem[]
}

const suggestions = [
  '我现在有哪些待办？',
  '帮我查一下请假流程状态',
  '年假和调休有什么区别？',
  '计算 (1250 + 750) × 0.13'
]

const composer = ref('')
const composerFocused = ref(false)
const submitting = ref(false)
const activeSteps = ref<AssistantChatProgressItem[]>([])
const errorText = ref('')
const conversationId = ref<string | number | null>(null)
const messages = ref<ChatMessage[]>([])
const threadRef = ref<HTMLElement>()
let messageSequence = 0

async function sendMessage() {
  const content = composer.value.trim()
  if (!content || submitting.value) return

  messages.value.push({ id: ++messageSequence, role: 'user', content })
  composer.value = ''
  submitting.value = true
  activeSteps.value = []
  errorText.value = ''
  await scrollToLatest()

  try {
    let response: AssistantChatResponse | undefined
    await streamAssistantChatTurn(
      {
        message: content,
        conversationId: conversationId.value
      },
      event => {
        if (event.event === 'progress') {
          appendProgressStep(event)
        }
        if (event.event === 'error') {
          throw new Error(event.message || '消息发送失败，请稍后重试')
        }
        if (event.event === 'done' && event.response) {
          response = event.response
        }
        void scrollToLatest()
      }
    )
    if (!response) {
      throw new Error('助手连接已结束，但没有返回完整结果')
    }
    conversationId.value = response.conversationId
    messages.value.push({
      id: ++messageSequence,
      role: 'assistant',
      content: response.reply || '本轮没有生成可展示的回复。',
      status: response.status,
      steps: completedProgressSteps()
    })
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '消息发送失败，请稍后重试'
    composer.value = content
  } finally {
    submitting.value = false
    await scrollToLatest()
  }
}

function sendSuggestion(suggestion: string) {
  composer.value = suggestion
  void sendMessage()
}

function startNewConversation() {
  conversationId.value = null
  messages.value = []
  activeSteps.value = []
  composer.value = ''
  errorText.value = ''
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.isComposing || event.key !== 'Enter' || event.shiftKey) return
  event.preventDefault()
  void sendMessage()
}

function statusMeta(status: AssistantChatStatus) {
  switch (status) {
    case 'AWAITING_INPUT':
      return { label: '等待补充', type: 'warning' as const }
    case 'AWAITING_CONFIRMATION':
      return { label: '等待确认', type: 'warning' as const }
    case 'PARTIALLY_COMPLETED':
      return { label: '部分完成', type: 'warning' as const }
    case 'FAILED':
      return { label: '执行失败', type: 'danger' as const }
    case 'INDETERMINATE':
      return { label: '结果待核对', type: 'warning' as const }
    case 'BLOCKED':
      return { label: '暂无法处理', type: 'danger' as const }
    default:
      return { label: '已完成', type: 'success' as const }
  }
}

function appendProgressStep(event: {
  step: string
  sequence: number
  phase: AssistantChatProgressItem['phase']
  message: string
}) {
  activeSteps.value.forEach(item => {
    if (item.state === 'running') item.state = 'completed'
  })
  const existing = activeSteps.value.find(item => item.sequence === event.sequence)
  if (existing) {
    Object.assign(existing, event, { state: 'running' as const })
    return
  }
  activeSteps.value.push({ ...event, state: 'running' })
}

function completedProgressSteps() {
  return activeSteps.value.map(item => ({ ...item, state: 'completed' as const }))
}

async function scrollToLatest() {
  await nextTick()
  threadRef.value?.scrollTo({ top: threadRef.value.scrollHeight, behavior: 'smooth' })
}
</script>

<style scoped>
.assistant-chat-page {
  min-height: calc(100vh - 112px);
}

.assistant-chat-hero {
  align-items: center;
}

.assistant-chat-workbench {
  display: grid;
  grid-template-rows: minmax(420px, 1fr) auto;
  min-height: calc(100vh - 260px);
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color);
}

.assistant-chat-thread {
  min-height: 0;
  max-height: calc(100vh - 420px);
  padding: 32px clamp(18px, 5vw, 72px);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.assistant-chat-empty {
  display: grid;
  justify-items: center;
  max-width: 760px;
  margin: 28px auto 0;
  text-align: center;
}

.assistant-mark {
  display: grid;
  width: 56px;
  height: 56px;
  margin-bottom: 18px;
  place-items: center;
  border-radius: 16px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 28px;
}

.assistant-chat-empty h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: clamp(24px, 3vw, 32px);
  letter-spacing: -0.02em;
}

.assistant-chat-empty > p {
  max-width: 620px;
  margin: 12px 0 24px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.assistant-suggestions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}

.assistant-suggestions button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 50px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 9px;
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 160ms ease, background-color 160ms ease;
}

.assistant-suggestions button:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.assistant-suggestions button:focus-visible {
  outline: 3px solid var(--el-color-primary-light-7);
  outline-offset: 2px;
}

.assistant-suggestions button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.assistant-suggestions .el-icon {
  flex: 0 0 auto;
  color: var(--el-color-primary);
}

.assistant-chat-message {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 12px;
  max-width: 860px;
  margin: 0 auto 22px;
}

.message-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}

.assistant-chat-message.is-assistant .message-avatar {
  border-color: var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.message-body {
  min-width: 0;
  padding-top: 2px;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.message-meta strong {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.message-content {
  margin: 6px 0 0;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.assistant-chat-message.is-user .message-content {
  display: inline-block;
  padding: 9px 12px;
  border-radius: 4px 12px 12px;
  background: var(--el-fill-color-light);
}

.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 7px;
  color: var(--el-text-color-secondary);
}

.thinking-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary-light-3);
  animation: thinking-pulse 1.2s ease-in-out infinite;
}

.thinking-indicator span:nth-child(2) { animation-delay: 120ms; }
.thinking-indicator span:nth-child(3) { animation-delay: 240ms; }

.thinking-indicator em {
  margin-left: 5px;
  font-size: 13px;
  font-style: normal;
}

.assistant-composer-shell {
  display: grid;
  gap: 10px;
  padding: 16px clamp(18px, 5vw, 72px) 18px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.assistant-composer {
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  padding: 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 11px;
  background: var(--el-bg-color);
  transition: border-color 160ms ease;
}

.assistant-composer.is-focused {
  border-color: var(--el-color-primary-light-3);
  outline: 3px solid var(--el-color-primary-light-9);
}

.assistant-composer :deep(.el-textarea__inner) {
  --el-input-border-color: transparent;
  --el-input-hover-border-color: transparent;
  --el-input-focus-border-color: transparent;
  padding: 4px 6px 10px;
  border: 0;
  background: transparent;
}

.assistant-composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 2px 0 6px;
}

.assistant-composer-actions > span,
.assistant-chat-footnote {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.assistant-chat-footnote {
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  text-align: center;
}

@keyframes thinking-pulse {
  0%, 60%, 100% { opacity: 0.35; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-3px); }
}

@media (max-width: 720px) {
  .assistant-chat-workbench {
    min-height: calc(100vh - 220px);
    border-radius: 8px;
  }

  .assistant-chat-thread {
    max-height: calc(100vh - 390px);
    padding: 24px 14px;
  }

  .assistant-suggestions {
    grid-template-columns: 1fr;
  }

  .assistant-composer-shell {
    padding: 12px;
  }

  .assistant-composer-actions > span {
    display: none;
  }

  .assistant-composer-actions {
    justify-content: flex-end;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-suggestions button,
  .assistant-composer {
    transition: none;
  }

  .thinking-indicator span {
    animation: none;
  }
}
</style>
