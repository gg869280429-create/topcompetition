package com.cie.competition.module.user.controller;

import com.cie.competition.common.result.Result;
import com.cie.competition.module.user.service.UserService;
import com.cie.competition.module.user.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/students")
    public Result<List<UserInfoVO>> listStudents(@RequestParam(required = false) String keyword) {
        return Result.success(userService.listStudents(keyword));
    }

    @GetMapping("/teachers")
    public Result<List<UserInfoVO>> listTeachers(@RequestParam(required = false) String keyword) {
        return Result.success(userService.listTeachers(keyword));
    }

    @GetMapping("/users")
    public Result<List<UserInfoVO>> listUsers(@RequestParam(required = false) String keyword) {
        return Result.success(userService.listUsers(keyword));
    }
}
