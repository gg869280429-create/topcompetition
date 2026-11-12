package com.cie.competition.module.user.vo;

public record ProfileVO(Long id, String username, String realName, Long majorId,
                               String majorName, String email, String phone) {}
