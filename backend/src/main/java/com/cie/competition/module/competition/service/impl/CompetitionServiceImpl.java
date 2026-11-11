package com.cie.competition.module.competition.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.ResultCode;
import com.cie.competition.common.security.LoginUser;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.competition.dto.CompetitionDTO;
import com.cie.competition.module.competition.dto.CompetitionQueryDTO;
import com.cie.competition.module.competition.entity.Competition;
import com.cie.competition.module.competition.mapper.CompetitionMapper;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.competition.vo.CompetitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompetitionServiceImpl implements CompetitionService {

    private static final Set<String> COMPETITION_TYPES = Set.of("PERSONAL", "TEAM");
    private static final Set<String> LEVELS = Set.of("SCHOOL", "CITY", "PROVINCE", "NATIONAL", "INTERNATIONAL");
    private static final Set<String> STATUSES = Set.of("DRAFT", "REGISTERING", "IN_PROGRESS", "ENDED", "CANCELLED");

    private final CompetitionMapper competitionMapper;
    private final com.cie.competition.module.file.service.FileService fileService;

    private final com.cie.competition.module.message.service.MessageService messageService;
    private final com.cie.competition.module.history.service.BusinessHistoryService history;

    @Override
    public Competition lockById(Long id) {
        Competition competition = competitionMapper.lockById(id);
        if (competition == null) throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "竞赛不存在");
        return competition;
    }

    @Override
    public void requireRegistrationOpen(Long id) {
        Competition competition = lockById(id);
        LocalDateTime now = LocalDateTime.now();
        if (!"REGISTERING".equals(competition.getStatus()) || competition.getRegistrationStartTime() == null
                || competition.getRegistrationEndTime() == null
                || now.isBefore(competition.getRegistrationStartTime()) || now.isAfter(competition.getRegistrationEndTime()))
            throw new BusinessException("当前不在竞赛报名时间内");
    }

    @Override
    public void requireManageable(Long id) { checkManagement(lockById(id)); }

    private void checkManagement(Competition competition) {
        LoginUser user = UserContext.get();
        if (user == null || !("ADMIN".equals(user.getRole())
                || Objects.equals(user.getUserId(), competition.getPublisherId())
                || Objects.equals(user.getUserId(), competition.getManagerId())))
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权管理此竞赛");
    }

    @Override
    public void requireGuidanceAssignable(Long id) {
        Competition competition = lockById(id);
        if (Set.of("ENDED", "CANCELLED").contains(competition.getStatus()))
            throw new BusinessException("竞赛已结束或取消，不能分配指导教师");
    }

    @Override
    public void validateTeacher(Long competitionId, Long teacherId) {
        if (teacherId == null || competitionMapper.countEligibleTeacher(competitionId, teacherId) == 0)
            throw new BusinessException("请选择本竞赛配置的启用指导教师");
    }

    @Override
    public PageResult<CompetitionVO> page(CompetitionQueryDTO query) {
        LambdaQueryWrapper<Competition> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getKeyword()), Competition::getTitle, query.getKeyword())
                .eq(StringUtils.hasText(query.getStatus()), Competition::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getCompetitionType()), Competition::getCompetitionType, query.getCompetitionType())
                .eq(StringUtils.hasText(query.getLevel()), Competition::getLevel, query.getLevel());
        if (isStudent() || isTeacher()) {
            wrapper.ne(Competition::getStatus, "DRAFT");
        }
        wrapper.orderByDesc(Competition::getCreateTime);
        Page<Competition> page = competitionMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return PageResult.of(page.convert(this::toVO));
    }

    @Override
    public CompetitionVO getById(Long id) {
        Competition competition = getCompetition(id);
        if (isStudent()) {
            if ("DRAFT".equals(competition.getStatus())) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "竞赛不存在");
            }
        } else if (isTeacher()) {
            if ("DRAFT".equals(competition.getStatus())) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "竞赛不存在");
            }
        }
        return toVO(competition);
    }

    @Override
    public Map<Long, String> listTitleMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = ids.stream().filter(id -> id != null).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return competitionMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(Competition::getId, Competition::getTitle));
    }

    @Override
    @Transactional
    public Long create(CompetitionDTO dto) {
        LoginUser user = UserContext.get();
        if (user == null || !Set.of("ADMIN", "MAJOR_ADMIN").contains(user.getRole()))
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权创建竞赛");
        validateEnums(dto);
        validateManager(null, dto.getManagerId());
        fileService.validateAttachment(dto.getDocumentFileId());
        Competition competition = new Competition();
        BeanUtils.copyProperties(dto, competition);
        // 个人赛团队人数固定为 1
        if ("PERSONAL".equals(dto.getCompetitionType())) {
            competition.setMaxMembers(1);
        }
        competition.setStatus("DRAFT");
        competition.setPublisherId(UserContext.getUserId());
        competitionMapper.insert(competition);
        history.record("COMPETITION", competition.getId(), "CREATED", null, competition, null);
        return competition.getId();
    }

    @Override
    @Transactional
    public void update(Long id, CompetitionDTO dto) {
        Competition competition = lockById(id);
        checkManagement(competition);
        Competition before = new Competition();
        BeanUtils.copyProperties(competition, before);
        validateEnums(dto);
        validateManager(competition.getManagerId(), dto.getManagerId());
        if (competitionMapper.countRegistrations(id) > 0
                && (!Objects.equals(competition.getCompetitionType(), dto.getCompetitionType())
                || !Objects.equals(competition.getAllowCrossMajor(), dto.getAllowCrossMajor())
                || dto.getMaxMembers() < competition.getMaxMembers()))
            throw new BusinessException("已有报名，不能更改赛制、跨专业规则或降低人数上限");
        if (!java.util.Objects.equals(competition.getDocumentFileId(), dto.getDocumentFileId())) fileService.validateAttachment(dto.getDocumentFileId());
        competitionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Competition>()
                .eq(Competition::getId, id).set(Competition::getDocumentFileId, dto.getDocumentFileId())
                .set(Competition::getManagerId, dto.getManagerId()));
        BeanUtils.copyProperties(dto, competition);
        if ("PERSONAL".equals(dto.getCompetitionType())) {
            competition.setMaxMembers(1);
        }
        competitionMapper.updateById(competition);
        history.record("COMPETITION", id, "UPDATED", before, competition, null);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Competition competition = lockById(id);
        checkManagement(competition);
        if (competitionMapper.countRegistrations(id) > 0) throw new BusinessException("已有报名，不能删除竞赛");
        competitionMapper.deleteCompetitionQuotas(id);
        competitionMapper.deleteById(id);
        history.record("COMPETITION", id, "DELETED", competition, null, null);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) { updateStatus(id, status, null); }

    @Override
    @Transactional
    public void updateStatus(Long id, String status, String reason) {
        Competition competition = lockById(id);
        checkManagement(competition);
        Competition before = new Competition();
        BeanUtils.copyProperties(competition, before);
        if (status == null || !STATUSES.contains(status)) throw new BusinessException("非法的竞赛状态");
        if ("CANCELLED".equals(status)) {
            if (!StringUtils.hasText(reason)) throw new BusinessException("取消竞赛必须填写原因");
            if (reason.trim().length() > 1000) throw new BusinessException("取消原因不能超过1000字");
            if (Set.of("ENDED", "CANCELLED").contains(competition.getStatus())) throw new BusinessException("竞赛已经结束或取消");
            competition.setCancellationReason(reason.trim());
            competition.setCancelledAt(LocalDateTime.now());
        } else {
            List<String> sequence = List.of("DRAFT", "REGISTERING", "IN_PROGRESS", "ENDED");
            int current = sequence.indexOf(competition.getStatus());
            if (current < 0 || current + 1 >= sequence.size() || !sequence.get(current + 1).equals(status))
                throw new BusinessException("竞赛状态必须按草稿、报名中、进行中、已结束顺序流转");
            if ("REGISTERING".equals(status)) {
                validateWindow(competition.getRegistrationStartTime(), competition.getRegistrationEndTime());
                if (!hasTeacherQuota(id)) throw new BusinessException("发布前请至少配置一位启用教师及指导名额");
            }
        }
        competition.setStatus(status);
        competitionMapper.updateById(competition);
        history.record("COMPETITION", id, "STATUS_CHANGED", before, competition, reason);
        if ("CANCELLED".equals(status)) for (Long participant : competitionMapper.listParticipantIds(id))
            messageService.sendBusinessMessage(participant, "竞赛取消通知", "竞赛「" + competition.getTitle() + "」已取消：" + competition.getCancellationReason(), "NOTICE", "COMPETITION", id);
    }

    private void validateManager(Long previous, Long managerId) {
        if (Objects.equals(previous, managerId)) return;
        if (!"ADMIN".equals(UserContext.get().getRole())) throw new BusinessException("仅管理员可指定竞赛负责人");
        if (managerId != null && competitionMapper.countActiveManager(managerId) == 0)
            throw new BusinessException("负责人必须是启用的管理员");
    }

    private void validateWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) throw new BusinessException("报名起止时间必填且开始时间必须早于截止时间");
    }

    private void validateEnums(CompetitionDTO dto) {
        validateWindow(dto.getRegistrationStartTime(), dto.getRegistrationEndTime());
        if (!COMPETITION_TYPES.contains(dto.getCompetitionType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "非法的赛制类型");
        }
        if (!LEVELS.contains(dto.getLevel())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "非法的竞赛级别");
        }
    }

    private boolean isStudent() {
        LoginUser user = UserContext.get();
        return user != null && "STUDENT".equals(user.getRole());
    }

    private boolean isTeacher() {
        LoginUser user = UserContext.get();
        return user != null && "TEACHER".equals(user.getRole());
    }

    private boolean hasTeacherQuota(Long competitionId) {
        Long count = competitionMapper.countTeacherQuota(competitionId);
        return count != null && count > 0;
    }

    private Competition getCompetition(Long id) {
        Competition competition = competitionMapper.selectById(id);
        if (competition == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "竞赛不存在");
        }
        return competition;
    }

    private CompetitionVO toVO(Competition competition) {
        CompetitionVO vo = new CompetitionVO();
        BeanUtils.copyProperties(competition, vo);
        if (competition.getDocumentFileId() != null) vo.setDocumentName(fileService.get(competition.getDocumentFileId()).getOriginalName());
        return vo;
    }
}
