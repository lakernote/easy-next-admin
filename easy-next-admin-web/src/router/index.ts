import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '@/layout/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { firstAuthorizedPath, resetDynamicRoutes, syncDynamicRoutes } from './dynamicRoutes'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/',
    name: 'AppShell',
    component: AppLayout,
    redirect: () => shellFallbackPath(),
    meta: { requiresAuth: true },
    children: [
      {
        path: '/403',
        name: 'Forbidden',
        component: () => import('@/views/error/ForbiddenView.vue'),
        meta: { title: '无权限', hidden: true, requiresAuth: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'DynamicRouteBootstrap',
    component: AppLayout,
    meta: { requiresAuth: true, hidden: true, dynamicBootstrap: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/login') {
    if (!auth.isLoggedIn) {
      return
    }
    if (!auth.profileLoaded) {
      try {
        await auth.loadProfile()
      } catch {
        auth.logout()
        return
      }
    }
    syncDynamicRoutes(router, auth.menus)
    return { path: firstAuthorizedPath(auth.menus) }
  }
  const matchedDynamicBootstrap = to.matched.some((item) => item.meta.dynamicBootstrap)
  if (matchedDynamicBootstrap || to.matched.length === 0) {
    if (!auth.isLoggedIn) {
      resetDynamicRoutes(router)
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    if (!auth.profileLoaded) {
      try {
        await auth.loadProfile()
      } catch {
        auth.logout()
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }
    syncDynamicRoutes(router, auth.menus)
    const resolved = router.resolve(to.fullPath)
    const resolvedTarget = resolved.matched.find((item) => !item.meta.dynamicBootstrap)
    if (resolvedTarget) {
      return { path: to.path, query: to.query, hash: to.hash, replace: true }
    }
    return { path: '/403', query: { from: to.fullPath } }
  }
  // 路由层只判断页面可见性；接口级权限统一由服务端 @EasyPermission 兜底。
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    resetDynamicRoutes(router)
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAuth && auth.isLoggedIn && !auth.profileLoaded) {
    try {
      await auth.loadProfile()
    } catch {
      auth.logout()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (to.meta.requiresAuth && auth.isLoggedIn && auth.profileLoaded) {
    const changed = syncDynamicRoutes(router, auth.menus)
    if (changed && !to.matched.some((item) => item.meta.dynamic)) {
      return { path: to.path, query: to.query, hash: to.hash, replace: true }
    }
  }
  if (to.meta.permissionCode && !auth.hasAnyPermission([to.meta.permissionCode])) {
    ElMessage.warning('没有权限访问该页面')
    return { path: '/403', query: { from: to.fullPath } }
  }
})

function shellFallbackPath() {
  const auth = useAuthStore()
  if (!auth.isLoggedIn || !auth.profileLoaded) {
    return '/login'
  }
  return firstAuthorizedPath(auth.menus)
}

export default router
