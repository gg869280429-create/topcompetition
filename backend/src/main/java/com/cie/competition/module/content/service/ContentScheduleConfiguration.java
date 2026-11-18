package com.cie.competition.module.content.service;
@org.springframework.context.annotation.Configuration
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.content-scheduling.enabled",havingValue="true",matchIfMissing=true)
@lombok.RequiredArgsConstructor
public class ContentScheduleConfiguration {
 private final ContentScheduleService service;
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=15000)
 public void updateSchedule(){service.refresh();}
}
