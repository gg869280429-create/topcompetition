package com.cie.competition.module.registration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 报名审核请求对象。
 */
@Data
public class ReviewDTO {

    @NotBlank(message = "审核结果不能为空")
    private String result;

    @jakarta.validation.constraints.Size(max = 255, message = "审核意见不能超过255个字符")
    private String comment;

    private Long teacherId;
}
