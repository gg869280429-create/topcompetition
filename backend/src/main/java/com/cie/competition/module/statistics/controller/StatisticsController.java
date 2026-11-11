package com.cie.competition.module.statistics.controller;

import com.cie.competition.common.result.Result;
import com.cie.competition.module.statistics.service.StatisticsService;
import com.cie.competition.module.statistics.vo.StatisticsOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<StatisticsOverviewVO> overview() {
        return Result.success(statisticsService.overview());
    }
}
