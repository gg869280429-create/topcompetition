package com.cie.competition.module.competition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CompetitionBatchDTO {
    @NotEmpty(message = "请至少选择一种赛制")
    @Size(max = 2, message = "最多同时创建两种赛制")
    private List<@NotNull @Valid CompetitionDTO> competitions;
}
