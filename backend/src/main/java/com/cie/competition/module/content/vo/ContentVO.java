package com.cie.competition.module.content.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容展示对象。
 */
@Data
public class ContentVO {

    private Long id;

    private String title;

    private String content;

    private java.time.LocalDateTime publishStartTime;
    private java.time.LocalDateTime publishEndTime;
    private java.time.LocalDateTime carouselStartTime;
    private java.time.LocalDateTime carouselEndTime;

    private Long coverFileId;
    private String officialUrl;
    private Boolean carouselEnabled = false;
    private Integer sortOrder = 0;


    private String category;

    private String status;

    private Long authorId;

    private String authorName;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private LocalDateTime createTime;
}
