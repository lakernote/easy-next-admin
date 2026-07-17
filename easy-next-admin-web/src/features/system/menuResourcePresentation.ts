import type { MenuResourceKind } from './menuNavigation'

export interface ResourceTypeOption {
  label: string
  value: MenuResourceKind
}

export interface MenuIconOption {
  label: string
  value: string
}

export interface MenuIconSection {
  title: string
  options: MenuIconOption[]
}

export interface NavigationStateInput {
  type: MenuResourceKind
  enable?: boolean
  visible?: boolean
}

export interface NavigationStateBadge {
  key: 'enabled' | 'disabled' | 'visible' | 'hidden'
  label: string
  tone: 'success' | 'muted' | 'info' | 'warning'
}

export interface NavigationStateBadgeOptions {
  showNormal?: boolean
}

export const navigationResourceTypeOptions: ResourceTypeOption[] = [
  { label: '分组', value: 'directory' },
  { label: '页面', value: 'menu' }
]

export const permissionResourceTypeOptions: ResourceTypeOption[] = [
  { label: '按钮权限', value: 'button' }
]

export const allResourceTypeOptions: ResourceTypeOption[] = [...navigationResourceTypeOptions, ...permissionResourceTypeOptions]

export const menuIconSections: MenuIconSection[] = [
  {
    title: '系统基础',
    options: [
      { label: '工作台', value: 'DataBoard' },
      { label: '系统管理', value: 'Setting' },
      { label: '用户', value: 'User' },
      { label: '用户组', value: 'UserFilled' },
      { label: '角色权限', value: 'Key' },
      { label: '菜单', value: 'Menu' },
      { label: '组织架构', value: 'OfficeBuilding' },
      { label: '平台', value: 'Platform' },
      { label: '管理', value: 'Management' },
      { label: '锁定', value: 'Lock' },
      { label: '解锁', value: 'Unlock' },
      { label: '主页', value: 'House' },
      { label: '组件', value: 'Grid' },
      { label: '列表', value: 'List' },
      { label: '集合', value: 'Collection' },
      { label: '标签集合', value: 'CollectionTag' },
      { label: '配置', value: 'SetUp' },
      { label: '指引', value: 'Guide' }
    ]
  },
  {
    title: '流程业务',
    options: [
      { label: '流程', value: 'Connection' },
      { label: '已办完成', value: 'Finished' },
      { label: '文档', value: 'Document' },
      { label: '新增文档', value: 'DocumentAdd' },
      { label: '复制文档', value: 'DocumentCopy' },
      { label: '文件夹', value: 'Folder' },
      { label: '打开文件夹', value: 'FolderOpened' },
      { label: '采购', value: 'ShoppingCart' },
      { label: '采购车', value: 'ShoppingTrolley' },
      { label: '商品', value: 'Goods' },
      { label: '合同票据', value: 'Tickets' },
      { label: '报修工具', value: 'Tools' },
      { label: '服务', value: 'Service' },
      { label: '审批勾选', value: 'Checked' },
      { label: '日历', value: 'Calendar' },
      { label: '备忘', value: 'Memo' },
      { label: '消息', value: 'Message' },
      { label: '通知', value: 'Notification' }
    ]
  },
  {
    title: '监控运维',
    options: [
      { label: '运行监控', value: 'Monitor' },
      { label: '应用指标', value: 'DataLine' },
      { label: '数据分析', value: 'DataAnalysis' },
      { label: '趋势图', value: 'TrendCharts' },
      { label: '柱状图', value: 'Histogram' },
      { label: '定时任务', value: 'Timer' },
      { label: '时钟', value: 'Clock' },
      { label: '计时器', value: 'Stopwatch' },
      { label: 'CPU', value: 'Cpu' },
      { label: '告警', value: 'Warning' },
      { label: '失败', value: 'Failed' },
      { label: '成功', value: 'SuccessFilled' },
      { label: '搜索', value: 'Search' },
      { label: '筛选', value: 'Filter' },
      { label: '刷新', value: 'Refresh' },
      { label: '下载', value: 'Download' },
      { label: '上传', value: 'Upload' }
    ]
  },
  {
    title: '审计合规',
    options: [
      { label: '审计中心', value: 'Operation' },
      { label: '行为审计', value: 'Operation' },
      { label: '合规记录', value: 'Document' },
      { label: '授权留痕', value: 'Lock' }
    ]
  },
  {
    title: '企业资源',
    options: [
      { label: '文件中心', value: 'Files' },
      { label: '资料', value: 'Notebook' },
      { label: '打印', value: 'Printer' },
      { label: '附件', value: 'Paperclip' },
      { label: '链接', value: 'Link' },
      { label: '钱包', value: 'Wallet' },
      { label: '资金', value: 'Money' },
      { label: '硬币', value: 'Coin' },
      { label: '信用卡', value: 'CreditCard' },
      { label: '公文包', value: 'Briefcase' },
      { label: '行李箱', value: 'Suitcase' },
      { label: '店铺', value: 'Shop' },
      { label: '定位', value: 'Location' },
      { label: '地图', value: 'MapLocation' },
      { label: '坐标', value: 'Coordinate' },
      { label: '手机', value: 'Cellphone' },
      { label: '客服耳机', value: 'Headset' }
    ]
  }
]

export const menuIconOptions: MenuIconOption[] = menuIconSections.flatMap((section) => section.options)

export function resourceTypeText(type: MenuResourceKind) {
  if (type === 'directory') return '分组'
  if (type === 'menu') return '页面'
  return '按钮权限'
}

export function resourceTypeIcon(type: MenuResourceKind, configuredIcon?: string) {
  if (type !== 'button' && configuredIcon) return configuredIcon
  if (type === 'directory') return 'FolderOpened'
  if (type === 'menu') return 'Document'
  return 'Operation'
}

export function navigationStateBadges(node: NavigationStateInput, options: NavigationStateBadgeOptions = {}): NavigationStateBadge[] {
  const showNormal = options.showNormal !== false
  const badges: NavigationStateBadge[] = []
  if (node.enable === false) {
    badges.push({ key: 'disabled', label: '停用', tone: 'muted' })
  } else if (showNormal) {
    badges.push({ key: 'enabled', label: '启用', tone: 'success' })
  }
  if (node.type !== 'button') {
    if (node.visible === false) {
      badges.push({ key: 'hidden', label: node.type === 'menu' ? '隐藏路由' : '侧栏隐藏', tone: 'warning' })
    } else if (showNormal) {
      badges.push({ key: 'visible', label: '侧栏显示', tone: 'info' })
    }
  }
  return badges
}

export function resourceNameLabel(type: MenuResourceKind) {
  if (type === 'directory') return '分组名称'
  if (type === 'menu') return '页面名称'
  return '按钮名称'
}

export function resourceNamePlaceholder(type: MenuResourceKind) {
  if (type === 'directory') return '例如 系统管理 / 流程中心'
  if (type === 'menu') return '例如 用户管理 / 我的流程'
  return '例如 新增用户 / 转办任务'
}

export function resourceFormHelp(type: MenuResourceKind) {
  if (type === 'directory') return '分组用于组织左侧导航，可以挂载子分组和页面。'
  if (type === 'menu') return '页面会出现在左侧导航，不选择上级导航时作为顶层页面。'
  return '按钮权限挂在页面下，用于页面内按钮授权。'
}
