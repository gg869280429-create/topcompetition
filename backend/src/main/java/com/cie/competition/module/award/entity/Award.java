package com.cie.competition.module.award.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 获奖实体，对应 award 表。
 */
@Data
@TableName("award")
public class Award {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long competitionId;

    private Long registrationId;

    /** 奖项等级：SPECIAL / FIRST / SECOND / THIRD / EXCELLENT */
    private String awardLevel;

    private Long certificateFileId;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    private LocalDateTime awardTime;

    private Long registrantId;
    private LocalDateTime reviewTime;
    private Long majorId;
    private String majorNameSnapshot;
    private String teamNameSnapshot;
    private String participantsSnapshot;
    private String revocationReason;

    private Long reviewerId;

    private String reviewComment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
