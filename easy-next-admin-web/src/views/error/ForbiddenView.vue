<template>
  <section class="forbidden-page">
    <div class="forbidden-shell">
      <main class="forbidden-main" aria-labelledby="forbidden-title">
        <div class="forbidden-status">
          <span class="forbidden-status__icon">
            <el-icon><Lock /></el-icon>
          </span>
          <span>访问被权限策略拦截</span>
          <el-tag effect="plain" type="warning">403</el-tag>
        </div>

        <h1 id="forbidden-title">当前账号没有访问权限</h1>
        <p class="forbidden-summary">
          你正在访问的页面未包含在当前角色授权范围内。系统已中止本次访问，页面数据不会被加载。
        </p>

        <dl class="forbidden-meta">
          <div>
            <dt>受限地址</dt>
            <dd>{{ blockedPath }}</dd>
          </div>
          <div>
            <dt>当前账号</dt>
            <dd>{{ accountLabel }}</dd>
          </div>
          <div>
            <dt>当前角色</dt>
            <dd>{{ roleLabel }}</dd>
          </div>
        </dl>

        <div class="forbidden-actions">
          <el-button :icon="RefreshLeft" @click="goBack">返回上一页</el-button>
          <el-button :icon="Refresh" :loading="refreshing" @click="refreshPermission">刷新权限</el-button>
          <el-button type="primary" :icon="House" @click="goHome">回到可访问首页</el-button>
        </div>
      </main>

      <aside class="forbidden-guide" aria-label="处理建议">
        <div class="forbidden-guide__head">
          <span>处理建议</span>
          <strong>权限调整后重新进入页面即可生效</strong>
        </div>
        <ol>
          <li>
            <span>1</span>
            <div>
              <strong>确认访问入口</strong>
              <p>如果是从旧链接进入，先返回工作台，使用左侧导航重新打开可访问页面。</p>
            </div>
          </li>
          <li>
            <span>2</span>
            <div>
              <strong>联系管理员授权</strong>
              <p>提供受限地址和当前角色，管理员可在角色权限中补充分配页面权限。</p>
            </div>
          </li>
          <li>
            <span>3</span>
            <div>
              <strong>刷新账号权限</strong>
              <p>授权完成后点击刷新权限，系统会重新拉取当前账号的菜单和按钮授权。</p>
            </div>
          </li>
        </ol>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { House, Lock, Refresh, RefreshLeft } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { firstAuthorizedPath } from '@/router/dynamicRoutes'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const refreshing = ref(false)

const fromPath = computed(() => {
  const from = route.query.from
  return typeof from === 'string' ? from : ''
})
const blockedPath = computed(() => fromPath.value || '未提供')
const accountLabel = computed(() => {
  const account = auth.user?.userName ? `（${auth.user.userName}）` : ''
  return `${auth.displayName}${account}`
})
const roleLabel = computed(() => auth.roleNames.join('、') || auth.roles.join('、') || '未分配角色')

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  goHome()
}

function goHome() {
  router.replace(firstAuthorizedPath(auth.menus))
}

async function refreshPermission() {
  refreshing.value = true
  try {
    await auth.loadProfile()
    ElMessage.success('权限信息已刷新')
    router.replace(fromPath.value || firstAuthorizedPath(auth.menus))
  } catch {
    ElMessage.error('权限刷新失败，请重新登录后再试')
  } finally {
    refreshing.value = false
  }
}
</script>

<style scoped>
.forbidden-page {
  min-height: calc(100vh - 160px);
  display: grid;
  align-items: start;
  padding: 112px 32px 32px;
  background: #f8fafc;
}

.forbidden-shell {
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr);
  gap: 18px;
  margin: 0 auto;
}

.forbidden-main,
.forbidden-guide {
  border: 1px solid #dbe3ef;
  border-radius: 10px;
  background: #fff;
}

.forbidden-main {
  padding: 34px 36px;
}

.forbidden-status {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 32px;
  color: #92400e;
  font-size: 13px;
  font-weight: 600;
}

.forbidden-status__icon {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fffbeb;
  color: #d97706;
  font-size: 16px;
}

.forbidden-status .el-tag {
  height: 24px;
  margin-left: 2px;
  font-weight: 700;
}

.forbidden-main h1 {
  margin: 22px 0 10px;
  color: #0f172a;
  font-size: 30px;
  line-height: 1.25;
}

.forbidden-summary {
  max-width: 680px;
  margin: 0;
  color: #526173;
  font-size: 15px;
  line-height: 1.8;
}

.forbidden-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 26px 0 0;
}

.forbidden-meta div {
  min-width: 0;
  padding: 13px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fbfdff;
}

.forbidden-meta dt {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.forbidden-meta dd {
  margin: 7px 0 0;
  overflow: hidden;
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.forbidden-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}

.forbidden-actions .el-button {
  min-width: 126px;
}

.forbidden-guide {
  padding: 24px;
}

.forbidden-guide__head {
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.forbidden-guide__head span,
.forbidden-guide__head strong {
  display: block;
}

.forbidden-guide__head span {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
}

.forbidden-guide__head strong {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
}

.forbidden-guide ol {
  display: grid;
  gap: 16px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.forbidden-guide li {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 12px;
}

.forbidden-guide li > span {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.forbidden-guide li strong {
  display: block;
  color: #1f2937;
  font-size: 14px;
  line-height: 1.4;
}

.forbidden-guide li p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
}

@media (max-width: 960px) {
  .forbidden-page {
    align-items: stretch;
    padding: 20px;
  }

  .forbidden-shell {
    grid-template-columns: 1fr;
  }

  .forbidden-main {
    padding: 28px;
  }
}

@media (max-width: 640px) {
  .forbidden-page {
    padding: 14px;
  }

  .forbidden-main,
  .forbidden-guide {
    border-radius: 8px;
  }

  .forbidden-main {
    padding: 22px;
  }

  .forbidden-main h1 {
    font-size: 24px;
  }

  .forbidden-meta {
    grid-template-columns: 1fr;
  }

  .forbidden-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
