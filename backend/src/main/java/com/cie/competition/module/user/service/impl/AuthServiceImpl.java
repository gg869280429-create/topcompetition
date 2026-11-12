package com.cie.competition.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.ResultCode;
import com.cie.competition.common.security.JwtUtil;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.user.dto.LoginDTO;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import com.cie.competition.module.user.service.AuthService;
import com.cie.competition.module.user.vo.LoginVO;
import com.cie.competition.module.user.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "账号已被禁用");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole(), user.getTokenVersion());
        UserInfoVO info = toUserInfoVO(user);
        return new LoginVO(token, info);
    }

    @Override
    public UserInfoVO getCurrentUser() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return toUserInfoVO(user);
    }

    private UserInfoVO toUserInfoVO(User user) {
        return new UserInfoVO(user.getId(), user.getUsername(), user.getRealName(), user.getRole(), user.getMajorId());
    }
}
