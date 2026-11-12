package com.cie.competition.module.registration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.guidance.service.GuidanceCompletionService;
import com.cie.competition.module.history.service.BusinessHistoryService;
import com.cie.competition.module.message.service.MessageService;
import com.cie.competition.module.registration.dto.TeamMembersDTO;
import com.cie.competition.module.registration.entity.*;
import com.cie.competition.module.registration.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TeamRosterService {
    private final RegistrationMapper registrations;
    private final RegistrationMemberMapper members;
    private final TeamInvitationMapper invitations;
    private final RegistrationMemberValidator validator;
    private final CompetitionService competitions;
    private final GuidanceCompletionService guidance;
    private final BusinessHistoryService history;
    private final MessageService messages;
    private final JdbcTemplate jdbc;

    private Registration lock(Long id) {
        if(UserContext.get()==null||!"STUDENT".equals(UserContext.get().getRole()))throw new BusinessException(403,"仅学生队长可调整队员");
        var previous=registrations.selectById(id);if(previous==null)throw new BusinessException(404,"报名不存在");
        competitions.lockById(previous.getCompetitionId());return registrations.lockById(id);
    }
    private void editable(Registration registration) {
        competitions.requireGuidanceAssignable(registration.getCompetitionId());
        if(!"APPROVED".equals(registration.getStatus()))throw new BusinessException("仅已通过报名可直接调整队员");
        if(registration.getRosterLockedAt()!=null||jdbc.queryForObject("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND status='COMPLETED'",Long.class,registration.getId())>0)throw new BusinessException("老师已确认参赛或完成指导，名单已锁定");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM award WHERE registration_id=? AND status='APPROVED'",Long.class,registration.getId())>0)throw new BusinessException("存在有效获奖记录，不能调整队员");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM withdrawal_request WHERE registration_id=? AND status='PENDING'",Long.class,registration.getId())>0)throw new BusinessException("请先处理待审核退赛申请");
    }
    private void captain(Registration registration) {if(!Objects.equals(registration.getLeaderId(),UserContext.getUserId()))throw new BusinessException(403,"仅队长可调整队员");}
    private List<RegistrationMember> current(Long id) {return members.selectList(new LambdaQueryWrapper<RegistrationMember>().eq(RegistrationMember::getRegistrationId,id).eq(RegistrationMember::getCurrentMember,1).orderByAsc(RegistrationMember::getId));}

    @Transactional
    public void beginAdjustment(Long id,String reason) {
        var registration=lock(id);captain(registration);editable(registration);
        if(reason==null||reason.isBlank()||reason.length()>1000)throw new BusinessException("请填写调整说明，最多1000字");
        history.record("REGISTRATION",id,"MEMBER_ADJUSTMENT_OPENED",null,null,reason);
    }

    @Transactional
    public void update(Long id,TeamMembersDTO dto) {
        var registration=lock(id);captain(registration);editable(registration);
        if(!"TEAM".equals(competitions.getById(registration.getCompetitionId()).getCompetitionType()))throw new BusinessException("个人赛不能调整队员");
        validator.validate(registration.getCompetitionId(),registration.getLeaderId(),dto.getMemberIds());
        var before=current(id);var desired=new HashSet<>(dto.getMemberIds());desired.add(registration.getLeaderId());
        var existing=new HashSet<Long>();before.forEach(m->existing.add(m.getUserId()));
        for(var member:before)if(!desired.contains(member.getUserId())) {
            members.update(null,new LambdaUpdateWrapper<RegistrationMember>().eq(RegistrationMember::getId,member.getId()).set(RegistrationMember::getCurrentMember,0).set(RegistrationMember::getActiveKey,null));
            revokeFor(id,member.getUserId());send(member.getUserId(),registration,"队员调整","队长已将您移出当前参赛名单，原有历史记录保留。");
        }
        for(var invitation:invitations.selectList(new LambdaQueryWrapper<TeamInvitation>().eq(TeamInvitation::getRegistrationId,id).eq(TeamInvitation::getStatus,"PENDING")))
            if(!desired.contains(invitation.getInviteeId())){revokeFor(id,invitation.getInviteeId());send(invitation.getInviteeId(),registration,"团队邀请撤销","队长已撤销团队邀请。");}
        for(Long studentId:dto.getMemberIds())if(!existing.contains(studentId)) {
            Long pending=invitations.selectCount(new LambdaQueryWrapper<TeamInvitation>().eq(TeamInvitation::getRegistrationId,id).eq(TeamInvitation::getInviteeId,studentId).eq(TeamInvitation::getStatus,"PENDING"));
            if(pending>0)continue;
            revokeFor(id,studentId);var invitation=new TeamInvitation();invitation.setRegistrationId(id);invitation.setInviteeId(studentId);invitation.setInviterId(registration.getLeaderId());invitation.setStatus("PENDING");invitation.setCreateTime(LocalDateTime.now());invitations.insert(invitation);
            messages.sendBusinessMessage(studentId,"团队邀请","队长邀请您加入团队「"+registration.getTeamName()+"」，请在报名管理中接受或拒绝。","NOTICE","INVITATION",invitation.getId());
        }
        refresh(registration);
        history.record("REGISTRATION",id,"MEMBERS_UPDATED",before,Map.of("members",current(id),"requestedMemberIds",dto.getMemberIds()),dto.getReason());
        notifyTeam(registration,"团队名单调整","队长已调整队员；新增成员接受邀请后加入，无需管理员重新审核。");
    }

    @Transactional
    public void respond(Long invitationId,boolean accept) {
        var invitation=invitations.selectById(invitationId);if(invitation==null)throw new BusinessException(404,"邀请不存在");
        var registration=lock(invitation.getRegistrationId());invitation=invitations.selectById(invitationId);
        if(!Objects.equals(invitation.getInviteeId(),UserContext.getUserId()))throw new BusinessException(403,"仅被邀请人可响应");
        editable(registration);
        if(!"PENDING".equals(invitation.getStatus()))throw new BusinessException("邀请已处理");
        var before=current(registration.getId());
        if(accept) {
            var ids=new ArrayList<>(before.stream().map(RegistrationMember::getUserId).filter(uid->!uid.equals(registration.getLeaderId())).toList());ids.add(invitation.getInviteeId());
            var people=validator.validate(registration.getCompetitionId(),registration.getLeaderId(),ids);
            var student=people.stream().filter(u->u.getId().equals(UserContext.getUserId())).findFirst().orElseThrow();
            var member=new RegistrationMember();member.setRegistrationId(registration.getId());member.setCompetitionId(registration.getCompetitionId());member.setUserId(student.getId());member.setMemberRole("MEMBER");member.setCurrentMember(1);member.setActiveKey(1);member.setJoinTime(LocalDateTime.now());member.setNameSnapshot(student.getRealName()==null||student.getRealName().isBlank()?student.getUsername():student.getRealName());member.setMajorIdSnapshot(student.getMajorId());
            var major=jdbc.queryForList("SELECT name FROM sys_major WHERE id=?",String.class,student.getMajorId());member.setMajorNameSnapshot(major.isEmpty()?null:major.get(0));
            try{members.insert(member);}catch(DuplicateKeyException ex){throw new BusinessException("您已参加该竞赛的其他团队，不能重复加入");}
        }
        invitation.setStatus(accept?"ACCEPTED":"REJECTED");invitation.setRespondedTime(LocalDateTime.now());invitations.updateById(invitation);
        refresh(registration);history.record("REGISTRATION",registration.getId(),accept?"INVITATION_ACCEPT":"INVITATION_REJECT",before,Map.of("invitation",invitation,"members",current(registration.getId())),null);
        send(invitation.getInviteeId(),registration,"团队邀请结果",accept?"您已加入团队。":"您已拒绝团队邀请。");notifyTeam(registration,"团队名单更新",accept?"新增队员已接受邀请并加入团队。":"新增队员已拒绝邀请，团队名单未增加。");
    }

    @Transactional
    public void revoke(Long invitationId) {
        var invitation=invitations.selectById(invitationId);if(invitation==null)throw new BusinessException(404,"邀请不存在");
        var registration=lock(invitation.getRegistrationId());captain(registration);editable(registration);invitation=invitations.selectById(invitationId);
        if(!List.of("PENDING","REJECTED").contains(invitation.getStatus()))throw new BusinessException("当前邀请不可撤销，请通过调整队员移除已加入成员");
        revokeFor(registration.getId(),invitation.getInviteeId());
        history.record("REGISTRATION",registration.getId(),"INVITATION_REVOKE",invitation,Map.of("status","REVOKED"),null);send(invitation.getInviteeId(),registration,"团队邀请撤销","队长已撤销团队邀请。");
    }
    private void revokeFor(Long registrationId,Long studentId) {invitations.update(null,new LambdaUpdateWrapper<TeamInvitation>().eq(TeamInvitation::getRegistrationId,registrationId).eq(TeamInvitation::getInviteeId,studentId).in(TeamInvitation::getStatus,"PENDING","ACCEPTED","REJECTED").set(TeamInvitation::getStatus,"REVOKED").set(TeamInvitation::getRespondedTime,LocalDateTime.now()));}
    private void refresh(Registration registration) {
        String snapshot=guidance.snapshotMembers(registration.getId());
        jdbc.update("UPDATE guidance SET members_snapshot=? WHERE registration_id=? AND status='ACTIVE'",snapshot,registration.getId());
        int cross=jdbc.queryForObject("SELECT COUNT(DISTINCT u.major_id) FROM registration_member rm JOIN sys_user u ON u.id=rm.user_id WHERE rm.registration_id=? AND rm.current_member=1",Integer.class,registration.getId())>1?1:0;
        jdbc.update("UPDATE registration SET cross_major=? WHERE id=?",cross,registration.getId());
    }
    private void send(Long userId,Registration registration,String title,String content) {messages.sendBusinessMessage(userId,title,content,"NOTICE","REGISTRATION",registration.getId());}
    private void notifyTeam(Registration registration,String title,String content) {
        var receivers=new LinkedHashSet<>(current(registration.getId()).stream().map(RegistrationMember::getUserId).toList());
        receivers.addAll(jdbc.queryForList("SELECT teacher_id FROM guidance WHERE registration_id=? AND status='ACTIVE'",Long.class,registration.getId()));
        for(Long user:receivers)send(user,registration,title,content);
    }
}
