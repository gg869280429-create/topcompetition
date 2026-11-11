package com.cie.competition.module.guidance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指导关系实体（师生匹配），对应 guidance 表。
 */
@Data
@TableName("guidance")
public class Guidance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long registrationId;

    private Long competitionId;

    private Long teacherId;

    /** 状态：ACTIVE 指导中 / ENDED 已结束 */
    private String status;

    private Long assignerId;

    private LocalDateTime assignTime;

    private Integer activeKey;

    private LocalDateTime endedTime;

    private String endReason;
    private String membersSnapshot;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
