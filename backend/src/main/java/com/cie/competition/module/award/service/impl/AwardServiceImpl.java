package com.cie.competition.module.award.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.security.*;
import com.cie.competition.module.award.dto.*;
import com.cie.competition.module.award.entity.Award;
import com.cie.competition.module.award.mapper.AwardMapper;
import com.cie.competition.module.award.service.AwardService;
import com.cie.competition.module.award.vo.AwardVO;
import com.cie.competition.module.registration.service.RegistrationService;
import com.cie.competition.module.registration.vo.RegistrationVO;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.file.service.FileService;
import com.cie.competition.module.history.service.BusinessHistoryService;
import com.cie.competition.module.history.entity.BusinessHistory;
import com.cie.competition.module.message.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AwardServiceImpl implements AwardService {
    private static final Set<String> LEVELS=Set.of("SPECIAL","FIRST","SECOND","THIRD","EXCELLENT");
    private final AwardMapper awardMapper;
    private final RegistrationService registrationService;
    private final CompetitionService competitionService;
    private final FileService fileService;
    private final BusinessHistoryService historyService;
    private final MessageService messageService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PageResult<AwardVO> page(AwardQueryDTO query) {
        var wrapper=new LambdaQueryWrapper<Award>()
                .eq(query.getCompetitionId()!=null,Award::getCompetitionId,query.getCompetitionId())
                .eq(StringUtils.hasText(query.getStatus()),Award::getStatus,query.getStatus())
                .eq(StringUtils.hasText(query.getAwardLevel()),Award::getAwardLevel,query.getAwardLevel())
                .orderByDesc(Award::getCreateTime);
        applyScope(wrapper);
        Page<Award> page=awardMapper.selectPage(new Page<>(query.getCurrent(),query.getSize()),wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(),page.getTotal(),page.getCurrent(),page.getSize());
    }

    public AwardVO getById(Long id) { Award award=getAward(id,false);authorize(award);return toVO(award); }
    public List<BusinessHistory> history(Long id) { getById(id);return historyService.list("AWARD",id); }

    @Transactional
    public Long create(AwardDTO dto) {
        requireManager();validate(dto);
        RegistrationVO initial=registrationService.getById(dto.getRegistrationId());
        competitionService.lockById(initial.getCompetitionId());
        RegistrationVO registration=registrationService.lockForAssignment(dto.getRegistrationId());
        requireActive(registration);
        Long count=awardMapper.selectCount(new LambdaQueryWrapper<Award>().eq(Award::getCompetitionId,registration.getCompetitionId()).eq(Award::getRegistrationId,dto.getRegistrationId()));
        if(count!=null && count>0)throw new BusinessException("该团队在此竞赛已登记获奖，原记录须保留");
        fileService.validateAwardCertificate(dto.getCertificateFileId());
        Award award=new Award();award.setCompetitionId(registration.getCompetitionId());award.setRegistrationId(dto.getRegistrationId());
        award.setRegistrantId(UserContext.getUserId());snapshot(award,registration);assign(award,dto);
        try{awardMapper.insert(award);}catch(DuplicateKeyException e){throw new BusinessException("该团队在此竞赛已登记获奖");}
        recordAndNotify("CREATE",null,award,null);return award.getId();
    }

    @Transactional
    public void update(Long id,AwardDTO dto) { edit(id,dto,false); }
    @Transactional
    public void correct(Long id,AwardDTO dto) { requireReason(dto.getReason());edit(id,dto,true); }
    private void edit(Long id,AwardDTO dto,boolean correction) {
        requireManager();validate(dto);Award award=lock(id);
        if(!award.getRegistrationId().equals(dto.getRegistrationId()))throw new BusinessException("不可变更获奖所属报名");
        if(correction ? !"APPROVED".equals(award.getStatus()) : !Set.of("PENDING","REJECTED").contains(award.getStatus()))
            throw new BusinessException("仅待审或驳回可编辑，已通过请使用更正流程");
        Award before=copy(award);
        if(!Objects.equals(award.getCertificateFileId(),dto.getCertificateFileId()))fileService.validateAwardCertificate(dto.getCertificateFileId());
        else fileService.get(dto.getCertificateFileId(),"AWARD_CERTIFICATE");
        assign(award,dto);award.setReviewerId(null);award.setReviewComment(null);award.setReviewTime(null);
        // MyBatis 默认忽略 null，显式清空本轮审核字段。
        awardMapper.update(award,new LambdaUpdateWrapper<Award>().eq(Award::getId,id).set(Award::getReviewerId,null).set(Award::getReviewComment,null).set(Award::getReviewTime,null));
        recordAndNotify(correction?"CORRECT":"REJECTED".equals(before.getStatus())?"RESUBMIT":"UPDATE",before,award,dto.getReason());
    }

    @Transactional
    public void delete(Long id) {requireManager();authorize(getAward(id,false));throw new BusinessException("获奖记录保留审计历史，请填写原因后撤销");}
    @Transactional
    public void revoke(Long id,String reason) {
        requireManager();requireReason(reason);Award award=lock(id,false);
        if("REVOKED".equals(award.getStatus()))throw new BusinessException("该获奖已撤销");
        Award before=copy(award);award.setStatus("REVOKED");award.setRevocationReason(reason);
        awardMapper.updateById(award);recordAndNotify("REVOKE",before,award,reason);
    }

    @Transactional
    public void review(Long id,AwardReviewDTO dto) {
        if(dto.getComment()!=null&&dto.getComment().length()>255)throw new BusinessException("审核意见最多255字");
        requireManager();
        if(!Set.of("APPROVED","REJECTED").contains(Objects.toString(dto.getResult(),"")))throw new BusinessException("非法审核结果");
        if("REJECTED".equals(dto.getResult()))requireReason(dto.getComment());
        Award award=lock(id);
        if(!"PENDING".equals(award.getStatus()))throw new BusinessException("仅待审核的获奖可审核");
        if(award.getAwardTime()==null || award.getCertificateFileId()==null)throw new BusinessException("请先补齐获奖日期和证明材料");
        fileService.get(award.getCertificateFileId(),"AWARD_CERTIFICATE");
        Award before=copy(award);award.setStatus(dto.getResult());award.setReviewerId(UserContext.getUserId());award.setReviewTime(LocalDateTime.now());award.setReviewComment(dto.getComment());
        awardMapper.updateById(award);recordAndNotify("REVIEW",before,award,dto.getComment());
    }

    private Award lock(Long id) { return lock(id,true); }
    private Award lock(Long id,boolean requireApproved) {
        Award initial=getAward(id,false);authorize(initial);
        competitionService.lockById(initial.getCompetitionId());
        String currentStatus=jdbc.queryForObject("SELECT status FROM registration WHERE id=? FOR UPDATE",String.class,initial.getRegistrationId());
        if(currentStatus==null)throw new BusinessException("报名不存在");
        if(requireApproved&&!"APPROVED".equals(currentStatus))throw new BusinessException("仅已通过且未退赛的报名可登记、修改或审核获奖");
        Award current=getAward(id,true);authorize(current);return current;
    }
    private void validate(AwardDTO dto) {
        if(dto.getRegistrationId()==null || dto.getAwardTime()==null || dto.getCertificateFileId()==null)throw new BusinessException("报名、获奖日期、证明材料均不能为空");
        if(dto.getAwardLevel()==null || !LEVELS.contains(dto.getAwardLevel()))throw new BusinessException("非法奖项等级");
    }
    private void assign(Award award,AwardDTO dto){award.setAwardLevel(dto.getAwardLevel());award.setAwardTime(dto.getAwardTime());award.setCertificateFileId(dto.getCertificateFileId());award.setStatus("PENDING");}
    private void requireActive(RegistrationVO reg){if(!"APPROVED".equals(reg.getStatus()))throw new BusinessException("仅已通过且未退赛的报名可登记、修改或审核获奖");}
    private void requireReason(String reason){if(!StringUtils.hasText(reason)||reason.length()>1000)throw new BusinessException("请填写不超过1000字的原因");}
    private Award copy(Award award){Award copy=new Award();BeanUtils.copyProperties(award,copy);return copy;}
    private Award getAward(Long id,boolean lock){Award award=lock?awardMapper.lockById(id):awardMapper.selectById(id);if(award==null)throw new BusinessException(404,"获奖记录不存在");return award;}
    private LoginUser requireReader(){
        LoginUser user=UserContext.get();
        if(user==null||user.getUserId()==null||user.getRole()==null||!Set.of("ADMIN","MAJOR_ADMIN","STUDENT","TEACHER").contains(user.getRole()))throw new BusinessException(403,"无权限访问获奖记录");
        if("MAJOR_ADMIN".equals(user.getRole())&&awardMapper.selectMajorId(user.getUserId())==null)throw new BusinessException(403,"专业管理员尚未配置专业");
        return user;
    }
    private void requireManager(){String role=requireReader().getRole();if(!Set.of("ADMIN","MAJOR_ADMIN").contains(role))throw new BusinessException(403,"无权限管理获奖记录");}
    private void authorize(Award award){LoginUser user=requireReader();if("TEACHER".equals(user.getRole()) && Integer.valueOf(0).equals(award.getLegacyRecord())){if(jdbc.queryForObject("SELECT COUNT(*) FROM award_teacher WHERE award_id=? AND teacher_id=?",Integer.class,award.getId(),user.getUserId())==0 && !Objects.equals(award.getRegistrantId(),user.getUserId()))throw new BusinessException(403,"无权查看其他教师成果");return;}if("MAJOR_ADMIN".equals(user.getRole())){if(!Objects.equals(awardMapper.selectMajorId(user.getUserId()),award.getMajorId()))throw new BusinessException(403,"无权限查看其他专业获奖");}else if("STUDENT".equals(user.getRole())){if(!participantIds(award).contains(user.getUserId()))throw new BusinessException(403,"无权限查看该获奖");}else if(!"ADMIN".equals(user.getRole()))registrationService.getById(award.getRegistrationId());}
    private void applyScope(LambdaQueryWrapper<Award> wrapper){LoginUser user=requireReader();long id=user.getUserId();switch(user.getRole()){
        case "MAJOR_ADMIN"->wrapper.eq(Award::getMajorId,awardMapper.selectMajorId(id));
        case "STUDENT"->wrapper.eq(Award::getStatus,"APPROVED").apply("((legacy_record=1 AND JSON_CONTAINS(COALESCE(participants_snapshot, '[]'), JSON_OBJECT('userId', {0}))) OR id IN (SELECT award_id FROM award_student WHERE student_id={0}))",id);
        case "TEACHER"->wrapper.apply("(id IN (SELECT award_id FROM award_teacher WHERE teacher_id={0}) OR registrant_id={0} OR (legacy_record=1 AND registration_id IN (SELECT registration_id FROM guidance WHERE status IN ('ACTIVE','COMPLETED') AND teacher_id={0})))",id);
        default->{} }}

    private void snapshot(Award award,RegistrationVO reg){
        award.setMajorId(reg.getMajorId());award.setTeamNameSnapshot(reg.getTeamName());
        List<String> majors=jdbc.queryForList("SELECT major_name_snapshot FROM registration WHERE id=?",String.class,reg.getId());
        award.setMajorNameSnapshot(majors.isEmpty()?null:majors.get(0));
        var members=jdbc.queryForList("SELECT user_id AS userId,name_snapshot AS name,major_id_snapshot AS majorId,major_name_snapshot AS majorName,member_role AS memberRole FROM registration_member WHERE registration_id=? AND current_member=1 ORDER BY id",reg.getId());
        try{award.setParticipantsSnapshot(json.writeValueAsString(members));}catch(JsonProcessingException e){throw new IllegalStateException("参与者快照保存失败",e);}
    }
    private void recordAndNotify(String operation,Award before,Award award,String reason){
        historyService.record("AWARD",award.getId(),operation,before,award,reason);
        Set<Long> receivers=new LinkedHashSet<>(participantIds(award));
        if(Integer.valueOf(0).equals(award.getLegacyRecord()))receivers.addAll(jdbc.queryForList("SELECT teacher_id FROM award_teacher WHERE award_id=?",Long.class,award.getId()));
        else receivers.addAll(jdbc.queryForList("SELECT teacher_id FROM guidance WHERE registration_id=? AND status IN ('ACTIVE','COMPLETED')",Long.class,award.getRegistrationId()));
        for(Long receiver:receivers)messageService.sendBusinessMessage(receiver,"获奖记录更新","获奖记录"+award.getId()+"："+operation+(StringUtils.hasText(reason)?"，原因："+reason:""),"SYSTEM","AWARD",award.getId());
    }
    private Set<Long> participantIds(Award award){
        Set<Long> result=new LinkedHashSet<>();
        if(Integer.valueOf(0).equals(award.getLegacyRecord()))return new LinkedHashSet<>(jdbc.queryForList("SELECT student_id FROM award_student WHERE award_id=?",Long.class,award.getId()));if(award.getParticipantsSnapshot()==null)return result;
        try{for(var person:json.readTree(award.getParticipantsSnapshot()))if(person.hasNonNull("userId"))result.add(person.get("userId").asLong());}
        catch(JsonProcessingException e){throw new IllegalStateException("获奖人员快照无效",e);}
        return result;
    }
    private AwardVO toVO(Award award){
        AwardVO vo=new AwardVO();BeanUtils.copyProperties(award,vo);vo.setTeamName(award.getTeamNameSnapshot());
        List<String> titles=jdbc.queryForList("SELECT title FROM competition WHERE id=?",String.class,award.getCompetitionId());
        if(!titles.isEmpty())vo.setCompetitionTitle(titles.get(0));
        List<String> names=jdbc.queryForList("SELECT leader_name_snapshot FROM registration WHERE id=?",String.class,award.getRegistrationId());
        if(!names.isEmpty())vo.setLeaderName(names.get(0));vo.setStudents(jdbc.queryForList("SELECT student_id AS userId,name_snapshot AS name,major_id_snapshot AS majorId,major_name_snapshot AS majorName FROM award_student WHERE award_id=? ORDER BY id",award.getId()));vo.setTeachers(jdbc.queryForList("SELECT teacher_id AS userId,name_snapshot AS name FROM award_teacher WHERE award_id=? ORDER BY id",award.getId()));return vo;
    }
}
