package com.cie.competition.module.guidance.controller;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.Result;
import com.cie.competition.module.guidance.dto.GuidanceAssignDTO;
import com.cie.competition.module.guidance.dto.GuidanceQuotaDTO;
import com.cie.competition.module.guidance.service.GuidanceService;
import com.cie.competition.module.guidance.vo.GuidanceQuotaVO;
import com.cie.competition.module.guidance.vo.GuidanceVO;
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

import java.util.List;

@RestController
@RequestMapping("/api/guidance")
@RequiredArgsConstructor
public class GuidanceController {

    private final GuidanceService guidanceService;
    private final com.cie.competition.module.guidance.service.GuidanceCompletionService completion;

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Void> complete(@PathVariable Long id) { completion.complete(id);return Result.success(); }

    @GetMapping("/{id}/results")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<?> results(@PathVariable Long id) { return Result.success(completion.results(id)); }

    @PutMapping("/{id}/results/team")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Void> teamResult(@PathVariable Long id,@Valid @RequestBody com.cie.competition.module.guidance.dto.ParticipationResultDTO dto) {
        completion.saveTeamResult(id,dto);return Result.success();
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/results/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Void> saveResult(@PathVariable Long id,@PathVariable Long studentId,@Valid @RequestBody com.cie.competition.module.guidance.dto.ParticipationResultDTO dto) { completion.saveResult(id,studentId,dto);return Result.success(); }

    @GetMapping("/teacher/{teacherId}/quotas")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<?> teacherQuotas(@PathVariable Long teacherId) { return Result.success(completion.teacherQuotas(teacherId)); }

    @PostMapping("/teacher/{teacherId}/quotas/{competitionId}/increase")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> increaseQuota(@PathVariable Long teacherId,@PathVariable Long competitionId,@Valid @RequestBody com.cie.competition.module.guidance.dto.QuotaIncreaseDTO dto) { completion.increaseQuota(teacherId,competitionId,dto.getAmount());return Result.success(); }

    @GetMapping("/quota/page")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<PageResult<GuidanceQuotaVO>> quotaPage(@RequestParam(required = false) Long competitionId,
                                                         @RequestParam(defaultValue = "1") long current,
                                                         @RequestParam(defaultValue = "10") long size) {
        return Result.success(guidanceService.quotaPage(competitionId, current, size));
    }

    @PostMapping("/quota")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Long> saveQuota(@Valid @RequestBody GuidanceQuotaDTO dto) {
        return Result.success(guidanceService.saveQuota(dto));
    }

    @DeleteMapping("/quota/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> deleteQuota(@PathVariable Long id) {
        guidanceService.deleteQuota(id);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<List<GuidanceVO>> listByRegistration(@RequestParam Long registrationId) {
        return Result.success(guidanceService.listByRegistration(registrationId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Long> assign(@Valid @RequestBody GuidanceAssignDTO dto) {
        return Result.success(guidanceService.assign(dto));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Long> replace(@PathVariable Long id, @Valid @RequestBody GuidanceAssignDTO dto) {
        return Result.success(guidanceService.replace(id, dto));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<PageResult<GuidanceVO>> pageMine(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size) {
        return Result.success(guidanceService.pageMine(current, size));
    }

    @GetMapping("/quota/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<PageResult<GuidanceQuotaVO>> myCompetitions(@RequestParam(defaultValue="1") long current, @RequestParam(defaultValue="10") long size) {
        return Result.success(guidanceService.quotaPage(null, current, size));
    }

    @GetMapping("/quota/summary")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<?> quotaSummary() { return Result.success(completion.myQuotaSummary()); }

    @GetMapping("/teachers")
    public Result<List<com.cie.competition.module.guidance.vo.CompetitionTeacherVO>> teachers(@RequestParam Long competitionId) {
        return Result.success(guidanceService.competitionTeachers(competitionId));
    }
}
