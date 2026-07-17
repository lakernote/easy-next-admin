package com.laker.admin.module.business.number.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.business.number.entity.BusinessNumberSequence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BusinessNumberSequenceMapper extends BaseMapper<BusinessNumberSequence> {

    @Update("UPDATE biz_number_sequence SET current_value = current_value + #{step}, updated_at = CURRENT_TIMESTAMP WHERE sequence_key = #{sequenceKey}")
    int incrementCurrentValue(@Param("sequenceKey") String sequenceKey, @Param("step") long step);

    @Select("SELECT current_value FROM biz_number_sequence WHERE sequence_key = #{sequenceKey}")
    Long selectCurrentValue(@Param("sequenceKey") String sequenceKey);
}
