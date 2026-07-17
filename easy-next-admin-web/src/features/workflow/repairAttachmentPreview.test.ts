import { describe, expect, it } from 'vitest'
import { repairAttachmentsForDetail } from './repairAttachmentPreview'
import type { RepairAttachment, WorkflowInstanceDetail } from './types'

const baseDetail = {
  instance: {
    id: 'i1',
    definitionId: 'd1',
    versionId: 'v1',
    processKey: 'repair_approval',
    businessType: 'repair',
    businessId: 'RP-1',
    title: '报修申请',
    initiatorId: 'u1',
    status: 'RUNNING'
  },
  tasks: [],
  events: [],
  ccList: []
} satisfies WorkflowInstanceDetail

describe('repairAttachmentsForDetail', () => {
  it('extracts repair image objects from workflow variables', () => {
    const detail: WorkflowInstanceDetail = {
      ...baseDetail,
      instance: {
        ...baseDetail.instance,
        variablesJson: JSON.stringify({
          repairAttachments: [
            {
              fileId: 'f1',
              fileName: 'fault.png',
              contentType: 'image/png',
              fileSize: 1024,
              url: '/api/workflow/repair/requests/attachments/f1'
            }
          ]
        })
      }
    }

    expect(repairAttachmentsForDetail(detail)).toEqual([
      {
        fileId: 'f1',
        fileName: 'fault.png',
        contentType: 'image/png',
        fileSize: 1024,
        url: '/api/workflow/repair/requests/attachments/f1'
      }
    ])
  })

  it('uses supplied business attachments before workflow snapshot data', () => {
    const supplied: RepairAttachment = {
      fileId: 'f1',
      fileName: 'current.png',
      contentType: 'image/png',
      fileSize: 2048,
      url: '/api/workflow/repair/requests/attachments/f1'
    }
    const detail: WorkflowInstanceDetail = {
      ...baseDetail,
      instance: {
        ...baseDetail.instance,
        variables: {
          repairAttachments: [{ fileId: 'f1', fileName: 'old.png' }]
        }
      }
    }

    expect(repairAttachmentsForDetail(detail, [supplied])).toEqual([supplied])
  })
})
