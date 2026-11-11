package com.cie.competition.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 内容新增 / 编辑请求对象。
 */
@Data
public class ContentDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题长度不能超过128个字符")
    private String title;

    private String content;

    private Long coverFileId;
    @jakarta.validation.constraints.Size(max=2048, message="官网地址不能超过2048个字符")
    private String officialUrl;
    private Boolean carouselEnabled = false;
    @jakarta.validation.constraints.Min(0)
    private Integer sortOrder = 0;


    @NotBlank(message = "分类不能为空")
    private String category;
}
