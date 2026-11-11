package com.cie.competition.module.competition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cie.competition.module.competition.entity.Competition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CompetitionMapper extends BaseMapper<Competition> {

    @Select("SELECT * FROM competition WHERE id=#{id} FOR UPDATE")
    Competition lockById(Long id);

    @Select("SELECT COUNT(*) FROM registration WHERE competition_id=#{id}")
    long countRegistrations(Long id);

    @org.apache.ibatis.annotations.Delete("DELETE FROM guidance_quota WHERE competition_id=#{id}")
    int deleteCompetitionQuotas(Long id);

    @Select("SELECT COUNT(*) FROM sys_user WHERE id=#{id} AND status=1 AND role IN ('ADMIN','MAJOR_ADMIN')")
    long countActiveManager(Long id);

    @Select("SELECT user_id FROM registration_member WHERE competition_id=#{id} UNION SELECT leader_id FROM registration WHERE competition_id=#{id}")
    List<Long> listParticipantIds(Long id);

    /** 查询已有教师指导名额的竞赛 ID 集合。 */
    @Select("SELECT DISTINCT q.competition_id FROM guidance_quota q JOIN sys_user u ON u.id=q.teacher_id WHERE q.quota>0 AND u.role='TEACHER' AND u.status=1")
    List<Long> listCompetitionIdsWithTeacherQuota();

    /** 查询某竞赛的教师指导名额记录数。 */
    @Select("SELECT COUNT(*) FROM guidance_quota q JOIN sys_user u ON u.id=q.teacher_id WHERE q.competition_id=#{competitionId} AND q.quota>0 AND u.role='TEACHER' AND u.status=1")
    Long countTeacherQuota(Long competitionId);
    @Select("SELECT COUNT(*) FROM guidance_quota q JOIN sys_user u ON u.id=q.teacher_id WHERE q.competition_id=#{competitionId} AND q.teacher_id=#{teacherId} AND q.quota>0 AND u.role='TEACHER' AND u.status=1")
    long countEligibleTeacher(Long competitionId, Long teacherId);
}
