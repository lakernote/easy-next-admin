import request from '@/api/request'
import { toData, type ApiResponse } from '@/api/types'
import type { RepairApplyPayload, RepairAttachment, RepairRequest } from './types'

const repairEndpoints = {
  requests: '/workflow/repair/requests',
  attachments: '/workflow/repair/requests/attachments',
  attachment: (fileId: RepairAttachment['fileId']) => `/workflow/repair/requests/attachments/${fileId}`,
  byWorkflowInstance: (instanceId: RepairRequest['workflowInstanceId']) =>
    `/workflow/repair/requests/by-workflow-instance/${instanceId}`
}

// 报修申请包含附件上传和实例详情回查，单独拆出便于学习文件型表单写法。
export async function applyRepairRequest(payload: RepairApplyPayload) {
  const response = await request.post<ApiResponse<RepairRequest>>(repairEndpoints.requests, payload)
  return toData(response.data)
}

export async function uploadRepairAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post<ApiResponse<RepairAttachment>>(repairEndpoints.attachments, formData)
  return toData(response.data)
}

export async function downloadRepairAttachment(fileId: RepairAttachment['fileId']) {
  const response = await request.get<Blob>(repairEndpoints.attachment(fileId), {
    responseType: 'blob'
  })
  return response.data
}

export async function getRepairRequestByWorkflowInstance(instanceId: RepairRequest['workflowInstanceId']) {
  const response = await request.get<ApiResponse<RepairRequest>>(repairEndpoints.byWorkflowInstance(instanceId))
  return toData(response.data)
}
