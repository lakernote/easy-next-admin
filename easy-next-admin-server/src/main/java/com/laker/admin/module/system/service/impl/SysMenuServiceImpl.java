package com.laker.admin.module.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.service.PermissionVersionService;
import com.laker.admin.module.audit.service.SensitiveAuditService;
import com.laker.admin.module.system.dto.MenuVo;
import com.laker.admin.module.system.dto.PermissionResourceDto;
import com.laker.admin.module.system.dto.PermissionResourceRequest;
import com.laker.admin.module.system.dto.workbench.PermissionResourceSummary;
import com.laker.admin.module.system.entity.SysPower;
import com.laker.admin.module.system.mapper.SysMenuMapper;
import com.laker.admin.module.system.service.ISysMenuService;
import com.laker.admin.module.system.service.ISysRolePowerService;
import com.laker.admin.common.util.EasyTreeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 系统菜单表 服务实现类
 * </p>
 *
 * @author laker
 * @since 2021-08-04
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysPower> implements ISysMenuService {
    private static final long ROOT_PARENT_ID = 0L;
    private static final int RESOURCE_TYPE_DIRECTORY = 0;
    private static final int RESOURCE_TYPE_PAGE = 1;
    private static final int RESOURCE_TYPE_BUTTON = 2;
    private static final int DEFAULT_SORT = 99;
    private static final int MAX_PARENT_LOOKUP_DEPTH = 32;

    private final SysMenuMapper sysMenuMapper;
    private final ISysRolePowerService sysRolePowerService;
    private final PermissionVersionService permissionVersionService;
    private final SensitiveAuditService sensitiveAuditService;

    public SysMenuServiceImpl(SysMenuMapper sysMenuMapper,
                              ISysRolePowerService sysRolePowerService,
                              PermissionVersionService permissionVersionService,
                              SensitiveAuditService sensitiveAuditService) {
        this.sysMenuMapper = sysMenuMapper;
        this.sysRolePowerService = sysRolePowerService;
        this.permissionVersionService = permissionVersionService;
        this.sensitiveAuditService = sensitiveAuditService;
    }

    @Override
    public List<MenuVo> menu() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal == null) {
            throw new EasyAuthException("未登录");
        }
        Long loginId = principal.getUserId();
        List<SysPower> sysPowers;
        if (principal.isSuperAdmin()) {
            sysPowers = sysMenuMapper.findAllByStatusOrderBySort(true);
        } else {
            sysPowers = listEnabledResourcesByUserId(loginId);
        }
        final List<MenuVo> menuInfo = getMenuVos(sysPowers);
        return EasyTreeUtil.toTree(menuInfo, 0L);
    }

    @Override
    public List<SysPower> listResources() {
        return this.list(Wrappers.<SysPower>lambdaQuery().orderByAsc(SysPower::getPid, SysPower::getSort, SysPower::getMenuId));
    }

    @Override
    public List<PermissionResourceDto> listResourceViews() {
        return listResources().stream().map(PermissionResourceDto::from).toList();
    }

    @Override
    public List<PermissionResourceDto> listEnabledResourceViews() {
        return this.list(Wrappers.<SysPower>lambdaQuery()
                        .eq(SysPower::getEnable, true)
                        .orderByAsc(SysPower::getPid, SysPower::getSort, SysPower::getMenuId))
                .stream()
                .map(PermissionResourceDto::from)
                .toList();
    }

    @Override
    public List<SysPower> listEnabledResourcesByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return sysMenuMapper.findEnabledByUserId(userId);
    }

    @Override
    public PermissionResourceSummary countPermissionResourceSummary() {
        return sysMenuMapper.selectPermissionResourceSummary();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionResourceDto saveResource(PermissionResourceRequest request) {
        validateResourceRequest(request);
        SysPower resource = toEntity(request);
        boolean saved = this.saveOrUpdate(resource);
        if (!saved) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存权限资源失败");
        }
        permissionVersionService.increaseForAllUsers();
        sensitiveAuditService.record("菜单管理", "保存菜单资源", "MENU", String.valueOf(resource.getMenuId()),
                "{\"title\":\"" + safe(resource.getTitle()) + "\"}");
        return PermissionResourceDto.from(this.getById(resource.getMenuId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteResource(Long resourceId) {
        SysPower resource = resourceId == null ? null : this.getById(resourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "权限资源不存在");
        }
        boolean hasChildren = this.lambdaQuery()
                .eq(SysPower::getPid, resourceId)
                .exists();
        if (hasChildren) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先删除或迁移下级资源");
        }
        sysRolePowerService.deleteByPowerId(resourceId);
        boolean deleted = this.removeById(resourceId);
        if (deleted) {
            permissionVersionService.increaseForAllUsers();
            sensitiveAuditService.record("菜单管理", "删除菜单资源", "MENU", String.valueOf(resourceId), "{\"deleted\":true}");
        }
        return deleted;
    }

    private SysPower toEntity(PermissionResourceRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "权限资源不能为空");
        }
        SysPower resource = new SysPower();
        resource.setMenuId(request.getMenuId());
        resource.setPid(request.getPid() == null ? ROOT_PARENT_ID : request.getPid());
        resource.setTitle(trim(request.getTitle()));
        resource.setIcon(trim(request.getIcon()));
        resource.setHref(request.getType() == RESOURCE_TYPE_PAGE ? trim(request.getHref()) : "");
        resource.setSort(request.getSort() == null ? DEFAULT_SORT : request.getSort());
        resource.setEnable(request.getEnable() == null ? Boolean.TRUE : request.getEnable());
        resource.setRemark(trim(request.getRemark()));
        resource.setType(request.getType());
        resource.setPowerCode(trim(request.getPowerCode()));
        resource.setComponentPath(request.getType() == RESOURCE_TYPE_PAGE ? trim(request.getComponentPath()) : null);
        resource.setVisible(request.getVisible() == null ? request.getType() != RESOURCE_TYPE_BUTTON : request.getVisible());
        return resource;
    }

    private void validateResourceRequest(PermissionResourceRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "权限资源不能为空");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "资源名称不能为空");
        }
        if (request.getType() == null || request.getType() < 0 || request.getType() > 2) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "资源类型不正确");
        }
        SysPower parent = findParentResource(request);
        validateParentCycle(request, parent);
        if (request.getType() == RESOURCE_TYPE_DIRECTORY && parent != null && RESOURCE_TYPE_BUTTON == parent.getType()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "目录不能挂在按钮权限下");
        }
        validateUniquePowerCode(request);
        if (request.getType() == RESOURCE_TYPE_PAGE) {
            if (parent != null && RESOURCE_TYPE_DIRECTORY != parent.getType()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "页面必须挂在目录下");
            }
            if (!StringUtils.hasText(request.getHref()) || !request.getHref().startsWith("/")) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "页面路由必须以 / 开头");
            }
            if (!StringUtils.hasText(request.getPowerCode())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "页面权限码不能为空");
            }
            if (!StringUtils.hasText(request.getComponentPath()) || !request.getComponentPath().startsWith("@/views/")) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "页面组件路径必须指向 src/views");
            }
            boolean routeExists = this.lambdaQuery()
                    .eq(SysPower::getType, 1)
                    .eq(SysPower::getHref, request.getHref())
                    .ne(request.getMenuId() != null, SysPower::getMenuId, request.getMenuId())
                    .exists();
            if (routeExists) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "页面路由已存在");
            }
        }
        if (request.getType() == RESOURCE_TYPE_BUTTON) {
            if (parent == null || RESOURCE_TYPE_PAGE != parent.getType()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "按钮权限必须挂在页面下");
            }
            if (!StringUtils.hasText(request.getPowerCode())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "按钮权限码不能为空");
            }
        }
    }

    private void validateUniquePowerCode(PermissionResourceRequest request) {
        if (request.getType() != RESOURCE_TYPE_BUTTON) {
            return;
        }
        String powerCode = trim(request.getPowerCode());
        if (!StringUtils.hasText(powerCode)) {
            return;
        }
        boolean exists = this.lambdaQuery()
                .eq(SysPower::getPowerCode, powerCode)
                .eq(SysPower::getType, RESOURCE_TYPE_BUTTON)
                .ne(request.getMenuId() != null, SysPower::getMenuId, request.getMenuId())
                .exists();
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "按钮权限码已存在");
        }
    }

    private void validateParentCycle(PermissionResourceRequest request, SysPower parent) {
        if (request.getMenuId() == null || parent == null) {
            return;
        }
        Long currentId = request.getMenuId();
        SysPower cursor = parent;
        int depth = 0;
        while (cursor != null && cursor.getPid() != null && cursor.getPid() > ROOT_PARENT_ID) {
            if (++depth > MAX_PARENT_LOOKUP_DEPTH) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "菜单层级过深");
            }
            if (currentId.equals(cursor.getMenuId()) || currentId.equals(cursor.getPid())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "上级资源不能选择当前资源的下级资源");
            }
            cursor = this.getById(cursor.getPid());
        }
    }

    private SysPower findParentResource(PermissionResourceRequest request) {
        Long parentId = request.getPid();
        if (parentId == null || ROOT_PARENT_ID == parentId) {
            return null;
        }
        if (parentId < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "上级资源不正确");
        }
        if (request.getMenuId() != null && request.getMenuId().equals(parentId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "上级资源不能选择自己");
        }
        SysPower parent = this.getById(parentId);
        if (parent == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "上级资源不存在");
        }
        return parent;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<MenuVo> getMenuVos(List<SysPower> sysPowers) {
        List<MenuVo> menuInfo = new ArrayList<>();
        for (SysPower e : sysPowers) {
            MenuVo menuVO = new MenuVo();
            menuVO.setId(e.getMenuId());
            menuVO.setPid(e.getPid());
            boolean page = e.getType() != null && e.getType() == RESOURCE_TYPE_PAGE;
            menuVO.setHref(page ? e.getHref() : null);
            menuVO.setTitle(e.getTitle());
            menuVO.setIcon(e.getIcon());
            menuVO.setSort(e.getSort());
            menuVO.setEnable(e.getEnable());
            menuVO.setVisible(e.getVisible());
            menuVO.setType(e.getType());
            menuVO.setPowerCode(e.getPowerCode());
            menuVO.setComponentPath(page ? e.getComponentPath() : null);
            menuInfo.add(menuVO);
        }
        return menuInfo;
    }

}
