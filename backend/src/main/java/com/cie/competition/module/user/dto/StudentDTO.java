package com.cie.competition.module.user.dto;
@lombok.Data
public class StudentDTO {
 @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) @jakarta.validation.constraints.Pattern(regexp="[a-zA-Z0-9_]+") private String username;
 @jakarta.validation.constraints.Size(max=72) private String password;
 @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) private String realName;
 @jakarta.validation.constraints.NotNull private Long majorId;
 @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(1) private Integer status;
}
