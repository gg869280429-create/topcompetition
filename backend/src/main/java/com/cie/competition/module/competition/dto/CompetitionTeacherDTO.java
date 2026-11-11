package com.cie.competition.module.competition.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompetitionTeacherDTO {
    @NotNull(message = "请选择教师")
    private Long teacherId;
    @NotNull(message = "请设置名额")
    @Min(value = 1, message = "指导名额至少为1")
    private Integer quota;
}
