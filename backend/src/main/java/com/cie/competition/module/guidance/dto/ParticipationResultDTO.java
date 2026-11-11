package com.cie.competition.module.guidance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ParticipationResultDTO {
    @NotNull private Boolean participated;
    @NotNull private Boolean won;
    @Size(max=1000) private String remark;
}
