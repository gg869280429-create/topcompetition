package com.cie.competition.module.guidance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.guidance.dto.ParticipationResultDTO;
import com.cie.competition.module.guidance.entity.Guidance;
import com.cie.competition.module.guidance.mapper.GuidanceMapper;
import com.cie.competition.module.guidance.mapper.GuidanceQuotaMapper;
import com.cie.competition.module.history.service.BusinessHistoryService;
import com.cie.competition.module.message.service.MessageService;
import com.cie.competition.module.user.mapper.UserMapper;
import com.cie.competition.module.user.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GuidanceCompletionService {
    private final GuidanceMapper guidances;
    private final GuidanceQuotaMapper quotas;
    private final UserMapper users;
    private final UserService userService;
    private final CompetitionService competitions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final BusinessHistoryService history;
    private final MessageService messages;

    @Transactional
    public void complete(Long id) {
        Guidance before=owned(id);
        competitions.lockById(before.getCompetitionId());
        jdbc.queryForList("SELECT id FROM registration WHERE id=? FOR UPDATE",before.getRegistrationId());
        users.lockById(before.getTeacherId()); quotas.lockQuota(before.getCompetitionId(),before.getTeacherId());
        Guidance current=guidances.selectOne(new LambdaQueryWrapper<Guidance>().eq(Guidance::getId,id).last("FOR UPDATE"));
        if("COMPLETED".equals(current.getStatus()))return;
        if(!"ACTIVE".equals(current.getStatus()))throw new BusinessException("该指导关系已结束，不能完成指导");
        if("CANCELLED".equals(competitions.lockById(current.getCompetitionId()).getStatus()))throw new BusinessException("竞赛已取消");
        confirmRoster(current);
        current.setStatus("COMPLETED"); current.setActiveKey(null); current.setEndedTime(LocalDateTime.now());current.setEndReason("教师确认完成指导");
        if(current.getMembersSnapshot()==null)current.setMembersSnapshot(snapshotMembers(current.getRegistrationId()));
        guidances.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Guidance>().eq(Guidance::getId,id)
                .set(Guidance::getStatus,current.getStatus()).set(Guidance::getActiveKey,null).set(Guidance::getEndedTime,current.getEndedTime())
                .set(Guidance::getEndReason,current.getEndReason()).set(Guidance::getMembersSnapshot,current.getMembersSnapshot()));
        history.record("GUIDANCE",id,"COMPLETED",before,current,"教师确认完成指导");
        for(var member:members(current))messages.sendBusinessMessage(((Number)member.get("userId")).longValue(),"指导已完成","教师已确认完成指导，指导记录与后续成果申报保留。","NOTICE","REGISTRATION",current.getRegistrationId());
    }

    public String snapshotMembers(Long registrationId) {
        var members=jdbc.queryForList("SELECT rm.user_id AS userId,COALESCE(rm.name_snapshot,NULLIF(u.real_name,''),u.username) AS name,COALESCE(rm.major_id_snapshot,u.major_id) AS majorId,COALESCE(rm.major_name_snapshot,m.name) AS majorName FROM registration_member rm LEFT JOIN sys_user u ON u.id=rm.user_id LEFT JOIN sys_major m ON m.id=u.major_id WHERE rm.registration_id=? AND rm.current_member=1 ORDER BY rm.id",registrationId);
        try{return json.writeValueAsString(members);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("指导名单无法保存",e);}
    }

    private List<Map<String,Object>> members(Guidance guidance) {
        String snapshot=guidance.getMembersSnapshot()==null?snapshotMembers(guidance.getRegistrationId()):guidance.getMembersSnapshot();
        try{return json.readValue(snapshot,new TypeReference<List<Map<String,Object>>>(){});}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("指导名单无法读取",e);}
    }

    private Guidance owned(Long id) {
        if(UserContext.get()==null||!"TEACHER".equals(UserContext.get().getRole()))throw new BusinessException(403,"仅教师可登记指导结果");
        Guidance value=guidances.selectById(id);if(value==null)throw new BusinessException(404,"指导记录不存在");
        if(!Objects.equals(value.getTeacherId(),UserContext.getUserId()))throw new BusinessException(403,"仅能操作本人指导记录");
        return value;
    }

    public List<Map<String,Object>> results(Long id) {
        Guidance guidance=owned(id);
        if(!List.of("ACTIVE","COMPLETED").contains(guidance.getStatus()))throw new BusinessException(403,"该指导关系已终止");
        var result=new ArrayList<Map<String,Object>>();
        for(var student:members(guidance)) {
            long studentId=((Number)student.get("userId")).longValue();
            var row=new LinkedHashMap<String,Object>();row.put("studentId",studentId);row.put("name",student.get("name"));row.put("participated",null);row.put("won",null);row.put("remark",null);
            var saved=jdbc.queryForList("SELECT participated,won,remark FROM participation_result WHERE guidance_id=? AND student_id=?",id,studentId);
            if(!saved.isEmpty()){row.put("participated",((Number)saved.get(0).get("participated")).intValue()==1);row.put("won",((Number)saved.get(0).get("won")).intValue()==1);row.put("remark",saved.get(0).get("remark"));}
            result.add(row);
        }
        return result;
    }

    @Transactional
    public void saveResult(Long id,Long studentId,ParticipationResultDTO dto) {
        Guidance initial=owned(id);competitions.lockById(initial.getCompetitionId());
        jdbc.queryForList("SELECT id FROM registration WHERE id=? FOR UPDATE",initial.getRegistrationId());
        Guidance guidance=guidances.selectOne(new LambdaQueryWrapper<Guidance>().eq(Guidance::getId,id).last("FOR UPDATE"));
        if(!List.of("ACTIVE","COMPLETED").contains(guidance.getStatus()))throw new BusinessException(403,"指导关系已终止");
        if("CANCELLED".equals(competitions.lockById(guidance.getCompetitionId()).getStatus()))throw new BusinessException("竞赛已取消，不能补录结果");
        if(!dto.getParticipated()&&dto.getWon())throw new BusinessException("未参赛不能选择获奖");
        var student=members(guidance).stream().filter(s->((Number)s.get("userId")).longValue()==studentId).findFirst().orElseThrow(()->new BusinessException(403,"该学生不在本人指导名单中"));
        if(dto.getParticipated())confirmRoster(guidance);
        var before=jdbc.queryForList("SELECT * FROM participation_result WHERE guidance_id=? AND student_id=?",id,studentId);
        jdbc.update("INSERT INTO participation_result(guidance_id,registration_id,student_id,teacher_id,student_name_snapshot,participated,won,remark) VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE participated=VALUES(participated),won=VALUES(won),remark=VALUES(remark)",id,guidance.getRegistrationId(),studentId,guidance.getTeacherId(),student.get("name"),dto.getParticipated()?1:0,dto.getWon()?1:0,dto.getRemark());
        history.record("GUIDANCE",id,"STUDENT_RESULT",before,Map.of("studentId",studentId,"participated",dto.getParticipated(),"won",dto.getWon()),dto.getRemark());
    }

    @Transactional
    public void saveTeamResult(Long id,ParticipationResultDTO dto) {
        Guidance initial=owned(id);var competition=competitions.lockById(initial.getCompetitionId());
        jdbc.queryForList("SELECT id FROM registration WHERE id=? FOR UPDATE",initial.getRegistrationId());
        var guidance=guidances.selectOne(new LambdaQueryWrapper<Guidance>().eq(Guidance::getId,id).last("FOR UPDATE"));
        if(!"TEAM".equals(competition.getCompetitionType()))throw new BusinessException("仅团队赛可整队登记结果");
        if(!List.of("ACTIVE","COMPLETED").contains(guidance.getStatus()))throw new BusinessException(403,"指导关系已终止");
        if("CANCELLED".equals(competition.getStatus()))throw new BusinessException("竞赛已取消，不能登记结果");
        if(!dto.getParticipated()&&dto.getWon())throw new BusinessException("未参赛不能选择获奖");
        var roster=members(guidance);if(roster.isEmpty())throw new BusinessException("指导名单为空，不能登记结果");
        for(var student:roster)saveResult(id,((Number)student.get("userId")).longValue(),dto);
        history.record("GUIDANCE",id,"TEAM_RESULT",null,Map.of("studentIds",roster.stream().map(s->s.get("userId")).toList(),"participated",dto.getParticipated(),"won",dto.getWon()),dto.getRemark());
    }

    private void confirmRoster(Guidance guidance) {
        var locked=jdbc.queryForObject("SELECT roster_locked_at IS NOT NULL FROM registration WHERE id=?",Boolean.class,guidance.getRegistrationId());
        if(Boolean.TRUE.equals(locked))return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM team_invitation WHERE registration_id=? AND status='PENDING'",Long.class,guidance.getRegistrationId())>0)throw new BusinessException("仍有待确认的成员邀请，请队长先处理后再确认参赛或完成指导");
        jdbc.update("UPDATE registration SET roster_locked_at=NOW(),roster_locked_by=? WHERE id=? AND roster_locked_at IS NULL",guidance.getTeacherId(),guidance.getRegistrationId());
        history.record("REGISTRATION",guidance.getRegistrationId(),"ROSTER_LOCKED",null,Map.of("teacherId",guidance.getTeacherId()),"老师确认参赛或完成指导，锁定名单");
        for(var member:members(guidance))messages.sendBusinessMessage(((Number)member.get("userId")).longValue(),"参赛名单已锁定","指导老师已确认，队长不能再调整队员。","NOTICE","REGISTRATION",guidance.getRegistrationId());
    }

    private void managedTeacher(Long teacherId) {
        var login=UserContext.get();if(login==null||!List.of("ADMIN","MAJOR_ADMIN").contains(login.getRole()))throw new BusinessException(403,"仅管理员可维护教师名额");
        var teacher=userService.getById(teacherId);if(!"TEACHER".equals(teacher.getRole()))throw new BusinessException("教师不存在");
        if("MAJOR_ADMIN".equals(login.getRole())){Long major=userService.getById(login.getUserId()).getMajorId();if(major==null||!major.equals(teacher.getMajorId()))throw new BusinessException(403,"仅能维护本专业教师");}
    }

    public List<Map<String,Object>> teacherQuotas(Long teacherId) {
        managedTeacher(teacherId);
        return jdbc.queryForList("SELECT q.id,q.competition_id AS competitionId,c.title AS competitionTitle,c.status AS competitionStatus,q.quota,(SELECT COUNT(*) FROM guidance g WHERE g.competition_id=q.competition_id AND g.teacher_id=q.teacher_id AND g.status='ACTIVE') AS assigned,q.quota-(SELECT COUNT(*) FROM guidance g WHERE g.competition_id=q.competition_id AND g.teacher_id=q.teacher_id AND g.status='ACTIVE') AS remaining FROM guidance_quota q JOIN competition c ON c.id=q.competition_id WHERE q.teacher_id=? ORDER BY q.id DESC",teacherId);
    }

    public Map<String,Object> myQuotaSummary() {
        if (UserContext.get() == null || !"TEACHER".equals(UserContext.get().getRole())) throw new BusinessException(403,"仅教师可查看本人指导名额");
        return jdbc.queryForMap("SELECT COALESCE(SUM(q.quota),0) AS total, COALESCE(SUM(COALESCE(g.assigned,0)),0) AS assigned, COALESCE(SUM(GREATEST(q.quota-COALESCE(g.assigned,0),0)),0) AS remaining FROM guidance_quota q LEFT JOIN (SELECT competition_id,teacher_id,COUNT(*) AS assigned FROM guidance WHERE status='ACTIVE' GROUP BY competition_id,teacher_id) g ON g.competition_id=q.competition_id AND g.teacher_id=q.teacher_id WHERE q.teacher_id=?",UserContext.getUserId());
    }

    @Transactional
    public void increaseQuota(Long teacherId,Long competitionId,int amount) {
        var competition=competitions.lockById(competitionId);managedTeacher(teacherId);competitions.requireManageable(competitionId);
        if(List.of("ENDED","CANCELLED").contains(competition.getStatus()))throw new BusinessException("已结束或已取消竞赛不能增加名额");
        var teacher=users.lockById(teacherId);if(teacher==null||!Integer.valueOf(1).equals(teacher.getStatus()))throw new BusinessException("教师已停用");
        var quota=quotas.lockQuota(competitionId,teacherId);if(quota==null)throw new BusinessException("请先在竞赛中配置该指导老师");
        if(amount<=0||(long)quota.getQuota()+amount>Integer.MAX_VALUE)throw new BusinessException("新增名额不合法");
        int before=quota.getQuota();quota.setQuota(before+amount);quotas.updateById(quota);
        history.record("GUIDANCE_QUOTA",quota.getId(),"INCREASED",Map.of("quota",before),Map.of("quota",quota.getQuota()),"管理员增加总名额");
        messages.sendBusinessMessage(teacherId,"指导名额增加","竞赛「"+competition.getTitle()+"」指导总名额增加"+amount+"个。","NOTICE","COMPETITION",competitionId);
    }
}
