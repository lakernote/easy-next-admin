<template>
  <div class="assistant-sample-groups">
    <p class="assistant-sample-note">
      “新会话”会自动清空上一轮状态；“续接当前会话”用于补字段、确认、取消和引用上一轮结果。
    </p>
    <el-tabs v-model="activeGroup" class="assistant-sample-tabs">
      <el-tab-pane
        v-for="group in assistantSampleGroups"
        :key="group.key"
        :label="group.title"
        :name="group.key"
      >
        <section class="assistant-sample-group">
          <p class="assistant-group-description">{{ group.description }}</p>
          <div class="assistant-samples">
            <button
              v-for="sample in group.samples"
              :key="sample.message"
              class="assistant-sample-card"
              type="button"
              :disabled="disabled"
              :title="sample.message"
              :aria-label="`选择测试场景：${sample.message}`"
              @click="emit('select', sample)"
            >
              <span class="assistant-sample-message">{{ sample.message }}</span>
              <el-tag :type="sample.session === 'NEW' ? 'info' : 'warning'" effect="plain" size="small">
                {{ sample.session === 'NEW' ? '新会话' : '续接当前会话' }}
              </el-tag>
              <span class="assistant-sample-expected">预期：{{ sample.expected }}</span>
            </button>
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { assistantSampleGroups } from '@/features/assistant/samples'
import type { AssistantSample } from '@/features/assistant/samples'

withDefaults(defineProps<{
  disabled?: boolean
}>(), {
  disabled: false
})

const emit = defineEmits<{
  select: [sample: AssistantSample]
}>()

const activeGroup = ref(assistantSampleGroups[0]?.key || '')
</script>

<style scoped>
.assistant-sample-groups {
  display: grid;
  gap: 12px;
  width: 100%;
}

.assistant-sample-note {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid var(--el-color-primary);
  border-radius: 4px 8px 8px 4px;
  background: var(--el-color-primary-light-9);
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.5;
}

.assistant-sample-tabs {
  min-width: 0;
}

.assistant-sample-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.assistant-sample-group {
  display: grid;
  gap: 10px;
}

.assistant-group-description {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.assistant-samples {
  display: grid;
  gap: 8px;
}

.assistant-sample-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 10px;
  width: 100%;
  min-height: 64px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color 180ms ease, background-color 180ms ease;
}

.assistant-sample-card:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.assistant-sample-card:focus-visible {
  outline: 3px solid var(--el-color-primary-light-7);
  outline-offset: 2px;
}

.assistant-sample-card:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.assistant-sample-card:disabled:hover {
  border-color: var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.assistant-sample-message {
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
}

.assistant-sample-expected {
  grid-column: 1 / -1;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

@media (prefers-reduced-motion: reduce) {
  .assistant-sample-card {
    transition: none;
  }
}
</style>
