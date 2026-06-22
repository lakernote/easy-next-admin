import request from '@/api/request'
import { toData, type ApiResponse } from '@/api/types'
import type { EntityId, SystemMenu, SystemMenuResourcePayload } from './types'
import { toSystemMenuTree } from './tree'

const menuEndpoints = {
  menus: '/system/menus',
  list: '/system/menus/list'
}

// 菜单配置以后端 sys_menu 为事实源，前端只负责展示和提交维护表单。
export async function listSystemMenus() {
  const response = await request.get<ApiResponse<SystemMenu[]>>(menuEndpoints.list)
  return toSystemMenuTree(toData(response.data))
}

export async function saveSystemMenuResource(data: SystemMenuResourcePayload) {
  const response = await request.post<ApiResponse<SystemMenu>>(menuEndpoints.menus, data)
  return toData(response.data)
}

export async function deleteSystemMenuResource(menuId: EntityId) {
  const response = await request.delete<ApiResponse<boolean>>(`${menuEndpoints.menus}/${menuId}`)
  return toData(response.data)
}
