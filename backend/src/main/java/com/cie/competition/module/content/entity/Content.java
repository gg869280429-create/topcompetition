package com.cie.competition.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容实体（新闻 / 通知 / 公告），对应 content 表。
 */
@Data
@TableName("content")
public class Content {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private Long coverFileId;
    private String officialUrl;
    private Boolean carouselEnabled = false;
    private Integer sortOrder = 0;


    /** 分类：NOTICE 通知 / NEWS 新闻 / ANNOUNCEMENT 公告 */
    private String category;

    /** 状态：DRAFT 草稿 / PUBLISHED 已发布 */
    private String status;

    private Long authorId;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
