package com.cie.competition.module.team.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record TeamCreateDTO(@NotBlank @Size(max=128) String name, @NotNull List<@NotNull Long> memberIds) {}
