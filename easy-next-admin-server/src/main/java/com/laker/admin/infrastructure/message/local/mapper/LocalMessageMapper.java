package com.laker.admin.infrastructure.message.local.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.infrastructure.message.local.entity.LocalMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface LocalMessageMapper extends BaseMapper<LocalMessage> {

    /**
     * 在短事务中锁定可投递消息。SKIP LOCKED 让多个广播实例直接跳过其他 Worker 已领取的记录。
     */
    @Select("""
            SELECT *
            FROM infra_local_message
            WHERE (status = 'PENDING' AND next_retry_at <= #{now})
               OR (status = 'PROCESSING' AND lease_until < #{now})
            ORDER BY id
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<LocalMessage> selectClaimable(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
