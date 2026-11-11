package com.cie.competition.module.guidance.vo;

import lombok.Data;

/**
 * 指导教师名额展示对象。
 */
@Data
public class GuidanceQuotaVO {

    private Long id;

    private Long competitionId;

    private Long teacherId;

    private String teacherName;

    private Integer quota;
    private String competitionTitle;
    private Long assigned;
    private Long remaining;
}
