package com.cie.competition.module.statistics.mapper;

import com.cie.competition.module.statistics.vo.NameCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 统计聚合查询，直接基于各业务表做 COUNT/GROUP BY，不依赖其他模块 Mapper。
 */
@Mapper
public interface StatisticsMapper {

    @Select("SELECT COUNT(*) FROM competition c WHERE (#{majorId} IS NULL OR EXISTS (SELECT 1 FROM registration r WHERE r.competition_id=c.id AND r.major_id=#{majorId}) OR EXISTS (SELECT 1 FROM sys_user u WHERE u.id=c.publisher_id AND u.major_id=#{majorId}))")
    long countCompetition(Long majorId);

    @Select("SELECT COUNT(*) FROM registration WHERE status<>'DRAFT' AND leader_name_snapshot IS NOT NULL AND (#{majorId} IS NULL OR major_id=#{majorId})")
    long countRegistration(Long majorId);

    @Select("SELECT COUNT(*) FROM award a JOIN registration r ON r.id=a.registration_id WHERE a.status = 'APPROVED' AND (#{majorId} IS NULL OR a.major_id=#{majorId})")
    long countAward(Long majorId);

    @Select("SELECT COUNT(*) FROM sys_user WHERE role = 'STUDENT' AND status = 1 AND (#{majorId} IS NULL OR major_id=#{majorId})")
    long countStudent(Long majorId);

    @Select("SELECT COUNT(*) FROM sys_user WHERE role = 'TEACHER' AND status = 1 AND (#{majorId} IS NULL OR major_id=#{majorId})")
    long countTeacher(Long majorId);

    @Select("SELECT COUNT(DISTINCT g.registration_id) FROM guidance g JOIN registration r ON r.id=g.registration_id JOIN competition c ON c.id=r.competition_id WHERE g.status = 'ACTIVE' AND r.status='APPROVED' AND c.status<>'CANCELLED' AND (#{majorId} IS NULL OR r.major_id=#{majorId})")
    long countGuidance(Long majorId);

    @Select("SELECT c.status AS name, COUNT(*) AS value FROM competition c WHERE (#{majorId} IS NULL OR EXISTS (SELECT 1 FROM registration r WHERE r.competition_id=c.id AND r.major_id=#{majorId}) OR EXISTS (SELECT 1 FROM sys_user u WHERE u.id=c.publisher_id AND u.major_id=#{majorId})) GROUP BY c.status ORDER BY value DESC")
    List<NameCountVO> countCompetitionByStatus(Long majorId);

    @Select("SELECT status AS name, COUNT(*) AS value FROM registration WHERE status<>'DRAFT' AND leader_name_snapshot IS NOT NULL AND (#{majorId} IS NULL OR major_id=#{majorId}) GROUP BY status ORDER BY value DESC")
    List<NameCountVO> countRegistrationByStatus(Long majorId);

    @Select("SELECT a.award_level AS name, COUNT(*) AS value FROM award a JOIN registration r ON r.id=a.registration_id WHERE a.status = 'APPROVED' AND (#{majorId} IS NULL OR a.major_id=#{majorId}) GROUP BY a.award_level ORDER BY value DESC")
    List<NameCountVO> countAwardByLevel(Long majorId);

    @Select("SELECT COALESCE(r.major_name_snapshot,m.name, '未分配专业') AS name, COUNT(*) AS value FROM registration r LEFT JOIN sys_major m ON r.major_id = m.id WHERE r.status<>'DRAFT' AND r.leader_name_snapshot IS NOT NULL AND (#{majorId} IS NULL OR r.major_id=#{majorId}) GROUP BY r.major_id, COALESCE(r.major_name_snapshot,m.name, '未分配专业') ORDER BY value DESC")
    List<NameCountVO> countRegistrationByMajor(Long majorId);
    @Select("SELECT COUNT(DISTINCT r.competition_id,p.user_id) FROM registration r JOIN business_history h ON h.id=(SELECT MAX(v.id) FROM business_history v WHERE v.biz_type='REGISTRATION' AND v.biz_id=r.id AND v.operation IN ('SUBMIT','MIGRATED_BASELINE')) JOIN JSON_TABLE(CAST(h.after_json AS JSON), '$.members[*]' COLUMNS(user_id BIGINT PATH '$.userId')) p WHERE r.status<>'DRAFT' AND r.leader_name_snapshot IS NOT NULL AND (#{majorId} IS NULL OR r.major_id=#{majorId})")
    long countRegistrationPeople(Long majorId);

    @Select("SELECT COUNT(*) FROM registration r JOIN competition c ON c.id=r.competition_id WHERE r.status='APPROVED' AND c.status<>'CANCELLED' AND (#{majorId} IS NULL OR r.major_id=#{majorId})")
    long countEffectiveRegistration(Long majorId);
}
