package com.cie.competition.module.user.service;

import com.cie.competition.module.user.vo.UserInfoVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserService {

    UserInfoVO getById(Long id);

    /**
     * 查询学生列表，供选择团队成员等场景复用。
     */
    List<UserInfoVO> listStudents(String keyword);

    /**
     * 查询教师列表，供师生匹配等场景复用。
     */
    List<UserInfoVO> listTeachers(String keyword);

    /**
     * 查询所有启用用户，供发送消息选择接收人等场景复用。
     */
    List<UserInfoVO> listUsers(String keyword);

    /**
     * 批量查询用户姓名，key 为用户 ID，value 为真实姓名（为空时回退用户名）。
     */
    Map<Long, String> listRealNameMap(Collection<Long> ids);
}
