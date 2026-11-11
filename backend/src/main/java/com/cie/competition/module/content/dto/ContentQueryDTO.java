package com.cie.competition.module.content.dto;

import lombok.Data;

/**
 * 内容分页查询条件对象。
 */
@Data
public class ContentQueryDTO {

    private long current = 1;

    private long size = 10;

    private String category;

    private String status;

    private String keyword;
}
