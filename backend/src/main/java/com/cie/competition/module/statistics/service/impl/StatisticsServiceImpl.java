package com.cie.competition.module.statistics.service.impl;

import com.cie.competition.module.statistics.mapper.StatisticsMapper;
import com.cie.competition.module.statistics.service.StatisticsService;
import com.cie.competition.module.statistics.vo.StatisticsOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final StatisticsMapper statisticsMapper;
    private final com.cie.competition.module.user.service.UserService userService;

    @Override
    public StatisticsOverviewVO overview() {
        var user = com.cie.competition.common.security.UserContext.get();
        if (user == null || !("ADMIN".equals(user.getRole()) || "MAJOR_ADMIN".equals(user.getRole())))
            throw new com.cie.competition.common.exception.BusinessException(403, "仅管理员可查看统计");
        Long majorId = null;
        if ("MAJOR_ADMIN".equals(user.getRole())) {
            majorId = userService.getById(user.getUserId()).getMajorId();
            if (majorId == null) throw new com.cie.competition.common.exception.BusinessException(403, "请先配置管理员所属专业");
        }
        StatisticsOverviewVO vo = new StatisticsOverviewVO();
        vo.setCompetitionCount(statisticsMapper.countCompetition(majorId));
        vo.setRegistrationCount(statisticsMapper.countRegistration(majorId));
        vo.setRegistrationPeopleCount(statisticsMapper.countRegistrationPeople(majorId));
        vo.setEffectiveRegistrationCount(statisticsMapper.countEffectiveRegistration(majorId));
        vo.setAwardCount(statisticsMapper.countAward(majorId));
        vo.setAwardTeamCount(statisticsMapper.countAwardTeams(majorId));
        vo.setAwardStudentCount(statisticsMapper.countAwardStudents(majorId));
        vo.setTeacherAchievementCount(statisticsMapper.countTeacherAchievements(majorId));
        vo.setStudentCount(statisticsMapper.countStudent(majorId));
        vo.setTeacherCount(statisticsMapper.countTeacher(majorId));
        vo.setGuidanceCount(statisticsMapper.countGuidance(majorId));
        vo.setCompetitionStatus(statisticsMapper.countCompetitionByStatus(majorId));
        vo.setRegistrationStatus(statisticsMapper.countRegistrationByStatus(majorId));
        vo.setAwardLevel(statisticsMapper.countAwardByLevel(majorId));
        vo.setRegistrationMajor(statisticsMapper.countRegistrationByMajor(majorId));
        return vo;
    }
}
