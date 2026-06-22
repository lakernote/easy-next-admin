package com.laker.admin.module.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author laker
 * @since 2021-08-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long roleId;

    /**
     * 角色名
     */
    private String roleName;

    /**
     * Key值
     */
    private String roleCode;

    /**
     * 描述
     */
    private String details;

    /**
     * 是否可用
     */
    private Boolean enable;

    private Integer roleLevel;

    /**
     * 数据范围编码：ALL、DEPT_AND_CHILDREN、DEPT、SELF、DEPT_SETS
     */
    private String dataScope;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private Long createDeptId;

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

    private String remark;

    /**
     * 提供前端 显示
     */
    @TableField(exist = false)
    private boolean checked = false;

    @TableField(exist = false)
    private Long userCount = 0L;


}
