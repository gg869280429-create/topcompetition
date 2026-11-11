package com.cie.competition.module.competition.dto;

import lombok.Data;

/**
 * 竞赛分页查询条件对象。
 */
@Data
public class CompetitionQueryDTO {

    private long current = 1;

    private long size = 10;

    private String keyword;

    private String status;

    private String competitionType;

    private String level;
}
