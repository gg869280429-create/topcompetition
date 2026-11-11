package com.cie.competition.module.guidance.service;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.module.guidance.dto.GuidanceAssignDTO;
import com.cie.competition.module.guidance.dto.GuidanceQuotaDTO;
import com.cie.competition.module.guidance.vo.GuidanceQuotaVO;
import com.cie.competition.module.guidance.vo.GuidanceVO;

import java.util.List;

public interface GuidanceService {

    PageResult<GuidanceQuotaVO> quotaPage(Long competitionId, long current, long size);

    Long saveQuota(GuidanceQuotaDTO dto);

    void deleteQuota(Long id);

    java.util.List<com.cie.competition.module.guidance.vo.CompetitionTeacherVO> competitionTeachers(Long competitionId);

    List<GuidanceVO> listByRegistration(Long registrationId);

    Long assign(GuidanceAssignDTO dto);

    Long confirmAssignment(GuidanceAssignDTO dto);

    Long replace(Long id, GuidanceAssignDTO dto);

    PageResult<GuidanceVO> pageMine(long current, long size);
}
