package com.cie.competition.module.competition.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 竞赛新增 / 编辑请求对象。
 */
@Data
public class CompetitionDTO {

    private Long majorId;

    @NotBlank(message = "竞赛名称不能为空")
    @Size(max = 128, message = "竞赛名称长度不能超过128个字符")
    private String title;

    @NotBlank(message = "赛制不能为空")
    private String competitionType;

    @NotBlank(message = "竞赛级别不能为空")
    private String level;

    private String category;

    private String description;

    private String organizer;

    private Long coverFileId;

    private Long documentFileId;

    @jakarta.validation.Valid
    @NotNull(message = "请配置指导教师")
    private java.util.List<CompetitionTeacherDTO> teachers;

    private Long managerId;

    @NotNull(message = "报名开始时间不能为空")
    private LocalDateTime registrationStartTime;

    @NotNull(message = "报名截止时间不能为空")
    private LocalDateTime registrationEndTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @NotNull(message = "团队人数上限不能为空")
    @Min(value = 1, message = "团队人数上限至少为1")
    private Integer maxMembers;

    @NotNull(message = "指导教师数量上限不能为空")
    @Min(value = 1, message = "指导教师数量上限至少为1")
    private Integer maxTeachers;

    /** 是否允许跨专业组队：1 是，0 否 */
    private Integer allowCrossMajor;
}
