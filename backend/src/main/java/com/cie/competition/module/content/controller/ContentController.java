package com.cie.competition.module.content.controller;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.Result;
import com.cie.competition.module.content.dto.ContentDTO;
import com.cie.competition.module.content.dto.ContentQueryDTO;
import com.cie.competition.module.content.service.ContentService;
import com.cie.competition.module.content.vo.ContentVO;
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
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/page")
    public Result<PageResult<ContentVO>> page(ContentQueryDTO query) {
        return Result.success(contentService.page(query));
    }

    @GetMapping("/{id}")
    public Result<ContentVO> getById(@PathVariable Long id) {
        return Result.success(contentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Long> create(@Valid @RequestBody ContentDTO dto) {
        return Result.success(contentService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ContentDTO dto) {
        contentService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        contentService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        contentService.updateStatus(id, status);
        return Result.success();
    }
}
