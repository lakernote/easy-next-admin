package com.laker.admin.module.batch.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 批处理任务明细。
 *
 * <p>一条明细对应一条业务数据或一个业务分区，持久化输入、领取次数和失败原因都落在这里，
 * 方便按 item 边界断点续跑。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("batch_task_item")
public class BatchTaskItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private String itemKey;
    private String itemName;
    /** 工作单元类型：RECORD 或 PARTITION。 */
    private String workType;
    private String status;
    /** 实际领取次数，首次执行为 1，租约接管会继续累加。 */
    private Integer attemptCount;
    /** 最后一次成功领取该明细的 Worker。 */
    private String workerId;
    /** 当前租约令牌，防止失去租约的旧 Worker 回写结果。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String leaseToken;
    /** 当前租约到期时间，超时后允许其他 Worker 接管。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime leaseUntil;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String inputJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resultMessage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;

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
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
    private String remark;
}
