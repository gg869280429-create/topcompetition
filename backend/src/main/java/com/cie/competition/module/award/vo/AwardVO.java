package com.cie.competition.module.award.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 获奖展示对象。
 */
@Data
public class AwardVO {

    private Long id;
    private String achievementName;
    private String achievementKey;
    private Integer legacyRecord;
    private Long officialListFileId;
    private Long registrationScreenshotFileId;
    private String processPhotoFileIds;
    private java.util.List<java.util.Map<String,Object>> students;
    private java.util.List<java.util.Map<String,Object>> teachers;

    private Long competitionId;

    private String competitionTitle;

    private Long registrationId;

    private String teamName;

    private String leaderName;

    private String awardLevel;

    private Long certificateFileId;

    private String status;

    private LocalDateTime awardTime;

    private Long registrantId;
    private LocalDateTime reviewTime;
    private Long majorId;
    private String majorNameSnapshot;
    private String teamNameSnapshot;
    private String participantsSnapshot;
    private String revocationReason;

    private Long reviewerId;

    private String reviewComment;

    private LocalDateTime createTime;
}
