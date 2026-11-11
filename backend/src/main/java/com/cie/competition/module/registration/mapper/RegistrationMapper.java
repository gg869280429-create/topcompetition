package com.cie.competition.module.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cie.competition.module.registration.entity.Registration;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM registration WHERE id=#{id} FOR UPDATE")
    Registration lockById(Long id);
}
