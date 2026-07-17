<template>
  <section class="api-docs-page">
    <header class="api-docs-header">
      <div>
        <h1>接口文档</h1>
        <p>面向二次开发和联调，统一查看 OpenAPI 地址、鉴权方式和业务接口分组。</p>
      </div>
      <div class="api-docs-actions">
        <el-button :icon="Open" @click="openSwaggerInNewWindow">新窗口打开 Swagger</el-button>
        <el-button type="primary" :icon="View" @click="showSwagger = !showSwagger">
          {{ showSwagger ? '收起 Swagger' : '当前页查看 Swagger' }}
        </el-button>
      </div>
    </header>

    <div class="api-docs-overview">
      <article v-for="item in docCards" :key="item.title">
        <span>{{ item.label }}</span>
        <strong>{{ item.title }}</strong>
        <p>{{ item.description }}</p>
      </article>
    </div>

    <section class="api-docs-panel">
      <div class="api-docs-panel-head">
        <div>
          <h2>接口联调入口</h2>
          <p>建议日常开发优先按模块查看接口，原生 Swagger 作为调试辅助入口。</p>
        </div>
        <el-tag effect="plain">OpenAPI 3.1</el-tag>
      </div>
      <div class="api-docs-link-grid">
        <a v-for="item in apiLinks" :key="item.href" :href="item.href" target="_blank" rel="noreferrer">
          <strong>{{ item.title }}</strong>
          <span>{{ item.href }}</span>
        </a>
      </div>
    </section>

    <div v-if="showSwagger" class="api-docs-frame-shell">
      <div class="api-docs-frame-head">
        <strong>Swagger 调试视图</strong>
        <span>原生页面来自后端 springdoc，仅作为接口调试补充。</span>
      </div>
      <iframe class="api-docs-frame" title="OpenAPI Swagger UI" :src="swaggerUrl"></iframe>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Open, View } from '@element-plus/icons-vue'

const swaggerUrl = '/swagger-ui.html'
const showSwagger = ref(false)

const docCards = [
  { label: '鉴权', title: 'Bearer Token', description: '登录后携带 Authorization 请求业务接口。' },
  { label: '响应', title: '统一 Response', description: '后端接口统一返回 code、message、data。' },
  { label: '分页', title: 'PageResponse', description: '列表接口统一使用 page、limit 和 total。' },
  { label: '分组', title: '按模块维护', description: '系统、运行监控、流程、审计等模块独立查看。' }
]

const apiLinks = [
  { title: 'Swagger UI', href: swaggerUrl },
  { title: 'OpenAPI JSON', href: '/v3/api-docs' },
  { title: '系统管理分组', href: '/v3/api-docs/1.system' }
]

function openSwaggerInNewWindow() {
  window.open(swaggerUrl, '_blank', 'noopener,noreferrer')
}
</script>

<style scoped>
.api-docs-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.api-docs-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  padding: 18px;
  background: #fff;
}

.api-docs-header h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
  line-height: 1.25;
}

.api-docs-header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

.api-docs-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.api-docs-overview,
.api-docs-link-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
  gap: 12px;
}

.api-docs-overview article,
.api-docs-panel,
.api-docs-frame-shell {
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  background: #fff;
}

.api-docs-overview article {
  padding: 14px 16px;
}

.api-docs-overview span,
.api-docs-overview strong,
.api-docs-overview p {
  display: block;
}

.api-docs-overview span {
  color: #64748b;
  font-size: 12px;
}

.api-docs-overview strong {
  margin-top: 8px;
  color: #0f172a;
  font-size: 18px;
}

.api-docs-overview p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.api-docs-panel {
  padding: 16px;
}

.api-docs-panel-head,
.api-docs-frame-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.api-docs-panel-head {
  margin-bottom: 14px;
}

.api-docs-panel h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.api-docs-panel p,
.api-docs-frame-head span {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.api-docs-link-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.api-docs-link-grid a {
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  color: inherit;
  text-decoration: none;
  background: #f8fafc;
}

.api-docs-link-grid a:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.api-docs-link-grid strong,
.api-docs-link-grid span {
  display: block;
}

.api-docs-link-grid strong {
  color: #0f172a;
}

.api-docs-link-grid span {
  margin-top: 6px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.api-docs-frame-shell {
  flex: 1 1 auto;
  overflow: hidden;
}

.api-docs-frame-head {
  border-bottom: 1px solid #e2e8f0;
  padding: 12px 14px;
  background: #f8fafc;
}

.api-docs-frame {
  width: 100%;
  height: 100%;
  min-height: 640px;
  border: 0;
  background: #fff;
}

@media (max-width: 720px) {
  .api-docs-header {
    flex-direction: column;
  }

  .api-docs-overview,
  .api-docs-link-grid {
    grid-template-columns: 1fr;
  }

  .api-docs-frame-shell,
  .api-docs-frame {
    min-height: 560px;
  }
}
</style>
