package com.cie.competition;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.cie.competition.common.config.AvatarOssConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarOssConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AvatarOssConfiguration.class);
    @Test void disabledByDefaultNeedsNoCloudConfiguration() {
        runner.run(context -> { assertThat(context).hasNotFailed(); assertThat(context).doesNotHaveBean(OSS.class); });
    }
    @Test void enablingWithoutEndpointAndBucketFailsClearly() {
        runner.withPropertyValues("app.avatar-oss.enabled=true").run(context -> assertThat(context).hasFailed());
    }
    @Test void configuredCredentialsAreUsedBySdk() {
        runner.withPropertyValues("app.avatar-oss.enabled=true",
                "app.avatar-oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                "app.avatar-oss.region=cn-hangzhou", "app.avatar-oss.bucket=avatar-tests",
                "app.avatar-oss.access-key-id=configuration-test-id",
                "app.avatar-oss.access-key-secret=configuration-test-secret").run(context -> {
                    org.junit.jupiter.api.Assertions.assertNull(context.getStartupFailure(), "Configured OSS credentials must be usable without environment variables");
                    OSS sdk = context.getBean(OSS.class);
                    var url = sdk.generatePresignedUrl("avatar-tests", "avatars/3/a.png", new Date(System.currentTimeMillis()+900000));
                    org.junit.jupiter.api.Assertions.assertTrue(url.getQuery().contains("configuration-test-id"), "SDK must sign with the configured identity");
                });
    }
    @Test void realSdkCanGeneratePrivateV4ImageUrlWithoutNetworkRequests() {
        var configuration = new ClientBuilderConfiguration(); configuration.setSignatureVersion(SignVersion.V4);
        OSS sdk = OSSClientBuilder.create().endpoint("https://oss-cn-hangzhou.aliyuncs.com").region("cn-hangzhou")
                .credentialsProvider(new DefaultCredentialProvider("test-only-id", "test-only-secret"))
                .clientConfiguration(configuration).build();
        try {
            var url = sdk.generatePresignedUrl("avatar-tests", "avatars/3/a.png", new Date(System.currentTimeMillis()+900000));
            assertThat(url.getHost()).isEqualTo("avatar-tests.oss-cn-hangzhou.aliyuncs.com");
            assertThat(url.getQuery()).contains("x-oss-signature-version=OSS4-HMAC-SHA256");
            int seconds = Integer.parseInt(java.util.Arrays.stream(url.getQuery().split("&"))
                    .filter(part -> part.startsWith("x-oss-expires=")).findFirst().orElseThrow().substring("x-oss-expires=".length()));
            assertThat(seconds).isBetween(895, 900);
        } finally { sdk.shutdown(); }
    }
}
