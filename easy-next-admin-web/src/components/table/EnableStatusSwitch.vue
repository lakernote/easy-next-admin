<template>
  <span class="enable-status-switch">
    <el-tooltip :disabled="!switchTip" :content="switchTip" placement="top">
      <span class="enable-status-switch__target">
        <button
          class="enable-status-switch__button"
          :class="{
            'is-active': modelValue,
            'is-inactive': !modelValue,
            'is-loading': loading,
            'is-disabled': switchDisabled
          }"
          type="button"
          role="switch"
          :aria-checked="modelValue"
          :aria-disabled="switchDisabled"
          :disabled="loading"
          :aria-label="ariaLabel || `${modelValue ? inactiveActionLabel : activeActionLabel}${targetName || ''}`"
          :title="modelValue ? activeLabel : inactiveLabel"
          @click="handleClick"
        >
          <span class="enable-status-switch__track" aria-hidden="true">
            <span class="enable-status-switch__thumb">
              <span v-if="loading" class="enable-status-switch__spinner" />
            </span>
          </span>
          <span class="enable-status-switch__label">
            {{ modelValue ? activeLabel : inactiveLabel }}
          </span>
        </button>
      </span>
    </el-tooltip>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    loading?: boolean
    disabled?: boolean
    readonly?: boolean
    disabledReason?: string
    targetName?: string
    ariaLabel?: string
    activeLabel?: string
    inactiveLabel?: string
    activeActionLabel?: string
    inactiveActionLabel?: string
  }>(),
  {
    loading: false,
    disabled: false,
    readonly: false,
    disabledReason: '',
    targetName: '',
    ariaLabel: '',
    activeLabel: '启用',
    inactiveLabel: '停用',
    activeActionLabel: '启用',
    inactiveActionLabel: '停用'
  }
)

const emit = defineEmits<{
  toggle: []
}>()

const switchDisabled = computed(() => props.disabled || props.readonly || props.loading)
const switchLocked = computed(() => props.disabled || props.readonly)
const switchTip = computed(() => (switchLocked.value ? props.disabledReason : ''))

function handleClick() {
  if (props.loading) return
  if (switchLocked.value) {
    if (props.disabledReason) {
      ElMessage.warning(props.disabledReason)
    }
    return
  }
  emit('toggle')
}
</script>

<style scoped>
.enable-status-switch {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  line-height: 1;
  vertical-align: middle;
}

.enable-status-switch__target {
  display: inline-flex;
  align-items: center;
}

.enable-status-switch__button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 32px;
  padding: 4px 6px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--ea-muted);
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--ea-motion-fast),
    color var(--ea-motion-fast),
    opacity var(--ea-motion-fast);
}

.enable-status-switch__track {
  position: relative;
  width: 38px;
  height: 22px;
  flex: 0 0 38px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: #cbd5e1;
  transition:
    background-color var(--ea-motion-fast),
    border-color var(--ea-motion-fast),
    outline-color var(--ea-motion-fast);
}

.enable-status-switch__thumb {
  position: absolute;
  top: 50%;
  left: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #ffffff;
  transform: translateY(-50%);
  transition: transform var(--ea-motion-fast);
}

.enable-status-switch__label {
  display: inline-flex;
  align-items: center;
  height: 20px;
  min-width: 26px;
  line-height: 20px;
  text-align: left;
}

.enable-status-switch__button.is-active {
  color: #15803d;
}

.enable-status-switch__button.is-active .enable-status-switch__track {
  background: #22c55e;
  border-color: #22c55e;
}

.enable-status-switch__button.is-active .enable-status-switch__thumb {
  transform: translate(18px, -50%);
}

.enable-status-switch__button.is-inactive {
  color: #64748b;
}

.enable-status-switch__button:hover:not(:disabled) {
  background: transparent;
  outline: none;
}

.enable-status-switch__button:hover:not(:disabled) .enable-status-switch__track {
  outline: 2px solid rgba(15, 23, 42, 0.04);
}

.enable-status-switch__button.is-active:hover:not(:disabled) .enable-status-switch__track {
  background: #16a34a;
  border-color: #16a34a;
  outline: 2px solid rgba(34, 197, 94, 0.1);
}

.enable-status-switch__button.is-inactive:hover:not(:disabled) .enable-status-switch__track {
  background: #94a3b8;
  border-color: #94a3b8;
  outline: 2px solid rgba(100, 116, 139, 0.1);
}

.enable-status-switch__button:focus-visible {
  background: transparent;
  outline: none;
}

.enable-status-switch__button:focus-visible .enable-status-switch__track {
  outline: 2px solid rgba(37, 99, 235, 0.18);
}

.enable-status-switch__button.is-disabled,
.enable-status-switch__button:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}

.enable-status-switch__spinner {
  width: 10px;
  height: 10px;
  border: 2px solid rgba(37, 99, 235, 0.18);
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: enable-status-spin 0.8s linear infinite;
}

@keyframes enable-status-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .enable-status-switch,
  .enable-status-switch__button {
    min-height: 44px;
  }
}
</style>
