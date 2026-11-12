package com.cie.competition.module.competition.controller;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.Result;
import com.cie.competition.module.competition.dto.CompetitionDTO;
import com.cie.competition.module.competition.dto.CompetitionQueryDTO;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.competition.vo.CompetitionVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/competition")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;
    private final com.cie.competition.module.competition.service.CompetitionManagementService managementService;
    private final com.cie.competition.module.file.service.FileService fileService;

    @GetMapping("/page")
    public Result<PageResult<CompetitionVO>> page(CompetitionQueryDTO query) {
        return Result.success(competitionService.page(query));
    }

    @GetMapping("/{id}")
    public Result<CompetitionVO> getById(@PathVariable Long id) {
        return Result.success(competitionService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Long> create(@Valid @RequestBody CompetitionDTO dto) {
        return Result.success(managementService.save(null, dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CompetitionDTO dto) {
        managementService.save(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        competitionService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/major")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> updateMajor(@PathVariable Long id,@RequestParam Long majorId) {
        competitionService.updateMajor(id,majorId);
        return Result.success();
    }

    @GetMapping("/{id}/document")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> document(@PathVariable Long id) {
        return fileService.download(competitionService.getById(id).getDocumentFileId());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status, @RequestParam(required = false) String reason) {
        competitionService.updateStatus(id, status, reason);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Long> publish(@PathVariable Long id, @Valid @RequestBody com.cie.competition.module.competition.dto.CompetitionPublishDTO dto) {
        return Result.success(managementService.publish(id, dto));
    }
}
