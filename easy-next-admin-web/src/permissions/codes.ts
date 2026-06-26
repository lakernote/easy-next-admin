// 前端按钮权限的类型化常量。菜单、页面和角色授权数据以服务端 sys_menu 为准。
export const PermissionCodes = {
  dashboard: {
    view: 'dashboard:view'
  },
  profile: {
    view: 'profile:view',
    edit: 'profile:edit',
    passwordChange: 'profile:password:change',
    sessionManage: 'profile:session:manage'
  },
  message: {
    view: 'message:view',
    read: 'message:read'
  },
  businessNumber: {
    list: 'business:number:list',
    edit: 'business:number:edit',
    generate: 'business:number:generate'
  },
  batch: {
    taskList: 'batch:task:list',
    taskManage: 'batch:task:manage'
  },
  auth: {
    sessionRevoke: 'auth:session:revoke'
  },
  system: {
    user: {
      list: 'sys:user:list',
      add: 'sys:user:add',
      edit: 'sys:user:edit',
      delete: 'sys:user:delete',
      import: 'sys:user:import',
      export: 'sys:user:export',
      resetPassword: 'sys:user:reset-password'
    },
    role: {
      list: 'sys:role:list',
      edit: 'sys:role:edit'
    },
    menu: {
      list: 'sys:menu:list',
      edit: 'sys:menu:edit'
    },
    dept: {
      list: 'sys:dept:list',
      edit: 'sys:dept:edit'
    },
    file: {
      list: 'sys:file:list',
      upload: 'sys:file:upload',
      delete: 'sys:file:delete'
    }
  },
  monitor: {
    serverView: 'monitor:server:view',
    onlineView: 'monitor:online:view',
    cacheView: 'monitor:cache:view',
    cacheClear: 'monitor:cache:clear',
    weblogView: 'monitor:weblog:view',
    weblogLevel: 'monitor:weblog:level'
  },
  audit: {
    behaviorView: 'audit:behavior:view'
  },
  report: {
    view: 'report:view'
  },
  schedule: {
    jobList: 'schedule:job:list',
    jobEdit: 'schedule:job:edit'
  },
  developer: {
    apiDocsView: 'developer:api-docs:view'
  },
  workflow: {
    view: 'workflow:view',
    definitionEdit: 'workflow:definition:edit',
    instanceStart: 'workflow:instance:start',
    instanceManage: 'workflow:instance:manage',
    instanceRevoke: 'workflow:instance:revoke',
    instanceTerminate: 'workflow:instance:terminate',
    taskApprove: 'workflow:task:approve',
    taskReject: 'workflow:task:reject',
    taskTransfer: 'workflow:task:transfer',
    taskDelegate: 'workflow:task:delegate',
    taskReturn: 'workflow:task:return',
    taskAddSign: 'workflow:task:add-sign',
    taskRemoveSign: 'workflow:task:remove-sign',
    taskRemind: 'workflow:task:remind'
  }
} as const

export type PermissionCodeGroup = typeof PermissionCodes
