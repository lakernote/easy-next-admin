import type { RepairAttachment, WorkflowInstanceDetail } from './types'

export function repairAttachmentsForDetail(
  detail: WorkflowInstanceDetail | undefined,
  supplied: RepairAttachment[] = []
) {
  const variableAttachments = normalizeAttachmentList(readWorkflowVariables(detail).repairAttachments)
  const merged = new Map<string, RepairAttachment>()
  const orderedAttachments = [...variableAttachments, ...supplied]
  orderedAttachments.forEach((item) => {
    if (!item?.fileId) return
    merged.set(String(item.fileId), item)
  })
  return Array.from(merged.values())
}

function readWorkflowVariables(detail: WorkflowInstanceDetail | undefined): Record<string, unknown> {
  if (!detail) return {}
  if (detail.instance.variables) return detail.instance.variables
  if (!detail.instance.variablesJson) return {}
  try {
    const parsed = JSON.parse(detail.instance.variablesJson)
    return isRecord(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function normalizeAttachmentList(value: unknown): RepairAttachment[] {
  if (!Array.isArray(value)) return []
  return value.map(normalizeAttachment).filter(Boolean) as RepairAttachment[]
}

function normalizeAttachment(value: unknown): RepairAttachment | null {
  if (!isRecord(value)) return null
  const fileId = value.fileId
  if (fileId === undefined || fileId === null || fileId === '') return null
  const fileName = value.fileName ?? `图片 ${fileId}`
  const url = value.url ?? `/api/workflow/repair/requests/attachments/${fileId}`
  return {
    fileId: fileId as RepairAttachment['fileId'],
    fileName: String(fileName),
    contentType: typeof value.contentType === 'string' ? value.contentType : undefined,
    fileSize: typeof value.fileSize === 'number' ? value.fileSize : undefined,
    url: String(url)
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
