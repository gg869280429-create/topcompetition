package com.cie.competition.module.user.controller;

import com.cie.competition.common.result.Result;
import com.cie.competition.module.user.dto.PasswordChangeDTO;
import com.cie.competition.module.user.dto.ProfileDTO;
import com.cie.competition.module.user.service.ProfileService;
import com.cie.competition.module.user.vo.ProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
public class ProfileController {
    private final ProfileService service;

    @GetMapping
    public Result<ProfileVO> get() { return Result.success(service.get()); }

    @PutMapping
    public Result<ProfileVO> update(@Valid @RequestBody ProfileDTO dto) { return Result.success(service.update(dto)); }

    @PutMapping("/password")
    public Result<Void> password(@Valid @RequestBody PasswordChangeDTO dto) {
        service.changePassword(dto);
        return Result.success();
    }
}
