package com.laker.admin.module.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.workflow.dto.WorkflowCcSummary;
import com.laker.admin.module.workflow.entity.WfCc;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WfCcMapper extends BaseMapper<WfCc> {

    @Select("""
            select count(*) as total,
                   coalesce(sum(case when read_status = 0 then 1 else 0 end), 0) as unread_total
              from wf_ru_cc
             where receiver_id = #{receiverId}
            """)
    WorkflowCcSummary selectSummaryByReceiverId(@Param("receiverId") Long receiverId);
}
