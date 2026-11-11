package com.cie.competition.module.award.controller;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.Result;
import com.cie.competition.module.award.dto.AwardDTO;
import com.cie.competition.module.award.dto.AwardQueryDTO;
import com.cie.competition.module.award.dto.AwardReviewDTO;
import com.cie.competition.module.award.service.AwardService;
import com.cie.competition.module.award.vo.AwardVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/award")
@RequiredArgsConstructor
public class AwardController {

    private final AwardService awardService;
    private final com.cie.competition.module.file.service.FileService fileService;

    @GetMapping("/{id}/history")
    public Result<java.util.List<com.cie.competition.module.history.entity.BusinessHistory>> history(@PathVariable Long id) {
        return Result.success(awardService.history(id));
    }

    @GetMapping("/{id}/certificate")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> certificate(@PathVariable Long id) {
        var award = awardService.getById(id);
        return fileService.download(award.getCertificateFileId(), "AWARD_CERTIFICATE");
    }

    @PutMapping("/{id}/correction")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> correct(@PathVariable Long id, @Valid @RequestBody AwardDTO dto) {
        awardService.correct(id, dto);
        return Result.success();
    }

    @PutMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> revoke(@PathVariable Long id, @RequestBody java.util.Map<String,String> dto) {
        awardService.revoke(id, dto.get("reason"));
        return Result.success();
    }

    @GetMapping("/page")
    public Result<PageResult<AwardVO>> page(AwardQueryDTO query) {
        return Result.success(awardService.page(query));
    }

    @GetMapping("/{id}")
    public Result<AwardVO> getById(@PathVariable Long id) {
        return Result.success(awardService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Long> create(@Valid @RequestBody AwardDTO dto) {
        return Result.success(awardService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AwardDTO dto) {
        awardService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        awardService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody AwardReviewDTO dto) {
        awardService.review(id, dto);
        return Result.success();
    }
}
