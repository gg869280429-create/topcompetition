package com.cie.competition.module.user.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cie.competition.common.config.AvatarOssProperties;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.ResultCode;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAvatarService {
    private final UserMapper users;
    private final ObjectProvider<OSS> ossClient;
    private final AvatarOssProperties properties;
    private final PlatformTransactionManager transactions;

    public String upload(MultipartFile file) {
        Long userId = UserContext.getUserId();
        User user = users.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()))
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        OSS oss = properties.isEnabled() ? ossClient.getIfAvailable() : null;
        if (oss == null) throw new BusinessException(503, "头像存储尚未配置，请联系管理员");
        if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
            throw new BusinessException("请选择不超过2MB的PNG或JPEG图片");
        byte[] bytes;
        String format;
        try {
            bytes = file.getBytes();
            try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new BusinessException("头像仅支持PNG或JPEG图片");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if (!java.util.Set.of("png", "jpeg").contains(format))
                        throw new BusinessException("头像仅支持PNG或JPEG图片");
                    if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096)
                        throw new BusinessException("头像宽高均不能超过4096像素");
                    if (reader.read(0) == null) throw new BusinessException("头像图片无法读取");
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new BusinessException("头像图片无法读取");
        }
        String key = "avatars/" + userId + "/" + UUID.randomUUID() + ("png".equals(format) ? ".png" : ".jpg");
        String address = "https://" + host() + "/" + key;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("png".equals(format) ? "image/png" : "image/jpeg");
        metadata.setContentLength(bytes.length);
        metadata.setContentDisposition("inline");
        try {
            oss.putObject(new PutObjectRequest(properties.getBucket(), key, new ByteArrayInputStream(bytes), metadata));
        } catch (RuntimeException e) {
            log.warn("头像OSS上传失败，异常类型={}", e.getClass().getSimpleName());
            throw new BusinessException(503, "头像上传失败，请稍后重试");
        }
        try {
            String signed = oss.generatePresignedUrl(properties.getBucket(), key, expires()).toString();
            // 先提交数据库事务；提交失败时只清理本次新对象，不删除之前的头像。
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                User current = users.lockById(userId);
                if (current == null || !Integer.valueOf(1).equals(current.getStatus()))
                    throw new BusinessException(ResultCode.UNAUTHORIZED);
                if (users.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                        .set(User::getAvatarUrl, address)) != 1) throw new BusinessException("头像保存失败");
            });
            return signed;
        } catch (RuntimeException e) {
            try { oss.deleteObject(properties.getBucket(), key); }
            catch (RuntimeException cleanup) { log.warn("本次头像上传回滚清理失败，异常类型={}", cleanup.getClass().getSimpleName()); }
            if (e instanceof BusinessException business) throw business;
            log.warn("头像地址保存失败，异常类型={}", e.getClass().getSimpleName());
            throw new BusinessException(503, "头像保存失败，请稍后重试");
        }
    }

    public String signedUrl(String address) {
        if (address == null || address.isBlank() || !properties.isEnabled()) return null;
        OSS oss = ossClient.getIfAvailable();
        if (oss == null) return null;
        try {
            URI uri = URI.create(address);
            if (!"https".equals(uri.getScheme()) || !host().equals(uri.getRawAuthority())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !uri.getRawPath().matches("/avatars/[0-9]+/[A-Za-z0-9-]+\\.(png|jpg)")) return null;
            return oss.generatePresignedUrl(properties.getBucket(), uri.getRawPath().substring(1), expires()).toString();
        } catch (RuntimeException e) {
            log.warn("头像临时地址生成失败，异常类型={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String host() { return properties.getBucket() + "." + URI.create(properties.getEndpoint()).getHost(); }
    private Date expires() { return new Date(System.currentTimeMillis() + 15 * 60 * 1000); }
}
