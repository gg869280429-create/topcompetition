package com.cie.competition.module.content.dto;
@lombok.Data public class CarouselDTO {
 @jakarta.validation.constraints.NotNull private Boolean carouselEnabled;
 @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) private Integer sortOrder;
 private java.time.LocalDateTime carouselStartTime;
 private java.time.LocalDateTime carouselEndTime;
}
