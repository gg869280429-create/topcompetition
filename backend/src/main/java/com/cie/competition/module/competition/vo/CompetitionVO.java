package com.cie.competition.module.competition.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 竞赛展示对象。
 */
@Data
public class CompetitionVO {

    private Long id;

    private String title;

    private String competitionType;

    private String level;

    private String category;

    private String description;

    private String organizer;

    private Long coverFileId;

    private Long documentFileId;

    private String documentName;

    private LocalDateTime registrationStartTime;

    private LocalDateTime registrationEndTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer maxMembers;

    private Integer maxTeachers;

    private Integer allowCrossMajor;

    private String status;

    private Long publisherId;

    private Long managerId;

    private String cancellationReason;

    private LocalDateTime cancelledAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
