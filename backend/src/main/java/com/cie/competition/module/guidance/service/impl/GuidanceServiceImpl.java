package com.cie.competition.module.guidance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.competition.vo.CompetitionVO;
import com.cie.competition.module.guidance.dto.GuidanceAssignDTO;
import com.cie.competition.module.guidance.dto.GuidanceQuotaDTO;
import com.cie.competition.module.guidance.entity.Guidance;
import com.cie.competition.module.guidance.entity.GuidanceQuota;
import com.cie.competition.module.guidance.mapper.GuidanceMapper;
import com.cie.competition.module.guidance.mapper.GuidanceQuotaMapper;
import com.cie.competition.module.guidance.service.GuidanceService;
import com.cie.competition.module.guidance.vo.GuidanceQuotaVO;
import com.cie.competition.module.guidance.vo.GuidanceVO;
import com.cie.competition.module.registration.service.RegistrationService;
import com.cie.competition.module.registration.vo.RegistrationVO;
import com.cie.competition.module.user.service.UserService;
import com.cie.competition.module.user.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GuidanceServiceImpl implements GuidanceService {

    private final GuidanceQuotaMapper quotaMapper;
    private final GuidanceMapper guidanceMapper;
    private final CompetitionService competitionService;
    private final RegistrationService registrationService;
    private final UserService userService;
    private final com.cie.competition.module.user.mapper.UserMapper userMapper;
    private final com.cie.competition.module.user.service.TeacherService teacherService;
    private final com.cie.competition.module.message.service.MessageService messageService;
    private final com.cie.competition.module.history.service.BusinessHistoryService historyService;
    private final com.cie.competition.module.guidance.service.GuidanceCompletionService completionService;

    @Override
    public PageResult<GuidanceQuotaVO> quotaPage(Long competitionId, long current, long size) {
        LambdaQueryWrapper<GuidanceQuota> wrapper = new LambdaQueryWrapper<GuidanceQuota>()
                .eq(competitionId != null, GuidanceQuota::getCompetitionId, competitionId)
                .orderByDesc(GuidanceQuota::getId);
        if ("TEACHER".equals(UserContext.get().getRole())) wrapper.eq(GuidanceQuota::getTeacherId, UserContext.getUserId());
        if ("MAJOR_ADMIN".equals(UserContext.get().getRole())) {
            Long majorId = userService.getById(UserContext.getUserId()).getMajorId();
            if (majorId == null) throw new BusinessException(403, "请先配置管理员所属专业");
            wrapper.inSql(GuidanceQuota::getTeacherId, "SELECT id FROM sys_user WHERE role='TEACHER' AND major_id=" + majorId);
        }
        Page<GuidanceQuota> page = quotaMapper.selectPage(new Page<>(current, size), wrapper);
        List<GuidanceQuota> records = page.getRecords();
        Map<Long, String> teacherNameMap = userService.listRealNameMap(
                records.stream().map(GuidanceQuota::getTeacherId).toList());
        var titleMap = competitionService.listTitleMap(records.stream().map(GuidanceQuota::getCompetitionId).toList());
        List<GuidanceQuotaVO> voList = records.stream().map(q -> {
            GuidanceQuotaVO vo = new GuidanceQuotaVO();
            BeanUtils.copyProperties(q, vo);
            vo.setTeacherName(teacherNameMap.get(q.getTeacherId()));
            vo.setCompetitionTitle(titleMap.get(q.getCompetitionId()));
            long assigned = assignedCount(q.getCompetitionId(), q.getTeacherId());
            vo.setAssigned(assigned); vo.setRemaining(Math.max(0, q.getQuota() - assigned));
            return vo;
        }).toList();
        return new PageResult<>(voList, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional
    public Long saveQuota(GuidanceQuotaDTO dto) {
        competitionService.requireManageable(dto.getCompetitionId());
        teacherService.lockActive(dto.getTeacherId());
        UserInfoVO teacher = userService.getById(dto.getTeacherId());
        if (!"TEACHER".equals(teacher.getRole())) {
            throw new BusinessException("只能为教师分配指导名额");
        }
        GuidanceQuota existing = quotaMapper.lockQuota(dto.getCompetitionId(), dto.getTeacherId());
        if ("MAJOR_ADMIN".equals(UserContext.get().getRole())) {
            Long major = userService.getById(UserContext.getUserId()).getMajorId();
            if (major == null || !major.equals(teacher.getMajorId())) throw new BusinessException(403, "仅能配置本专业教师");
        }
        if (dto.getQuota() < currentAssignedCount(dto.getCompetitionId(), dto.getTeacherId())) throw new BusinessException("名额不能低于已分配团队数");
        if (existing != null) {
            existing.setQuota(dto.getQuota());
            quotaMapper.updateById(existing);
            return existing.getId();
        }
        GuidanceQuota quota = new GuidanceQuota();
        quota.setCompetitionId(dto.getCompetitionId());
        quota.setTeacherId(dto.getTeacherId());
        quota.setQuota(dto.getQuota());
        quotaMapper.insert(quota);
        return quota.getId();
    }

    @Override
    @Transactional
    public void deleteQuota(Long id) {
        var quota = quotaMapper.selectById(id);
        if (quota == null) throw new BusinessException("名额配置不存在");
        competitionService.requireManageable(quota.getCompetitionId());
        userMapper.lockById(quota.getTeacherId());
        quotaMapper.lockQuota(quota.getCompetitionId(), quota.getTeacherId());
        if ("MAJOR_ADMIN".equals(UserContext.get().getRole())) {
            Long major = userService.getById(UserContext.getUserId()).getMajorId();
            if (major == null || !major.equals(userService.getById(quota.getTeacherId()).getMajorId())) throw new BusinessException(403, "仅能配置本专业教师");
        }
        if (currentAssignedCount(quota.getCompetitionId(), quota.getTeacherId()) > 0) throw new BusinessException("请先调整该教师已有的指导团队");
        quotaMapper.deleteById(id);
    }

    @Override
    public List<com.cie.competition.module.guidance.vo.CompetitionTeacherVO> competitionTeachers(Long competitionId) {
        competitionService.getById(competitionId);
        var quotas = quotaMapper.selectList(new LambdaQueryWrapper<GuidanceQuota>()
                .eq(GuidanceQuota::getCompetitionId, competitionId).gt(GuidanceQuota::getQuota, 0).orderByAsc(GuidanceQuota::getId));
        Map<Long, com.cie.competition.module.user.vo.TeacherVO> profiles = teacherService.activeProfiles(
                quotas.stream().map(GuidanceQuota::getTeacherId).toList()).stream().collect(Collectors.toMap(com.cie.competition.module.user.vo.TeacherVO::getId, t -> t));
        return quotas.stream().filter(q -> profiles.containsKey(q.getTeacherId())).map(q -> {
            var teacher = profiles.get(q.getTeacherId());
            var vo = new com.cie.competition.module.guidance.vo.CompetitionTeacherVO();
            vo.setTeacherId(q.getTeacherId()); vo.setTeacherName(teacher.getRealName()); vo.setMajorName(teacher.getMajorName());
            vo.setExpertise(teacher.getExpertise()); vo.setBiography(teacher.getBiography()); vo.setQuota(q.getQuota());
            long assigned = assignedCount(competitionId, q.getTeacherId());
            vo.setAssigned(assigned); vo.setRemaining(Math.max(0, q.getQuota() - assigned)); return vo;
        }).toList();
    }

    @Override
    public List<GuidanceVO> listByRegistration(Long registrationId) {
        List<Guidance> list = guidanceMapper.selectList(new LambdaQueryWrapper<Guidance>()
                .eq(Guidance::getRegistrationId, registrationId)
                .orderByAsc(Guidance::getId));
        registrationService.getById(registrationId);
        return assembleVOs(list);
    }

    @Override
    @Transactional
    public Long assign(GuidanceAssignDTO dto) {
        Long id = confirmAssignment(dto);
        notifyAssignment(dto);
        return id;
    }

    @Override
    @Transactional
    public Long confirmAssignment(GuidanceAssignDTO dto) {
        requireAdministrator();
        var readable = registrationService.getById(dto.getRegistrationId());
        competitionService.lockById(readable.getCompetitionId());
        competitionService.requireGuidanceAssignable(readable.getCompetitionId());
        RegistrationVO registration = registrationService.lockForAssignment(dto.getRegistrationId());
        if (!"APPROVED".equals(registration.getStatus())) {
            throw new BusinessException("仅已通过的报名可分配指导");
        }
        if (registration.getPreferredTeacherId() != null && !registration.getPreferredTeacherId().equals(dto.getTeacherId())
                && !org.springframework.util.StringUtils.hasText(dto.getComment())) throw new BusinessException("调剂教师时必须填写原因");
        teacherService.lockActive(dto.getTeacherId());
        UserInfoVO teacher = userService.getById(dto.getTeacherId());
        if (!"TEACHER".equals(teacher.getRole())) {
            throw new BusinessException("只能分配教师进行指导");
        }
        Long competitionId = registration.getCompetitionId();
        competitionService.validateTeacher(competitionId, dto.getTeacherId());
        CompetitionVO competition = competitionService.getById(competitionId);

        long teacherCount = guidanceMapper.selectList(new LambdaQueryWrapper<Guidance>()
                .eq(Guidance::getRegistrationId, dto.getRegistrationId()).eq(Guidance::getStatus, "ACTIVE").last("FOR UPDATE")).size();
        if (teacherCount >= competition.getMaxTeachers()) {
            throw new BusinessException("该团队指导教师数量已达上限");
        }

        GuidanceQuota quota = quotaMapper.lockQuota(competitionId, dto.getTeacherId());
        int allowed = quota == null ? 0 : quota.getQuota();
        // 使用当前读；等待名额行锁后必须看见上一事务新分配的团队。
        long assigned = currentAssignedCount(competitionId, dto.getTeacherId());
        if (assigned >= allowed) {
            throw new BusinessException("该教师在此竞赛的指导名额已用完");
        }

        Long dupCount = guidanceMapper.selectCount(new LambdaQueryWrapper<Guidance>()
                .eq(Guidance::getRegistrationId, dto.getRegistrationId())
                .eq(Guidance::getStatus, "ACTIVE")
                .eq(Guidance::getTeacherId, dto.getTeacherId()));
        if (dupCount != null && dupCount > 0) {
            throw new BusinessException("该教师已指导此团队");
        }

        Guidance guidance = new Guidance();
        guidance.setRegistrationId(dto.getRegistrationId());
        guidance.setCompetitionId(competitionId);
        guidance.setTeacherId(dto.getTeacherId());
        guidance.setStatus("ACTIVE");
        guidance.setActiveKey(1);
        guidance.setAssignerId(UserContext.getUserId());
        guidance.setAssignTime(LocalDateTime.now());
        guidance.setMembersSnapshot(completionService.snapshotMembers(dto.getRegistrationId()));
        try {
            guidanceMapper.insert(guidance);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该教师已指导此团队");
        }
        historyService.record("GUIDANCE", guidance.getId(), "ASSIGNED", null, guidance, dto.getComment());
        return guidance.getId();
    }

    @Override
    @Transactional
    public Long replace(Long id, GuidanceAssignDTO dto) {
        requireAdministrator();
        if (!org.springframework.util.StringUtils.hasText(dto.getComment())) throw new BusinessException("请填写调剂原因");
        Guidance previous = guidanceMapper.selectById(id);
        if (previous == null || !previous.getRegistrationId().equals(dto.getRegistrationId())) throw new BusinessException("指导关系不存在或与报名不匹配");
        competitionService.lockById(previous.getCompetitionId());
        competitionService.requireGuidanceAssignable(previous.getCompetitionId());
        registrationService.lockForAssignment(previous.getRegistrationId());
        if (previous.getTeacherId().equals(dto.getTeacherId())) throw new BusinessException("请选择不同的指导教师");
        // 调剂两端按统一顺序锁定教师和名额，失败时恢复原指导关系。
        var teacherIds = java.util.stream.Stream.of(previous.getTeacherId(), dto.getTeacherId()).sorted().toList();
        for (Long teacherId : teacherIds) teacherService.lockActive(teacherId);
        for (Long teacherId : teacherIds) quotaMapper.lockQuota(previous.getCompetitionId(), teacherId);
        var locked = guidanceMapper.selectOne(new LambdaQueryWrapper<Guidance>().eq(Guidance::getId, id).last("FOR UPDATE"));
        if (locked == null || !"ACTIVE".equals(locked.getStatus())) throw new BusinessException("指导关系已调整，请刷新页面");
        guidanceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Guidance>()
                .eq(Guidance::getId, id).set(Guidance::getStatus, "ENDED").set(Guidance::getActiveKey, null)
                .set(Guidance::getEndedTime, LocalDateTime.now()).set(Guidance::getEndReason, dto.getComment()));
        historyService.record("GUIDANCE", id, "REASSIGNED", locked, java.util.Map.of("status", "ENDED", "replacementTeacherId", dto.getTeacherId()), dto.getComment());
        Long replacement = confirmAssignment(dto);
        notifyAssignment(dto);
        messageService.sendBusinessMessage(previous.getTeacherId(), "指导团队调整", "团队指导安排已调整，原因：" + dto.getComment(), "NOTICE", "REGISTRATION", dto.getRegistrationId());
        return replacement;
    }

    private void notifyAssignment(GuidanceAssignDTO dto) {
        var registration = registrationService.getById(dto.getRegistrationId());
        String teacherName = userService.getById(dto.getTeacherId()).getRealName();
        String content = "团队「" + registration.getTeamName() + "」的指导教师已确认为「" + teacherName + "」。"
                + (org.springframework.util.StringUtils.hasText(dto.getComment()) ? "原因：" + dto.getComment() : "");
        for (var member : registration.getMembers()) messageService.sendBusinessMessage(member.getUserId(), "指导安排", content, "NOTICE", "REGISTRATION", dto.getRegistrationId());
        messageService.sendBusinessMessage(dto.getTeacherId(), "新增指导团队", content, "NOTICE", "REGISTRATION", dto.getRegistrationId());
    }

    private void requireAdministrator() {
        String role = UserContext.get().getRole();
        if (!"ADMIN".equals(role) && !"MAJOR_ADMIN".equals(role)) throw new BusinessException(403, "仅管理员可分配指导教师");
    }

    @Override
    public PageResult<GuidanceVO> pageMine(long current, long size) {
        Long teacherId = UserContext.getUserId();
        Page<Guidance> page = guidanceMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Guidance>()
                        .eq(Guidance::getTeacherId, teacherId)
                        .in(Guidance::getStatus, "ACTIVE", "COMPLETED")
                        .orderByDesc(Guidance::getAssignTime));
        return new PageResult<>(assembleVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    private long assignedCount(Long competitionId, Long teacherId) {
        return guidanceMapper.selectCount(new LambdaQueryWrapper<Guidance>()
                .eq(Guidance::getCompetitionId, competitionId).eq(Guidance::getTeacherId, teacherId).eq(Guidance::getStatus, "ACTIVE"));
    }

    private long currentAssignedCount(Long competitionId, Long teacherId) {
        return guidanceMapper.selectList(new LambdaQueryWrapper<Guidance>()
                .eq(Guidance::getCompetitionId, competitionId).eq(Guidance::getTeacherId, teacherId).eq(Guidance::getStatus, "ACTIVE").last("FOR UPDATE")).size();
    }

    private List<GuidanceVO> assembleVOs(List<Guidance> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> registrationIds = list.stream().map(Guidance::getRegistrationId).distinct().toList();
        List<Long> teacherIds = list.stream().map(Guidance::getTeacherId).distinct().toList();
        Map<Long, RegistrationVO> registrationMap = registrationService.listByIds(registrationIds).stream()
                .collect(Collectors.toMap(RegistrationVO::getId, r -> r));
        Map<Long, String> teacherNameMap = userService.listRealNameMap(teacherIds);

        return list.stream().map(g -> {
            GuidanceVO vo = new GuidanceVO();
            BeanUtils.copyProperties(g, vo);
            if (UserContext.get() != null && "TEACHER".equals(UserContext.get().getRole())
                    && !java.util.Objects.equals(g.getTeacherId(), UserContext.getUserId())) vo.setMembersSnapshot(null);
            RegistrationVO reg = registrationMap.get(g.getRegistrationId());
            if (reg != null) {
                vo.setTeamName(reg.getTeamName());
                vo.setCompetitionId(reg.getCompetitionId());
                vo.setCompetitionTitle(reg.getCompetitionTitle());
            }
            vo.setTeacherName(teacherNameMap.get(g.getTeacherId()));
            return vo;
        }).toList();
    }
}
