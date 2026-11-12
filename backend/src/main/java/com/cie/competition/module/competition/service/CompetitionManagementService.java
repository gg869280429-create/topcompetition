package com.cie.competition.module.competition.service;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.module.competition.dto.CompetitionDTO;
import com.cie.competition.module.competition.dto.CompetitionTeacherDTO;
import com.cie.competition.module.competition.dto.CompetitionPublishDTO;
import com.cie.competition.module.content.service.ContentService;
import com.cie.competition.module.history.service.BusinessHistoryService;
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
    private final ContentService contentService;
    private final BusinessHistoryService historyService;

    @Transactional
    public Long publish(Long id, CompetitionPublishDTO dto) {
        competitionService.updateStatus(id, "REGISTERING");
        if (!Boolean.TRUE.equals(dto.getSyncNews())) return null;
        if (dto.getNews() == null) throw new BusinessException("请填写同步发布的新闻信息");
        if (!"NEWS".equals(dto.getNews().getCategory())) throw new BusinessException("同步发布的内容必须为新闻");
        Long newsId = contentService.create(dto.getNews());
        contentService.updateStatus(newsId, "PUBLISHED");
        historyService.record("COMPETITION", id, "NEWS_PUBLISHED", null, java.util.Map.of("newsId", newsId), "发布竞赛时同步发布新闻");
        return newsId;
    }

    @Transactional
    public Long save(Long id, CompetitionDTO dto) {
        if (dto.getTeachers() == null || dto.getTeachers().isEmpty()) throw new BusinessException("至少配置一位指导老师");
        var teachers = new TreeMap<Long, CompetitionTeacherDTO>();
        for (var teacher : dto.getTeachers()) {
            if (teachers.put(teacher.getTeacherId(), teacher) != null) throw new BusinessException("指导老师不能重复");
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
