package com.cie.competition.module.registration.vo;

import lombok.Data;

/**
 * 团队成员展示对象。
 */
@Data
public class RegistrationMemberVO {

    private Long userId;

    private String name;

    private String memberRole;
}
