package com.cie.competition.module.award.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cie.competition.module.award.entity.Award;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AwardMapper extends BaseMapper<Award> {
    @Select("SELECT major_id FROM sys_user WHERE id = #{userId}")
    Long selectMajorId(@Param("userId") Long userId);

    @Select("SELECT * FROM award WHERE id = #{id} FOR UPDATE")
    Award lockById(@Param("id") Long id);
}
