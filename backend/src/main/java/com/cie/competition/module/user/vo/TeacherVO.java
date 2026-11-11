package com.cie.competition.module.user.vo;

import lombok.Data;

@Data
public class TeacherVO {
    private Long id;
    private String username;
    private String realName;
    private Long majorId;
    private String majorName;
    private String expertise;
    private String biography;
    private Integer status;
}
