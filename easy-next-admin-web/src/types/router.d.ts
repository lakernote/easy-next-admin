import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    requiresAuth?: boolean
    hidden?: boolean
    permissionCode?: string
    dynamic?: boolean
    dynamicBootstrap?: boolean
    fixed?: boolean
    componentPath?: string
    componentMissing?: boolean
  }
}
