package com.cie.competition.module.content.service;
@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class ContentScheduleService {
 private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 @org.springframework.transaction.annotation.Transactional
 public void refresh(){
  jdbc.update("UPDATE content SET status='DRAFT' WHERE status IN ('PUBLISHED','SCHEDULED') AND publish_end_time<=NOW()");
  jdbc.update("UPDATE content SET status='PUBLISHED',publish_time=COALESCE(publish_start_time,NOW()) WHERE status='SCHEDULED' AND (publish_start_time IS NULL OR publish_start_time<=NOW()) AND (publish_end_time IS NULL OR publish_end_time>NOW())");
  jdbc.update("UPDATE content SET carousel_enabled=0 WHERE carousel_enabled=1 AND carousel_end_time<=NOW()");
 }
}
