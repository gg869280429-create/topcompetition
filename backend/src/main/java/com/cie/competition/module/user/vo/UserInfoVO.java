package com.cie.competition.module.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户信息展示对象。
 */
@Data
@AllArgsConstructor
public class UserInfoVO {

    private Long id;
    private String username;
    private String realName;
    private String role;
    private Long majorId;
}
