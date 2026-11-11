package com.cie.competition.module.history.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BusinessHistory {
    private Long id;
    private String bizType;
    private Long bizId;
    private String operation;
    private Long actorId;
    private String actorName;
    private LocalDateTime operationTime;
    private String beforeJson;
    private String afterJson;
    private String reason;
}
