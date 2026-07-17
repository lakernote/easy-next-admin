## 变更说明

-

## 影响范围

- 后端：
- 前端：
- 数据库 / 配置：
- 文档：

## 验证

- [ ] `mvn -pl easy-next-admin-server -am verify`
- [ ] `cd easy-next-admin-web && npm run test:unit`
- [ ] `cd easy-next-admin-web && npm run build`
- [ ] 不涉及对应模块

## 检查清单

- [ ] 新增接口使用标准 `Response<T>` / `PageResponse<T>`
- [ ] 写操作和敏感查询已加 `@EasyPermission`
- [ ] 新页面已补齐菜单资源、权限码和前端 API wrapper
- [ ] 文档只描述真实存在、可验证的能力
