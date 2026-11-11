package com.cie.competition.module.registration.service;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.module.registration.dto.RegistrationDTO;
import com.cie.competition.module.registration.dto.RegistrationQueryDTO;
import com.cie.competition.module.registration.dto.ReviewDTO;
import com.cie.competition.module.registration.vo.RegistrationVO;

import java.util.Collection;
import java.util.List;

public interface RegistrationService {
    RegistrationVO lockForAssignment(Long id);

    PageResult<RegistrationVO> page(RegistrationQueryDTO query);

    PageResult<RegistrationVO> pageMine(RegistrationQueryDTO query);

    RegistrationVO getById(Long id);

    List<RegistrationVO> listByIds(Collection<Long> ids);

    Long create(RegistrationDTO dto);

    void update(Long id, RegistrationDTO dto);

    void cancel(Long id);

    void review(Long id, ReviewDTO dto);
}
