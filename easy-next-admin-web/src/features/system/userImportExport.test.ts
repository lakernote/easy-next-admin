import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/api/request'
import { resolveDownloadFileName } from '@/utils/download'
import { downloadUserImportTemplate, exportUsers, importUsers } from './userApi'

vi.mock('@/api/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

describe('system user import and export api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses user scoped endpoints for template, import and export', async () => {
    const blob = new Blob(['用户名,姓名'])
    vi.mocked(request.get).mockResolvedValueOnce({
      data: blob,
      headers: {
        'content-disposition': "attachment; filename*=UTF-8''%E7%94%A8%E6%88%B7%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF.csv"
      }
    })
    vi.mocked(request.get).mockResolvedValueOnce({
      data: blob,
      headers: {}
    })
    vi.mocked(request.post).mockResolvedValue({
      data: {
        code: 200,
        data: { totalRows: 1, successRows: 1, failedRows: 0, errors: [] }
      }
    })

    const template = await downloadUserImportTemplate()
    const uploadFile = new Blob(['用户名,姓名'], { type: 'text/csv' }) as File
    const result = await importUsers(uploadFile)
    const exported = await exportUsers({ keyWord: '林员工', enable: 1 })

    expect(request.get).toHaveBeenNthCalledWith(1, '/system/users/import-template', { responseType: 'blob' })
    expect(request.post).toHaveBeenCalledWith('/system/users/import', expect.any(FormData), {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    expect(request.get).toHaveBeenNthCalledWith(2, '/system/users/export', {
      params: { keyWord: '林员工', deptId: undefined, enable: 1 },
      responseType: 'blob'
    })
    expect(template.fileName).toBe('用户导入模板.csv')
    expect(result.successRows).toBe(1)
    expect(exported.fileName).toBe('用户导出.csv')
  })

  it('resolves utf8 download file names', () => {
    expect(resolveDownloadFileName("attachment; filename*=UTF-8''%E7%94%A8%E6%88%B7%E5%AF%BC%E5%87%BA.csv")).toBe('用户导出.csv')
  })
})
