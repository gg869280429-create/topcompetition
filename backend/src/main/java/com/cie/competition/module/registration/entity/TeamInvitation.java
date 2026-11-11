package com.cie.competition.module.registration.entity;
@lombok.Data
@com.baomidou.mybatisplus.annotation.TableName("team_invitation")
public class TeamInvitation {
 @com.baomidou.mybatisplus.annotation.TableId(type=com.baomidou.mybatisplus.annotation.IdType.AUTO)
 private Long id;
 private Long registrationId;
 private Long inviterId;
 private Long inviteeId;
 private String status;
 private java.time.LocalDateTime createTime;
 private java.time.LocalDateTime respondedTime;
}
