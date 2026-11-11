package com.cie.competition.module.award.service;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.award.dto.*;
import com.cie.competition.module.award.entity.Award;
import com.cie.competition.module.award.mapper.AwardMapper;
import com.cie.competition.module.file.service.FileService;
import com.cie.competition.module.history.service.BusinessHistoryService;
import com.cie.competition.module.message.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AchievementWorkflowService {
 private final AwardMapper mapper;
 private final AwardService awards;
 private final JdbcTemplate jdbc;
 private final FileService files;
 private final BusinessHistoryService history;
 private final MessageService messages;
 private final ObjectMapper json;

 private boolean manager(){return Set.of("ADMIN","MAJOR_ADMIN").contains(UserContext.get().getRole());}
 private void teacherOrManager(){if(!manager()&&!"TEACHER".equals(UserContext.get().getRole()))throw new BusinessException(403,"无权申报成果");}
 private void scope(Long major){if("MAJOR_ADMIN".equals(UserContext.get().getRole())){Long own=jdbc.queryForObject("SELECT major_id FROM sys_user WHERE id=?",Long.class,UserContext.getUserId());if(own==null||major==null||!own.equals(major))throw new BusinessException(403,"无权操作无归属或其他专业成果");}}
 private Map<String,Object> lockRegistration(Long id, boolean checkScope){
  var initial=jdbc.queryForList("SELECT competition_id FROM registration WHERE id=?",id);if(initial.isEmpty())throw new BusinessException(404,"报名不存在");
  Long competition=((Number)initial.get(0).get("competition_id")).longValue();
  String state=jdbc.queryForObject("SELECT status FROM competition WHERE id=? FOR UPDATE",String.class,competition);
  if("CANCELLED".equals(state))throw new BusinessException("竞赛已取消，不能申报成果");
  var r=jdbc.queryForMap("SELECT * FROM registration WHERE id=? FOR UPDATE",id);
  if(!"APPROVED".equals(r.get("status")))throw new BusinessException("仅已通过且未退赛的报名可申报成果");
  if(checkScope)scope((Long)r.get("major_id"));
  if(!manager()&&jdbc.queryForList("SELECT id FROM guidance WHERE registration_id=? AND teacher_id=? AND status IN ('ACTIVE','COMPLETED') FOR UPDATE",id,UserContext.getUserId()).isEmpty())throw new BusinessException(403,"仅可登记本人指导团队的成果");
  return r;
 }
 private List<Long> distinct(List<Long> ids){if(ids==null)return List.of();if(ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size())throw new BusinessException("人员不能重复或为空");return ids;}
 private void validate(AwardDTO d,boolean submitting,Set<Long> retained){
  if(d.getAchievementName()!=null&&d.getAchievementName().length()>160)throw new BusinessException("成果名称最多160字");
  if(submitting&&(d.getAchievementName()==null||d.getAchievementName().isBlank()||d.getAwardTime()==null||d.getCertificateFileId()==null||d.getRegistrationScreenshotFileId()==null||d.getProcessPhotoFileIds()==null||d.getProcessPhotoFileIds().size()!=2||distinct(d.getProcessPhotoFileIds()).size()!=2||distinct(d.getStudentIds()).isEmpty()||distinct(d.getTeacherIds()).isEmpty()))throw new BusinessException("提交需成果名称、获奖学生和教师、日期、证书、报名截图及恰好2张过程照片");
  if(!Set.of("SPECIAL","FIRST","SECOND","THIRD","EXCELLENT").contains(d.getAwardLevel()))throw new BusinessException("非法奖项等级");
  if(d.getAwardTime()!=null&&d.getAwardTime().isAfter(LocalDateTime.now()))throw new BusinessException("获奖日期不能晚于当前时间");
  if(d.getCertificateFileId()!=null)validateFile(d.getCertificateFileId(),"AWARD_CERTIFICATE",retained);
  if(d.getOfficialListFileId()!=null)validateFile(d.getOfficialListFileId(),"AWARD_OFFICIAL_LIST",retained);
  if(d.getRegistrationScreenshotFileId()!=null)validateFile(d.getRegistrationScreenshotFileId(),"AWARD_REGISTRATION_SCREENSHOT",retained);
  if(d.getProcessPhotoFileIds()!=null){if(d.getProcessPhotoFileIds().size()>2)throw new BusinessException("过程照片最多2张");for(Long file:distinct(d.getProcessPhotoFileIds()))validateFile(file,"AWARD_PROCESS_PHOTO",retained);}
 }
 private void validateFile(Long id,String type,Set<Long> retained){if(retained.contains(id))files.get(id,type);else files.validateAwardMaterial(id,type);}
 private Set<Long> materialIds(Award a){var ids=new HashSet<Long>();if(a==null)return ids;ids.add(a.getCertificateFileId());ids.add(a.getOfficialListFileId());ids.add(a.getRegistrationScreenshotFileId());try{for(var item:json.readTree(Objects.toString(a.getProcessPhotoFileIds(),"[]")))ids.add(item.asLong());}catch(Exception e){throw new BusinessException("过程照片信息无效");}ids.remove(null);return ids;}
 private AwardDTO storedDto(Award a){AwardDTO d=new AwardDTO();d.setRegistrationId(a.getRegistrationId());d.setAchievementName(a.getAchievementName());d.setAwardLevel(a.getAwardLevel());d.setAwardTime(a.getAwardTime());d.setCertificateFileId(a.getCertificateFileId());d.setOfficialListFileId(a.getOfficialListFileId());d.setRegistrationScreenshotFileId(a.getRegistrationScreenshotFileId());try{d.setProcessPhotoFileIds(json.readValue(Objects.toString(a.getProcessPhotoFileIds(),"[]"),new com.fasterxml.jackson.core.type.TypeReference<List<Long>>(){}));}catch(Exception e){throw new BusinessException("请补录过程照片");}d.setStudentIds(jdbc.queryForList("SELECT student_id FROM award_student WHERE award_id=? FOR UPDATE",Long.class,a.getId()));d.setTeacherIds(jdbc.queryForList("SELECT teacher_id FROM award_teacher WHERE award_id=? FOR UPDATE",Long.class,a.getId()));return d;}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 private String fingerprint(AwardDTO d){if(d.getAchievementName()==null||d.getAchievementName().isBlank()||d.getAwardTime()==null)return null;try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((d.getAchievementName().trim().toLowerCase(Locale.ROOT)+"|"+d.getAwardLevel()+"|"+d.getAwardTime().toLocalDate()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 @Transactional
 public Long save(Long id,AwardDTO d,boolean correction){
  teacherOrManager();if(d.getRegistrationId()==null)throw new BusinessException("请选择报名");
  var r=lockRegistration(d.getRegistrationId(),id==null);Award award=id==null?new Award():mapper.lockById(id);
  if(id!=null){if(award==null)throw new BusinessException(404,"成果不存在");awards.getById(id);if(!award.getRegistrationId().equals(d.getRegistrationId()))throw new BusinessException("不能修改成果所属报名");if(!manager()&&!Objects.equals(award.getRegistrantId(),UserContext.getUserId()))throw new BusinessException(403,"仅申报人可修改草稿");
   if(correction){if(!"APPROVED".equals(award.getStatus())||d.getReason()==null||d.getReason().isBlank()||d.getReason().length()>1000)throw new BusinessException("仅已通过成果可更正，需填写原因");}
   else if(!Set.of("DRAFT","REJECTED").contains(award.getStatus()))throw new BusinessException("仅草稿或已驳回成果可编辑，待审核不可修改");
  }
  boolean submitting=correction||!Boolean.TRUE.equals(d.getDraft());validate(d,submitting,materialIds(id==null?null:award));
  String before=id==null?null:encode(Map.of("award",award,"students",jdbc.queryForList("SELECT student_id,name_snapshot,major_id_snapshot,major_name_snapshot FROM award_student WHERE award_id=?",id),"teachers",jdbc.queryForList("SELECT teacher_id,name_snapshot FROM award_teacher WHERE award_id=?",id)));
  award.setCompetitionId(((Number)r.get("competition_id")).longValue());award.setRegistrationId(d.getRegistrationId());if(id==null)award.setMajorId((Long)r.get("major_id"));
  if(id==null){award.setTeamNameSnapshot((String)r.get("team_name"));award.setMajorNameSnapshot((String)r.get("major_name_snapshot"));}
  var roster=jdbc.queryForList("SELECT user_id AS userId,name_snapshot AS name,major_id_snapshot AS majorId,major_name_snapshot AS majorName FROM registration_member WHERE registration_id=? AND current_member=1 ORDER BY id FOR UPDATE",d.getRegistrationId());
  if(!manager()){
   var snapshots=jdbc.queryForList("SELECT members_snapshot FROM guidance WHERE registration_id=? AND teacher_id=? AND status IN ('ACTIVE','COMPLETED') ORDER BY id DESC LIMIT 1 FOR UPDATE",String.class,d.getRegistrationId(),UserContext.getUserId());
   if(!snapshots.isEmpty()&&snapshots.get(0)!=null)try{roster=json.readValue(snapshots.get(0),new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){});}catch(Exception e){throw new BusinessException("指导人员快照无效，请联系管理员");}
  }
  if(id!=null && award.getParticipantsSnapshot()!=null)try{roster=json.readValue(award.getParticipantsSnapshot(),new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){});}catch(Exception e){throw new BusinessException("原团队快照无效");}
  var memberIds=new HashSet<Long>();for(var m:roster)memberIds.add(((Number)m.get("userId")).longValue());
  for(Long student:distinct(d.getStudentIds()))if(!memberIds.contains(student))throw new BusinessException("获奖学生必须属于该团队");
  for(Long teacher:distinct(d.getTeacherIds()))if(jdbc.queryForList("SELECT id FROM guidance WHERE registration_id=? AND teacher_id=? AND status IN ('ACTIVE','COMPLETED') FOR UPDATE",d.getRegistrationId(),teacher).isEmpty())throw new BusinessException("指导教师必须具备该团队有效或已完成指导关系");
  if(!manager()&&!distinct(d.getTeacherIds()).contains(UserContext.getUserId())&&submitting)throw new BusinessException("申报人必须是对应指导教师");
  award.setParticipantsSnapshot(encode(roster));award.setAchievementName(d.getAchievementName());award.setAchievementKey(fingerprint(d));award.setAwardLevel(d.getAwardLevel());award.setAwardTime(d.getAwardTime());award.setCertificateFileId(d.getCertificateFileId());award.setOfficialListFileId(d.getOfficialListFileId());award.setRegistrationScreenshotFileId(d.getRegistrationScreenshotFileId());award.setProcessPhotoFileIds(encode(d.getProcessPhotoFileIds()==null?List.of():d.getProcessPhotoFileIds()));award.setLegacyRecord(0);award.setStatus(submitting?"PENDING":"DRAFT");
  if(id==null){award.setRegistrantId(UserContext.getUserId());try{mapper.insert(award);}catch(org.springframework.dao.DuplicateKeyException e){throw new BusinessException("同一成果已登记，请关联实际获奖人员和教师，勿重复申报");}}
  else{try{mapper.update(award,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Award>().eq(Award::getId,id).set(Award::getReviewerId,null).set(Award::getReviewTime,null).set(Award::getReviewComment,null).set(Award::getOfficialListFileId,d.getOfficialListFileId()).set(Award::getCertificateFileId,d.getCertificateFileId()).set(Award::getRegistrationScreenshotFileId,d.getRegistrationScreenshotFileId()).set(Award::getAwardTime,d.getAwardTime()).set(Award::getAchievementKey,award.getAchievementKey()));}catch(org.springframework.dao.DuplicateKeyException e){throw new BusinessException("同一成果已登记");}}
  var previousTeachers=new HashMap<Long,String>();if(id!=null)for(var t:jdbc.queryForList("SELECT teacher_id,name_snapshot FROM award_teacher WHERE award_id=?",id))previousTeachers.put(((Number)t.get("teacher_id")).longValue(),(String)t.get("name_snapshot"));
  id=award.getId();jdbc.update("DELETE FROM award_student WHERE award_id=?",id);jdbc.update("DELETE FROM award_teacher WHERE award_id=?",id);
  for(Long student:distinct(d.getStudentIds())){var person=roster.stream().filter(m->((Number)m.get("userId")).longValue()==student).findFirst().orElseThrow();jdbc.update("INSERT INTO award_student(award_id,student_id,name_snapshot,major_id_snapshot,major_name_snapshot) VALUES (?,?,?,?,?)",id,student,person.get("name"),person.get("majorId"),person.get("majorName"));}
  for(Long teacher:distinct(d.getTeacherIds())){String name=previousTeachers.containsKey(teacher)?previousTeachers.get(teacher):jdbc.queryForObject("SELECT COALESCE(NULLIF(real_name,''),username) FROM sys_user WHERE id=?",String.class,teacher);jdbc.update("INSERT INTO award_teacher(award_id,teacher_id,name_snapshot) VALUES (?,?,?)",id,teacher,name);}
  history.record("AWARD",id,correction?"CORRECT":submitting?"SUBMIT":"SAVE_DRAFT",before,Map.of("award",award,"students",jdbc.queryForList("SELECT student_id,name_snapshot,major_id_snapshot,major_name_snapshot FROM award_student WHERE award_id=?",id),"teachers",jdbc.queryForList("SELECT teacher_id,name_snapshot FROM award_teacher WHERE award_id=?",id)),d.getReason());
  if(submitting)for(Long reviewer:jdbc.queryForList("SELECT id FROM sys_user WHERE status=1 AND (role='ADMIN' OR (role='MAJOR_ADMIN' AND major_id=?))",Long.class,award.getMajorId()))messages.sendBusinessMessage(reviewer,"成果待审核",award.getAchievementName(),"AUDIT","AWARD",id);
  return id;
 }
 @Transactional public void submit(Long id){teacherOrManager();Award initial=mapper.selectById(id);if(initial==null)throw new BusinessException(404,"成果不存在");lockRegistration(initial.getRegistrationId(),false);Award locked=mapper.lockById(id);awards.getById(id);AwardDTO d=storedDto(locked);d.setDraft(false);save(id,d,false);}
 @Transactional public void review(Long id,AwardReviewDTO d){
  if(!manager())throw new BusinessException(403,"仅管理员可审核");Award initial=mapper.selectById(id);if(initial==null)throw new BusinessException(404,"成果不存在");lockRegistration(initial.getRegistrationId(),false);Award a=mapper.lockById(id);scope(a.getMajorId());
  if(!"PENDING".equals(a.getStatus()))throw new BusinessException("仅待审核成果可审核");
  if(!Set.of("APPROVED","REJECTED").contains(Objects.toString(d.getResult(),"")))throw new BusinessException("非法审核结果");if(d.getComment()!=null&&d.getComment().length()>255)throw new BusinessException("审核意见最多255字");if("REJECTED".equals(d.getResult())&&(d.getComment()==null||d.getComment().isBlank()))throw new BusinessException("驳回必须填写原因");
  if("APPROVED".equals(d.getResult())&&(Integer.valueOf(1).equals(a.getLegacyRecord())||a.getCertificateFileId()==null||a.getRegistrationScreenshotFileId()==null||a.getProcessPhotoFileIds()==null||jdbc.queryForObject("SELECT COUNT(*) FROM award_student WHERE award_id=?",Integer.class,id)==0||jdbc.queryForObject("SELECT COUNT(*) FROM award_teacher WHERE award_id=?",Integer.class,id)==0))throw new BusinessException("请先补录获奖学生、教师及全部必传材料");
  if("APPROVED".equals(d.getResult()))validate(storedDto(a),true,materialIds(a));
  String before=encode(a);a.setStatus(d.getResult());a.setReviewerId(UserContext.getUserId());a.setReviewTime(LocalDateTime.now());a.setReviewComment(d.getComment());mapper.updateById(a);history.record("AWARD",id,"REVIEW",before,a,d.getComment());
  var teachers=jdbc.queryForList("SELECT teacher_id FROM award_teacher WHERE award_id=?",Long.class,id);var receivers=new LinkedHashSet<>(teachers);receivers.addAll(jdbc.queryForList("SELECT student_id FROM award_student WHERE award_id=?",Long.class,id));
  for(Long receiver:receivers)messages.sendBusinessMessage(receiver,"成果审核结果",a.getAchievementName()+"："+a.getStatus()+Objects.toString(d.getComment(),""),"AUDIT","AWARD",id);
  if("APPROVED".equals(a.getStatus()))for(Long teacher:teachers)jdbc.update("INSERT IGNORE INTO award_celebration(award_id,teacher_id) VALUES (?,?)",id,teacher);
 }
 public List<Map<String,Object>> pendingCelebrations(){if(!"TEACHER".equals(UserContext.get().getRole()))throw new BusinessException(403,"仅教师可查询庆祝");return jdbc.queryForList("SELECT a.id,a.achievement_name,a.award_level,a.award_time,a.team_name_snapshot,c.title AS competition_title FROM award_celebration x JOIN award a ON a.id=x.award_id JOIN competition c ON c.id=a.competition_id WHERE x.teacher_id=? AND x.displayed_time IS NULL AND a.status='APPROVED' ORDER BY a.review_time,a.id",UserContext.getUserId());}
 @Transactional public boolean claimCelebration(Long id){if(!"TEACHER".equals(UserContext.get().getRole()))throw new BusinessException(403,"仅教师可领取庆祝");Award a=mapper.lockById(id);if(a==null||!"APPROVED".equals(a.getStatus()))return false;return jdbc.update("UPDATE award_celebration SET displayed_time=NOW() WHERE award_id=? AND teacher_id=? AND displayed_time IS NULL",id,UserContext.getUserId())==1;}
 public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> material(Long id,Long file){var a=awards.getById(id);String type=null;if(Objects.equals(file,a.getCertificateFileId()))type="AWARD_CERTIFICATE";else if(Objects.equals(file,a.getOfficialListFileId()))type="AWARD_OFFICIAL_LIST";else if(Objects.equals(file,a.getRegistrationScreenshotFileId()))type="AWARD_REGISTRATION_SCREENSHOT";else try{for(var value:json.readTree(Objects.toString(a.getProcessPhotoFileIds(),"[]")))if(value.asLong()==file)type="AWARD_PROCESS_PHOTO";}catch(Exception e){throw new BusinessException("材料信息无效");}if(type==null)throw new BusinessException(403,"文件不属于该成果");return files.download(file,type);}
}
