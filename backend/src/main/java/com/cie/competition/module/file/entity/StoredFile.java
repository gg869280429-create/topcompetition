package com.cie.competition.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_file")
public class StoredFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String originalName;
    private String storageName;
    private String url;
    private Long size;
    private String contentType;
    private String bizType;
    private Long uploaderId;
}
