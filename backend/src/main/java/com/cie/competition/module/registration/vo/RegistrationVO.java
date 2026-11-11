package com.cie.competition.module.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报名展示对象。
 */
@Data
public class RegistrationVO {

    private Long id;

    private Long competitionId;

    private Long preferredTeacherId;

    private String preferredTeacherName;

    private String competitionTitle;

    private String teamName;

    private Long leaderId;

    private String leaderName;

    private Long majorId;

    private Integer crossMajor;

    private String status;

    private LocalDateTime applyTime;

    private LocalDateTime reviewTime;

    private Long reviewerId;

    private String reviewComment;

    private List<RegistrationMemberVO> members;

    private LocalDateTime createTime;
}
