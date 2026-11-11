package com.cie.competition.common.security;

/**
 * 当前登录用户上下文，基于 ThreadLocal 保存当前请求的登录用户，请求结束后清理。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
