package com.cie.competition.module.user.controller;

import com.cie.competition.common.result.Result;
import com.cie.competition.module.user.dto.LoginDTO;
import com.cie.competition.module.user.service.AuthService;
import com.cie.competition.module.user.vo.LoginVO;
import com.cie.competition.module.user.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success("登录成功", authService.login(dto));
    }

    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        return Result.success(authService.getCurrentUser());
    }
}
