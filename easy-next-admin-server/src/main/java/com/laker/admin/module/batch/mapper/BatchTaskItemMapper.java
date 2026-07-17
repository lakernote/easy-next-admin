package com.laker.admin.module.batch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.batch.dto.BatchTaskItemStatusCount;
import com.laker.admin.module.batch.entity.BatchTaskItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BatchTaskItemMapper extends BaseMapper<BatchTaskItem> {
    @Select("""
            SELECT status, COUNT(*) AS item_count
            FROM batch_task_item
            WHERE task_id = #{taskId}
            GROUP BY status
            """)
    List<BatchTaskItemStatusCount> countStatusByTaskId(@Param("taskId") Long taskId);

    @Select("""
            SELECT *
            FROM batch_task_item
            WHERE task_id = #{taskId}
              AND (status = 'PENDING'
                   OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until < #{now})))
            ORDER BY id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    BatchTaskItem selectClaimable(@Param("taskId") Long taskId, @Param("now") LocalDateTime now);
}
