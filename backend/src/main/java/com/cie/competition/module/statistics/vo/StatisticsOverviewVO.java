package com.cie.competition.module.statistics.vo;

import lombok.Data;

import java.util.List;

/**
 * 全局统计概览，含关键数字与各维度分布。
 */
@Data
public class StatisticsOverviewVO {

    private Long competitionCount;

    private Long registrationCount;

    private Long registrationPeopleCount;

    private Long effectiveRegistrationCount;

    private Long awardCount;

    private Long studentCount;

    private Long teacherCount;

    private Long guidanceCount;

    private List<NameCountVO> competitionStatus;

    private List<NameCountVO> registrationStatus;

    private List<NameCountVO> awardLevel;

    private List<NameCountVO> registrationMajor;
}
