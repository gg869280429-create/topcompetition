package com.cie.competition.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cie.competition.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
    User lockById(Long id);
    @org.apache.ibatis.annotations.Select("SELECT id, name FROM sys_major WHERE status = 1 ORDER BY id")
    java.util.List<com.cie.competition.module.user.vo.MajorVO> listMajors();

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM guidance_quota q JOIN competition c ON c.id=q.competition_id WHERE q.teacher_id=#{teacherId} AND c.status NOT IN ('ENDED','CANCELLED')")
    long countOpenTeacherQuotas(Long teacherId);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM guidance g JOIN competition c ON c.id=g.competition_id WHERE g.teacher_id=#{teacherId} AND g.status='ACTIVE' AND c.status NOT IN ('ENDED','CANCELLED')")
    long countOpenGuidance(Long teacherId);
}
