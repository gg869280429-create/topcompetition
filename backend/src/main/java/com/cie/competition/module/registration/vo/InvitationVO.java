package com.cie.competition.module.registration.vo;
@lombok.Data
public class InvitationVO {
 private Long id;
 private Long registrationId;
 private Long inviterId;
 private String inviterName;
 private Long inviteeId;
 private String teamName;
 private String competitionTitle;
 private String status;
 private java.time.LocalDateTime createTime;
 private java.time.LocalDateTime respondedTime;
}
