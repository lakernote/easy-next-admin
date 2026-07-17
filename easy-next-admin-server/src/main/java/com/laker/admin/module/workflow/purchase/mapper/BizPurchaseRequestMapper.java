package com.laker.admin.module.workflow.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.workflow.purchase.batch.PurchaseReconciliationBounds;
import com.laker.admin.module.workflow.purchase.batch.PurchaseReconciliationCandidate;
import com.laker.admin.module.workflow.purchase.entity.BizPurchaseRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BizPurchaseRequestMapper extends BaseMapper<BizPurchaseRequest> {
    @Select("""
            SELECT MIN(p.id) AS min_id, MAX(p.id) AS max_id
            FROM biz_purchase_request p
            LEFT JOIN wf_ru_process_instance r ON r.id = p.workflow_instance_id
            LEFT JOIN wf_hi_process_instance h ON h.id = p.workflow_instance_id
            WHERE (r.id IS NOT NULL OR h.id IS NOT NULL)
              AND ((p.updated_at >= #{startTime} AND p.updated_at < #{endTime})
                   OR (r.updated_at >= #{startTime} AND r.updated_at < #{endTime})
                   OR (h.updated_at >= #{startTime} AND h.updated_at < #{endTime}))
            """)
    PurchaseReconciliationBounds selectReconciliationBounds(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT p.id AS purchase_id,
                   p.status AS purchase_status,
                   COALESCE(r.status, h.status) AS workflow_status
            FROM biz_purchase_request p
            LEFT JOIN wf_ru_process_instance r ON r.id = p.workflow_instance_id
            LEFT JOIN wf_hi_process_instance h ON h.id = p.workflow_instance_id
            WHERE p.id > #{lastId}
              AND p.id BETWEEN #{startId} AND #{endId}
              AND (r.id IS NOT NULL OR h.id IS NOT NULL)
              AND ((p.updated_at >= #{startTime} AND p.updated_at < #{endTime})
                   OR (r.updated_at >= #{startTime} AND r.updated_at < #{endTime})
                   OR (h.updated_at >= #{startTime} AND h.updated_at < #{endTime}))
            ORDER BY p.id
            LIMIT #{limit}
            """)
    List<PurchaseReconciliationCandidate> selectReconciliationCandidates(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("startId") long startId,
            @Param("endId") long endId,
            @Param("lastId") long lastId,
            @Param("limit") int limit);

    @Update("""
            UPDATE biz_purchase_request
            SET status = #{targetStatus}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{purchaseId} AND status = #{sourceStatus}
            """)
    int updateStatusIfUnchanged(@Param("purchaseId") Long purchaseId,
                                @Param("sourceStatus") String sourceStatus,
                                @Param("targetStatus") String targetStatus);
}
