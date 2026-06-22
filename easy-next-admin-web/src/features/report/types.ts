export interface EnterpriseReportOverview {
  organizationName: string
  reportPeriod: string
  generatedAt: string
  preparedBy: string
  dataScopeLabel: string
  organizationLedger: OrganizationLedgerReport
  purchaseReview: PurchaseReviewReport
}

export interface OrganizationLedgerReport {
  reportNo: string
  metrics: ReportMetric[]
  rows: DepartmentLedgerRow[]
  signatures: SignatureCell[]
}

export interface PurchaseReviewReport {
  reportNo: string
  metrics: ReportMetric[]
  rows: PurchaseReviewRow[]
  signatures: SignatureCell[]
}

export interface ReportMetric {
  label: string
  value: string
}

export interface DepartmentLedgerRow {
  index: number
  departmentName: string
  leaderName: string
  userCount: number
  enabledCount: number
  managerSummary: string
  positionSummary: string
  lastLoginSummary: string
}

export interface PurchaseReviewRow {
  index: number
  requestNo: string
  applicantName: string
  departmentName: string
  itemName: string
  category: string
  quantity: number
  estimatedAmount?: number | string
  requiredDate: string
  statusText: string
  currentNodeName: string
}

export interface SignatureCell {
  label: string
  value?: string
}
