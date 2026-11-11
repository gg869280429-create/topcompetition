package com.cie.competition.module.file.controller;

import com.cie.competition.common.result.Result;
import com.cie.competition.module.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {
    private final FileService service;
    @PostMapping("/award-certificate")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN','TEACHER')")
    public Result<Map<String, Object>> uploadAwardCertificate(@RequestParam MultipartFile file) throws IOException {
        var stored = service.uploadAwardCertificate(file);
        return Result.success(Map.of("id", stored.getId(), "name", stored.getOriginalName()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Map<String, Object>> upload(@RequestParam MultipartFile file) throws IOException {
        var stored = service.upload(file);
        return Result.success(Map.of("id", stored.getId(), "name", stored.getOriginalName()));
    }
    @PostMapping("/content-cover")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Map<String,Object>> contentCover(@RequestParam MultipartFile file) throws IOException {
        var stored=service.uploadContentCover(file); return Result.success(Map.of("id",stored.getId(),"name",stored.getOriginalName()));
    }
    @PostMapping("/award-material")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN','TEACHER')")
    public Result<Map<String,Object>> awardMaterial(@RequestParam MultipartFile file,@RequestParam String bizType) throws IOException {
        var stored=service.uploadAwardMaterial(file,bizType); return Result.success(Map.of("id",stored.getId(),"name",stored.getOriginalName()));
    }}
