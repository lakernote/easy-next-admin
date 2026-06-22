import { readFileSync } from 'node:fs'
import { readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function viewSource(path: string) {
  return readFileSync(resolve(__dirname, path), 'utf-8')
}

function viewFiles(dir = __dirname): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(dir, entry.name)
    if (entry.isDirectory()) return viewFiles(path)
    return entry.isFile() && entry.name.endsWith('.vue') ? [path] : []
  })
}

describe('enterprise table action consistency', () => {
  it('keeps table pagination and column controls consistent across enterprise pages', () => {
    const sources = [
      viewSource('system/UserView.vue'),
      viewSource('system/RoleView.vue'),
      viewSource('file/FileCenterView.vue'),
      viewSource('message/MessageCenterView.vue'),
      viewSource('monitor/OnlineUserView.vue'),
      viewSource('audit/BehaviorAuditView.vue'),
      viewSource('schedule/JobView.vue'),
      viewSource('workflow/WorkflowTaskCenterView.vue'),
      viewSource('workflow/WorkflowInstanceMonitorView.vue'),
      viewSource('workflow/WorkflowView.vue'),
      viewSource('workflow/LeaveRequestView.vue'),
      viewSource('workflow/PurchaseRequestView.vue'),
      viewSource('workflow/RepairRequestView.vue')
    ]

    sources.forEach((source) => {
      expect(source).not.toContain(':page-sizes="[10, 20, 50, 100]"')
      expect(source).not.toContain(':page-sizes="[8, 10, 20, 50]"')
    })

    const toolbarSource = readFileSync(resolve(__dirname, '../components/table/TableToolbar.vue'), 'utf-8')
    expect(toolbarSource).toContain('列设置')
    expect(toolbarSource).not.toContain('circle')
  })

  it('keeps all view tables on the shared enterprise table surface', () => {
    viewFiles().forEach((file) => {
      const source = readFileSync(file, 'utf-8')
      const tableTags = source.match(/<el-table(?:\s|>)([\s\S]*?)(?:>|\/>)/g) || []

      tableTags.forEach((tag) => {
        expect(tag).toContain('admin-table')
      })
    })
  })

  it('keeps primary list pages on the shared fluid table layout', () => {
    const fluidPanelSources = [
      viewSource('system/UserView.vue'),
      viewSource('system/RoleView.vue'),
      viewSource('system/DepartmentView.vue'),
      viewSource('file/FileCenterView.vue'),
      viewSource('message/MessageCenterView.vue'),
      viewSource('monitor/OnlineUserView.vue'),
      viewSource('monitor/CacheMonitorView.vue'),
      viewSource('audit/BehaviorAuditView.vue'),
      viewSource('schedule/JobView.vue'),
      viewSource('workflow/WorkflowInstanceMonitorView.vue')
    ]

    fluidPanelSources.forEach((source) => {
      expect(source).toContain('is-fluid-table')
      expect(source).toContain('useFluidTableHeight')
      expect(source).toContain(':height="tableHeight"')
    })

    const workflowDefinitionSource = viewSource('workflow/WorkflowView.vue')
    expect(workflowDefinitionSource).toContain('useFluidTableHeight(definitionTablePanelRef)')
    expect(workflowDefinitionSource).toContain(':height="tableHeight"')

    const workflowTaskSource = viewSource('workflow/WorkflowTaskCenterView.vue')
    expect(workflowTaskSource).toContain('useFluidTableHeight(taskBoardRef')
    expect(workflowTaskSource).toContain(':height="taskTableHeight')
  })

  it('keeps page headers direct without redundant eyebrow tags', () => {
    const sources = [
      viewSource('monitor/OnlineUserView.vue'),
      viewSource('monitor/MonitorView.vue'),
      viewSource('monitor/CacheMonitorView.vue'),
      viewSource('monitor/CacheListView.vue'),
      viewSource('workflow/WorkflowStartView.vue'),
      viewSource('workflow/WorkflowInstanceMonitorView.vue'),
      viewSource('workflow/WorkflowTaskCenterView.vue')
    ]

    sources.forEach((source) => {
      expect(source).not.toContain('会话监控')
      expect(source).not.toContain('head-kicker')
      expect(source).not.toContain('<el-tag type="primary" effect="plain">流程中心</el-tag>')
      expect(source).not.toContain('scopeTagType')
    })
  })

  it('keeps file center primary download visible and destructive actions in a compact more menu', () => {
    const source = viewSource('file/FileCenterView.vue')

    expect(source).toContain('class="admin-table file-table"')
    expect(source).toContain('label="操作" width="168" fixed="right"')
    expect(source).toContain('class="file-download-button"')
    expect(source).toContain('class="file-row-more"')
    expect(source).toContain('file-delete-menu-')
    expect(source).not.toContain('label="操作" width="230"')
  })

  it('keeps department status and destructive row actions visually aligned with system tables', () => {
    const source = viewSource('system/DepartmentView.vue')

    expect(source).toContain('class="admin-table department-table"')
    expect(source).toContain('class="department-status-switch"')
    expect(source).toContain('class="department-row-more"')
    expect(source).toContain('class="department-filter-summary"')
    expect(source).toContain('selectedDepartmentId')
    expect(source).toContain('is-active-department-row')
    expect(source).toContain('department-delete-menu-')
    expect(source).toContain('label="操作" width="132" fixed="right"')
    expect(source).not.toContain('keyword.value = row.deptName')
  })

  it('keeps user role tags readable and account status explicit in the user drawer', () => {
    const source = viewSource('system/UserView.vue')

    expect(source).toContain('label="角色" min-width="184"')
    expect(source).toContain('label="联系方式" min-width="168"')
    expect(source).toContain('label="直接上级" min-width="150"')
    expect(source).not.toContain('prop="phone" label="手机号" width="132" fixed="right"')
    expect(source).toContain('class="user-role-list"')
    expect(source).toContain('class="direct-manager-cell"')
    expect(source).toContain(':content="role"')
    expect(source).toContain('class="account-status-control"')
    expect(source).toContain('class="account-status-copy"')
  })

  it('keeps the mobile user table focused on readable user identity and primary actions', () => {
    const source = viewSource('system/UserView.vue')

    expect(source).toContain('isCompactUserTable')
    expect(source).toContain('compactUserMeta(row)')
    expect(source).toContain('class="user-compact-status"')
    expect(source).toContain('v-if="!isCompactUserTable" type="selection"')
    expect(source).toContain(':width="isCompactUserTable ? 112 : 168"')
  })

  it('keeps message rows focused on actionable unread and navigation states', () => {
    const source = viewSource('message/MessageCenterView.vue')

    expect(source).toContain('class="admin-table message-table"')
    expect(source).toContain('label="操作" width="148" fixed="right"')
    expect(source).toContain('class="row-actions message-row-actions"')
    expect(source).toContain('v-if="!row.read"')
  })

  it('keeps workflow task row actions compact by moving secondary actions into menus', () => {
    const source = viewSource('workflow/WorkflowTaskCenterView.vue')

    expect(source).toContain('label="操作" width="214" fixed="right"')
    expect(source).toContain('label="操作" width="168" fixed="right"')
    expect(source).toContain('class="workflow-row-action-menu"')
    expect(source).toContain('class="process-subline"')
    expect(source).toContain('>详情</el-button>')
    expect(source).not.toContain('label="操作" width="360"')
    expect(source).not.toContain('label="操作" width="230"')
  })

  it('keeps workflow definition destructive actions inside a secondary menu', () => {
    const source = viewSource('workflow/WorkflowView.vue')

    expect(source).toContain('label="操作" width="168" fixed="right"')
    expect(source).toContain('class="workflow-definition-row-action-menu"')
    expect(source).toContain('删除流程')
    expect(source).not.toContain('label="操作" width="220"')
  })

  it('keeps cache clear as a secondary destructive table action', () => {
    const source = viewSource('monitor/CacheMonitorView.vue')

    expect(source).toContain('label="操作" width="158" fixed="right"')
    expect(source).toContain('class="cache-row-more"')
    expect(source).toContain('class="cache-row-action-menu"')
    expect(source).toContain(':value="item.value"')
    expect(source).toContain('清理缓存')
    expect(source).not.toContain(':label="item.value"')
    expect(source).not.toContain('label="操作" width="196"')
  })

  it('keeps service monitor focused on resource charts instead of actuator metric tables', () => {
    const source = viewSource('monitor/MonitorView.vue')

    expect(source).toContain('class="monitor-chart-grid"')
    expect(source).toContain('运维关注项')
    expect(source).toContain('资源水位')
    expect(source).toContain('GC 统计')
    expect(source).not.toContain('Actuator 指标')
    expect(source).not.toContain('micrometerMetrics')
  })

  it('keeps workflow request pages focused on direct application forms', () => {
    const sources = [
      viewSource('workflow/LeaveRequestView.vue'),
      viewSource('workflow/PurchaseRequestView.vue'),
      viewSource('workflow/RepairRequestView.vue')
    ]

    sources.forEach((source) => {
      expect(source).toContain('class="paper-form-area"')
      expect(source).toContain('WorkflowApplicationPaper')
      expect(source).not.toContain('class="admin-table')
      expect(source).not.toContain('pageLeaveRequests')
      expect(source).not.toContain('pagePurchaseRequests')
      expect(source).not.toContain('pageRepairRequests')
    })
  })

  it('keeps workflow designer selection from exposing internal ids or mutating edge paths', () => {
    const source = viewSource('workflow/WorkflowView.vue')

    expect(source).toContain('adjustEdge: false')
    expect(source).toContain('adjustEdgeStartAndEnd: false')
    expect(source).toContain('adjustEdgeMiddle: false')
    expect(source).toContain('grid: { size: 12, visible: false }')
    expect(source).toContain('snapGrid: true')
    expect(source).toContain('edgeGenerator:')
    expect(source).toContain('...edgeVisualProperties()')
    expect(source).toContain('anchorLine: {')
    expect(source).toContain('stroke: edgeDefaultColor')
    expect(source).toContain('edge:add')
    expect(source).toContain('normalizeAddedEditorEdge')
    expect(source).toContain('rememberEditorEdgeRoutes')
    expect(source).toContain('restoreEditorEdgeRoutesSoon')
    expect(source).toContain('Promise.resolve().then(restoreEditorEdgeRoutes)')
    expect(source).toContain('node:drop')
    expect(source).not.toContain('<el-tag effect="plain">{{ nodeForm.id }}</el-tag>')
  })

  it('keeps organization and user import screens free of low-value department codes', () => {
    const departmentSource = viewSource('system/DepartmentView.vue')
    const userSource = viewSource('system/UserView.vue')
    const auditSource = viewSource('audit/BehaviorAuditView.vue')

    expect(departmentSource).not.toContain('部门编码')
    expect(departmentSource).not.toContain('deptCode')
    expect(departmentSource).not.toContain('未设编码')
    expect(departmentSource).not.toContain('`#${pid}`')
    expect(userSource).toContain('CSV 使用部门名称和角色编码')
    expect(userSource).not.toContain('CSV 使用部门编码')
    expect(auditSource).toContain('function resetAuditFilters()')
  })
})

describe('enterprise workflow page density', () => {
  it('keeps workflow launch focused on real application entry points', () => {
    const source = viewSource('workflow/WorkflowStartView.vue')

    expect(source).toContain('class="surface workflow-start-main"')
    expect(source).toContain('class="workflow-card-grid"')
    expect(source).toContain('canManageWorkflowDefinitions')
    expect(source).toContain('PermissionCodes.workflow.definitionEdit')
    expect(source).toContain('v-if="canManageWorkflowDefinitions"')
    expect(source).not.toContain('workflow-start-side')
    expect(source).not.toContain('流程能力')
  })

  it('renders workflow request rules as compact route summaries instead of card blocks', () => {
    const sources = [
      viewSource('workflow/LeaveRequestView.vue'),
      viewSource('workflow/PurchaseRequestView.vue'),
      viewSource('workflow/RepairRequestView.vue')
    ]

    sources.forEach((source) => {
      expect(source).toContain('class="surface route-summary"')
      expect(source).toContain('class="approval-rules"')
      expect(source).not.toContain('class="surface rule-panel"')
      expect(source).not.toContain('min-height: 92px')
    })
  })
})

describe('enterprise audit page density', () => {
  it('keeps audit review in a fixed data workbench instead of low-value metric cards', () => {
    const source = viewSource('audit/BehaviorAuditView.vue')

    expect(source).toContain('class="resource-page audit-page"')
    expect(source).toContain('class="audit-snapshot"')
    expect(source).toContain('class="table-control-row audit-control-row"')
    expect(source).toContain(':height="tableHeight" class="admin-table audit-table"')
    expect(source).toContain('<h1>审计中心</h1>')
    expect(source).toContain('label="操作" width="92" fixed="right" align="center" header-align="center"')
    expect(source).toContain('is-fluid-table')
    expect(source).toContain('useFluidTableHeight(auditPanelRef)')
    expect(source).toContain('overflow: hidden;')
    expect(source).not.toContain('class="resource-metrics is-four"')
    expect(source).not.toContain('当前页来源')
  })
})

describe('enterprise paper report surface', () => {
  it('keeps report center on printable A4 sheets instead of chart dashboards', () => {
    const source = viewSource('report/EnterpriseReportView.vue')

    expect(source).toContain('class="report-page"')
    expect(source).toContain('class="report-sheet is-a4"')
    expect(source).toContain('report-official-table')
    expect(source).toContain('class="report-red-stamp"')
    expect(source).toContain('class="report-watermark"')
    expect(source).toContain('class="report-instruction-row"')
    expect(source).toContain('organization-report-table')
    expect(source).toContain('purchase-report-table')
    expect(source).toContain('print-color-adjust: exact;')
    expect(source).toContain('break-after: auto;')
    expect(source).toContain('window.print()')
    expect(source).toContain('PermissionCodes.report.view')
    expect(source).not.toContain('EasyChart')
    expect(source).not.toContain('echarts')
  })
})
