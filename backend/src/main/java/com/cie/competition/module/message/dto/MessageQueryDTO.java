package com.cie.competition.module.message.dto;

import lombok.Data;

/**
 * 消息分页查询条件对象。
 */
@Data
public class MessageQueryDTO {

    private long current = 1;

    private long size = 10;

    private String type;

    private Integer isRead;
}
