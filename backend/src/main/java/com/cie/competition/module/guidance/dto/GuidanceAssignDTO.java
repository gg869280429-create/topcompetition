package com.cie.competition.module.guidance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 师生匹配（分配指导）请求对象。
 */
@Data
public class GuidanceAssignDTO {
    @jakarta.validation.constraints.Size(max = 255, message = "调剂原因不能超过255个字符")
    private String comment;

    @NotNull(message = "报名不能为空")
    private Long registrationId;

    @NotNull(message = "教师不能为空")
    private Long teacherId;
}
