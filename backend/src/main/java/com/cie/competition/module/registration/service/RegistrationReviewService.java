package com.cie.competition.module.registration.service;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.module.registration.dto.ReviewDTO;
import com.cie.competition.module.guidance.dto.GuidanceAssignDTO;
import com.cie.competition.module.guidance.service.GuidanceService;
import com.cie.competition.module.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RegistrationReviewService {
    private final RegistrationService registrationService;
    private final GuidanceService guidanceService;
    private final MessageService messageService;

    @Transactional
    public void review(Long id, ReviewDTO dto) {
        // 先审核并锁定报名，后续分配或消息失败时统一回滚。
        registrationService.review(id, dto);
        var registration = registrationService.getById(id);
        if ("APPROVED".equals(dto.getResult())) {
            if (dto.getTeacherId() == null) throw new BusinessException("请确认最终指导教师");
            if (registration.getPreferredTeacherId() != null && !Objects.equals(dto.getTeacherId(), registration.getPreferredTeacherId())
                    && !StringUtils.hasText(dto.getComment())) throw new BusinessException("调剂教师时必须填写原因");
            var assign = new GuidanceAssignDTO(); assign.setRegistrationId(id); assign.setTeacherId(dto.getTeacherId());
            assign.setComment(dto.getComment());
            guidanceService.confirmAssignment(assign);
            messageService.sendBusinessMessage(dto.getTeacherId(), "新增指导团队", "团队「" + registration.getTeamName() + "」已分配给您，请在我的指导中查看。", "NOTICE", "REGISTRATION", id);
        } else if (!StringUtils.hasText(dto.getComment())) throw new BusinessException("请填写驳回原因");
        String content = "团队「" + registration.getTeamName() + "」报名" + ("APPROVED".equals(dto.getResult()) ? "已通过，指导教师已确认。" : "已驳回，可修改后重新提交。")
                + (StringUtils.hasText(dto.getComment()) ? "审核意见：" + dto.getComment() : "");
        for (var member : registration.getMembers()) messageService.sendBusinessMessage(member.getUserId(), "报名审核结果", content, "AUDIT", "REGISTRATION", id);
    }
}
