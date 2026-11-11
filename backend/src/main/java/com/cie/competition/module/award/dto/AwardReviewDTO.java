package com.cie.competition.module.award.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 获奖审核请求对象。
 */
@Data
public class AwardReviewDTO {

    @NotBlank(message = "审核结果不能为空")
    private String result;

    private String comment;
}
