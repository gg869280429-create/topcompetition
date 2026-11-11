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
        requireRoles("ADMIN", "MAJOR_ADMIN"); return upload(upload, "COMPETITION_DOCUMENT");
    }

    public StoredFile uploadAwardCertificate(MultipartFile upload) throws IOException {
        return uploadAwardMaterial(upload, "AWARD_CERTIFICATE");
    }

    public StoredFile uploadContentCover(MultipartFile file) throws IOException {
        requireRoles("ADMIN", "MAJOR_ADMIN"); return upload(file, "CONTENT_COVER");
    }
    public StoredFile uploadAwardMaterial(MultipartFile file, String bizType) throws IOException {
        requireRoles("ADMIN", "MAJOR_ADMIN", "TEACHER");
        if (!Set.of("AWARD_CERTIFICATE", "AWARD_OFFICIAL_LIST", "AWARD_REGISTRATION_SCREENSHOT", "AWARD_PROCESS_PHOTO").contains(bizType)) throw new BusinessException("非法的成果材料类型");
        return upload(file, bizType);
    }
    private void requireRoles(String... roles) {
        var user = UserContext.get();
        if (user == null || !Arrays.asList(roles).contains(user.getRole())) throw new BusinessException(403, "无权操作该文件");
    }
    private StoredFile upload(MultipartFile upload, String bizType) throws IOException {
        if (upload.isEmpty() || upload.getSize() > 10 * 1024 * 1024) throw new BusinessException("请选择不超过10MB的文件");
        String original = upload.getOriginalFilename();
        if (original == null) throw new BusinessException("文件名不能为空");
        String name = original.replace('\\', '/'); name = name.substring(name.lastIndexOf('/') + 1);
        if (name.length() > 255) throw new BusinessException("文件名过长");
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        boolean image = Set.of("png", "jpg", "jpeg", "webp").contains(ext);
        boolean imageOnly = Set.of("CONTENT_COVER", "AWARD_REGISTRATION_SCREENSHOT", "AWARD_PROCESS_PHOTO").contains(bizType);
        boolean allowed = imageOnly ? image : "COMPETITION_DOCUMENT".equals(bizType) ? Set.of("pdf", "doc", "docx").contains(ext) : "AWARD_OFFICIAL_LIST".equals(bizType) ? image || "pdf".equals(ext) : image || Set.of("pdf", "doc", "docx").contains(ext);
        if (!allowed) throw new BusinessException(imageOnly ? "该材料必须为PNG、JPEG或WEBP图片" : "文件格式不受支持");
        byte[] bytes = upload.getBytes();
        boolean valid = switch (ext) {
            case "pdf" -> bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
            case "doc" -> bytes.length >= 4 && (bytes[0] & 255) == 0xD0 && (bytes[1] & 255) == 0xCF && (bytes[2] & 255) == 0x11 && (bytes[3] & 255) == 0xE0;
            case "png" -> bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes,8), new byte[]{(byte)137,80,78,71,13,10,26,10});
            case "jpg", "jpeg" -> bytes.length >= 3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
            case "webp" -> bytes.length >= 12 && new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP");
            default -> bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
        };
        if (!valid) throw new BusinessException("文件内容与扩展名不匹配");
        Files.createDirectories(directory);
        String storageName = UUID.randomUUID() + "." + ext;
        Path target = directory.resolve(storageName);
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        StoredFile file = new StoredFile(); file.setOriginalName(name); file.setStorageName(storageName);
        file.setUrl("private/" + storageName); file.setSize(upload.getSize()); file.setContentType(image ? (ext.equals("jpg") || ext.equals("jpeg") ? "image/jpeg" : "image/"+ext) : upload.getContentType());
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

    public void validateAwardCertificate(Long id) { validateAwardMaterial(id, "AWARD_CERTIFICATE"); }
    public void validateAwardMaterial(Long id, String bizType) {
        requireRoles("ADMIN", "MAJOR_ADMIN", "TEACHER");
        var file = get(id, bizType);
        if (!"ADMIN".equals(UserContext.get().getRole()) && !UserContext.getUserId().equals(file.getUploaderId())) throw new BusinessException(403, "无权关联其他用户的材料");
    }
    public void validateContentCover(Long id) {
        requireRoles("ADMIN", "MAJOR_ADMIN");
        var file = get(id, "CONTENT_COVER");
        if (!"ADMIN".equals(UserContext.get().getRole()) && !UserContext.getUserId().equals(file.getUploaderId())) throw new BusinessException(403,"无权关联其他用户的封面");
    }
    public ResponseEntity<org.springframework.core.io.Resource> contentCover(Long id) {
        var file = get(id,"CONTENT_COVER");
        Path path = directory.resolve(file.getStorageName()).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) throw new BusinessException("封面图片已丢失");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline").header("X-Content-Type-Options","nosniff").body(new FileSystemResource(path));
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
