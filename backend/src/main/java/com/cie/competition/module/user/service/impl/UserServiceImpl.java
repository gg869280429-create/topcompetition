package com.cie.competition.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.ResultCode;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import com.cie.competition.module.user.service.UserService;
import com.cie.competition.module.user.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserInfoVO getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        return toVO(user);
    }

    @Override
    public List<UserInfoVO> listStudents(String keyword) {
        return listByRole("STUDENT", keyword);
    }

    @Override
    public List<UserInfoVO> listTeachers(String keyword) {
        return listByRole("TEACHER", keyword);
    }

    @Override
    public List<UserInfoVO> listUsers(String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .orderByAsc(User::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getRealName, keyword));
        }
        return userMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    private List<UserInfoVO> listByRole(String role, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, role)
                .eq(User::getStatus, 1)
                .orderByAsc(User::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getRealName, keyword));
        }
        return userMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public Map<Long, String> listRealNameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = ids.stream().filter(id -> id != null).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
    }

    private UserInfoVO toVO(User user) {
        return new UserInfoVO(user.getId(), user.getUsername(), user.getRealName(), user.getRole(), user.getMajorId());
    }
}
