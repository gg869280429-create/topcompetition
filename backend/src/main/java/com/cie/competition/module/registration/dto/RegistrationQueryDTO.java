package com.cie.competition.module.registration.dto;

import lombok.Data;

/**
 * 报名分页查询条件对象。
 */
@Data
public class RegistrationQueryDTO {

    private long current = 1;

    private long size = 10;

    private Long competitionId;

    private String status;

    private String keyword;
}
