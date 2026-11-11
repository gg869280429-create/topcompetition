package com.cie.competition.module.history.service;

import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.history.entity.BusinessHistory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessHistoryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String bizType, Long bizId, String operation, Object before, Object after, String reason) {
        Long actorId = UserContext.getUserId();
        List<String> names = actorId == null ? List.of() : jdbc.queryForList(
                "SELECT COALESCE(NULLIF(real_name,''),username) FROM sys_user WHERE id=?", String.class, actorId);
        String actorName = names.isEmpty() ? "系统" : names.get(0);
        jdbc.update("INSERT INTO business_history(biz_type,biz_id,operation,actor_id,actor_name,before_json,after_json,reason) VALUES (?,?,?,?,?,?,?,?)",
                bizType, bizId, operation, actorId, actorName, serialize(before), serialize(after), reason);
    }

    public List<BusinessHistory> list(String bizType, Long bizId) {
        return jdbc.query("SELECT * FROM business_history WHERE biz_type=? AND biz_id=? ORDER BY id",
                BeanPropertyRowMapper.newInstance(BusinessHistory.class), bizType, bizId);
    }

    private String serialize(Object value) {
        if (value == null) return null;
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("业务历史内容无法序列化", e); }
    }
}
