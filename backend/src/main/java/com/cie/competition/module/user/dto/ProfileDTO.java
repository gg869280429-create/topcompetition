package com.cie.competition.module.user.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProfileDTO {
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过64个字符")
    private String realName;
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;
    @Pattern(regexp = "^[0-9+()\\-\\s]{0,32}$", message = "联系电话格式不正确")
    private String phone;
}
