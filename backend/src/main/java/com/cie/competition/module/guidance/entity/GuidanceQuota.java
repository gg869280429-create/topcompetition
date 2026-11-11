package com.cie.competition.module.guidance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指导教师名额实体，对应 guidance_quota 表。
 */
@Data
@TableName("guidance_quota")
public class GuidanceQuota {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long competitionId;

    private Long teacherId;

    private Integer quota;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
