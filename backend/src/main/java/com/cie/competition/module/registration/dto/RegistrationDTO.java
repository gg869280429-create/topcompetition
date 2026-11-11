package com.cie.competition.module.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 报名创建 / 编辑请求对象。成员列表中不含队长，队长为当前登录学生。
 */
@Data
public class RegistrationDTO {

    @NotNull(message = "竞赛不能为空")
    private Long competitionId;

    @NotNull(message = "请选择一位意向教师")
    private Long preferredTeacherId;

    @NotBlank(message = "团队名称不能为空")
    @Size(max = 128, message = "团队名称长度不能超过128个字符")
    private String teamName;

    private List<Long> memberIds;
}
