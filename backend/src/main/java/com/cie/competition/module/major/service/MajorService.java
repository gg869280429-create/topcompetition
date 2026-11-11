package com.cie.competition.module.major.service;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MajorService {
    private final JdbcTemplate jdbc;

    private void admin() {
        if (UserContext.get() == null || !"ADMIN".equals(UserContext.get().getRole()))
            throw new BusinessException(403, "仅系统管理员可管理专业");
    }

    public List<Map<String, Object>> list(String keyword) {
        admin();
        return jdbc.queryForList("SELECT id,name,code,status FROM sys_major WHERE name LIKE ? ORDER BY id", "%" + (keyword == null ? "" : keyword) + "%");
    }

    @Transactional
    public Long save(Long id, String name) {
        admin();
        String normalized = name.trim();
        if (normalized.isEmpty() || normalized.length() > 64) throw new BusinessException("专业名称须为1至64字");
        try {
            if (id == null) {
                var key = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    var statement = connection.prepareStatement("INSERT INTO sys_major(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, normalized);
                    return statement;
                }, key);
                return key.getKey().longValue();
            }
            if (jdbc.queryForList("SELECT id FROM sys_major WHERE id=? FOR UPDATE", id).isEmpty())
                throw new BusinessException(404, "专业不存在");
            jdbc.update("UPDATE sys_major SET name=? WHERE id=?", normalized, id);
            return id;
        } catch (DuplicateKeyException e) {
            throw new BusinessException("专业名称已存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        admin();
        if (jdbc.queryForList("SELECT id FROM sys_major WHERE id=? FOR UPDATE", id).isEmpty())
            throw new BusinessException(404, "专业不存在");
        long references = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM sys_user WHERE major_id=?)+(SELECT COUNT(*) FROM competition WHERE major_id=?)+(SELECT COUNT(*) FROM registration WHERE major_id=?)+(SELECT COUNT(*) FROM registration_member WHERE major_id_snapshot=?)+(SELECT COUNT(*) FROM award WHERE major_id=?)", Long.class, id, id, id, id, id);
        if (references > 0) throw new BusinessException("专业已关联用户、竞赛或历史业务，不能删除");
        jdbc.update("DELETE FROM sys_major WHERE id=?", id);
    }
}
