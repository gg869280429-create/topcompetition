package com.cie.competition.module.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 消息发送请求对象。
 */
@Data
public class MessageDTO {

    @NotNull(message = "接收人不能为空")
    private Long receiverId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题长度不能超过128个字符")
    private String title;

    private String content;

    private String type;
}
