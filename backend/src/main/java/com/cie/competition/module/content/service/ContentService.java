package com.cie.competition.module.content.service;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.module.content.dto.ContentDTO;
import com.cie.competition.module.content.dto.ContentQueryDTO;
import com.cie.competition.module.content.vo.ContentVO;

public interface ContentService {

    PageResult<ContentVO> page(ContentQueryDTO query);

    ContentVO getById(Long id);
    java.util.List<ContentVO> carousel();
    Long coverFileId(Long id);

    Long create(ContentDTO dto);

    void update(Long id, ContentDTO dto);

    void delete(Long id);

    void updateStatus(Long id, String status);
}
