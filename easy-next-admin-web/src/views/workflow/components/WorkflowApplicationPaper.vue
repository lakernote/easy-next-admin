<template>
  <div class="workflow-application-paper application-paper-sheet">
    <div class="application-paper-watermark" aria-hidden="true">{{ eyebrow }}</div>

    <div class="application-paper-title">
      <span>{{ eyebrow }}</span>
      <strong>{{ title }}</strong>
    </div>

    <div class="application-paper-meta">
      <div v-for="item in meta" :key="item.key || item.label">
        <span>{{ item.label }}</span>
        <strong v-if="item.tone" :class="['application-paper-status', `is-${item.tone}`]">{{ item.value || '-' }}</strong>
        <strong v-else>{{ item.value || '-' }}</strong>
      </div>
    </div>

    <div v-if="fields.length" class="application-paper-grid">
      <div v-for="field in fields" :key="field.key" :class="paperFieldClass(field)">
        <span>{{ field.label }}</span>
        <slot :name="`field-${field.key}`" :field="field">
          <strong>{{ field.value || '-' }}</strong>
        </slot>
      </div>
    </div>

    <slot name="attachments"></slot>

    <div v-if="approvals.length || emptyApprovalText" class="application-paper-signatures">
      <div class="application-paper-signature-title">审批记录</div>
      <div v-if="approvals.length" class="application-paper-signature-grid">
        <div
          v-for="item in approvals"
          :key="item.id"
          :class="['application-paper-signature-cell', item.tone ? `is-${item.tone}` : '']"
        >
          <span>{{ item.nodeName }}</span>
          <strong>{{ item.actor || '-' }}</strong>
          <small>{{ approvalStatusLine(item) }}</small>
        </div>
      </div>
      <div v-else class="application-paper-empty-signature">{{ emptyApprovalText }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface PaperMetaItem {
  key?: string
  label: string
  value?: string
  tone?: 'running' | 'approved' | 'rejected' | 'ended' | 'draft'
}

interface PaperFieldItem {
  key: string
  label: string
  value?: string
  wide?: boolean
  span?: 2 | 3 | 4
}

interface PaperApprovalItem {
  id: string
  nodeName: string
  actor?: string
  statusText?: string
  timeText?: string
  tone?: 'done' | 'current' | 'danger' | 'todo'
}

withDefaults(defineProps<{
  eyebrow?: string
  title: string
  meta: PaperMetaItem[]
  fields?: PaperFieldItem[]
  approvals?: PaperApprovalItem[]
  emptyApprovalText?: string
}>(), {
  eyebrow: '业务申请单',
  fields: () => [],
  approvals: () => [],
  emptyApprovalText: ''
})

function paperFieldClass(field: PaperFieldItem) {
  return {
    'is-wide': field.wide,
    'is-span-2': field.span === 2,
    'is-span-3': field.span === 3,
    'is-span-4': field.span === 4
  }
}

function approvalStatusLine(item: PaperApprovalItem) {
  const parts = [item.statusText, item.timeText].filter((part) => part && part !== '-')
  return parts.length ? parts.join(' · ') : '-'
}
</script>

<style scoped>
.workflow-application-paper {
  position: relative;
  max-width: 980px;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid #2a1d14;
  border-radius: 2px;
  background: #fffaf0;
  color: #24160d;
  font-family: "Songti SC", "STSong", "SimSun", "Noto Serif CJK SC", serif;
  font-variant-numeric: tabular-nums;
}

.workflow-application-paper::before {
  display: none;
  content: none;
}

.workflow-application-paper::after {
  display: none;
  content: none;
}

.workflow-application-paper > * {
  position: relative;
  z-index: 1;
}

.application-paper-watermark {
  position: absolute;
  top: 46%;
  left: 50%;
  z-index: 0;
  color: rgba(136, 31, 31, 0.018);
  font-size: 50px;
  font-weight: 800;
  letter-spacing: 14px;
  pointer-events: none;
  transform: translate(-50%, -50%) rotate(-23deg);
  white-space: nowrap;
}

.application-paper-title {
  display: grid;
  gap: 7px;
  justify-items: center;
  border-bottom: 1px solid rgba(155, 28, 31, 0.86);
  padding: 26px 44px 24px;
  background: rgba(255, 250, 240, 0.72);
  text-align: center;
}

.application-paper-title span {
  width: min(560px, 100%);
  border-top: 2px solid rgba(155, 28, 31, 0.95);
  border-bottom: 1px solid rgba(155, 28, 31, 0.32);
  padding: 5px 0;
  color: #6a4526;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 2px;
  line-height: 1.35;
}

.application-paper-title strong {
  max-width: 100%;
  color: #231815;
  font-size: 25px;
  font-weight: 800;
  line-height: 1.35;
  letter-spacing: 2px;
  word-break: keep-all;
  overflow-wrap: normal;
}

.application-paper-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-bottom: 1px solid rgba(42, 29, 20, 0.9);
  background: rgb(255 250 240 / 82%);
}

.application-paper-meta > div,
.application-paper-grid > div,
.application-paper-signature-cell {
  min-width: 0;
  border-right: 1px solid rgba(42, 29, 20, 0.9);
  padding: 14px 17px;
}

.application-paper-meta > div:last-child,
.application-paper-grid > div:nth-child(4n),
.application-paper-signature-cell:last-child {
  border-right: 0;
}

.application-paper-meta span,
.application-paper-grid span,
.application-paper-signature-cell span {
  display: block;
  color: #60411f;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  white-space: nowrap;
}

.application-paper-meta strong,
.application-paper-grid strong,
.application-paper-signature-cell strong {
  display: block;
  margin-top: 7px;
  color: #231815;
  font-weight: 800;
  line-height: 1.55;
  word-break: keep-all;
  overflow: hidden;
  overflow-wrap: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.application-paper-meta strong {
  font-size: 15px;
}

.application-paper-grid strong {
  font-size: 15px;
}

.application-paper-status {
  width: fit-content;
  border: 1px solid currentColor;
  border-radius: 2px;
  padding: 2px 8px;
  background: rgba(255, 250, 240, 0.72);
  font-size: 14px;
  white-space: nowrap;
}

.application-paper-status.is-running {
  color: #9a5b00;
}

.application-paper-status.is-approved {
  color: #1f7a3f;
}

.application-paper-status.is-rejected {
  color: #9b1c1f;
}

.application-paper-status.is-ended,
.application-paper-status.is-draft {
  color: #60411f;
}

.application-paper-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.application-paper-grid > div {
  min-height: 78px;
  border-bottom: 1px solid rgba(42, 29, 20, 0.9);
  background: rgb(255 250 240 / 64%);
}

.application-paper-grid > div.is-wide {
  grid-column: 1 / -1;
  border-right: 0;
}

.application-paper-grid > div.is-span-2 {
  grid-column: span 2;
}

.application-paper-grid > div.is-span-3 {
  grid-column: span 3;
  border-right: 0;
}

.application-paper-grid > div.is-span-4 {
  grid-column: 1 / -1;
  border-right: 0;
}

.application-paper-signatures {
  border-top: 0;
  background: rgb(255 250 240 / 82%);
}

.application-paper-signature-title {
  border-top: 1px solid rgba(42, 29, 20, 0.9);
  border-bottom: 1px solid rgba(42, 29, 20, 0.9);
  padding: 13px 18px;
  color: #7a1b1d;
  font-size: 14px;
  font-weight: 800;
  background: rgba(155, 28, 31, 0.07);
}

.application-paper-signature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
}

.application-paper-signature-cell {
  min-height: 94px;
  display: grid;
  align-content: start;
  gap: 7px;
  background: transparent;
}

.application-paper-signature-cell small {
  display: block;
  margin-top: 0;
  color: #6a4526;
  font-size: 13px;
  line-height: 1.55;
}

.application-paper-signature-cell.is-current small {
  color: #9a5b00;
}

.application-paper-signature-cell.is-danger small {
  color: #9b1c1f;
}

.application-paper-empty-signature {
  padding: 14px 16px;
  color: #6a4526;
  font-size: 13px;
}

:slotted(.paper-form-item) {
  margin: 7px 0 0;
}

:slotted(.paper-form-item .el-form-item__label) {
  display: none;
}

:slotted(.paper-form-item .el-input__wrapper),
:slotted(.paper-form-item .el-select__wrapper),
:slotted(.paper-form-item .el-textarea__inner) {
  border-radius: 0 !important;
  background-color: rgba(255, 250, 240, 0.72) !important;
}

:deep(.paper-form-item .el-input__wrapper),
:deep(.paper-form-item .el-select__wrapper),
:deep(.paper-form-item .el-input-number .el-input__wrapper) {
  min-height: 30px;
  border-radius: 0 !important;
  border-bottom: 1px solid rgba(96, 65, 31, 0.48);
  padding: 0 4px;
  background: transparent !important;
  --el-input-border-color: transparent;
  --el-input-hover-border-color: transparent;
  --el-input-focus-border-color: transparent;
}

:deep(.paper-form-item .el-input__inner),
:deep(.paper-form-item .el-select__placeholder),
:deep(.paper-form-item .el-select__selected-item),
:deep(.paper-form-item .el-input-number .el-input__inner) {
  color: #24160d;
  font-size: 14px;
  font-weight: 800;
}

:deep(.paper-form-item .el-textarea__inner) {
  border-radius: 0 !important;
  border: 1px solid rgba(139, 111, 84, 0.42);
  background-color: #fffaf0 !important;
  color: #24160d;
  font-weight: 700;
  line-height: 30px;
  resize: none;
  --el-input-border-color: rgba(139, 111, 84, 0.42);
  --el-input-hover-border-color: #8b6f54;
  --el-input-focus-border-color: #9b1c1f;
}

:deep(.paper-form-item.is-error .el-input__wrapper),
:deep(.paper-form-item.is-error .el-select__wrapper),
:deep(.paper-form-item.is-error .el-input-number .el-input__wrapper) {
  border-bottom-color: rgba(155, 28, 31, 0.58);
}

:deep(.paper-form-item.is-error .el-textarea__inner) {
  border-color: rgba(155, 28, 31, 0.5);
}

:slotted(.full-width) {
  width: 100%;
}

:slotted(.paper-field-help) {
  margin: 6px 0 0;
  color: #6a4526;
  font-size: 12px;
  line-height: 1.45;
}

@media (max-width: 560px) {
  .application-paper-meta,
  .application-paper-grid,
  .application-paper-signature-grid {
    grid-template-columns: 1fr;
  }

  .application-paper-title {
    padding-inline: 24px;
  }

  .application-paper-meta > div,
  .application-paper-grid > div,
  .application-paper-signature-cell,
  .application-paper-grid > div:nth-child(4n) {
    border-right: 0;
    border-bottom: 1px solid rgba(42, 29, 20, 0.9);
  }

  .application-paper-grid > div.is-wide,
  .application-paper-grid > div.is-span-2,
  .application-paper-grid > div.is-span-3,
  .application-paper-grid > div.is-span-4 {
    grid-column: 1 / -1;
  }

  .application-paper-signature-cell:last-child,
  .application-paper-grid > div:last-child {
    border-bottom: 0;
  }

  .application-paper-title strong {
    font-size: 20px;
    letter-spacing: 1px;
  }
}
</style>
