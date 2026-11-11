package com.cie.competition.module.award.dto;

import lombok.Data;

/**
 * 获奖分页查询条件对象。
 */
@Data
public class AwardQueryDTO {

    private long current = 1;

    private long size = 10;

    private Long competitionId;

    private String status;

    private String awardLevel;
}
