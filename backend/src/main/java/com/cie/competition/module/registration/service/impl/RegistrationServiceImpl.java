package com.cie.competition.module.registration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.ResultCode;
import com.cie.competition.common.security.LoginUser;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.registration.dto.RegistrationDTO;
import com.cie.competition.module.registration.dto.RegistrationQueryDTO;
import com.cie.competition.module.registration.dto.ReviewDTO;
import com.cie.competition.module.registration.entity.Registration;
import com.cie.competition.module.registration.entity.RegistrationMember;
import com.cie.competition.module.registration.mapper.RegistrationMapper;
import com.cie.competition.module.registration.mapper.RegistrationMemberMapper;
import com.cie.competition.module.registration.service.RegistrationService;
import com.cie.competition.module.registration.vo.RegistrationMemberVO;
import com.cie.competition.module.registration.vo.RegistrationVO;
import com.cie.competition.module.user.service.UserService;
import com.cie.competition.module.guidance.entity.Guidance;
import com.cie.competition.module.guidance.mapper.GuidanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationMapper registrationMapper;
    private final RegistrationMemberMapper memberMapper;
    private final CompetitionService competitionService;
    private final UserService userService;
    private final GuidanceMapper guidanceMapper;
    private final com.cie.competition.module.registration.service.RegistrationWorkflowService workflow;

    @Override
    public RegistrationVO lockForAssignment(Long id) {
        requireRole("ADMIN", "MAJOR_ADMIN");
        Registration registration = lockRegistration(id);
        requireReadable(registration);
        return assembleVOs(List.of(registration)).get(0);
    }

    @Override
    public PageResult<RegistrationVO> page(RegistrationQueryDTO query) {
        requireRole("ADMIN", "MAJOR_ADMIN");
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<Registration>()
                .eq(query.getCompetitionId() != null, Registration::getCompetitionId, query.getCompetitionId())
                .eq(StringUtils.hasText(query.getStatus()), Registration::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getKeyword()), Registration::getTeamName, query.getKeyword())
                .orderByDesc(Registration::getCreateTime);
        if ("MAJOR_ADMIN".equals(UserContext.get().getRole())) {
            Long major = userService.getById(UserContext.getUserId()).getMajorId();
            if (major == null) throw new BusinessException(403, "专业管理员尚未配置专业");
            wrapper.eq(Registration::getMajorId, major);
        }
        Page<Registration> page = registrationMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return toPageResult(page);
    }

    @Override
    public PageResult<RegistrationVO> pageMine(RegistrationQueryDTO query) {
        requireRole("STUDENT");
        Long userId = UserContext.getUserId();
        List<RegistrationMember> myMembers = memberMapper.selectList(
                new LambdaQueryWrapper<RegistrationMember>().eq(RegistrationMember::getUserId, userId)
                        .and(w -> w.isNotNull(RegistrationMember::getNameSnapshot).or().eq(RegistrationMember::getMemberRole, "LEADER")));
        List<Long> registrationIds = myMembers.stream()
                .map(RegistrationMember::getRegistrationId).distinct().toList();
        if (registrationIds.isEmpty()) {
            return new PageResult<>(List.of(), 0, query.getCurrent(), query.getSize());
        }
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<Registration>()
                .in(Registration::getId, registrationIds)
                .eq(query.getCompetitionId() != null, Registration::getCompetitionId, query.getCompetitionId())
                .eq(StringUtils.hasText(query.getStatus()), Registration::getStatus, query.getStatus())
                .orderByDesc(Registration::getCreateTime);
        Page<Registration> page = registrationMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return toPageResult(page);
    }

    @Override
    public RegistrationVO getById(Long id) {
        Registration registration = getRegistration(id);
        requireReadable(registration);
        return assembleVOs(List.of(registration)).get(0);
    }

    @Override
    public List<RegistrationVO> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = ids.stream().filter(id -> id != null).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        List<Registration> registrations = registrationMapper.selectBatchIds(distinctIds);
        registrations.forEach(this::requireReadable);
        return assembleVOs(registrations);
    }

    @Override
    @Transactional
    public Long create(RegistrationDTO dto) {
        return workflow.create(dto);
    }

    @Override
    @Transactional
    public void update(Long id, RegistrationDTO dto) {
        workflow.update(id, dto);
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        workflow.cancel(id);
    }

    @Override
    @Transactional
    public void review(Long id, ReviewDTO dto) {
        requireRole("ADMIN", "MAJOR_ADMIN");
        if ("REJECTED".equals(dto.getResult()) && !StringUtils.hasText(dto.getComment())) {
            throw new BusinessException("请填写驳回原因");
        }
        if (!"APPROVED".equals(dto.getResult()) && !"REJECTED".equals(dto.getResult())) {
            throw new BusinessException("非法的审核结果");
        }
        Registration registration = lockRegistration(id);
        if (!"PENDING".equals(registration.getStatus())) {
            throw new BusinessException("该报名已审核");
        }
        // 专业管理员只能审核本专业的报名
        LoginUser loginUser = UserContext.get();
        if ("MAJOR_ADMIN".equals(loginUser.getRole())) {
            Long adminMajorId = userService.getById(loginUser.getUserId()).getMajorId();
            if (adminMajorId == null || !adminMajorId.equals(registration.getMajorId())) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权限审核其他专业的报名");
            }
        }
        workflow.recordReview(registration, dto.getResult(), dto.getComment());
        registration.setStatus(dto.getResult());
        registration.setReviewTime(LocalDateTime.now());
        registration.setReviewerId(loginUser.getUserId());
        registration.setReviewComment(dto.getComment());
        registrationMapper.updateById(registration);
    }

    private Registration lockRegistration(Long id) {
        Registration previous = getRegistration(id);
        competitionService.lockById(previous.getCompetitionId());
        Registration locked = registrationMapper.lockById(id);
        if (locked == null) throw new BusinessException("报名记录不存在");
        return locked;
    }

    private void requireRole(String... roles) {
        LoginUser login = UserContext.get();
        if (login == null || !List.of(roles).contains(login.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权限执行此操作");
        }
    }

    private void requireReadable(Registration registration) {
        requireRole("ADMIN", "MAJOR_ADMIN", "STUDENT", "TEACHER");
        LoginUser login = UserContext.get();
        if ("ADMIN".equals(login.getRole())) return;
        if ("MAJOR_ADMIN".equals(login.getRole())) {
            Long major = userService.getById(login.getUserId()).getMajorId();
            if (major != null && major.equals(registration.getMajorId())) return;
        }
        if ("STUDENT".equals(login.getRole())) {
            Long count = memberMapper.selectCount(new LambdaQueryWrapper<RegistrationMember>()
                    .eq(RegistrationMember::getRegistrationId, registration.getId())
                    .eq(RegistrationMember::getUserId, login.getUserId())
                    .and(w -> w.isNotNull(RegistrationMember::getNameSnapshot).or().eq(RegistrationMember::getMemberRole, "LEADER")));
            if (count != null && count > 0) return;
        }
        if ("TEACHER".equals(login.getRole())) {
            Long count = guidanceMapper.selectCount(new LambdaQueryWrapper<Guidance>()
                    .eq(Guidance::getRegistrationId, registration.getId())
                    .eq(Guidance::getTeacherId, login.getUserId()).in(Guidance::getStatus, "ACTIVE", "COMPLETED"));
            if (count != null && count > 0) return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权限查看该报名");
    }

    private Registration getRegistration(Long id) {
        Registration registration = registrationMapper.selectById(id);
        if (registration == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "报名记录不存在");
        }
        return registration;
    }

    private PageResult<RegistrationVO> toPageResult(Page<Registration> page) {
        return new PageResult<>(assembleVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    private List<RegistrationVO> assembleVOs(List<Registration> registrations) {
        if (registrations.isEmpty()) {
            return List.of();
        }
        List<Long> competitionIds = registrations.stream().map(Registration::getCompetitionId).distinct().toList();
        List<Long> leaderIds = registrations.stream().map(Registration::getLeaderId).distinct().toList();
        List<Long> registrationIds = registrations.stream().map(Registration::getId).toList();
        Map<Long, String> titleMap = competitionService.listTitleMap(competitionIds);
        Map<Long, String> leaderNameMap = userService.listRealNameMap(leaderIds);
        List<Guidance> guidances = guidanceMapper.selectList(new LambdaQueryWrapper<Guidance>()
                .in(Guidance::getRegistrationId, registrationIds)
                .in(Guidance::getStatus, "ACTIVE", "COMPLETED").orderByAsc(Guidance::getId));
        Map<Long, String> teacherNames = userService.listRealNameMap(java.util.stream.Stream.concat(
                registrations.stream().map(Registration::getPreferredTeacherId), guidances.stream().map(Guidance::getTeacherId))
                .filter(java.util.Objects::nonNull).distinct().toList());
        Map<Long, List<Guidance>> guidancesByReg = guidances.stream().collect(Collectors.groupingBy(Guidance::getRegistrationId));

        List<RegistrationMember> allMembers = memberMapper.selectList(new LambdaQueryWrapper<RegistrationMember>()
                .in(RegistrationMember::getRegistrationId, registrationIds)
                .eq(RegistrationMember::getCurrentMember, 1)
                .orderByAsc(RegistrationMember::getId));
        Map<Long, List<RegistrationMember>> membersByReg = allMembers.stream()
                .collect(Collectors.groupingBy(RegistrationMember::getRegistrationId));
        List<Long> memberUserIds = allMembers.stream().map(RegistrationMember::getUserId).distinct().toList();
        Map<Long, String> memberNameMap = userService.listRealNameMap(memberUserIds);

        return registrations.stream().map(r -> {
            RegistrationVO vo = new RegistrationVO();
            BeanUtils.copyProperties(r, vo);
            vo.setCompetitionTitle(titleMap.get(r.getCompetitionId()));
            vo.setLeaderName(r.getLeaderNameSnapshot() != null ? r.getLeaderNameSnapshot() : leaderNameMap.get(r.getLeaderId()));
            if (r.getPreferredTeacherId() != null) vo.setPreferredTeacherName(teacherNames.get(r.getPreferredTeacherId()));
            vo.setGuidanceTeacherNames(guidancesByReg.getOrDefault(r.getId(), List.of()).stream()
                    .map(g -> teacherNames.get(g.getTeacherId())).filter(java.util.Objects::nonNull).distinct().collect(Collectors.joining("、")));
            vo.setCanAdjustMembers("APPROVED".equals(r.getStatus()) && r.getRosterLockedAt() == null
                    && guidancesByReg.getOrDefault(r.getId(), List.of()).stream().noneMatch(g -> "COMPLETED".equals(g.getStatus())));
            List<RegistrationMemberVO> memberVOs = membersByReg.getOrDefault(r.getId(), List.of()).stream().map(m -> {
                RegistrationMemberVO mv = new RegistrationMemberVO();
                mv.setUserId(m.getUserId());
                mv.setName(m.getNameSnapshot() != null ? m.getNameSnapshot() : memberNameMap.get(m.getUserId()));
                mv.setMemberRole(m.getMemberRole());
                return mv;
            }).toList();
            if (UserContext.get() != null && "TEACHER".equals(UserContext.get().getRole())) {
                var own = guidanceMapper.selectList(new LambdaQueryWrapper<Guidance>()
                        .eq(Guidance::getRegistrationId,r.getId()).eq(Guidance::getTeacherId,UserContext.getUserId())
                        .in(Guidance::getStatus,"ACTIVE","COMPLETED"));
                if (own.stream().noneMatch(g -> "ACTIVE".equals(g.getStatus()))) {
                    var saved = new java.util.LinkedHashMap<Long,RegistrationMemberVO>();
                    for (var guidance : own) if (guidance.getMembersSnapshot() != null) {
                        try {
                            var roster = new com.fasterxml.jackson.databind.ObjectMapper().readTree(guidance.getMembersSnapshot());
                            for (var member : roster) {
                                var snapshot = new RegistrationMemberVO();snapshot.setUserId(member.path("userId").asLong());
                                snapshot.setName(member.path("name").asText());
                                snapshot.setMemberRole(java.util.Objects.equals(snapshot.getUserId(),r.getLeaderId())?"LEADER":"MEMBER");
                                saved.putIfAbsent(snapshot.getUserId(),snapshot);
                            }
                        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                            throw new BusinessException("指导人员快照异常，请联系管理员");
                        }
                    }
                    memberVOs = java.util.List.copyOf(saved.values());
                }
            }
            vo.setMembers(memberVOs);
            return vo;
        }).toList();
    }
}
