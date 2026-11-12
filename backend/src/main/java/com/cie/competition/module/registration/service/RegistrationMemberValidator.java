package com.cie.competition.module.registration.service;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RegistrationMemberValidator {
    private final CompetitionService competitions;
    private final UserMapper users;

    public List<User> validate(Long competitionId,Long leader,List<Long> memberIds) {
        var competition=competitions.getById(competitionId);
        var others=memberIds==null?List.<Long>of():memberIds;
        if(others.stream().anyMatch(Objects::isNull)||others.contains(leader)||others.stream().distinct().count()!=others.size())throw new BusinessException("成员不能重复、为空或重复包含队长");
        var ids=new ArrayList<Long>();ids.add(leader);ids.addAll(others);
        if(ids.size()>competition.getMaxMembers()||("PERSONAL".equals(competition.getCompetitionType())&&ids.size()!=1))throw new BusinessException("成员人数不符合赛制");
        var people=new ArrayList<User>();
        for(Long id:ids){var user=users.selectById(id);if(user==null||!"STUDENT".equals(user.getRole())||!Integer.valueOf(1).equals(user.getStatus())||user.getMajorId()==null)throw new BusinessException("成员须为已启用且有专业的真实学生");people.add(user);}
        if(people.stream().map(User::getMajorId).distinct().count()>1&&!Integer.valueOf(1).equals(competition.getAllowCrossMajor()))throw new BusinessException("不允许跨专业组队");
        return people;
    }
}
