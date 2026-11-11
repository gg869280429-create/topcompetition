package com.cie.competition.module.registration.entity;
@lombok.Data
@com.baomidou.mybatisplus.annotation.TableName("withdrawal_request")
public class WithdrawalRequest {
 @com.baomidou.mybatisplus.annotation.TableId(type=com.baomidou.mybatisplus.annotation.IdType.AUTO)
 private Long id;
 private Long registrationId;
 private String status;
 private String reason;
 private Long reviewerId;
 private String reviewComment;
 private java.time.LocalDateTime createTime;
 private java.time.LocalDateTime reviewTime;
}
