package com.cie.competition.module.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报名实体，对应 registration 表。
 */
@Data
@TableName("registration")
public class Registration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long competitionId;

    private Long preferredTeacherId;

    private String teamName;

    private Long leaderId;

    private Long majorId;
    private String leaderNameSnapshot;
    private String majorNameSnapshot;

    /** 是否跨专业：1 是，0 否 */
    private Integer crossMajor;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    private LocalDateTime applyTime;

    private LocalDateTime reviewTime;

    private Long reviewerId;

    private String reviewComment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
