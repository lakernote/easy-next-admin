package com.laker.admin.infrastructure.observability.remote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.infrastructure.observability.remote.entity.RemoteCallLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface RemoteCallLogMapper extends BaseMapper<RemoteCallLog> {

    /**
     * Physically removes expired rows, including rows that were already logically deleted.
     *
     * <p>This deliberately bypasses MyBatis-Plus logical deletion so the local diagnosis table
     * cannot grow forever.</p>
     */
    @Delete("""
            DELETE FROM observability_remote_call_log
            WHERE create_time < #{cutoff}
            ORDER BY create_time
            LIMIT #{batchSize}
            """)
    int physicallyDeleteBefore(@Param("cutoff") LocalDateTime cutoff,
                               @Param("batchSize") int batchSize);
}
