package com.cie.competition.module.team.service;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.history.service.BusinessHistoryService;
import com.cie.competition.module.message.service.MessageService;
import com.cie.competition.module.registration.dto.RegistrationDTO;
import com.cie.competition.module.registration.service.RegistrationWorkflowService;
import com.cie.competition.module.team.dto.TeamCreateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor public class StudentTeamService  {
    private final JdbcTemplate jdbc;
    private final BusinessHistoryService history;
    private final MessageService messages;
    private final CompetitionService competitions;
    private final RegistrationWorkflowService workflow;
    private Long actor() {
        if(UserContext.get()==null||!"STUDENT".equals(UserContext.get().getRole()))throw new BusinessException(403,"仅学生可使用组队功能");
        return UserContext.getUserId();
    }
    private Map<String,Object> team(Long id,boolean lock) {
        actor();
        var list=jdbc.queryForList("SELECT id,name,creator_id AS creatorId,captain_id AS captainId,nominated_captain_id AS nominatedCaptainId,status,create_time AS createTime FROM student_team WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(list.isEmpty())throw new BusinessException(404,"队伍不存在");
        return list.get(0);
    }
    private boolean same(Object id,Long expected) {
        return id instanceof Number n&&n.longValue()==expected;
    }
    private boolean joined(Long team,Long user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM student_team_member WHERE team_id=? AND user_id=? AND status='ACCEPTED'",Long.class,team,user)>0;
    }
    private void accessible(Map<String,Object> team) {
        Long id=((Number)team.get("id")).longValue();
        if(!same(team.get("creatorId"),actor())&&jdbc.queryForObject("SELECT COUNT(*) FROM student_team_member WHERE team_id=? AND user_id=? AND status IN ('INVITED','ACCEPTED')",Long.class,id,actor())==0)throw new BusinessException(403,"只能查看本人参与的队伍");
    }
    private void forming(Map<String,Object> team) {
        if("DISBANDED".equals(team.get("status")))throw new BusinessException("队伍已解散");
    }
    private void creator(Map<String,Object> team) {
        if(!same(team.get("creatorId"),actor()))throw new BusinessException(403,"仅创建人可以管理队伍及指定队长");
    }
    private void student(Long id) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND role='STUDENT' AND status=1",Long.class,id)!=1)throw new BusinessException("只能邀请真实且启用的学生");
    }
    private List<Map<String,Object>> members(Long id) {
        return jdbc.queryForList("SELECT m.user_id AS userId,m.status,m.invited_by AS invitedBy,COALESCE(NULLIF(u.real_name,''),u.username) AS name,u.username,ma.name AS majorName FROM student_team_member m LEFT JOIN sys_user u ON u.id=m.user_id LEFT JOIN sys_major ma ON ma.id=u.major_id WHERE m.team_id=? ORDER BY m.id",id);
    }
    private void resetCaptain(Long id) {
        jdbc.update("UPDATE student_team SET status='FORMING',captain_id=NULL,nominated_captain_id=NULL WHERE id=?",id);
    }
    private void notify(Long id,Long user,String title,String content) {
        messages.sendBusinessMessage(user,title,content,"NOTICE","TEAM",id);
    }
    private void notifyMembers(Long id,String title,String content) {
        for(Long user:jdbc.queryForList("SELECT user_id FROM student_team_member WHERE team_id=? AND status='ACCEPTED'",Long.class,id))notify(id,user,title,content);
    }
    public List<Map<String,Object>> mine() {
        return jdbc.queryForList("SELECT t.id,t.name,t.creator_id AS creatorId,t.captain_id AS captainId,t.nominated_captain_id AS nominatedCaptainId,t.status,COALESCE(NULLIF(u.real_name,''),u.username) AS captainName,(SELECT COUNT(*) FROM student_team_member s WHERE s.team_id=t.id AND s.status='ACCEPTED') AS memberCount FROM student_team t LEFT JOIN sys_user u ON u.id=t.captain_id WHERE t.creator_id=? OR EXISTS(SELECT 1 FROM student_team_member m WHERE m.team_id=t.id AND m.user_id=? AND m.status='ACCEPTED') ORDER BY t.id DESC",actor(),actor());
    }
    public List<Map<String,Object>> invitations() {
        return jdbc.queryForList("SELECT t.id AS teamId,t.name,t.status,COALESCE(NULLIF(u.real_name,''),u.username) AS inviterName FROM student_team_member m JOIN student_team t ON t.id=m.team_id JOIN sys_user u ON u.id=m.invited_by WHERE m.user_id=? AND m.status='INVITED' AND t.status<>'DISBANDED' ORDER BY m.id DESC",actor());
    }
    @Transactional(readOnly=true)  public Map<String,Object> detail(Long id) {
        var team=team(id,false);
        accessible(team);
        team.put("members",members(id));
        team.put("history",history.list("TEAM",id));
        return team;
    }
    @Transactional  public Long create(TeamCreateDTO dto) {
        Long owner=actor();
        student(owner);
        var ids=dto.memberIds();
        if(ids.contains(owner)||new HashSet<>(ids).size()!=ids.size())throw new BusinessException("邀请不能重复或包含本人");
        ids.forEach(this::student);
        jdbc.update("INSERT INTO student_team(name,creator_id) VALUES(?,?)",dto.name().trim(),owner);
        Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO student_team_member(team_id,user_id,invited_by,status,responded_time) VALUES(?,?,?,'ACCEPTED',NOW())",id,owner,owner);
        history.record("TEAM",id,"CREATE",null,Map.of("name",dto.name()),null);
        if(!ids.isEmpty())invite(id,ids);
        return id;
    }
    @Transactional  public void invite(Long id,List<Long> ids) {
        var team=team(id,true);
        forming(team);
        if(!joined(id,actor()))throw new BusinessException(403,"仅已加入成员可以邀请学生");
        if(ids.isEmpty()||ids.contains(actor())||new HashSet<>(ids).size()!=ids.size())throw new BusinessException("请选择不重复的其他学生");
        for(Long user:ids) {
            student(user);
            var state=jdbc.queryForList("SELECT status FROM student_team_member WHERE team_id=? AND user_id=?",String.class,id,user);
            if(!state.isEmpty()&&List.of("ACCEPTED","INVITED").contains(state.get(0)))throw new BusinessException("该学生已经加入或正在等待确认");
            jdbc.update("INSERT INTO student_team_member(team_id,user_id,invited_by,status) VALUES(?,?,?,'INVITED') ON DUPLICATE KEY UPDATE invited_by=VALUES(invited_by),status='INVITED',responded_time=NULL",id,user,actor());
            notify(id,user,"组队邀请","您收到队伍「"+team.get("name")+"」邀请，请在我的组队中接受或拒绝。");
        }
        resetCaptain(id);
        history.record("TEAM",id,"INVITE",team,Map.of("memberIds",ids),null);
    }
    @Transactional  public void respond(Long id,boolean accepted) {
        var team=team(id,true);
        forming(team);
        var own=jdbc.queryForList("SELECT status FROM student_team_member WHERE team_id=? AND user_id=?",String.class,id,actor());
        if(own.isEmpty())throw new BusinessException(403,"仅被邀请学生可以响应");
        if(!"INVITED".equals(own.get(0)))throw new BusinessException("邀请已处理");
        if(accepted)student(actor());
        jdbc.update("UPDATE student_team_member SET status=?,responded_time=NOW() WHERE team_id=? AND user_id=?",accepted?"ACCEPTED":"REJECTED",id,actor());
        resetCaptain(id);
        history.record("TEAM",id,accepted?"ACCEPT":"REJECT",null,Map.of("userId",actor()),null);
        notify(id,((Number)team.get("creatorId")).longValue(),"组队邀请响应",accepted?"学生已接受组队邀请。":"学生已拒绝组队邀请。");
    }
    @Transactional  public void remove(Long id,Long user) {
        var team=team(id,true);
        forming(team);
        if(!same(team.get("creatorId"),actor())&&!actor().equals(user))throw new BusinessException(403,"仅创建人可移除成员，学生仅能退出本人");
        if(same(team.get("creatorId"),user))throw new BusinessException("创建人请通过解散队伍退出");
        var state=jdbc.queryForList("SELECT status FROM student_team_member WHERE team_id=? AND user_id=?",String.class,id,user);
        if(state.isEmpty()||!List.of("ACCEPTED","INVITED").contains(state.get(0)))throw new BusinessException("当前成员不在队伍或邀请已处理");
        String next="ACCEPTED".equals(state.get(0))?"LEFT":"REVOKED";
        jdbc.update("UPDATE student_team_member SET status=?,responded_time=NOW() WHERE team_id=? AND user_id=?",next,id,user);
        resetCaptain(id);
        history.record("TEAM",id,next,Map.of("userId",user,"status",state.get(0)),Map.of("status",next),null);
        notify(id,user,"组队名单变更","您已退出队伍或组队邀请已撤销，已提交报名不受影响。");
        notify(id,((Number)team.get("creatorId")).longValue(),"组队名单变更","组队名单发生变更，请重新确认队长。");
    }
    @Transactional  public void nominate(Long id,Long candidate) {
        var team=team(id,true);
        creator(team);
        forming(team);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM student_team_member WHERE team_id=? AND status='INVITED'",Long.class,id)>0)throw new BusinessException("请先处理全部待确认邀请");
        if(!joined(id,candidate))throw new BusinessException("只能指定已加入队伍的成员为队长");
        student(candidate);
        jdbc.update("UPDATE student_team SET captain_id=NULL,nominated_captain_id=?,status='FORMING' WHERE id=?",candidate,id);
        history.record("TEAM",id,"CAPTAIN_NOMINATED",team,Map.of("candidateId",candidate),null);
        notify(id,candidate,"队长确认","您被指定为队长，请本人确认后再报名；队长可代表当前名单报名多个团队赛。");
    }
    @Transactional  public void confirmCaptain(Long id,boolean accepted) {
        var team=team(id,true);
        forming(team);
        if(!same(team.get("nominatedCaptainId"),actor()))throw new BusinessException(403,"仅被指定的学生可以确认队长");
        if(!joined(id,actor()))throw new BusinessException("您已不在队伍中");
        student(actor());
        jdbc.update("UPDATE student_team SET captain_id=?,nominated_captain_id=NULL,status=? WHERE id=?",accepted?actor():null,accepted?"READY":"FORMING",id);
        history.record("TEAM",id,accepted?"CAPTAIN_CONFIRMED":"CAPTAIN_REJECTED",team,Map.of("candidateId",actor()),null);
        notifyMembers(id,"队长确认结果",accepted?"队长已确认，可使用当前名单报名团队赛。":"被指定学生拒绝担任队长，请重新指定。");
    }
    @Transactional  public void disband(Long id) {
        var team=team(id,true);
        creator(team);
        forming(team);
        notifyMembers(id,"队伍已解散","组队已解散，已提交的竞赛报名及历史名单保留。");
        jdbc.update("UPDATE student_team SET status='DISBANDED',nominated_captain_id=NULL WHERE id=?",id);
        jdbc.update("UPDATE student_team_member SET status='REVOKED',responded_time=NOW() WHERE team_id=? AND status='INVITED'",id);
        history.record("TEAM",id,"DISBANDED",team,Map.of("status","DISBANDED"),null);
    }
    @Transactional  public Long register(RegistrationDTO request) {
        actor();
        var competition=competitions.lockById(request.getCompetitionId());
        var team=team(request.getSourceTeamId(),true);
        forming(team);
        if(!"READY".equals(team.get("status")))throw new BusinessException("请先完成成员邀请并由队长本人确认");
        if(!same(team.get("captainId"),actor()))throw new BusinessException(403,"仅已确认的队长可报名");
        if(!"TEAM".equals(competition.getCompetitionType()))throw new BusinessException("组队只能报名团队赛");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM student_team_member WHERE team_id=? AND status='INVITED'",Long.class,request.getSourceTeamId())>0)throw new BusinessException("仍有未确认成员");
        var available=jdbc.queryForList("SELECT q.quota-(SELECT COUNT(*) FROM guidance g WHERE g.teacher_id=q.teacher_id AND g.competition_id=q.competition_id AND g.status='ACTIVE') FROM guidance_quota q WHERE q.competition_id=? AND q.teacher_id=?",Long.class,request.getCompetitionId(),request.getPreferredTeacherId());
        if(available.isEmpty()||available.get(0)<=0)throw new BusinessException("指导老师剩余名额不足，请刷新后重新选择");
        var dto=new RegistrationDTO();
        dto.setCompetitionId(request.getCompetitionId());
        dto.setPreferredTeacherId(request.getPreferredTeacherId());
        dto.setTeamName(team.get("name").toString());
        dto.setMemberIds(jdbc.queryForList("SELECT user_id FROM student_team_member WHERE team_id=? AND status='ACCEPTED' AND user_id<>? ORDER BY id",Long.class,request.getSourceTeamId(),actor()));
        return workflow.createFromTeam(dto,request.getSourceTeamId());
    }
}
