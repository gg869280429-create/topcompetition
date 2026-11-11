package com.cie.competition.module.major.dto;
@lombok.Data
public class MajorDTO {
 @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) private String name;
}
