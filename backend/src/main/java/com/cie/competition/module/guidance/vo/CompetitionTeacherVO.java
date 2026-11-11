package com.cie.competition.module.guidance.vo;

import lombok.Data;

@Data
public class CompetitionTeacherVO {
    private Long teacherId;
    private String teacherName;
    private String majorName;
    private String expertise;
    private String biography;
    private Integer quota;
    private Long assigned;
    private Long remaining;
}
