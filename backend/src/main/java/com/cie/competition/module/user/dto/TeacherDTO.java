package com.cie.competition.module.user.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TeacherDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64)
    @Pattern(regexp = "[a-zA-Z0-9_]+", message = "用户名仅支持字母、数字和下划线")
    private String username;
    @Size(max = 72)
    private String password;
    @NotBlank(message = "教师姓名不能为空")
    @Size(max = 64)
    private String realName;
    @NotNull(message = "请选择专业")
    private Long majorId;
    @Size(max = 255)
    private String expertise;
    @Size(max = 4000)
    private String biography;
    @NotNull
    @Min(0) @Max(1)
    private Integer status;
}
