package com.cie.competition.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.user.dto.TeacherDTO;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import com.cie.competition.module.user.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public List<MajorVO> majors() { return userMapper.listMajors(); }

    public PageResult<TeacherVO> page(String keyword, long current, long size) {
        var wrapper = new LambdaQueryWrapper<User>().eq(User::getRole, "TEACHER");
        if (StringUtils.hasText(keyword)) wrapper.and(w -> w.like(User::getRealName, keyword).or().like(User::getUsername, keyword));
        if ("MAJOR_ADMIN".equals(UserContext.get().getRole())) wrapper.eq(User::getMajorId, adminMajor());
        var page = userMapper.selectPage(new Page<>(current, size), wrapper.orderByAsc(User::getId));
        return new PageResult<>(profiles(page.getRecords()), page.getTotal(), current, size);
    }

    public List<TeacherVO> activeProfiles(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return profiles(userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, ids).eq(User::getRole, "TEACHER").eq(User::getStatus, 1)));
    }

    public void lockActive(Long id) {
        User user = userMapper.lockById(id);
        if (user == null || !"TEACHER".equals(user.getRole()) || !Integer.valueOf(1).equals(user.getStatus()))
            throw new BusinessException("教师不存在或已停用");
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long save(Long id, TeacherDTO dto) {
        if ("MAJOR_ADMIN".equals(UserContext.get().getRole()) && !adminMajor().equals(dto.getMajorId()))
            throw new BusinessException(403, "仅能维护本专业教师");
        if (majors().stream().noneMatch(m -> m.getId().equals(dto.getMajorId()))) throw new BusinessException("专业不存在或已停用");
        User user = id == null ? new User() : userMapper.lockById(id);
        if (user == null || (id != null && !"TEACHER".equals(user.getRole()))) throw new BusinessException("教师不存在");
        if (id != null && "MAJOR_ADMIN".equals(UserContext.get().getRole()) && !adminMajor().equals(user.getMajorId()))
            throw new BusinessException(403, "仅能维护本专业教师");
        if (id == null && !StringUtils.hasText(dto.getPassword())) throw new BusinessException("新增教师需要设置初始密码");
        if (StringUtils.hasText(dto.getPassword())) {
            if (dto.getPassword().length() < 8 || dto.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new BusinessException("密码须至少8个字符且不超过72字节");
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (id != null && dto.getStatus() == 0 && (userMapper.countOpenTeacherQuotas(id) > 0 || userMapper.countOpenGuidance(id) > 0))
            throw new BusinessException("教师仍有关联的未结束竞赛或指导任务，请先调整安排");
        user.setUsername(dto.getUsername()); user.setRealName(dto.getRealName()); user.setMajorId(dto.getMajorId());
        user.setExpertise(dto.getExpertise()); user.setBiography(dto.getBiography()); user.setStatus(dto.getStatus()); user.setRole("TEACHER");
        try { if (id == null) userMapper.insert(user); else userMapper.updateById(user); }
        catch (DuplicateKeyException e) { throw new BusinessException("用户名已存在"); }
        return user.getId();
    }

    private Long adminMajor() {
        Long major = userMapper.selectById(UserContext.getUserId()).getMajorId();
        if (major == null) throw new BusinessException(403, "请先为专业管理员配置所属专业");
        return major;
    }

    private List<TeacherVO> profiles(List<User> users) {
        Map<Long, String> names = majors().stream().collect(Collectors.toMap(MajorVO::getId, MajorVO::getName));
        return users.stream().map(u -> { var vo = new TeacherVO(); BeanUtils.copyProperties(u, vo); vo.setMajorName(names.get(u.getMajorId())); return vo; }).toList();
    }
}
