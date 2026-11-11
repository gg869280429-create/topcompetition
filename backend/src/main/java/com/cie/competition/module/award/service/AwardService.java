package com.cie.competition.module.award.service;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.module.award.dto.AwardDTO;
import com.cie.competition.module.award.dto.AwardQueryDTO;
import com.cie.competition.module.award.dto.AwardReviewDTO;
import com.cie.competition.module.award.vo.AwardVO;

public interface AwardService {

    PageResult<AwardVO> page(AwardQueryDTO query);

    AwardVO getById(Long id);

    Long create(AwardDTO dto);

    void update(Long id, AwardDTO dto);

    void delete(Long id);

    void correct(Long id, AwardDTO dto);

    void revoke(Long id, String reason);

    java.util.List<com.cie.competition.module.history.entity.BusinessHistory> history(Long id);

    void review(Long id, AwardReviewDTO dto);
}
