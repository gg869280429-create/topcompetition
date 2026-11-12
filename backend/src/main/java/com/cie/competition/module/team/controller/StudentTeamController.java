package com.cie.competition.module.team.controller;
import com.cie.competition.common.result.Result;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.module.team.service.StudentTeamService;
import com.cie.competition.module.team.dto.*;
import com.cie.competition.module.registration.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/team") @RequiredArgsConstructor @PreAuthorize("hasRole('STUDENT')") public class StudentTeamController  {
    private final StudentTeamService teams;
    @GetMapping("/mine") public Result<?> mine() {
        return Result.success(teams.mine());
    }
    @GetMapping("/invitations") public Result<?> invitations() {
        return Result.success(teams.invitations());
    }
    @GetMapping("/{id}") public Result<?> detail(@PathVariable Long id) {
        return Result.success(teams.detail(id));
    }
    @PostMapping public Result<Long> create(@Valid @RequestBody TeamCreateDTO dto) {
        return Result.success(teams.create(dto));
    }
    @PostMapping("/{id}/invitations") public Result<?> invite(@PathVariable Long id,@Valid @RequestBody TeamMembersDTO dto) {
        teams.invite(id,dto.getMemberIds());
        return Result.success();
    }
    @PutMapping("/{id}/response") public Result<?> respond(@PathVariable Long id,@RequestBody RegistrationActionDTO dto) {
        if(dto.getAccepted()==null)throw new BusinessException("请选择接受或拒绝");
        teams.respond(id,dto.getAccepted());
        return Result.success();
    }
    @PutMapping("/{id}/captain") public Result<?> captain(@PathVariable Long id,@Valid @RequestBody CaptainDTO dto) {
        teams.nominate(id,dto.userId());
        return Result.success();
    }
    @PutMapping("/{id}/captain/response") public Result<?> confirm(@PathVariable Long id,@RequestBody RegistrationActionDTO dto) {
        if(dto.getAccepted()==null)throw new BusinessException("请选择接受或拒绝");
        teams.confirmCaptain(id,dto.getAccepted());
        return Result.success();
    }
    @DeleteMapping("/{id}/members/{userId}") public Result<?> remove(@PathVariable Long id,@PathVariable Long userId) {
        teams.remove(id,userId);
        return Result.success();
    }
    @DeleteMapping("/{id}") public Result<?> disband(@PathVariable Long id) {
        teams.disband(id);
        return Result.success();
    }
}
