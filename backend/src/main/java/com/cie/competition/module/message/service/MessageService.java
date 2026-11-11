package com.cie.competition.module.message.service;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.module.message.dto.MessageDTO;
import com.cie.competition.module.message.dto.MessageQueryDTO;
import com.cie.competition.module.message.vo.MessageVO;

public interface MessageService {

    PageResult<MessageVO> pageMine(MessageQueryDTO query);

    long unreadCount();

    MessageVO getById(Long id);

    void markRead(Long id);

    void markAllRead();

    void delete(Long id);

    Long send(MessageDTO dto);

    void sendSystemMessage(Long receiverId, String title, String content, String type);

    void sendBusinessMessage(Long receiverId, String title, String content, String type, String bizType, Long bizId);
}
