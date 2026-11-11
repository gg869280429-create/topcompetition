package com.cie.competition.module.message.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息展示对象。
 */
@Data
public class MessageVO {

    private Long id;

    private Long senderId;

    private String senderName;

    private Long receiverId;

    private String title;

    private String content;

    private String type;

    private String bizType;

    private Long bizId;

    private Integer isRead;

    private LocalDateTime sendTime;
}
