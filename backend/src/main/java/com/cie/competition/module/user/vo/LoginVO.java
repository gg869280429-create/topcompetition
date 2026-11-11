package com.cie.competition.module.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录结果：JWT Token + 用户信息。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    private String token;
    private UserInfoVO user;
}
