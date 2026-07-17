<template>
  <section class="resource-page workflow-start-page">
    <div class="resource-hero">
      <div>
        <h1>发起流程</h1>
        <p>从常用业务表单发起审批，提交后进入我的流程跟踪待办、已办、抄送和流转记录。</p>
      </div>
      <div class="resource-actions">
        <el-button :icon="Finished" @click="router.push('/workflow/tasks')">我的流程</el-button>
        <el-button v-if="canManageWorkflowDefinitions" type="primary" :icon="Connection" @click="router.push('/workflow/console')">流程配置</el-button>
      </div>
    </div>

    <section class="surface workflow-start-main">
      <div class="section-head">
        <div>
          <h2>业务申请</h2>
          <p>选择场景后填写业务表单，系统按流程定义自动分派审批任务。</p>
        </div>
      </div>

      <div class="workflow-card-grid">
        <button
          v-for="item in workflowApps"
          :key="item.path"
          type="button"
          class="workflow-start-card"
          :aria-label="`直接填写${item.title}`"
          @click="openWorkflowApply(item.path)"
        >
          <span :class="['workflow-start-icon', item.tone]">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <span class="workflow-start-content">
            <strong>{{ item.title }}</strong>
            <small>{{ item.description }}</small>
          </span>
          <em>{{ item.action }}</em>
        </button>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Connection, Document, Finished, ShoppingCart, Tools } from '@element-plus/icons-vue'
import { PermissionCodes } from '@/permissions/codes'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const canManageWorkflowDefinitions = computed(() => auth.hasAnyPermission([PermissionCodes.workflow.definitionEdit]))

function openWorkflowApply(path: string) {
  void router.push(path)
}

const workflowApps = [
  {
    title: '请假申请',
    description: '员工提交请假时间、原因和交接说明，按时长进入部门负责人或总经办审批。',
    action: '开始填写',
    path: '/workflow/leave',
    icon: Document,
    tone: 'is-blue'
  },
  {
    title: '采购申请',
    description: '提交采购预算、用途和供应商信息，按金额进入负责人和财务复核。',
    action: '开始填写',
    path: '/workflow/purchase',
    icon: ShoppingCart,
    tone: 'is-green'
  },
  {
    title: '报修申请',
    description: '登记设备、位置、故障描述和附件，提交后由运维处理并归档。',
    action: '开始填写',
    path: '/workflow/repair',
    icon: Tools,
    tone: 'is-amber'
  }
]
</script>

<style scoped>
.workflow-start-page {
  gap: 14px;
}

.workflow-start-main {
  padding: 18px;
}

.workflow-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
}

.workflow-start-card {
  display: grid;
  min-width: 0;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fff;
  padding: 18px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    transform 0.18s ease;
}

.workflow-start-card:hover {
  border-color: #93c5fd;
  transform: translateY(-2px);
}

.workflow-start-icon {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 8px;
  font-size: 22px;
}

.workflow-start-icon.is-blue {
  color: #2563eb;
  background: #eff6ff;
}

.workflow-start-icon.is-green {
  color: #16a34a;
  background: #f0fdf4;
}

.workflow-start-icon.is-amber {
  color: #d97706;
  background: #fffbeb;
}

.workflow-start-content {
  min-width: 0;
}

.workflow-start-card strong,
.workflow-start-card small,
.workflow-start-card em {
  display: block;
  min-width: 0;
}

.workflow-start-card strong {
  color: var(--ea-text);
  font-size: 17px;
}

.workflow-start-card small {
  margin-top: 7px;
  color: var(--ea-muted);
  font-size: 13px;
  line-height: 1.65;
}

.workflow-start-card em {
  justify-self: end;
  color: var(--ea-primary);
  font-size: 13px;
  font-style: normal;
  font-weight: 800;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .workflow-start-card {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .workflow-start-card em {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
