package com.cie.competition.module.competition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 竞赛实体，对应 competition 表。
 */
@Data
@TableName("competition")
public class Competition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long majorId;

    private String title;

    /** 赛制：PERSONAL 个人赛 / TEAM 团队赛 */
    private String competitionType;

    /** 级别：SCHOOL / CITY / PROVINCE / NATIONAL / INTERNATIONAL */
    private String level;

    private String category;

    private String description;

    private String organizer;

    private Long coverFileId;

    private Long documentFileId;

    private LocalDateTime registrationStartTime;

    private LocalDateTime registrationEndTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer maxMembers;

    private Integer maxTeachers;

    /** 是否允许跨专业组队：1 是，0 否 */
    private Integer allowCrossMajor;

    /** 状态：DRAFT / REGISTERING / IN_PROGRESS / ENDED / CANCELLED */
    private String status;

    private Long publisherId;

    private Long managerId;

    private String cancellationReason;

    private LocalDateTime cancelledAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
