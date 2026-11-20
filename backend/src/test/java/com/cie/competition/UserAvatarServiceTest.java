package com.cie.competition;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.cie.competition.common.config.AvatarOssProperties;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import com.cie.competition.module.user.service.UserAvatarService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAvatarServiceTest {
    UserMapper users = mock(UserMapper.class);
    OSS oss = mock(OSS.class);
    @SuppressWarnings("unchecked") ObjectProvider<OSS> provider = mock(ObjectProvider.class);
    AvatarOssProperties properties = new AvatarOssProperties();
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    UserAvatarService service = new UserAvatarService(users, provider, properties, transactions);

    @BeforeEach void fixture() throws Exception {
        properties.setEnabled(true); properties.setBucket("avatar-tests");
        properties.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        when(provider.getIfAvailable()).thenReturn(oss);
        when(oss.generatePresignedUrl(anyString(), anyString(), any(Date.class))).thenReturn(new URL("https://signed.example/avatar"));
        User user = new User(); user.setId(3L); user.setStatus(1);
        when(users.selectById(3L)).thenReturn(user); when(users.lockById(3L)).thenReturn(user);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        UserContext.set(new com.cie.competition.common.security.LoginUser(3L, "fixture", "STUDENT"));
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    MockMultipartFile image(String format) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB),format,out);
        return new MockMultipartFile("file", "untrusted.svg", "image/svg+xml", out.toByteArray());
    }
    @Test void disabledStorageDoesNotAttemptUploadOrBreakAvatarRead() throws Exception {
        properties.setEnabled(false);
        assertEquals(503, assertThrows(BusinessException.class, () -> service.upload(image("png"))).getCode());
        assertNull(service.signedUrl("https://example.com/avatars/3/old.png")); verifyNoInteractions(oss);
    }
    @Test void failedDatabaseCommitCleansOnlyTheNewObject() throws Exception {
        when(users.update(isNull(), any())).thenReturn(1);
        doThrow(new org.springframework.transaction.TransactionSystemException("simulated commit failure")).when(transactions).commit(any());
        assertEquals(503, assertThrows(BusinessException.class, () -> service.upload(image("png"))).getCode());
        var request = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(oss).putObject(request.capture());
        verify(oss).deleteObject("avatar-tests", request.getValue().getKey());
        assertTrue(request.getValue().getKey().startsWith("avatars/3/"));
    }
    @Test void signsOnlyOurCanonicalAvatarUrlsAndExpiresWithinFifteenMinutes() {
        for (String address : java.util.List.of("https://evil.example/avatars/3/a.png",
                "https://avatar-tests.oss-cn-hangzhou.aliyuncs.com/avatars/3/a.png?stale=true",
                "https://avatar-tests.oss-cn-hangzhou.aliyuncs.com/private/a.png",
                "https://avatar-tests.oss-cn-hangzhou.aliyuncs.com/avatars/3/../a.png")) assertNull(service.signedUrl(address));
        verifyNoInteractions(oss);
        long before = System.currentTimeMillis();
        assertNotNull(service.signedUrl("https://avatar-tests.oss-cn-hangzhou.aliyuncs.com/avatars/3/a.png"));
        var expiry = org.mockito.ArgumentCaptor.forClass(Date.class);
        verify(oss).generatePresignedUrl(eq("avatar-tests"), eq("avatars/3/a.png"), expiry.capture());
        assertTrue(expiry.getValue().getTime() >= before + 899000);
        assertTrue(expiry.getValue().getTime() <= System.currentTimeMillis() + 900000);
    }
    @Test void jpegUsesDecodedTypeRatherThanUntrustedFilenameAndMimeType() throws Exception {
        when(users.update(isNull(), any())).thenReturn(1);
        service.upload(image("jpeg"));
        var request = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(oss).putObject(request.capture());
        assertTrue(request.getValue().getKey().endsWith(".jpg"));
        assertEquals("image/jpeg", request.getValue().getMetadata().getContentType());
    }
}
