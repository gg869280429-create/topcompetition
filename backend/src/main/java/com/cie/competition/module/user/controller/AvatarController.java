package com.cie.competition.module.user.controller;

import com.cie.competition.common.result.Result;
import com.cie.competition.module.user.service.UserAvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
public class AvatarController {
    private final UserAvatarService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AvatarVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(new AvatarVO(service.upload(file)));
    }

    public record AvatarVO(String avatarUrl) {}
}
