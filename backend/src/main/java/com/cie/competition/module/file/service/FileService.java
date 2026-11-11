package com.cie.competition.module.file.service;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.file.entity.StoredFile;
import com.cie.competition.module.file.mapper.StoredFileMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Service
public class FileService {
    private final StoredFileMapper mapper;
    private final Path directory;

    public FileService(StoredFileMapper mapper, @Value("${app.upload-directory:./uploads}") String directory) {
        this.mapper = mapper; this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public StoredFile upload(MultipartFile upload) throws IOException {
        return upload(upload, "COMPETITION_DOCUMENT");
    }

    public StoredFile uploadAwardCertificate(MultipartFile upload) throws IOException {
        var user = UserContext.get();
        if (user == null || !("ADMIN".equals(user.getRole()) || "MAJOR_ADMIN".equals(user.getRole())))
            throw new BusinessException(403, "无权上传获奖证明");
        return upload(upload, "AWARD_CERTIFICATE");
    }

    private StoredFile upload(MultipartFile upload, String bizType) throws IOException {
        if (upload.isEmpty() || upload.getSize() > 10 * 1024 * 1024) throw new BusinessException("请选择不超过10MB的文件");
        String original = upload.getOriginalFilename();
        if (original == null) throw new BusinessException("文件名不能为空");
        String name = original.replace('\\', '/'); name = name.substring(name.lastIndexOf('/') + 1);
        if (name.length() > 255) throw new BusinessException("文件名过长");
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("pdf", "doc", "docx").contains(ext)) throw new BusinessException("仅支持PDF、DOC、DOCX文件");
        byte[] bytes = upload.getBytes();
        boolean valid = switch (ext) {
            case "pdf" -> bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
            case "doc" -> bytes.length >= 4 && (bytes[0] & 255) == 0xD0 && (bytes[1] & 255) == 0xCF && (bytes[2] & 255) == 0x11 && (bytes[3] & 255) == 0xE0;
            default -> bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
        };
        if (!valid) throw new BusinessException("文件内容与扩展名不匹配");
        Files.createDirectories(directory);
        String storageName = UUID.randomUUID() + "." + ext;
        Path target = directory.resolve(storageName);
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        StoredFile file = new StoredFile(); file.setOriginalName(name); file.setStorageName(storageName);
        file.setUrl("private/" + storageName); file.setSize(upload.getSize()); file.setContentType(upload.getContentType());
        file.setBizType(bizType); file.setUploaderId(UserContext.getUserId());
        try { mapper.insert(file); } catch (RuntimeException e) { Files.deleteIfExists(target); throw e; }
        return file;
    }

    public StoredFile get(Long id) {
        return get(id, "COMPETITION_DOCUMENT");
    }

    public StoredFile get(Long id, String bizType) {
        StoredFile file = id == null ? null : mapper.selectById(id);
        if (file == null || !bizType.equals(file.getBizType())) throw new BusinessException("文件不存在或用途不匹配");
        return file;
    }

    public void validateAttachment(Long id) {
        if (id == null) return;
        StoredFile file = get(id);
        if (!"ADMIN".equals(UserContext.get().getRole()) && !UserContext.getUserId().equals(file.getUploaderId()))
            throw new BusinessException(403, "无权关联其他管理员的文件");
    }

    public void validateAwardCertificate(Long id) {
        StoredFile file = get(id, "AWARD_CERTIFICATE");
        var user = UserContext.get();
        if (user == null || !("ADMIN".equals(user.getRole()) || "MAJOR_ADMIN".equals(user.getRole())))
            throw new BusinessException(403, "无权关联获奖证明");
        if (!"ADMIN".equals(user.getRole()) && !user.getUserId().equals(file.getUploaderId()))
            throw new BusinessException(403, "无权关联其他管理员的文件");
    }

    public ResponseEntity<org.springframework.core.io.Resource> download(Long id) {
        return download(id, "COMPETITION_DOCUMENT");
    }

    public ResponseEntity<org.springframework.core.io.Resource> download(Long id, String bizType) {
        var file = get(id, bizType);
        Path path = directory.resolve(file.getStorageName()).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) throw new BusinessException("竞赛文件已丢失，请联系管理员");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }
}
