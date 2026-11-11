package com.cie.competition.module.competition.service;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.module.competition.dto.CompetitionDTO;
import com.cie.competition.module.competition.dto.CompetitionQueryDTO;
import com.cie.competition.module.competition.vo.CompetitionVO;

import java.util.Collection;
import java.util.Map;

public interface CompetitionService {
    com.cie.competition.module.competition.entity.Competition lockById(Long id);

    void requireRegistrationOpen(Long id);

    void requireManageable(Long id);

    void requireGuidanceAssignable(Long id);

    void validateTeacher(Long competitionId, Long teacherId);

    PageResult<CompetitionVO> page(CompetitionQueryDTO query);

    CompetitionVO getById(Long id);

    Map<Long, String> listTitleMap(Collection<Long> ids);

    Long create(CompetitionDTO dto);

    void update(Long id, CompetitionDTO dto);

    void delete(Long id);

    default void updateStatus(Long id, String status) { updateStatus(id, status, null); }

    void updateStatus(Long id, String status, String reason);
}
