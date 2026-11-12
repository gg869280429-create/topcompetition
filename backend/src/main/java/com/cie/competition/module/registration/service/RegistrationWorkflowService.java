package com.cie.competition.module.registration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.guidance.service.GuidanceLifecycleService;
import com.cie.competition.module.history.service.BusinessHistoryService;
import com.cie.competition.module.message.service.MessageService;
import com.cie.competition.module.registration.dto.RegistrationDTO;
import com.cie.competition.module.registration.entity.*;
import com.cie.competition.module.registration.mapper.*;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RegistrationWorkflowService {
 private final RegistrationMapper registrations;
 private final RegistrationMemberMapper members;
 private final TeamInvitationMapper invitations;
 private final WithdrawalRequestMapper withdrawals;
 private final CompetitionService competitions;
 private final UserMapper users;
 private final BusinessHistoryService history;
 private final GuidanceLifecycleService guidance;
 private final MessageService messages;
 private final JdbcTemplate jdbc;
 private final RegistrationMemberValidator memberValidator;
 private final TeamRosterService roster;

 @Transactional
 public Long create(RegistrationDTO dto) {
  role("STUDENT"); competitions.lockById(dto.getCompetitionId()); competitions.requireRegistrationOpen(dto.getCompetitionId());
  var people = validate(dto, UserContext.getUserId());
  Registration r = new Registration(); r.setCompetitionId(dto.getCompetitionId()); r.setLeaderId(UserContext.getUserId());
  r.setMajorId(people.get(0).getMajorId()); r.setTeamName(dto.getTeamName()); r.setPreferredTeacherId(dto.getPreferredTeacherId()); r.setStatus("DRAFT"); r.setCrossMajor(cross(people));
  registrations.insert(r); replaceMembers(r, people);
  history.record("REGISTRATION",r.getId(),"CREATE",null,r,null);
  if(people.size()==1) submit(r.getId());
  return r.getId();
 }

 @Transactional
 public void update(Long id,RegistrationDTO dto) {
  role("STUDENT"); Registration r=lock(id); leader(r);
  if(!List.of("DRAFT","PENDING","REJECTED").contains(r.getStatus())) throw new BusinessException("当前状态不能普通编辑，请申请调整或重新申请");
  competitions.requireRegistrationOpen(r.getCompetitionId());
  if(!Objects.equals(r.getCompetitionId(),dto.getCompetitionId())) throw new BusinessException("不能变更竞赛");
  var people=validate(dto,r.getLeaderId());
  history.record("REGISTRATION",id,"EDIT",snapshot(r),dto,null);
  release(id); r.setStatus("DRAFT");r.setTeamName(dto.getTeamName());r.setPreferredTeacherId(dto.getPreferredTeacherId());r.setMajorId(people.get(0).getMajorId());r.setCrossMajor(cross(people));
  registrations.updateById(r); replaceMembers(r,people);
 }

 @Transactional
 public void submit(Long id) {
  role("STUDENT"); Registration r=lock(id); leader(r);
  if(!List.of("DRAFT","REJECTED").contains(r.getStatus())) throw new BusinessException("仅草稿或被驳回报名可以正式提交");
  competitions.requireRegistrationOpen(r.getCompetitionId());
  var current=current(id);
  RegistrationDTO dto=new RegistrationDTO();dto.setCompetitionId(r.getCompetitionId());dto.setTeamName(r.getTeamName());dto.setPreferredTeacherId(r.getPreferredTeacherId());
  dto.setMemberIds(current.stream().map(RegistrationMember::getUserId).filter(x->!x.equals(r.getLeaderId())).toList());
  var people=validate(dto,r.getLeaderId());
  if(current.size()!=people.size()) throw new BusinessException("当前成员记录不完整");
  for(var m:current) if(!m.getUserId().equals(r.getLeaderId()) && !accepted(id,m.getUserId())) throw new BusinessException("全部成员接受邀请后才能提交");
  for(var p:people) {
   Long count=members.selectCount(new LambdaQueryWrapper<RegistrationMember>().eq(RegistrationMember::getCompetitionId,r.getCompetitionId()).eq(RegistrationMember::getUserId,p.getId()).eq(RegistrationMember::getActiveKey,1).ne(RegistrationMember::getRegistrationId,id));
   if(count!=null&&count>0) throw new BusinessException("团队中有成员已参与该竞赛");
  }
  Object beforeSubmission=snapshot(r);
  Map<Long,User> byId=new HashMap<>();people.forEach(p->byId.put(p.getId(),p));
  try {
   for(var m:current) {
    User p=byId.get(m.getUserId()); m.setActiveKey(1);m.setNameSnapshot(displayName(p));m.setMajorIdSnapshot(p.getMajorId());m.setMajorNameSnapshot(majorName(p.getMajorId()));members.updateById(m);
   }
  }catch(DuplicateKeyException ex){throw new BusinessException("团队中有成员已参与该竞赛");}
  r.setReviewTime(null);r.setReviewerId(null);r.setReviewComment(null);
  registrations.update(null,new LambdaUpdateWrapper<Registration>().eq(Registration::getId,id).set(Registration::getReviewTime,null).set(Registration::getReviewerId,null).set(Registration::getReviewComment,null));
  r.setStatus("PENDING");r.setApplyTime(LocalDateTime.now());r.setMajorId(people.get(0).getMajorId());r.setCrossMajor(cross(people));r.setLeaderNameSnapshot(displayName(people.get(0)));r.setMajorNameSnapshot(majorName(r.getMajorId()));registrations.updateById(r);
  history.record("REGISTRATION",id,"SUBMIT",beforeSubmission,snapshot(r),null);
  notifyMembers(r,"报名已提交","团队已正式提交，等待审核");
 }

 @Transactional
 public void cancel(Long id) {
  role("STUDENT");Registration r=lock(id);leader(r);
  if(!List.of("DRAFT","PENDING","REJECTED").contains(r.getStatus()))throw new BusinessException("当前状态不能取消报名");
  competitions.requireRegistrationOpen(r.getCompetitionId());
  history.record("REGISTRATION",id,"CANCEL",snapshot(r),"CANCELLED",null);
  r.setStatus("CANCELLED");registrations.updateById(r);release(id);closeInvitations(id);notifyMembers(r,"报名取消","队长已取消报名");
 }

 @Transactional
 public void adjust(Long id,String reason) {
  roster.beginAdjustment(id,reason);
 }

 @Transactional
 public void reapply(Long id,String reason) {
  role("STUDENT");Registration r=lock(id);leader(r);reason(reason);competitions.requireRegistrationOpen(r.getCompetitionId());
  if(!List.of("CANCELLED","WITHDRAWN").contains(r.getStatus()))throw new BusinessException("仅取消或退赛记录可重新申请");
  noAward(id);history.record("REGISTRATION",id,"REAPPLY",snapshot(r),"DRAFT",reason);r.setStatus("DRAFT");registrations.updateById(r);
  for(var m:current(id))if(!m.getUserId().equals(r.getLeaderId()))invite(r,m.getUserId(),true);
 }

 public java.util.List<com.cie.competition.module.registration.vo.InvitationVO> mineInvitations() {
  role("STUDENT");
  return jdbc.query("SELECT i.*,r.team_name,c.title AS competition_title,COALESCE(NULLIF(u.real_name,''),u.username) AS inviter_name FROM team_invitation i JOIN registration r ON r.id=i.registration_id JOIN competition c ON c.id=r.competition_id JOIN sys_user u ON u.id=i.inviter_id WHERE i.invitee_id=? ORDER BY i.id DESC", org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(com.cie.competition.module.registration.vo.InvitationVO.class),UserContext.getUserId());
 }

 @Transactional
 public void respond(Long invitationId,boolean accept) {
  role("STUDENT");TeamInvitation previous=invitations.selectById(invitationId);if(previous==null)throw new BusinessException("邀请不存在");
  Registration r=lock(previous.getRegistrationId());TeamInvitation invite=invitations.selectById(invitationId);
  if("APPROVED".equals(r.getStatus())){roster.respond(invitationId,accept);return;}
  if(!Objects.equals(invite.getInviteeId(),UserContext.getUserId()))throw new BusinessException(403,"仅被邀请人可响应");
  if(!"PENDING".equals(invite.getStatus())||!"DRAFT".equals(r.getStatus()))throw new BusinessException("邀请已处理或报名不可变更");
  competitions.requireRegistrationOpen(r.getCompetitionId());
  if(accept){User u=users.selectById(invite.getInviteeId());if(u==null||!"STUDENT".equals(u.getRole())||!Integer.valueOf(1).equals(u.getStatus())||u.getMajorId()==null)throw new BusinessException("用户已失效");
   members.update(null,new LambdaUpdateWrapper<RegistrationMember>().eq(RegistrationMember::getRegistrationId,r.getId()).eq(RegistrationMember::getUserId,u.getId()).eq(RegistrationMember::getCurrentMember,1).set(RegistrationMember::getNameSnapshot,displayName(u)).set(RegistrationMember::getMajorIdSnapshot,u.getMajorId()).set(RegistrationMember::getMajorNameSnapshot,majorName(u.getMajorId())));
  }
  invite.setStatus(accept?"ACCEPTED":"REJECTED");invite.setRespondedTime(LocalDateTime.now());invitations.updateById(invite);
  history.record("REGISTRATION",r.getId(),accept?"INVITATION_ACCEPT":"INVITATION_REJECT",null,invite,null);
  message(r.getLeaderId(),r,"成员邀请响应","成员已"+(accept?"接受":"拒绝")+"邀请");
 }

 @Transactional
 public void revoke(Long invitationId) {
  role("STUDENT");var previous=invitations.selectById(invitationId);if(previous==null)throw new BusinessException("邀请不存在");
  Registration r=lock(previous.getRegistrationId());leader(r);var invite=invitations.selectById(invitationId);
  if("APPROVED".equals(r.getStatus())){roster.revoke(invitationId);return;}
  if(!"DRAFT".equals(r.getStatus())||!List.of("PENDING","ACCEPTED","REJECTED").contains(invite.getStatus()))throw new BusinessException("当前邀请不可撤销");
  competitions.requireRegistrationOpen(r.getCompetitionId());
  invite.setStatus("REVOKED");invite.setRespondedTime(LocalDateTime.now());invitations.updateById(invite);
  members.update(null,new LambdaUpdateWrapper<RegistrationMember>().eq(RegistrationMember::getRegistrationId,r.getId()).eq(RegistrationMember::getUserId,invite.getInviteeId()).eq(RegistrationMember::getCurrentMember,1).set(RegistrationMember::getCurrentMember,0).set(RegistrationMember::getActiveKey,null));
  history.record("REGISTRATION",r.getId(),"INVITATION_REVOKE",null,invite,null);message(invite.getInviteeId(),r,"邀请撤销","队长已撤销邀请");
 }

 @Transactional
 public Long requestWithdrawal(Long id,String reason) {
  role("STUDENT");Registration r=lock(id);leader(r);reason(reason);
  if(!"APPROVED".equals(r.getStatus()))throw new BusinessException("仅已通过报名可申请退赛");
  if(List.of("ENDED","CANCELLED").contains(competitions.getById(r.getCompetitionId()).getStatus()))throw new BusinessException("竞赛已结束或取消");
  if(withdrawals.selectCount(new LambdaQueryWrapper<WithdrawalRequest>().eq(WithdrawalRequest::getRegistrationId,id).eq(WithdrawalRequest::getStatus,"PENDING"))>0)throw new BusinessException("已有待审核退赛申请");
  WithdrawalRequest request=new WithdrawalRequest();request.setRegistrationId(id);request.setStatus("PENDING");request.setReason(reason);request.setCreateTime(LocalDateTime.now());withdrawals.insert(request);
  history.record("REGISTRATION",id,"WITHDRAWAL_REQUEST",null,request,reason);
  jdbc.queryForList("SELECT id FROM sys_user WHERE status=1 AND (role='ADMIN' OR (role='MAJOR_ADMIN' AND major_id=?))",Long.class,r.getMajorId()).forEach(uid->message(uid,r,"退赛待审核",r.getTeamName()+"申请退赛："+reason));
  return request.getId();
 }

 @Transactional
 public void reviewWithdrawal(Long requestId,String result,String comment) {
  role("ADMIN","MAJOR_ADMIN");var previous=withdrawals.selectById(requestId);if(previous==null)throw new BusinessException("退赛申请不存在");
  Registration r=lock(previous.getRegistrationId());adminScope(r);var request=withdrawals.selectById(requestId);
  if(!"APPROVED".equals(result)&&!"REJECTED".equals(result))throw new BusinessException("非法审核结果");
  if(comment!=null&&comment.length()>1000)throw new BusinessException("审核意见最多1000字");
  if(result.equals(request.getStatus()))return;
  if(!"PENDING".equals(request.getStatus()))throw new BusinessException("申请已审核");
  if(!"APPROVED".equals(r.getStatus()))throw new BusinessException("报名状态已变更");
  if("REJECTED".equals(result))reason(comment);
  if("APPROVED".equals(result)) {
   noAward(r.getId());guidance.endActive(r.getId(),request.getReason());release(r.getId());r.setStatus("WITHDRAWN");registrations.updateById(r);
  }
  request.setStatus(result);request.setReviewerId(UserContext.getUserId());request.setReviewComment(comment);request.setReviewTime(LocalDateTime.now());withdrawals.updateById(request);
  history.record("REGISTRATION",r.getId(),"WITHDRAWAL_"+result,previous,request,comment);notifyMembers(r,"退赛审核结果","退赛申请"+result+ (comment==null?"":"："+comment));
 }

 public Map<String,Object> details(Long id) {
  var r=registrations.selectById(id);if(r==null)throw new BusinessException("报名不存在");readable(r);
  if("TEACHER".equals(UserContext.get().getRole()) && jdbc.queryForObject("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND teacher_id=? AND status='ACTIVE'",Integer.class,id,UserContext.getUserId())==0)
   return Map.of("history",List.of(),"invitations",List.of(),"withdrawals",List.of());
  Map<String,Object> result=new LinkedHashMap<>();result.put("history",history.list("REGISTRATION",id));result.put("invitations",invitations.selectList(new LambdaQueryWrapper<TeamInvitation>().eq(TeamInvitation::getRegistrationId,id)));result.put("withdrawals",withdrawals.selectList(new LambdaQueryWrapper<WithdrawalRequest>().eq(WithdrawalRequest::getRegistrationId,id)));return result;
 }
 private void release(Long id) {members.update(null,new LambdaUpdateWrapper<RegistrationMember>().eq(RegistrationMember::getRegistrationId,id).set(RegistrationMember::getActiveKey,null));}
 public void recordReview(Registration r,String result,String reason) {history.record("REGISTRATION",r.getId(),"REVIEW",snapshot(r),result,reason);if("REJECTED".equals(result))release(r.getId());}
 private void readable(Registration r){
  role("ADMIN","MAJOR_ADMIN","TEACHER","STUDENT");String role=UserContext.get().getRole();
  if("ADMIN".equals(role))return;
  if("MAJOR_ADMIN".equals(role)){adminScope(r);return;}
  if("STUDENT".equals(role)&&members.selectCount(new LambdaQueryWrapper<RegistrationMember>().eq(RegistrationMember::getRegistrationId,r.getId()).eq(RegistrationMember::getUserId,UserContext.getUserId()).and(w->w.isNotNull(RegistrationMember::getNameSnapshot).or().eq(RegistrationMember::getMemberRole,"LEADER")))>0)return;
  if("TEACHER".equals(role)&&jdbc.queryForObject("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND teacher_id=? AND status IN ('ACTIVE','COMPLETED')",Long.class,r.getId(),UserContext.getUserId())>0)return;
  throw new BusinessException(403,"无权查看报名历史");
 }
 private Registration lock(Long id){var previous=registrations.selectById(id);if(previous==null)throw new BusinessException("报名不存在");competitions.lockById(previous.getCompetitionId());var r=registrations.lockById(id);if(r==null)throw new BusinessException("报名不存在");return r;}
 private void role(String...allowed){if(UserContext.get()==null||!List.of(allowed).contains(UserContext.get().getRole()))throw new BusinessException(403,"无权限执行此操作");}
 private void leader(Registration r){if(!Objects.equals(r.getLeaderId(),UserContext.getUserId()))throw new BusinessException(403,"仅队长可操作");}
 private void adminScope(Registration r){if("MAJOR_ADMIN".equals(UserContext.get().getRole())){User u=users.selectById(UserContext.getUserId());if(u==null||u.getMajorId()==null||!u.getMajorId().equals(r.getMajorId()))throw new BusinessException(403,"无权审核其他专业");}}
 private void reason(String reason){if(!StringUtils.hasText(reason)||reason.length()>1000)throw new BusinessException("请填写原因，最多1000字");}
 private List<RegistrationMember> current(Long id){return members.selectList(new LambdaQueryWrapper<RegistrationMember>().eq(RegistrationMember::getRegistrationId,id).eq(RegistrationMember::getCurrentMember,1));}
 private String displayName(User user){return StringUtils.hasText(user.getRealName())?user.getRealName():user.getUsername();}
 private int cross(List<User> users){return users.stream().map(User::getMajorId).distinct().count()>1?1:0;}
 private String majorName(Long id){var names=jdbc.queryForList("SELECT name FROM sys_major WHERE id=?",String.class,id);return names.isEmpty()?null:names.get(0);}
 private List<User> validate(RegistrationDTO dto,Long leader){
  competitions.validateTeacher(dto.getCompetitionId(),dto.getPreferredTeacherId());
  return memberValidator.validate(dto.getCompetitionId(),leader,dto.getMemberIds());
 }
 private void replaceMembers(Registration r,List<User> people){
  var before=current(r.getId());Set<Long> oldIds=new HashSet<>();before.forEach(m->oldIds.add(m.getUserId()));Set<Long> newIds=new HashSet<>();people.forEach(u->newIds.add(u.getId()));
  for(var old:before)if(!newIds.contains(old.getUserId())){old.setCurrentMember(0);old.setActiveKey(null);members.updateById(old);revokeUser(r,old.getUserId());}
  for(var u:people){if(!oldIds.contains(u.getId())){var m=new RegistrationMember();m.setRegistrationId(r.getId());m.setCompetitionId(r.getCompetitionId());m.setUserId(u.getId());m.setCurrentMember(1);m.setMemberRole(u.getId().equals(r.getLeaderId())?"LEADER":"MEMBER");m.setJoinTime(LocalDateTime.now());members.insert(m);}
   if(!u.getId().equals(r.getLeaderId())&&!accepted(r.getId(),u.getId()))invite(r,u.getId(),false);
  }
 }
 private boolean accepted(Long reg,Long user){var latest=invitations.selectList(new LambdaQueryWrapper<TeamInvitation>().eq(TeamInvitation::getRegistrationId,reg).eq(TeamInvitation::getInviteeId,user).orderByDesc(TeamInvitation::getId).last("LIMIT 1"));return !latest.isEmpty()&&"ACCEPTED".equals(latest.get(0).getStatus());}
 private void invite(Registration r,Long uid,boolean force){
  var pending=invitations.selectCount(new LambdaQueryWrapper<TeamInvitation>().eq(TeamInvitation::getRegistrationId,r.getId()).eq(TeamInvitation::getInviteeId,uid).eq(TeamInvitation::getStatus,"PENDING"));
  if(!force&&pending>0)return;revokeUser(r,uid);var i=new TeamInvitation();i.setRegistrationId(r.getId());i.setInviteeId(uid);i.setInviterId(r.getLeaderId());i.setStatus("PENDING");i.setCreateTime(LocalDateTime.now());invitations.insert(i);messages.sendBusinessMessage(uid,"团队邀请","您收到团队「"+r.getTeamName()+"」邀请，请在我的报名中确认","NOTICE","INVITATION",i.getId());
 }
 private void revokeUser(Registration r,Long uid){invitations.update(null,new LambdaUpdateWrapper<TeamInvitation>().eq(TeamInvitation::getRegistrationId,r.getId()).eq(TeamInvitation::getInviteeId,uid).in(TeamInvitation::getStatus,List.of("PENDING","ACCEPTED")).set(TeamInvitation::getStatus,"REVOKED").set(TeamInvitation::getRespondedTime,LocalDateTime.now()));}
 private void closeInvitations(Long id){invitations.update(null,new LambdaUpdateWrapper<TeamInvitation>().eq(TeamInvitation::getRegistrationId,id).eq(TeamInvitation::getStatus,"PENDING").set(TeamInvitation::getStatus,"REVOKED").set(TeamInvitation::getRespondedTime,LocalDateTime.now()));}
 private void noAward(Long id){if(jdbc.queryForObject("SELECT COUNT(*) FROM award WHERE registration_id=? AND status='APPROVED'",Long.class,id)>0)throw new BusinessException("存在有效获奖记录，不能执行此操作");}
 private Map<String,Object> snapshot(Registration r){Map<String,Object> s=new LinkedHashMap<>();Registration copy=new Registration();org.springframework.beans.BeanUtils.copyProperties(r,copy);s.put("registration",copy);s.put("members",current(r.getId()));return s;}
 private void message(Long uid,Registration r,String title,String content){messages.sendBusinessMessage(uid,title,content,"NOTICE","REGISTRATION",r.getId());}
 private void notifyMembers(Registration r,String title,String content){members.selectList(new LambdaQueryWrapper<RegistrationMember>().eq(RegistrationMember::getRegistrationId,r.getId()).eq(RegistrationMember::getCurrentMember,1).and(w->w.eq(RegistrationMember::getMemberRole,"LEADER").or().isNotNull(RegistrationMember::getNameSnapshot))).stream().map(RegistrationMember::getUserId).distinct().forEach(uid->message(uid,r,title,content));}
}
