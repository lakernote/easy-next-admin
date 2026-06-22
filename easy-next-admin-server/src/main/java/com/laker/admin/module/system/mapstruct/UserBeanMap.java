package com.laker.admin.module.system.mapstruct;

import com.laker.admin.module.system.dto.user.UserBO;
import com.laker.admin.module.system.dto.user.UserRequest;
import com.laker.admin.module.system.entity.SysUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserBeanMap {

    @Mapping(target = "dept", ignore = true)
    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createDeptId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "permissionVersion", ignore = true)
    @Mapping(target = "remark", ignore = true)
    @Mapping(target = "lastLoginTime", ignore = true)
    UserBO requestToBo(UserRequest request);

    @Mapping(target = "dept", ignore = true)
    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createDeptId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "permissionVersion", ignore = true)
    @Mapping(target = "remark", ignore = true)
    SysUser boToEntity(UserBO bo);

    @Mapping(target = "dept", ignore = true)
    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createDeptId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "permissionVersion", ignore = true)
    @Mapping(target = "remark", ignore = true)
    @Mapping(target = "lastLoginTime", ignore = true)
    SysUser requestToEntity(UserRequest userRequest);
}
