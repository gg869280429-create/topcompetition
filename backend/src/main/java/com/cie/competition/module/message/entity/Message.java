package com.cie.competition.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息实体，对应 message 表。
 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发送人用户 ID，0 表示系统 */
    private Long senderId;

    private Long receiverId;

    private String title;

    private String content;

    /** 类型：SYSTEM 系统 / NOTICE 通知 / AUDIT 审核 */
    private String type;

    private String bizType;

    private Long bizId;

    /** 是否已读：1 是，0 否 */
    private Integer isRead;

    private LocalDateTime sendTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
