package com.cie.competition.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时初始化默认账号：admin / majoradmin，密码均为 admin123。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createIfAbsent("admin", "系统管理员", "ADMIN");
        createIfAbsent("majoradmin", "专业管理员", "MAJOR_ADMIN");
    }

    private void createIfAbsent(String username, String realName, String role) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count != null && count > 0) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setRealName(realName);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        log.info("已初始化账号 {}（{}）", username, role);
    }
}
