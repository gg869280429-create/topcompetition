package com.cie.competition.module.registration.controller;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.Result;
import com.cie.competition.module.registration.dto.RegistrationDTO;
import com.cie.competition.module.registration.dto.RegistrationQueryDTO;
import com.cie.competition.module.registration.dto.ReviewDTO;
import com.cie.competition.module.registration.service.RegistrationService;
import com.cie.competition.module.registration.vo.RegistrationVO;
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
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;
    private final com.cie.competition.module.registration.service.RegistrationWorkflowService workflow;
    private final com.cie.competition.module.registration.service.RegistrationReviewService reviewService;
    private final com.cie.competition.module.registration.service.TeamRosterService roster;

    @PutMapping("/{id}/members")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Void> members(@PathVariable Long id,@Valid @RequestBody com.cie.competition.module.registration.dto.TeamMembersDTO dto) {
        roster.update(id,dto);return Result.success();
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<PageResult<RegistrationVO>> page(RegistrationQueryDTO query) {
        return Result.success(registrationService.page(query));
    }

    @GetMapping("/mine")
    public Result<PageResult<RegistrationVO>> pageMine(RegistrationQueryDTO query) {
        return Result.success(registrationService.pageMine(query));
    }

    @GetMapping("/{id}")
    public Result<RegistrationVO> getById(@PathVariable Long id) {
        return Result.success(registrationService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Long> create(@Valid @RequestBody RegistrationDTO dto) {
        return Result.success(registrationService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RegistrationDTO dto) {
        registrationService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Void> cancel(@PathVariable Long id) {
        registrationService.cancel(id);
        return Result.success();
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewDTO dto) {
        reviewService.review(id, dto);
        return Result.success();
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) { workflow.submit(id); return Result.success(); }

    @GetMapping("/invitations")
    public Result<?> invitations() { return Result.success(workflow.mineInvitations()); }

    @PutMapping("/invitations/{id}/response")
    public Result<Void> respond(@PathVariable Long id,@RequestBody com.cie.competition.module.registration.dto.RegistrationActionDTO dto) {
        if(dto.getAccepted()==null) throw new com.cie.competition.common.exception.BusinessException("请选择接受或拒绝");
        workflow.respond(id,dto.getAccepted());return Result.success();
    }
    @DeleteMapping("/invitations/{id}")
    public Result<Void> revoke(@PathVariable Long id){workflow.revoke(id);return Result.success();}

    @GetMapping("/{id}/history")
    public Result<?> history(@PathVariable Long id){return Result.success(workflow.details(id));}

    @PostMapping("/{id}/adjustment")
    public Result<Void> adjust(@PathVariable Long id,@RequestBody com.cie.competition.module.registration.dto.RegistrationActionDTO dto){workflow.adjust(id,dto.getReason());return Result.success();}

    @PostMapping("/{id}/reapply")
    public Result<Void> reapply(@PathVariable Long id,@RequestBody com.cie.competition.module.registration.dto.RegistrationActionDTO dto){workflow.reapply(id,dto.getReason());return Result.success();}

    @PostMapping("/{id}/withdrawals")
    public Result<Long> withdrawal(@PathVariable Long id,@RequestBody com.cie.competition.module.registration.dto.RegistrationActionDTO dto){return Result.success(workflow.requestWithdrawal(id,dto.getReason()));}

    @PutMapping("/withdrawals/{id}/review")
    public Result<Void> reviewWithdrawal(@PathVariable Long id,@RequestBody com.cie.competition.module.registration.dto.RegistrationActionDTO dto){workflow.reviewWithdrawal(id,dto.getResult(),dto.getComment());return Result.success();}
}
