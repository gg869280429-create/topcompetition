package com.cie.competition.common.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(AvatarOssProperties.class)
public class AvatarOssConfiguration {
    @Bean(name = "avatarOssClient", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.avatar-oss", name = "enabled", havingValue = "true")
    public OSS avatarOssClient(AvatarOssProperties properties) throws Exception {
        URI endpoint = URI.create(properties.getEndpoint() == null ? "" : properties.getEndpoint());
        String region = properties.getRegion();
        if (region == null || !region.matches("[a-z0-9-]+")
                || !"https".equals(endpoint.getScheme())
                || !("oss-" + region + ".aliyuncs.com").equals(endpoint.getHost())
                || endpoint.getRawQuery() != null || endpoint.getRawUserInfo() != null || endpoint.getRawFragment() != null
                || endpoint.getPort() != -1 || !(endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()))
                || properties.getBucket() == null || !properties.getBucket().matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")) {
            throw new IllegalStateException("请配置头像 OSS 的公网 HTTPS Endpoint、匹配地域及 Bucket");
        }
        if (!org.springframework.util.StringUtils.hasText(properties.getAccessKeyId())
                || !org.springframework.util.StringUtils.hasText(properties.getAccessKeySecret()))
            throw new IllegalStateException("请填写头像 OSS 的 AccessKey ID 和 Secret 配置");
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        configuration.setConnectionTimeout(3000);
        configuration.setSocketTimeout(8000);
        configuration.setMaxErrorRetry(0);
        return OSSClientBuilder.create().endpoint(properties.getEndpoint()).region(region)
                .credentialsProvider(new DefaultCredentialProvider(properties.getAccessKeyId(), properties.getAccessKeySecret()))
                .clientConfiguration(configuration).build();
    }
}
