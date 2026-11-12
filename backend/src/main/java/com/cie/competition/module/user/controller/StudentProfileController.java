package com.cie.competition.module.user.controller;

import com.cie.competition.common.result.Result;
import com.cie.competition.module.user.dto.StudentPasswordDTO;
import com.cie.competition.module.user.dto.StudentProfileDTO;
import com.cie.competition.module.user.service.StudentProfileService;
import com.cie.competition.module.user.vo.StudentProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentProfileController {
    private final StudentProfileService service;

    @GetMapping
    public Result<StudentProfileVO> get() { return Result.success(service.get()); }

    @PutMapping
    public Result<StudentProfileVO> update(@Valid @RequestBody StudentProfileDTO dto) {
        return Result.success(service.update(dto));
    }

    @PutMapping("/password")
    public Result<Void> password(@Valid @RequestBody StudentPasswordDTO dto) {
        service.changePassword(dto);
        return Result.success();
    }
}
