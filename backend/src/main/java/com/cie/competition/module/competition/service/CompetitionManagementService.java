package com.cie.competition.module.competition.service;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.module.competition.dto.CompetitionDTO;
import com.cie.competition.module.competition.dto.CompetitionTeacherDTO;
import com.cie.competition.module.guidance.dto.GuidanceQuotaDTO;
import com.cie.competition.module.guidance.service.GuidanceService;
import com.cie.competition.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompetitionManagementService {
    private final CompetitionService competitionService;
    private final GuidanceService guidanceService;
    private final UserMapper userMapper;

    @Transactional
    public Long save(Long id, CompetitionDTO dto) {
        if (dto.getTeachers() == null || dto.getTeachers().isEmpty()) throw new BusinessException("至少配置一位指导教师");
        var teachers = new TreeMap<Long, CompetitionTeacherDTO>();
        for (var teacher : dto.getTeachers()) {
            if (teachers.put(teacher.getTeacherId(), teacher) != null) throw new BusinessException("指导教师不能重复");
        }
        if (id == null) {
            id = competitionService.create(dto);
            competitionService.requireManageable(id);
        } else {
            competitionService.requireManageable(id);
            competitionService.update(id, dto);
        }
        var existing = guidanceService.quotaPage(id, 1, Integer.MAX_VALUE).getRecords().stream()
                .collect(Collectors.toMap(com.cie.competition.module.guidance.vo.GuidanceQuotaVO::getTeacherId, q -> q));
        var teacherIds = new TreeSet<>(teachers.keySet());
        teacherIds.addAll(existing.keySet());
        // 先锁全部新旧教师，避免新增较大编号后再删除较小编号造成反向加锁；旧教师允许已停用。
        for (Long teacherId : teacherIds) userMapper.lockById(teacherId);
        for (Long teacherId : teacherIds) {
            var teacher = teachers.get(teacherId);
            if (teacher == null) {
                guidanceService.deleteQuota(existing.get(teacherId).getId());
            } else {
                var quota = new GuidanceQuotaDTO();
                quota.setCompetitionId(id); quota.setTeacherId(teacherId); quota.setQuota(teacher.getQuota());
                guidanceService.saveQuota(quota);
            }
        }
        return id;
    }
}
