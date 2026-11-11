package com.cie.competition.module.user.controller;

import com.cie.competition.common.result.*;
import com.cie.competition.module.user.dto.TeacherDTO;
import com.cie.competition.module.user.service.TeacherService;
import com.cie.competition.module.user.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
public class TeacherController {
    private final TeacherService service;
    @GetMapping("/page")
    public Result<PageResult<TeacherVO>> page(@RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="1") @Min(1) long current,
            @RequestParam(defaultValue="10") @Min(1) @Max(100) long size) { return Result.success(service.page(keyword, current, size)); }
    @GetMapping("/majors")
    public Result<List<MajorVO>> majors() { return Result.success(service.majors()); }
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TeacherDTO dto) { return Result.success(service.save(null, dto)); }
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody TeacherDTO dto) { return Result.success(service.save(id, dto)); }
}
