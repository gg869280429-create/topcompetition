package com.cie.competition.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体，对应 sys_user 表。
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String realName;

    /** 角色：ADMIN / TEACHER / STUDENT */
    private String role;

    /** 所属专业 ID */
    private Long majorId;

    private String email;

    private String phone;

    private String expertise;

    private String biography;

    /** 状态：1 正常，0 禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
