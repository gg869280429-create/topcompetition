package com.cie.competition.module.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.ResultCode;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.user.dto.PasswordChangeDTO;
import com.cie.competition.module.user.dto.ProfileDTO;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import com.cie.competition.module.user.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserMapper users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public ProfileVO get() {
        return view(current(false));
    }

    @Transactional
    public ProfileVO update(ProfileDTO dto) {
        User user = current(true);
        user.setRealName(dto.getRealName().trim());
        user.setEmail(dto.getEmail() == null ? "" : dto.getEmail().trim());
        user.setPhone(dto.getPhone() == null ? "" : dto.getPhone().trim());
        users.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, user.getId())
                .set(User::getRealName, user.getRealName()).set(User::getEmail, user.getEmail())
                .set(User::getPhone, user.getPhone()));
        return view(user);
    }

    @Transactional
    public void changePassword(PasswordChangeDTO dto) {
        User user = current(true);
        if (!dto.getNewPassword().equals(dto.getConfirmPassword()))
            throw new BusinessException("两次输入的新密码不一致");
        if (dto.getNewPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException("新密码的UTF-8编码不能超过72字节");
        if (encoder.matches(dto.getNewPassword(), user.getPassword()))
            throw new BusinessException("新密码不能与原密码相同");
        long nextVersion = (user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1;
        users.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, user.getId())
                .set(User::getPassword, encoder.encode(dto.getNewPassword()))
                .set(User::getTokenVersion, nextVersion));
    }

    private User current(boolean lock) {
        User user = lock ? users.lockById(UserContext.getUserId()) : users.selectById(UserContext.getUserId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()))
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        if (!java.util.List.of("STUDENT", "TEACHER").contains(user.getRole())) throw new BusinessException(ResultCode.FORBIDDEN);
        return user;
    }

    private ProfileVO view(User user) {
        var names = jdbc.queryForList("SELECT name FROM sys_major WHERE id=?", String.class, user.getMajorId());
        return new ProfileVO(user.getId(), user.getUsername(), user.getRealName(), user.getMajorId(),
                names.isEmpty() ? null : names.get(0), user.getEmail(), user.getPhone());
    }
}
