package com.cie.competition.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.user.dto.StudentDTO;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import com.cie.competition.module.user.vo.StudentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {
    private final UserMapper users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    private Long scope() {
        var user = UserContext.get();
        if (user == null || !List.of("ADMIN", "MAJOR_ADMIN").contains(user.getRole()))
            throw new BusinessException(403, "无权管理学生");
        if ("ADMIN".equals(user.getRole())) return null;
        var own = users.selectById(user.getUserId());
        if (own == null || own.getMajorId() == null)
            throw new BusinessException(403, "请配置专业管理员所属专业");
        return own.getMajorId();
    }

    private void check(User student, Long major) {
        if (student == null || !"STUDENT".equals(student.getRole()))
            throw new BusinessException(404, "学生不存在");
        if (major != null && !major.equals(student.getMajorId()))
            throw new BusinessException(403, "仅能维护本专业学生");
    }

    public PageResult<StudentVO> page(String keyword, Long majorId, Integer status, long current, long size) {
        Long major = scope();
        var query = new LambdaQueryWrapper<User>().eq(User::getRole, "STUDENT");
        if (major != null) query.eq(User::getMajorId, major);
        else if (majorId != null) query.eq(User::getMajorId, majorId);
        if (status != null) query.eq(User::getStatus, status);
        if (keyword != null && !keyword.isBlank())
            query.and(w -> w.like(User::getUsername, keyword).or().like(User::getRealName, keyword));
        var page = users.selectPage(new Page<>(current, size), query.orderByAsc(User::getId));
        var names = new HashMap<Long, String>();
        users.listMajors().forEach(m -> names.put(m.getId(), m.getName()));
        var records = page.getRecords().stream().map(student -> {
            var vo = new StudentVO();
            BeanUtils.copyProperties(student, vo);
            vo.setMajorName(names.get(student.getMajorId()));
            return vo;
        }).toList();
        return new PageResult<>(records, page.getTotal(), current, size);
    }

    @Transactional
    public Long save(Long id, StudentDTO dto) {
        Long major = scope();
        if (major != null && !major.equals(dto.getMajorId()))
            throw new BusinessException(403, "仅能维护本专业学生");
        if (jdbc.queryForList("SELECT id FROM sys_major WHERE id=? AND status=1 FOR UPDATE", dto.getMajorId()).isEmpty())
            throw new BusinessException("专业不存在或已停用");
        User student = id == null ? new User() : users.lockById(id);
        if (id != null) check(student, major);
        String password = dto.getPassword();
        if (id == null && (password == null || password.isBlank()))
            throw new BusinessException("新增学生需要初始密码");
        if (password != null && !password.isBlank()) {
            if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
                throw new BusinessException("密码须至少8字符且不超过72字节");
            student.setPassword(encoder.encode(password));
        }
        student.setUsername(dto.getUsername());
        student.setRealName(dto.getRealName());
        student.setMajorId(dto.getMajorId());
        student.setStatus(dto.getStatus());
        student.setRole("STUDENT");
        try {
            if (id == null) users.insert(student);
            else users.updateById(student);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("用户名已存在");
        }
        return student.getId();
    }

    @Transactional
    public void delete(Long id) {
        Long major = scope();
        User student = users.lockById(id);
        check(student, major);
        long references = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM registration WHERE leader_id=?)+(SELECT COUNT(*) FROM registration_member WHERE user_id=?)+(SELECT COUNT(*) FROM award_student WHERE student_id=?)+(SELECT COUNT(*) FROM participation_result WHERE student_id=?)", Long.class, id, id, id, id);
        if (references > 0 || jdbc.queryForObject("SELECT COUNT(*) FROM student_team_member WHERE user_id=?", Long.class, id) > 0)
            throw new BusinessException("学生有关联组队、报名、指导或获奖历史，请使用停用");
        users.deleteById(id);
    }
}
