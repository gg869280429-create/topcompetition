package com.cie.competition.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.avatar-oss")
public class AvatarOssProperties {
    private boolean enabled;
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
}
