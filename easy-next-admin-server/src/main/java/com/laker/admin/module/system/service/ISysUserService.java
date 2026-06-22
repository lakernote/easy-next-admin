package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.module.system.dto.FlowAssigneVo;
import com.laker.admin.module.system.dto.SystemRoleView;
import com.laker.admin.module.system.dto.SystemUserQuery;
import com.laker.admin.module.system.dto.SystemUserView;
import com.laker.admin.module.system.dto.UserStatusRequest;
import com.laker.admin.module.system.dto.user.UserBO;
import com.laker.admin.module.system.dto.user.UserImportResult;
import com.laker.admin.module.system.dto.user.UserRequest;
import com.laker.admin.module.system.dto.workbench.EnabledCountSummary;
import com.laker.admin.module.system.entity.SysUser;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author laker
 * @since 2021-08-05
 */
public interface ISysUserService extends IService<SysUser> {
    PageResponse<SystemUserView> pageUsers(SystemUserQuery query);

    SystemUserView createUser(UserRequest request);

    SystemUserView updateUser(Long userId, UserRequest request);

    SystemUserView saveUser(UserRequest request);

    byte[] userImportTemplate();

    UserImportResult importUsers(MultipartFile file);

    byte[] exportUsers(SystemUserQuery query);

    boolean switchUserStatus(UserStatusRequest request);

    boolean deleteUser(Long userId);

    boolean deleteUsers(Collection<Long> userIds);

    boolean resetPassword(Long userId);

    boolean updateCurrentAvatar(String avatar);

    List<SystemRoleView> listAssignableRoles(Long userId);

    List<FlowAssigneVo> listWorkflowAssignees();

    List<FlowAssigneVo> listWorkflowAssignees(Long userId);

    SystemUserView getUserAndDeptById(Long userId);

    EnabledCountSummary countEnabledSummary();

    boolean save(UserBO userBO);
}
