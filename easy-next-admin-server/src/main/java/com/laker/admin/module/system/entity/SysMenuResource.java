package com.laker.admin.module.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 菜单权限资源。
 *
 * <p>一张 sys_menu 表同时承载目录、页面和按钮权限，前端动态路由和后端权限码都从这里取数。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_menu")
public class SysMenuResource implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 资源 ID，对应 sys_menu.id。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long menuId;

    /**
     * 父ID
     */
    private Long pid;

    /**
     * 名称
     */
    private String title;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 页面路由。按钮权限不维护接口地址，接口鉴权由 @EasyPermission 负责。
     */
    private String href;

    /**
     * 菜单排序
     */
    private Integer sort;

    /**
     * 状态(0:禁用,1:启用)
     */
    private Boolean enable;

    /**
     * 备注信息
     */
    private String remark;

    /** 资源类型：0目录，1菜单页面，2按钮权限。 */
    private Integer type;

    /** 权限码，页面和按钮资源必须配置，接口侧由 @EasyPermission 使用。 */
    private String permissionCode;

    /** Vue 页面组件路径，只对页面资源生效。 */
    private String componentPath;

    /** 是否在菜单树中展示；按钮资源默认不展示。 */
    private Boolean visible;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private Long createDeptId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

}
