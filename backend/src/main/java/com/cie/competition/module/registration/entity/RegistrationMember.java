package com.cie.competition.module.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队成员实体，对应 registration_member 表。
 */
@Data
@TableName("registration_member")
public class RegistrationMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long registrationId;

    private Long competitionId;

    private Long userId;
    private Integer activeKey;
    private Integer currentMember;
    private String nameSnapshot;
    private Long majorIdSnapshot;
    private String majorNameSnapshot;

    /** 角色：LEADER 队长 / MEMBER 成员 */
    private String memberRole;

    private LocalDateTime joinTime;
}
