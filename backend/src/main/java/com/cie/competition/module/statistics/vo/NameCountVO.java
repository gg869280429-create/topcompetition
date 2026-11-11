package com.cie.competition.module.statistics.vo;

import lombok.Data;

/**
 * 名称-数量统计项，供各类分布图表复用。
 */
@Data
public class NameCountVO {

    private String name;

    private Long value;
}
