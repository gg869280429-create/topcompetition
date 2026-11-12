package com.cie.competition.module.competition.dto;

import com.cie.competition.module.content.dto.ContentDTO;
import jakarta.validation.Valid;
import lombok.Data;

@Data
public class CompetitionPublishDTO {
    private Boolean syncNews = false;
    @Valid
    private ContentDTO news;
}
