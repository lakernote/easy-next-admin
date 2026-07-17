<template>
  <section class="report-page">
    <div class="resource-hero report-hero">
      <div>
        <h1>报表中心</h1>
        <p>按当前账号数据范围生成组织台账和采购复核纸质报表。</p>
      </div>
      <div class="resource-actions">
        <el-button :icon="RefreshRight" :loading="loading" @click="loadReport">刷新</el-button>
        <el-button
          v-permission:disable="{ permissions: PermissionCodes.report.view, reason: '缺少报表查看权限' }"
          type="primary"
          :icon="Printer"
          :disabled="!overview"
          @click="printReport"
        >
          打印
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="errorMessage && !overview"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="inline-error"
    >
      <template #default>
        <el-button type="primary" :icon="RefreshRight" :loading="loading" @click="loadReport">重新加载</el-button>
      </template>
    </el-alert>

    <div v-if="initialLoading" class="surface report-loading">
      <el-skeleton :rows="16" animated />
    </div>

    <template v-else-if="overview">
      <section class="surface report-control-bar">
        <el-radio-group v-model="activeReport" size="large">
          <el-radio-button value="organization">组织人员台账</el-radio-button>
          <el-radio-button value="purchase">采购流程复核</el-radio-button>
        </el-radio-group>
        <div class="report-control-meta">
          <span>{{ overview.reportPeriod }}</span>
          <strong>{{ overview.dataScopeLabel }}</strong>
        </div>
      </section>

      <div class="report-paper-stage">
        <article v-if="activeReport === 'organization'" class="report-sheet is-a4">
          <div class="report-binding" aria-hidden="true"></div>
          <div class="report-watermark" aria-hidden="true">内部报表</div>
          <div class="report-red-stamp" aria-hidden="true">
            <span>易企</span>
            <strong>报表专用章</strong>
          </div>

          <header class="report-sheet-head">
            <div class="report-confidential">内部资料 注意保管</div>
            <span class="report-org-name">{{ overview.organizationName }}</span>
            <h2>组织人员台账报表</h2>
            <small>{{ overview.organizationLedger.reportNo }}</small>
          </header>

          <dl class="report-meta-grid">
            <div>
              <dt>报表期间</dt>
              <dd>{{ overview.reportPeriod }}</dd>
            </div>
            <div>
              <dt>生成时间</dt>
              <dd>{{ overview.generatedAt }}</dd>
            </div>
            <div>
              <dt>制表人</dt>
              <dd>{{ overview.preparedBy }}</dd>
            </div>
            <div>
              <dt>数据口径</dt>
              <dd>{{ overview.dataScopeLabel }}</dd>
            </div>
          </dl>

          <section class="report-instruction-row">
            <strong>填报说明</strong>
            <span>本表用于组织人员月度核验，按当前账号数据范围自动汇总，纸质归档时需经制表、复核、负责人签批后留存。</span>
          </section>

          <div class="report-summary-strip">
            <div v-for="metric in overview.organizationLedger.metrics" :key="metric.label">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </div>
          </div>

          <table class="report-official-table organization-report-table">
            <colgroup>
              <col class="col-seq" />
              <col class="col-dept" />
              <col class="col-person" />
              <col class="col-count" />
              <col class="col-count" />
              <col class="col-manager" />
              <col class="col-position" />
              <col class="col-login" />
            </colgroup>
            <thead>
              <tr>
                <th>序号</th>
                <th>部门</th>
                <th>负责人</th>
                <th>人数</th>
                <th>启用</th>
                <th>直属上级</th>
                <th>岗位分布</th>
                <th>登录情况</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in overview.organizationLedger.rows" :key="row.index">
                <td>{{ row.index }}</td>
                <td>{{ row.departmentName }}</td>
                <td>{{ row.leaderName }}</td>
                <td>{{ row.userCount }}</td>
                <td>{{ row.enabledCount }}</td>
                <td>{{ row.managerSummary }}</td>
                <td>{{ row.positionSummary }}</td>
                <td>{{ row.lastLoginSummary }}</td>
              </tr>
            </tbody>
          </table>

          <footer class="report-signature-grid">
            <div v-for="cell in overview.organizationLedger.signatures" :key="cell.label">
              <span>{{ cell.label }}</span>
              <strong>{{ cell.value || '' }}</strong>
            </div>
          </footer>
        </article>

        <article v-else class="report-sheet is-a4">
          <div class="report-binding" aria-hidden="true"></div>
          <div class="report-watermark" aria-hidden="true">复核留痕</div>
          <div class="report-red-stamp" aria-hidden="true">
            <span>易企</span>
            <strong>流程复核章</strong>
          </div>

          <header class="report-sheet-head">
            <div class="report-confidential">内部资料 注意保管</div>
            <span class="report-org-name">{{ overview.organizationName }}</span>
            <h2>采购流程复核报表</h2>
            <small>{{ overview.purchaseReview.reportNo }}</small>
          </header>

          <dl class="report-meta-grid">
            <div>
              <dt>报表期间</dt>
              <dd>{{ overview.reportPeriod }}</dd>
            </div>
            <div>
              <dt>生成时间</dt>
              <dd>{{ overview.generatedAt }}</dd>
            </div>
            <div>
              <dt>制表人</dt>
              <dd>{{ overview.preparedBy }}</dd>
            </div>
            <div>
              <dt>数据口径</dt>
              <dd>{{ overview.dataScopeLabel }}</dd>
            </div>
          </dl>

          <section class="report-instruction-row">
            <strong>复核口径</strong>
            <span>本表按可见采购流程记录汇总，重点核对预算金额、当前节点和要求到货日期，打印件用于财务与内控复核留档。</span>
          </section>

          <div class="report-summary-strip">
            <div v-for="metric in overview.purchaseReview.metrics" :key="metric.label">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.label === '预算金额' ? formatCurrency(metric.value) : metric.value }}</strong>
            </div>
          </div>

          <table class="report-official-table purchase-report-table">
            <colgroup>
              <col class="col-seq" />
              <col class="col-request" />
              <col class="col-applicant" />
              <col class="col-dept" />
              <col class="col-item" />
              <col class="col-category" />
              <col class="col-count" />
              <col class="col-money" />
              <col class="col-date" />
              <col class="col-status" />
              <col class="col-node" />
            </colgroup>
            <thead>
              <tr>
                <th>序号</th>
                <th>单号</th>
                <th>申请人</th>
                <th>部门</th>
                <th>采购事项</th>
                <th>类别</th>
                <th>数量</th>
                <th>预算金额</th>
                <th>要求日期</th>
                <th>状态</th>
                <th>当前节点</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in overview.purchaseReview.rows" :key="row.requestNo">
                <td>{{ row.index }}</td>
                <td>{{ row.requestNo }}</td>
                <td>{{ row.applicantName }}</td>
                <td>{{ row.departmentName }}</td>
                <td>{{ row.itemName }}</td>
                <td>{{ row.category }}</td>
                <td>{{ row.quantity }}</td>
                <td>{{ formatCurrency(row.estimatedAmount) }}</td>
                <td>{{ row.requiredDate }}</td>
                <td>{{ row.statusText }}</td>
                <td>{{ row.currentNodeName }}</td>
              </tr>
            </tbody>
          </table>

          <footer class="report-signature-grid">
            <div v-for="cell in overview.purchaseReview.signatures" :key="cell.label">
              <span>{{ cell.label }}</span>
              <strong>{{ cell.value || '' }}</strong>
            </div>
          </footer>
        </article>
      </div>
    </template>

    <el-empty v-else description="暂无报表数据" class="report-empty" />
  </section>
</template>

<script setup lang="ts">
import { Printer, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { getEnterprisePaperReport } from '@/features/report/api'
import type { EnterpriseReportOverview } from '@/features/report/types'
import { PermissionCodes } from '@/permissions/codes'

type ReportKind = 'organization' | 'purchase'

const overview = ref<EnterpriseReportOverview>()
const activeReport = ref<ReportKind>('organization')
const loading = ref(false)
const errorMessage = ref('')
const initialLoading = computed(() => loading.value && !overview.value)

onMounted(loadReport)

async function loadReport() {
  loading.value = true
  errorMessage.value = ''
  try {
    overview.value = await getEnterprisePaperReport()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '报表加载失败'
  } finally {
    loading.value = false
  }
}

function printReport() {
  if (!overview.value) {
    ElMessage.warning('报表数据尚未加载')
    return
  }
  window.print()
}

function formatCurrency(value?: string | number) {
  const numeric = Number(value || 0)
  return numeric.toLocaleString('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    maximumFractionDigits: 2
  })
}
</script>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: auto;
  min-height: 100%;
  min-width: 0;
}

.report-hero {
  align-items: center;
}

.report-control-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
}

.report-control-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ea-muted);
  font-size: 13px;
}

.report-control-meta strong {
  border: 1px solid #cbd5e1;
  border-radius: 2px;
  padding: 3px 8px;
  color: #334155;
  background: #fff;
}

.report-loading {
  min-height: 520px;
}

.report-paper-stage {
  min-width: 0;
  display: flex;
  justify-content: center;
  overflow-x: auto;
  padding: 18px 20px 40px;
  border: 1px solid #d8cdbd;
  border-radius: 8px;
  background: #eee6d8;
}

.report-sheet {
  position: relative;
  box-sizing: border-box;
  width: min(100%, 210mm);
  min-width: 760px;
  min-height: 297mm;
  border: 1px solid #231815;
  background: #fffaf0;
  color: #24160d;
  font-family: "Songti SC", "STSong", "SimSun", "Noto Serif CJK SC", serif;
  font-variant-numeric: tabular-nums;
}

.report-sheet.is-a4 {
  padding: 18mm 16mm 17mm 18mm;
}

.report-sheet::before {
  position: absolute;
  inset: 7mm 8mm 8mm 9mm;
  border: 1px solid rgba(92, 54, 27, 0.46);
  content: "";
  pointer-events: none;
}

.report-sheet::after {
  position: absolute;
  inset: 10mm 11mm 11mm 12mm;
  border: 1px solid rgba(150, 32, 32, 0.18);
  content: "";
  pointer-events: none;
}

.report-sheet > * {
  position: relative;
  z-index: 1;
}

.report-binding {
  position: absolute;
  top: 17mm;
  bottom: 17mm;
  left: 7.5mm;
  width: 7px;
  z-index: 0;
  border-left: 2px solid rgba(136, 31, 31, 0.72);
  border-right: 1px dashed rgba(117, 77, 40, 0.35);
}

.report-binding::before,
.report-binding::after {
  position: absolute;
  left: -4px;
  width: 9px;
  height: 9px;
  border: 1px solid rgba(117, 77, 40, 0.48);
  border-radius: 50%;
  background: #fffaf0;
  content: "";
}

.report-binding::before {
  top: 60mm;
}

.report-binding::after {
  bottom: 60mm;
}

.report-watermark {
  position: absolute;
  top: 104mm;
  left: 50%;
  z-index: 0;
  color: rgba(136, 31, 31, 0.055);
  font-size: 72px;
  font-weight: 800;
  letter-spacing: 18px;
  pointer-events: none;
  transform: translateX(-50%) rotate(-24deg);
  white-space: nowrap;
}

.report-red-stamp {
  position: absolute;
  top: 31mm;
  right: 22mm;
  z-index: 3;
  width: 31mm;
  height: 31mm;
  display: grid;
  place-items: center;
  border: 2px solid rgba(180, 31, 36, 0.72);
  border-radius: 50%;
  color: rgba(180, 31, 36, 0.78);
  text-align: center;
  transform: rotate(-11deg);
}

.report-red-stamp::before {
  position: absolute;
  inset: 3.3mm;
  border: 1px solid rgba(180, 31, 36, 0.48);
  border-radius: 50%;
  content: "";
}

.report-red-stamp span {
  display: block;
  margin-top: 2px;
  font-size: 17px;
  font-weight: 900;
  line-height: 1;
}

.report-red-stamp strong {
  display: block;
  margin-top: 4px;
  font-size: 10px;
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: 1px;
}

.report-sheet-head {
  display: grid;
  gap: 5px;
  justify-items: center;
  border-bottom: 3px double #9b1c1f;
  padding: 0 34mm 15px 34mm;
  text-align: center;
}

.report-confidential {
  justify-self: stretch;
  border-top: 2px solid #9b1c1f;
  border-bottom: 1px solid rgba(155, 28, 31, 0.55);
  padding: 4px 0;
  color: #9b1c1f;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 8px;
}

.report-sheet-head .report-org-name {
  color: #5b3b21;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 2px;
}

.report-sheet-head h2 {
  margin: 0;
  color: #231815;
  font-size: 29px;
  font-weight: 800;
  letter-spacing: 4px;
}

.report-sheet-head small {
  color: #6c4c2f;
  font-family: var(--ea-code-font);
  font-size: 12px;
}

.report-meta-grid {
  display: grid;
  grid-template-columns: 1.05fr 1.48fr 1fr 1.34fr;
  margin: 15px 0 0;
  border-top: 1px solid #231815;
  border-left: 1px solid #231815;
  font-size: 11px;
}

.report-meta-grid div {
  min-width: 0;
  display: grid;
  grid-template-columns: 60px minmax(0, 1fr);
  border-right: 1px solid #231815;
  border-bottom: 1px solid #231815;
}

.report-meta-grid dt,
.report-meta-grid dd {
  min-width: 0;
  margin: 0;
  padding: 8px 7px;
  line-height: 1.45;
}

.report-meta-grid dt {
  border-right: 1px solid #231815;
  background: rgba(119, 74, 32, 0.1);
  color: #432818;
  font-weight: 700;
  white-space: nowrap;
}

.report-meta-grid dd {
  color: #24160d;
  white-space: nowrap;
  overflow-wrap: normal;
}

.report-instruction-row {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  margin-top: 12px;
  border: 1px solid #231815;
  color: #24160d;
  font-size: 12px;
  line-height: 1.55;
}

.report-instruction-row strong {
  display: grid;
  place-items: center;
  border-right: 1px solid #231815;
  background: rgba(155, 28, 31, 0.08);
  color: #7a1b1d;
  font-weight: 800;
  white-space: nowrap;
}

.report-instruction-row span {
  padding: 8px 10px;
}

.report-summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 12px;
  border: 1px solid #231815;
  border-right: 0;
}

.report-summary-strip div {
  min-width: 0;
  border-right: 1px solid #231815;
  padding: 10px 12px;
  background: rgba(155, 28, 31, 0.045);
}

.report-summary-strip span,
.report-summary-strip strong {
  display: block;
}

.report-summary-strip span {
  color: #60411f;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.report-summary-strip strong {
  margin-top: 4px;
  color: #231815;
  font-size: 18px;
  line-height: 1.25;
  white-space: nowrap;
  overflow-wrap: normal;
}

.report-official-table {
  width: 100%;
  margin-top: 14px;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 11px;
  line-height: 1.38;
  background: rgba(255, 250, 240, 0.82);
}

.report-official-table th,
.report-official-table td {
  border: 1px solid #231815;
  padding: 6px 7px;
  vertical-align: middle;
  word-break: keep-all;
  overflow-wrap: normal;
}

.report-official-table th {
  background: rgba(119, 74, 32, 0.13);
  color: #231815;
  font-weight: 800;
  text-align: center;
  white-space: nowrap;
}

.report-official-table tbody tr:nth-child(even) td {
  background: rgba(119, 74, 32, 0.035);
}

.report-official-table td:first-child,
.report-official-table td:nth-child(4),
.report-official-table td:nth-child(5),
.report-official-table td:nth-child(7),
.report-official-table td:nth-child(8) {
  text-align: center;
}

.organization-report-table .col-seq {
  width: 8%;
}

.organization-report-table .col-dept {
  width: 14%;
}

.organization-report-table .col-person {
  width: 16%;
}

.organization-report-table .col-count {
  width: 8%;
}

.organization-report-table .col-manager {
  width: 15%;
}

.organization-report-table .col-position {
  width: 16%;
}

.organization-report-table .col-login {
  width: 15%;
}

.purchase-report-table .col-seq {
  width: 6%;
}

.purchase-report-table .col-request {
  width: 12%;
}

.purchase-report-table .col-applicant {
  width: 10%;
}

.purchase-report-table .col-dept {
  width: 11%;
}

.purchase-report-table .col-item {
  width: 15%;
}

.purchase-report-table .col-category {
  width: 8%;
}

.purchase-report-table .col-count {
  width: 6%;
}

.purchase-report-table .col-money {
  width: 11%;
}

.purchase-report-table .col-date {
  width: 9%;
}

.purchase-report-table .col-status {
  width: 6%;
}

.purchase-report-table .col-node {
  width: 6%;
}

.report-official-table td:first-child,
.report-official-table td:nth-child(4),
.report-official-table td:nth-child(5),
.purchase-report-table td:nth-child(2),
.purchase-report-table td:nth-child(7),
.purchase-report-table td:nth-child(8),
.purchase-report-table td:nth-child(9),
.purchase-report-table td:nth-child(10) {
  white-space: nowrap;
}

.report-official-table tr {
  break-inside: avoid;
}

.report-signature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 24px;
  border-top: 1px solid #231815;
  border-left: 1px solid #231815;
}

.report-signature-grid div {
  min-height: 72px;
  border-right: 1px solid #231815;
  border-bottom: 1px solid #231815;
  padding: 10px 12px;
  background: rgba(255, 250, 240, 0.72);
}

.report-signature-grid span {
  display: block;
  color: #432818;
  font-size: 12px;
  font-weight: 700;
}

.report-signature-grid strong {
  display: block;
  min-height: 36px;
  margin-top: 8px;
  border-bottom: 1px solid #8b6f54;
}

.report-empty {
  min-height: 420px;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fff;
}

@media (max-width: 900px) {
  .report-hero,
  .report-control-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .report-control-meta {
    justify-content: space-between;
  }

  .report-paper-stage {
    padding-inline: 8px;
  }
}

@page {
  size: A4;
  margin: 0;
}

@media print {
  * {
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }

  :global(body),
  :global(html),
  :global(#app) {
    height: auto;
    overflow: visible;
    background: #fff;
  }

  :global(.app-sidebar),
  :global(.app-header),
  :global(.tags-view),
  :global(a[href="#main-content"]),
  .report-hero,
  .report-control-bar,
  .inline-error {
    display: none !important;
  }

  :global(.app-main) {
    height: auto;
    overflow: visible;
    padding: 0;
  }

  .report-page {
    display: block;
    height: auto;
    min-height: 0;
  }

  .report-paper-stage {
    display: block;
    overflow: visible;
    border: 0;
    border-radius: 0;
    background: transparent;
    padding: 0;
  }

  .report-sheet {
    box-sizing: border-box;
    width: 210mm;
    height: 297mm;
    min-width: 0;
    min-height: 0;
    border-color: #231815;
    background: #fffaf0;
    break-after: auto;
    page-break-after: auto;
  }

  .report-sheet.is-a4 {
    padding: 14mm 13mm 13mm 15mm;
  }

  .report-sheet::before {
    inset: 6mm;
  }

  .report-sheet::after {
    inset: 8mm;
  }

  .report-binding {
    top: 14mm;
    bottom: 14mm;
    left: 7mm;
  }

  .report-watermark {
    color: rgba(136, 31, 31, 0.045);
  }

  .report-red-stamp {
    top: 28mm;
    right: 19mm;
  }

  .report-sheet-head {
    padding-inline: 32mm;
    padding-bottom: 12px;
  }

  .report-sheet-head h2 {
    font-size: 27px;
  }

  .report-confidential {
    padding-block: 3px;
  }

  .report-meta-grid {
    margin-top: 12px;
  }

  .report-meta-grid dt,
  .report-meta-grid dd {
    padding: 7px 8px;
    font-size: 11px;
  }

  .report-instruction-row {
    margin-top: 10px;
    font-size: 11px;
    line-height: 1.42;
  }

  .report-instruction-row span {
    padding: 7px 8px;
  }

  .report-summary-strip {
    margin-top: 10px;
  }

  .report-summary-strip div {
    padding: 8px 10px;
  }

  .report-summary-strip strong {
    font-size: 16px;
  }

  .report-official-table {
    margin-top: 12px;
    font-size: 10px;
    line-height: 1.28;
  }

  .report-official-table th,
  .report-official-table td {
    padding: 5px 6px;
  }

  .report-signature-grid {
    margin-top: 18px;
  }

  .report-signature-grid div {
    min-height: 56px;
    padding: 8px 10px;
  }

  .report-signature-grid strong {
    min-height: 28px;
    margin-top: 7px;
  }

  .report-official-table thead {
    display: table-header-group;
  }

  .report-signature-grid {
    break-inside: avoid;
  }
}
</style>
