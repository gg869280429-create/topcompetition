package com.cie.competition.module.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.ResultCode;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.message.dto.MessageDTO;
import com.cie.competition.module.message.dto.MessageQueryDTO;
import com.cie.competition.module.message.entity.Message;
import com.cie.competition.module.message.mapper.MessageMapper;
import com.cie.competition.module.message.service.MessageService;
import com.cie.competition.module.message.vo.MessageVO;
import com.cie.competition.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserService userService;

    @Override
    public PageResult<MessageVO> pageMine(MessageQueryDTO query) {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(StringUtils.hasText(query.getType()), Message::getType, query.getType())
                .eq(query.getIsRead() != null, Message::getIsRead, query.getIsRead())
                .orderByDesc(Message::getSendTime);
        Page<Message> page = messageMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return new PageResult<>(assembleVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public long unreadCount() {
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, UserContext.getUserId())
                .eq(Message::getIsRead, 0));
        return count == null ? 0 : count;
    }

    @Override
    public MessageVO getById(Long id) {
        Message message = getMessage(id);
        checkReceiver(message);
        if (message.getIsRead() == null || message.getIsRead() == 0) {
            message.setIsRead(1);
            messageMapper.updateById(message);
        }
        return assembleVOs(List.of(message)).get(0);
    }

    @Override
    public void markRead(Long id) {
        Message message = getMessage(id);
        checkReceiver(message);
        if (message.getIsRead() == null || message.getIsRead() == 0) {
            message.setIsRead(1);
            messageMapper.updateById(message);
        }
    }

    @Override
    public void markAllRead() {
        LambdaUpdateWrapper<Message> wrapper = new LambdaUpdateWrapper<Message>()
                .eq(Message::getReceiverId, UserContext.getUserId())
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1);
        messageMapper.update(null, wrapper);
    }

    @Override
    public void delete(Long id) {
        Message message = getMessage(id);
        checkReceiver(message);
        messageMapper.deleteById(id);
    }

    @Override
    public Long send(MessageDTO dto) {
        Message message = buildMessage(UserContext.getUserId(), dto.getReceiverId(),
                dto.getTitle(), dto.getContent(), dto.getType());
        messageMapper.insert(message);
        return message.getId();
    }

    @Override
    public void sendSystemMessage(Long receiverId, String title, String content, String type) {
        Message message = buildMessage(0L, receiverId, title, content, type);
        messageMapper.insert(message);
    }

    @Override
    public void sendBusinessMessage(Long receiverId, String title, String content, String type, String bizType, Long bizId) {
        if (content != null && content.codePointCount(0, content.length()) > 1024)
            content = content.substring(0, content.offsetByCodePoints(0, 1000)) + "…详见业务详情";
        Message message = buildMessage(0L, receiverId, title, content, type);
        message.setBizType(bizType);
        message.setBizId(bizId);
        messageMapper.insert(message);
    }

    private Message buildMessage(Long senderId, Long receiverId, String title, String content, String type) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setTitle(title);
        message.setContent(content);
        message.setType(StringUtils.hasText(type) ? type : "SYSTEM");
        message.setIsRead(0);
        message.setSendTime(LocalDateTime.now());
        return message;
    }

    private void checkReceiver(Message message) {
        if (!UserContext.getUserId().equals(message.getReceiverId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权限操作该消息");
        }
    }

    private Message getMessage(Long id) {
        Message message = messageMapper.selectById(id);
        if (message == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "消息不存在");
        }
        return message;
    }

    private List<MessageVO> assembleVOs(List<Message> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> senderIds = list.stream().map(Message::getSenderId)
                .filter(id -> id != null && id != 0).distinct().toList();
        Map<Long, String> senderNameMap = userService.listRealNameMap(senderIds);
        return list.stream().map(m -> {
            MessageVO vo = new MessageVO();
            BeanUtils.copyProperties(m, vo);
            if (m.getSenderId() == null || m.getSenderId() == 0) {
                vo.setSenderName("系统");
            } else {
                vo.setSenderName(senderNameMap.get(m.getSenderId()));
            }
            return vo;
        }).toList();
    }
}
