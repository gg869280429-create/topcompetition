package com.cie.competition.module.award.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 获奖登记 / 编辑请求对象。
 */
@Data
public class AwardDTO {

    @NotNull(message = "报名不能为空")
    private Long registrationId;

    @NotBlank(message = "奖项等级不能为空")
    private String awardLevel;

    @NotNull(message = "获奖日期不能为空")
    private LocalDateTime awardTime;

    @NotNull(message = "获奖证明不能为空")
    private Long certificateFileId;

    private String reason;
}
