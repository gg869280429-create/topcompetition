package com.cie.competition.module.guidance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cie.competition.module.guidance.entity.GuidanceQuota;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GuidanceQuotaMapper extends BaseMapper<GuidanceQuota> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM guidance_quota WHERE competition_id=#{competitionId} AND teacher_id=#{teacherId} FOR UPDATE")
    GuidanceQuota lockQuota(Long competitionId, Long teacherId);
}
