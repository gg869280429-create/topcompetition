package com.cie.competition.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeDTO {
    @NotBlank(message = "请输入新密码") @Size(min = 8, max = 72, message = "新密码长度需为8至72个字符")
    private String newPassword;
    @NotBlank(message = "请确认新密码") @Size(max = 72)
    private String confirmPassword;
}
