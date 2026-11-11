package com.cie.competition.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户信息，从 JWT 解析后放入安全上下文。
 */
@Data
@AllArgsConstructor
public class LoginUser {

    private Long userId;
    private String username;
    private String role;
}
