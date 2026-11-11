package com.cie.competition;

import com.cie.competition.module.registration.dto.RegistrationDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationValidationTest {
    @Test
    void newRegistrationMustChooseOneTeacher() {
        RegistrationDTO dto = new RegistrationDTO();
        dto.setCompetitionId(1L);
        dto.setTeamName("测试团队");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(dto).stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("preferredTeacherId")),
                    "新报名未选择意向教师时必须拒绝提交");
        }
    }
}
