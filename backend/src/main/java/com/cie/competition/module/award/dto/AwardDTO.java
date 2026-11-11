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

    private LocalDateTime awardTime;

    private Long certificateFileId;

    private String reason;
    @jakarta.validation.constraints.Size(max=160)
    private String achievementName;
    private java.util.List<Long> studentIds;
    private java.util.List<Long> teacherIds;
    private Long officialListFileId;
    private Long registrationScreenshotFileId;
    private java.util.List<Long> processPhotoFileIds;
    private Boolean draft;
}
