package com.cie.competition.module.registration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class TeamMembersDTO {
    @NotNull(message="请选择队员，队长不需重复添加")
    private List<@NotNull Long> memberIds;
    @Size(max=1000,message="调整说明不能超过1000字")
    private String reason;
}
