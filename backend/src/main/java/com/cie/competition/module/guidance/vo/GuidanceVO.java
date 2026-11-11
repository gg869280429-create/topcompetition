package com.cie.competition.module.guidance.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指导关系展示对象。
 */
@Data
public class GuidanceVO {

    private Long id;

    private Long registrationId;

    private String teamName;

    private Long competitionId;

    private String competitionTitle;

    private Long teacherId;

    private String teacherName;

    private String status;

    private LocalDateTime assignTime;

    private LocalDateTime endedTime;

    private String endReason;
}
