package com.cie.competition.module.guidance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 指导教师名额设置请求对象。
 */
@Data
public class GuidanceQuotaDTO {

    @NotNull(message = "竞赛不能为空")
    private Long competitionId;

    @NotNull(message = "教师不能为空")
    private Long teacherId;

    @NotNull(message = "名额不能为空")
    @Min(value = 0, message = "名额不能为负数")
    private Integer quota;
}
