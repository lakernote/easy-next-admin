package com.laker.admin.module.business.number.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.business.number.entity.BusinessNumberSequence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BusinessNumberSequenceMapper extends BaseMapper<BusinessNumberSequence> {

    @Select("SELECT * FROM biz_number_sequence WHERE sequence_key = #{sequenceKey} FOR UPDATE")
    BusinessNumberSequence selectForUpdate(@Param("sequenceKey") String sequenceKey);
}
